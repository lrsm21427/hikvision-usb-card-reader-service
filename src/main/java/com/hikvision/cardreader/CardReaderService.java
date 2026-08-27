package com.hikvision.cardreader;

import com.sun.jna.Pointer;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CardReaderService implements AutoCloseable {
    private static final String[] CARD_TYPES = {
            "Type-A M1卡", "Type-A CPU卡", "Type-B卡", "125KHz ID卡", "Felica卡", "Desfire卡"
    };

    private final HCUsbSDK sdk = HCUsbSDK.INSTANCE;
    private boolean initialized;
    private int userId = -1;
    private DeviceInfo connectedDevice;

    public synchronized CardResult readOnce() {
        long startedAt = System.nanoTime();
        try {
            return readOnceInternal();
        } catch (CardReaderException error) {
            if (error.errorCode != 262) {
                throw error;
            }
            resetLogin();
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            if (elapsedMillis < 1_500L) {
                return readOnceInternal();
            }
            throw error;
        }
    }

    private CardResult readOnceInternal() {
        ensureConnected();

        HCUsbSDK.USB_WAIT_SECOND wait = new HCUsbSDK.USB_WAIT_SECOND();
        wait.dwSize = wait.size();
        wait.byWait = 5;
        wait.write();

        HCUsbSDK.USB_ACTIVATE_CARD_RES result = new HCUsbSDK.USB_ACTIVATE_CARD_RES();
        result.dwSize = result.size();
        result.write();

        HCUsbSDK.USB_CONFIG_INPUT_INFO input = new HCUsbSDK.USB_CONFIG_INPUT_INFO();
        input.lpInBuffer = wait.getPointer();
        input.dwInBufferSize = wait.size();
        input.write();

        HCUsbSDK.USB_CONFIG_OUTPUT_INFO output = new HCUsbSDK.USB_CONFIG_OUTPUT_INFO();
        output.lpOutBuffer = result.getPointer();
        output.dwOutBufferSize = result.size();
        output.write();

        if (!sdk.USB_GetDeviceConfig(userId, HCUsbSDK.USB_GET_ACTIVATE_CARD, input, output)) {
            int error = sdk.USB_GetLastError();
            if (error == 262) {
                throw new CardReaderException(error,
                        "读取超时。若刚读取过，请先将卡片完全移开 1 秒，再重新贴近读卡区域");
            }
            throw new CardReaderException(error, "读取卡片失败，SDK错误码：" + error);
        }

        result.read();
        int serialLength = result.bySerialLen & 0xff;
        if (serialLength <= 0 || serialLength > result.bySerial.length) {
            throw new CardReaderException(-1, "设备返回了无效的卡片序列号长度");
        }

        String uidHexRaw = bytesToHex(result.bySerial, serialLength, false);
        String uidHex = bytesToHex(result.bySerial, serialLength, true);
        String uidBytes = bytesToSpacedHex(result.bySerial, serialLength);
        String uidDecimalRaw = new BigInteger(uidHexRaw, 16).toString(10);
        String uidDecimal = new BigInteger(uidHex, 16).toString(10);
        int cardTypeIndex = result.byCardType & 0xff;
        String cardType = cardTypeIndex < CARD_TYPES.length
                ? CARD_TYPES[cardTypeIndex]
                : "未知类型（" + cardTypeIndex + "）";
        int selectVerifyLength = result.bySelectVerifyLen & 0xff;
        if (selectVerifyLength > result.bySelectVerify.length) {
            selectVerifyLength = result.bySelectVerify.length;
        }
        String selectVerifyHex = bytesToSpacedHex(result.bySelectVerify, selectVerifyLength);

        return new CardResult(
                uidHex,
                uidDecimal,
                uidHexRaw,
                uidDecimalRaw,
                uidBytes,
                serialLength,
                cardType,
                cardTypeIndex,
                selectVerifyHex,
                selectVerifyLength,
                connectedDevice,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    public synchronized String connectionStatusJson() {
        try {
            ensureInitialized();
            HCUsbSDK.USB_DEVICE_INFO device = findFirstDevice();
            if (device == null) {
                resetLogin();
                connectedDevice = null;
                return connectionJson(false, "未检测到读卡器，请检查 USB 连接", null);
            }

            DeviceInfo detected = toDeviceInfo(device);
            if (connectedDevice != null
                    && !connectedDevice.serialNumber.equals(detected.serialNumber)) {
                resetLogin();
            }
            connectedDevice = detected;
            return connectionJson(true, "读卡器已连接", detected);
        } catch (CardReaderException error) {
            return connectionJson(false, error.getMessage(), null);
        } catch (Throwable error) {
            String message = error.getMessage() == null ? "读卡服务初始化失败" : error.getMessage();
            return connectionJson(false, message, null);
        }
    }

    private void ensureConnected() {
        ensureInitialized();
        HCUsbSDK.USB_DEVICE_INFO device = findFirstDevice();
        if (device == null) {
            resetLogin();
            connectedDevice = null;
            throw new CardReaderException(0, "未发现读卡器，请检查 USB 连接");
        }

        DeviceInfo detected = toDeviceInfo(device);
        if (userId >= 0 && connectedDevice != null
                && connectedDevice.serialNumber.equals(detected.serialNumber)) {
            return;
        }
        resetLogin();

        HCUsbSDK.USB_USER_LOGIN_INFO login = new HCUsbSDK.USB_USER_LOGIN_INFO();
        login.dwSize = login.size();
        login.dwTimeout = 5000;
        login.dwDevIndex = device.dwIndex;
        login.dwVID = device.dwVID;
        login.dwPID = device.dwPID;
        copy("admin".getBytes(StandardCharsets.UTF_8), login.szUserName);
        copy("12345".getBytes(StandardCharsets.UTF_8), login.szPassword);
        copy(device.szSerialNumber, login.szSerialNumber);
        login.write();

        HCUsbSDK.USB_DEVICE_REG_RES registerResult = new HCUsbSDK.USB_DEVICE_REG_RES();
        registerResult.dwSize = registerResult.size();
        registerResult.write();

        userId = sdk.USB_Login(login, registerResult);
        if (userId < 0) {
            throw new CardReaderException(sdk.USB_GetLastError(), "登录读卡器失败，请确认账号和密码");
        }

        connectedDevice = detected;
    }

    private void ensureInitialized() {
        if (!initialized) {
            if (!sdk.USB_Init()) {
                throw new CardReaderException(sdk.USB_GetLastError(), "HCUSBSDK 初始化失败");
            }
            initialized = true;
        }
    }

    private HCUsbSDK.USB_DEVICE_INFO findFirstDevice() {
        int count = sdk.USB_GetDeviceCount();
        if (count <= 0) {
            return null;
        }

        HCUsbSDK.OUT_USB_DEVICE_INFO devices = new HCUsbSDK.OUT_USB_DEVICE_INFO();
        devices.init(count);
        devices.write();
        if (!sdk.USB_EnumDevices(count, devices.getPointer())) {
            throw new CardReaderException(sdk.USB_GetLastError(), "枚举读卡器失败");
        }
        devices.read();
        return devices.struDeviceArr[0];
    }

    private static DeviceInfo toDeviceInfo(HCUsbSDK.USB_DEVICE_INFO device) {
        return new DeviceInfo(
                nullTerminated(device.szManufacturer),
                nullTerminated(device.szDeviceName),
                nullTerminated(device.szSerialNumber),
                nullTerminated(device.szDevicePath),
                device.dwIndex,
                device.dwVID,
                device.dwPID,
                device.byHaveAudio & 0xff,
                device.iColorType & 0xff,
                device.byDeviceType & 0xff,
                device.dwBCD,
                device.byProtocolType & 0xff
        );
    }

    private void resetLogin() {
        if (userId >= 0) {
            sdk.USB_Logout(userId);
            userId = -1;
        }
    }

    private static void copy(byte[] source, byte[] target) {
        System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private static String nullTerminated(byte[] value) {
        int length = 0;
        while (length < value.length && value[length] != 0) {
            length++;
        }
        return new String(value, 0, length, StandardCharsets.UTF_8).trim();
    }

    private static String bytesToHex(byte[] value, int length, boolean reverse) {
        StringBuilder hex = new StringBuilder(length * 2);
        for (int offset = 0; offset < length; offset++) {
            int index = reverse ? length - 1 - offset : offset;
            hex.append(String.format("%02X", value[index] & 0xff));
        }
        return hex.toString();
    }

    private static String bytesToSpacedHex(byte[] value, int length) {
        StringBuilder hex = new StringBuilder(Math.max(0, length * 3 - 1));
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                hex.append(' ');
            }
            hex.append(String.format("%02X", value[index] & 0xff));
        }
        return hex.length() == 0 ? "无" : hex.toString();
    }

    @Override
    public synchronized void close() {
        resetLogin();
        if (initialized) {
            sdk.USB_Cleanup();
            initialized = false;
        }
    }

    public static final class CardResult {
        final String uidHex;
        final String uidDecimal;
        final String uidHexRaw;
        final String uidDecimalRaw;
        final String uidBytes;
        final int uidLength;
        final String cardType;
        final int cardTypeCode;
        final String selectVerifyHex;
        final int selectVerifyLength;
        final DeviceInfo device;
        final String readAt;

        CardResult(String uidHex, String uidDecimal, String uidHexRaw, String uidDecimalRaw,
                   String uidBytes, int uidLength, String cardType, int cardTypeCode,
                   String selectVerifyHex, int selectVerifyLength, DeviceInfo device, String readAt) {
            this.uidHex = uidHex;
            this.uidDecimal = uidDecimal;
            this.uidHexRaw = uidHexRaw;
            this.uidDecimalRaw = uidDecimalRaw;
            this.uidBytes = uidBytes;
            this.uidLength = uidLength;
            this.cardType = cardType;
            this.cardTypeCode = cardTypeCode;
            this.selectVerifyHex = selectVerifyHex;
            this.selectVerifyLength = selectVerifyLength;
            this.device = device;
            this.readAt = readAt;
        }

        String toJson() {
            return "{\"success\":true"
                    + ",\"uidHex\":\"" + json(uidHex) + "\""
                    + ",\"uidDecimal\":\"" + json(uidDecimal) + "\""
                    + ",\"uidHexRaw\":\"" + json(uidHexRaw) + "\""
                    + ",\"uidDecimalRaw\":\"" + json(uidDecimalRaw) + "\""
                    + ",\"uidBytes\":\"" + json(uidBytes) + "\""
                    + ",\"uidLength\":" + uidLength
                    + ",\"cardType\":\"" + json(cardType) + "\""
                    + ",\"cardTypeCode\":" + cardTypeCode
                    + ",\"selectVerifyHex\":\"" + json(selectVerifyHex) + "\""
                    + ",\"selectVerifyLength\":" + selectVerifyLength
                    + ",\"readAt\":\"" + json(readAt) + "\""
                    + ",\"device\":" + device.toJson() + "}";
        }
    }

    static final class DeviceInfo {
        final String manufacturer;
        final String name;
        final String serialNumber;
        final String path;
        final int index;
        final int vid;
        final int pid;
        final int haveAudio;
        final int colorType;
        final int deviceType;
        final int bcd;
        final int protocolType;

        DeviceInfo(String manufacturer, String name, String serialNumber, String path, int index,
                   int vid, int pid, int haveAudio, int colorType, int deviceType, int bcd,
                   int protocolType) {
            this.manufacturer = manufacturer;
            this.name = name;
            this.serialNumber = serialNumber;
            this.path = path;
            this.index = index;
            this.vid = vid;
            this.pid = pid;
            this.haveAudio = haveAudio;
            this.colorType = colorType;
            this.deviceType = deviceType;
            this.bcd = bcd;
            this.protocolType = protocolType;
        }

        String toJson() {
            return "{\"manufacturer\":\"" + json(manufacturer) + "\""
                    + ",\"name\":\"" + json(name) + "\""
                    + ",\"serialNumber\":\"" + json(serialNumber) + "\""
                    + ",\"path\":\"" + json(path) + "\""
                    + ",\"index\":" + Integer.toUnsignedLong(index)
                    + ",\"vid\":" + Integer.toUnsignedLong(vid)
                    + ",\"pid\":" + Integer.toUnsignedLong(pid)
                    + ",\"vidHex\":\"" + String.format("%04X", Integer.toUnsignedLong(vid)) + "\""
                    + ",\"pidHex\":\"" + String.format("%04X", Integer.toUnsignedLong(pid)) + "\""
                    + ",\"haveAudio\":" + haveAudio
                    + ",\"colorType\":" + colorType
                    + ",\"deviceType\":" + deviceType
                    + ",\"bcd\":" + Integer.toUnsignedLong(bcd)
                    + ",\"bcdHex\":\"" + String.format("%08X", Integer.toUnsignedLong(bcd)) + "\""
                    + ",\"protocolType\":" + protocolType + "}";
        }
    }

    public static final class CardReaderException extends RuntimeException {
        final int errorCode;

        CardReaderException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        String toJson() {
            return "{\"success\":false,\"errorCode\":" + errorCode
                    + ",\"message\":\"" + json(getMessage()) + "\"}";
        }
    }

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String connectionJson(boolean connected, String message, DeviceInfo device) {
        StringBuilder json = new StringBuilder()
                .append("{\"success\":true")
                .append(",\"connected\":").append(connected)
                .append(",\"message\":\"").append(json(message)).append("\"");
        if (device != null) {
            json.append(",\"device\":").append(device.toJson());
        }
        return json.append("}").toString();
    }
}
