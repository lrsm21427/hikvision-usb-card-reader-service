# 海康 USB IC 读卡器本地服务

基于 Java 17、JNA 和海康 HCUSBSDK 的 Windows 本地读卡服务，为浏览器业务系统提供 USB
读卡器状态检测和 IC 卡 UID 读取能力。

服务仅监听 `127.0.0.1:18080`，不写卡、不保存卡号。读卡结果同时返回十六进制
`uidHex` 和十进制 `uidDecimal`。

## 功能

- 检测本地海康 USB 读卡器连接状态
- 读取普通 IC 卡 UID，并转换为十进制卡号
- 提供本机 HTTP API，便于 Vue 等前端项目调用
- 系统托盘运行状态、启动提示和重复启动保护
- 使用 `jpackage` 生成自带 Java 运行时的 Windows 应用
- 使用 WiX 生成单文件 EXE 安装包

## HTTP API

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| `GET` | `http://127.0.0.1:18080/api/status` | 查询服务及读卡器状态 |
| `POST` | `http://127.0.0.1:18080/api/read-card` | 等待卡片并读取一次 UID |

业务系统通常使用响应中的十进制 `uidDecimal`。

读卡响应同时提供两套 UID 转换结果：

- `uidBytes`、`uidHexRaw`、`uidDecimalRaw`：HCUSBSDK 返回的原始字节顺序。
- `uidHex`、`uidDecimal`：按原项目规则反转字节后的兼容结果，OA 绑定继续使用
  `uidDecimal`。
- `cardTypeCode`、`selectVerifyHex`：SDK 返回的卡片类型代码和选择确认数据。
- `device`：读卡器厂商、型号、序列号、VID/PID、设备类型及协议版本等枚举信息。

## 开发环境

- Windows x64
- 64 位 JDK 17，且 `JAVA_HOME` 已配置
- Maven 3.8+
- 海康 HCUSBSDK 及对应 USB 驱动

项目不提交海康 SDK、DLL、Java Runtime、WiX 和安装包等二进制文件。

请从海康官方 SDK 包获取以下文件，并放在项目同级的 `../lib` 目录：

```text
HCUSBSDK.dll
hpr.dll
libcrypto-3-x64.dll
libssl-3-x64.dll
libusb-1.0.dll
SystemTransform.dll
zlib1.dll
```

发布或再分发海康 SDK 和 DLL 前，请自行确认海康的授权条款。

## 开发启动

```bat
run.bat
```

主类：`com.hikvision.cardreader.CardReaderWebApplication`

## 生成 Windows 应用目录

```bat
package-windows.bat
```

输出目录：

```text
dist/海康IC读卡器/
```

该目录包含 Java 运行时、JNA 和海康 DLL，客户端不需要单独安装 Java。

## 生成单文件 EXE 安装包

先生成应用目录，然后执行：

```bat
package-installer.bat
```

脚本首次运行时会自动下载便携版 WiX 3.14.1，并校验 SHA-256。输出文件：

```text
installer/海康IC读卡器-1.0.5.exe
```

安装包不应提交到源码历史，建议作为 GitHub Release 附件发布。

## 前端调用

前端默认调用：

```text
http://127.0.0.1:18080
```

如需调整，可在前端项目设置：

```text
VITE_CARD_READER_URL=http://127.0.0.1:18080
```

生产环境建议通过 JVM 参数限制允许访问本地服务的网页来源：

```text
-Dcardreader.allowed.origins=https://oa.example.com,https://oa-backup.example.com
```

## 客户端使用

普通用户只需要从 GitHub Releases 下载并安装 `海康IC读卡器-1.0.5.exe`，无需安装
Java、JDK 或 Maven。读卡器无法识别时，请先安装海康 USB 驱动。
