package com.hikvision.cardreader;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

import java.io.File;
import java.net.URISyntaxException;

public interface HCUsbSDK extends StdCallLibrary {
    int MAX_MANUFACTURE_LEN = 32;
    int MAX_DEVICE_NAME_LEN = 32;
    int MAX_SERIAL_NUMBER_LEN = 48;
    int MAX_USERNAME_LEN = 32;
    int MAX_PASSWORD_LEN = 16;
    int HPR_MAX_PATH = 260;
    int USB_GET_ACTIVATE_CARD = 1004;

    HCUsbSDK INSTANCE = Loader.load();

    final class Loader {
        private Loader() {
        }

        private static HCUsbSDK load() {
            String sdkDir = System.getProperty("hcusb.sdk.dir");
            if (sdkDir == null || sdkDir.trim().isEmpty()) {
                sdkDir = findPackagedSdkDir();
            }
            File library = new File(sdkDir, "HCUSBSDK");
            return Native.load(library.getAbsolutePath(), HCUsbSDK.class);
        }

        private static String findPackagedSdkDir() {
            try {
                File location = new File(HCUsbSDK.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI());
                File appDir = location.isFile() ? location.getParentFile() : location;
                File packagedSdk = new File(appDir, "sdk");
                if (packagedSdk.isDirectory()) {
                    return packagedSdk.getAbsolutePath();
                }
            } catch (URISyntaxException ignored) {
                // 开发环境继续使用 SDK Demo 的默认目录。
            }
            return "..\\lib";
        }
    }

    @Structure.FieldOrder({"dwSize", "dwIndex", "dwVID", "dwPID", "szManufacturer", "szDeviceName",
            "szSerialNumber", "byHaveAudio", "iColorType", "szDevicePath", "byDeviceType", "dwBCD", "byRes"})
    class USB_DEVICE_INFO extends Structure {
        public int dwSize;
        public int dwIndex;
        public int dwVID;
        public int dwPID;
        public byte[] szManufacturer = new byte[MAX_MANUFACTURE_LEN];
        public byte[] szDeviceName = new byte[MAX_DEVICE_NAME_LEN];
        public byte[] szSerialNumber = new byte[MAX_SERIAL_NUMBER_LEN];
        public byte byHaveAudio;
        public byte iColorType;
        public byte[] szDevicePath = new byte[HPR_MAX_PATH];
        public byte byDeviceType;
        public int dwBCD;
        public byte[] byRes = new byte[249];
    }

    @Structure.FieldOrder({"struDeviceArr"})
    class OUT_USB_DEVICE_INFO extends Structure {
        public USB_DEVICE_INFO[] struDeviceArr;

        public void init(int count) {
            struDeviceArr = new USB_DEVICE_INFO[count];
            for (int i = 0; i < count; i++) {
                struDeviceArr[i] = new USB_DEVICE_INFO();
            }
        }
    }

    @Structure.FieldOrder({"dwSize", "dwTimeout", "dwDevIndex", "dwVID", "dwPID", "szUserName", "szPassword",
            "szSerialNumber", "byLoginMode", "byRes2", "dwFd", "byRes"})
    class USB_USER_LOGIN_INFO extends Structure {
        public int dwSize;
        public int dwTimeout;
        public int dwDevIndex;
        public int dwVID;
        public int dwPID;
        public byte[] szUserName = new byte[MAX_USERNAME_LEN];
        public byte[] szPassword = new byte[MAX_PASSWORD_LEN];
        public byte[] szSerialNumber = new byte[MAX_SERIAL_NUMBER_LEN];
        public byte byLoginMode;
        public byte[] byRes2 = new byte[3];
        public int dwFd;
        public byte[] byRes = new byte[248];
    }

    @Structure.FieldOrder({"dwSize", "szDeviceName", "szSerialNumber", "dwSoftwareVersion", "wYear", "byMonth",
            "byDay", "byRetryLoginTimes", "byRes1", "dwSurplusLockTime", "byRes"})
    class USB_DEVICE_REG_RES extends Structure {
        public int dwSize;
        public byte[] szDeviceName = new byte[MAX_DEVICE_NAME_LEN];
        public byte[] szSerialNumber = new byte[MAX_SERIAL_NUMBER_LEN];
        public int dwSoftwareVersion;
        public short wYear;
        public byte byMonth;
        public byte byDay;
        public byte byRetryLoginTimes;
        public byte[] byRes1 = new byte[3];
        public int dwSurplusLockTime;
        public byte[] byRes = new byte[256];
    }

    @Structure.FieldOrder({"dwSize", "byWait", "byRes"})
    class USB_WAIT_SECOND extends Structure {
        public int dwSize;
        public byte byWait;
        public byte[] byRes = new byte[27];
    }

    @Structure.FieldOrder({"dwSize", "byCardType", "bySerialLen", "bySerial", "bySelectVerifyLen", "bySelectVerify",
            "byRes"})
    class USB_ACTIVATE_CARD_RES extends Structure {
        public int dwSize;
        public byte byCardType;
        public byte bySerialLen;
        public byte[] bySerial = new byte[10];
        public byte bySelectVerifyLen;
        public byte[] bySelectVerify = new byte[3];
        public byte[] byRes = new byte[12];
    }

    @Structure.FieldOrder({"lpCondBuffer", "dwCondBufferSize", "lpInBuffer", "dwInBufferSize", "byRes"})
    class USB_CONFIG_INPUT_INFO extends Structure {
        public Pointer lpCondBuffer;
        public int dwCondBufferSize;
        public Pointer lpInBuffer;
        public int dwInBufferSize;
        public byte[] byRes = new byte[48];
    }

    @Structure.FieldOrder({"lpOutBuffer", "dwOutBufferSize", "byRes"})
    class USB_CONFIG_OUTPUT_INFO extends Structure {
        public Pointer lpOutBuffer;
        public int dwOutBufferSize;
        public byte[] byRes = new byte[56];
    }

    boolean USB_Init();

    boolean USB_Cleanup();

    int USB_GetLastError();

    int USB_GetDeviceCount();

    boolean USB_EnumDevices(int count, Pointer deviceInfoList);

    int USB_Login(USB_USER_LOGIN_INFO loginInfo, USB_DEVICE_REG_RES deviceRegResult);

    boolean USB_Logout(int userId);

    boolean USB_GetDeviceConfig(int userId, int command,
                                USB_CONFIG_INPUT_INFO inputInfo,
                                USB_CONFIG_OUTPUT_INFO outputInfo);
}
