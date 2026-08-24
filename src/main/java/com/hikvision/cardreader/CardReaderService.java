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

        StringBuilder hexBuilder = new StringBuilder(serialLength * 2);
        for (int i = 0; i < serialLength; i++) {
            int value = result.bySerial[i] & 0xff;
            hexBuilder.insert(0, String.format("%02X", value));
        }

        String uidHex = hexBuilder.toString();
        String uidDecimal = new BigInteger(uidHex, 16).toString(10);
        int cardTypeIndex = result.byCardType & 0xff;
        String cardType = cardTypeIndex < CARD_TYPES.length
                ? CARD_TYPES[cardTypeIndex]
                : "未知类型（" + cardTypeIndex + "）";

        return new CardResult(
                uidHex,
                uidDecimal,
                serialLength,
                cardType,
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
                nullTerminated(device.szDeviceName),
                nullTerminated(device.szSerialNumber),
                device.dwVID,
                device.dwPID
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
        final int uidLength;
        final String cardType;
        final DeviceInfo device;
        final String readAt;

        CardResult(String uidHex, String uidDecimal, int uidLength, String cardType,
                   DeviceInfo device, String readAt) {
            this.uidHex = uidHex;
            this.uidDecimal = uidDecimal;
            this.uidLength = uidLength;
            this.cardType = cardType;
            this.device = device;
            this.readAt = readAt;
        }

        String toJson() {
            return "{\"success\":true"
                    + ",\"uidHex\":\"" + json(uidHex) + "\""
                    + ",\"uidDecimal\":\"" + json(uidDecimal) + "\""
                    + ",\"uidLength\":" + uidLength
                    + ",\"cardType\":\"" + json(cardType) + "\""
                    + ",\"readAt\":\"" + json(readAt) + "\""
                    + ",\"device\":{\"name\":\"" + json(device.name) + "\""
                    + ",\"serialNumber\":\"" + json(device.serialNumber) + "\""
                    + ",\"vid\":" + device.vid
                    + ",\"pid\":" + device.pid + "}}";
        }
    }

    static final class DeviceInfo {
        final String name;
        final String serialNumber;
        final int vid;
        final int pid;

        DeviceInfo(String name, String serialNumber, int vid, int pid) {
            this.name = name;
            this.serialNumber = serialNumber;
            this.vid = vid;
            this.pid = pid;
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
            json.append(",\"device\":{\"name\":\"").append(json(device.name)).append("\"")
                    .append(",\"serialNumber\":\"").append(json(device.serialNumber)).append("\"")
                    .append(",\"vid\":").append(device.vid)
                    .append(",\"pid\":").append(device.pid)
                    .append("}");
        }
        return json.append("}").toString();
    }
}
