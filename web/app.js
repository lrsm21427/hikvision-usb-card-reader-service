const readButton = document.querySelector("#readButton");
const readerCard = document.querySelector(".reader-card");
const resultPanel = document.querySelector("#resultPanel");
const operationMessage = document.querySelector("#operationMessage");
const serviceStatus = document.querySelector("#serviceStatus");
const statusText = document.querySelector("#statusText");
const statusCheckButton = document.querySelector("#statusCheckButton");
let deviceConnected = false;

const fields = {
    uidDecimal: document.querySelector("#uidDecimal"),
    uidHex: document.querySelector("#uidHex"),
    uidBytes: document.querySelector("#uidBytes"),
    uidHexRaw: document.querySelector("#uidHexRaw"),
    uidDecimalRaw: document.querySelector("#uidDecimalRaw"),
    cardType: document.querySelector("#cardType"),
    cardTypeCode: document.querySelector("#cardTypeCode"),
    uidLength: document.querySelector("#uidLength"),
    selectVerifyHex: document.querySelector("#selectVerifyHex"),
    selectVerifyLength: document.querySelector("#selectVerifyLength"),
    readAt: document.querySelector("#readAt"),
    deviceName: document.querySelector("#deviceName"),
    deviceSerial: document.querySelector("#deviceSerial"),
    deviceManufacturer: document.querySelector("#deviceManufacturer"),
    deviceIndex: document.querySelector("#deviceIndex"),
    deviceVidPid: document.querySelector("#deviceVidPid"),
    deviceType: document.querySelector("#deviceType"),
    deviceProtocol: document.querySelector("#deviceProtocol"),
    deviceBcd: document.querySelector("#deviceBcd"),
    deviceAudio: document.querySelector("#deviceAudio"),
    deviceColorType: document.querySelector("#deviceColorType"),
    devicePath: document.querySelector("#devicePath"),
    resultTitle: document.querySelector("#resultTitle")
};

function showDevice(device) {
    if (!device) {
        fields.deviceName.textContent = "未连接";
        fields.deviceSerial.textContent = "—";
        fields.deviceManufacturer.textContent = "—";
        fields.deviceIndex.textContent = "—";
        fields.deviceVidPid.textContent = "—";
        fields.deviceType.textContent = "—";
        fields.deviceProtocol.textContent = "—";
        fields.deviceBcd.textContent = "—";
        fields.deviceAudio.textContent = "—";
        fields.deviceColorType.textContent = "—";
        fields.devicePath.textContent = "—";
        return;
    }

    fields.deviceName.textContent = device.name || "USB 读卡器";
    fields.deviceSerial.textContent = device.serialNumber || "—";
    fields.deviceManufacturer.textContent = device.manufacturer || "—";
    fields.deviceIndex.textContent = String(device.index ?? "—");
    fields.deviceVidPid.textContent = `${device.vidHex || "—"} / ${device.pidHex || "—"}（${device.vid ?? "—"} / ${device.pid ?? "—"}）`;
    fields.deviceType.textContent = String(device.deviceType ?? "—");
    fields.deviceProtocol.textContent = String(device.protocolType ?? "—");
    fields.deviceBcd.textContent = `${device.bcdHex || "—"}（${device.bcd ?? "—"}）`;
    fields.deviceAudio.textContent = device.haveAudio === 1 ? "是（1）" : `否（${device.haveAudio ?? "—"}）`;
    fields.deviceColorType.textContent = String(device.colorType ?? "—");
    fields.devicePath.textContent = device.path || "—";
}

async function checkStatus() {
    statusCheckButton.disabled = true;
    statusText.textContent = "正在检测读卡器";
    serviceStatus.classList.remove("ready", "disconnected");
    try {
        const response = await fetch("/api/status", {cache: "no-store"});
        const data = await response.json();
        deviceConnected = Boolean(data.connected);
        serviceStatus.classList.toggle("ready", deviceConnected);
        serviceStatus.classList.toggle("disconnected", !deviceConnected);
        statusText.textContent = data.message || (deviceConnected ? "读卡器已连接" : "未检测到读卡器");
        if (data.device) {
            showDevice(data.device);
            operationMessage.classList.remove("error");
            operationMessage.textContent = "连接正常，可以开始读取卡片";
        } else {
            showDevice(null);
            operationMessage.classList.add("error");
            operationMessage.textContent = data.message || "请连接读卡器后重新检测";
        }
    } catch (error) {
        deviceConnected = false;
        serviceStatus.classList.add("disconnected");
        statusText.textContent = "本地服务未连接";
        operationMessage.classList.add("error");
        operationMessage.textContent = "无法连接本地读卡服务";
    } finally {
        statusCheckButton.disabled = false;
        setReading(false);
    }
}

function setReading(reading) {
    readButton.disabled = reading || !deviceConnected;
    readerCard.classList.toggle("is-reading", reading);
}

function showResult(data) {
    fields.uidDecimal.textContent = data.uidDecimal;
    fields.uidHex.textContent = data.uidHex;
    fields.uidBytes.textContent = data.uidBytes;
    fields.uidHexRaw.textContent = data.uidHexRaw;
    fields.uidDecimalRaw.textContent = data.uidDecimalRaw;
    fields.cardType.textContent = data.cardType;
    fields.cardTypeCode.textContent = String(data.cardTypeCode);
    fields.uidLength.textContent = `${data.uidLength} 字节`;
    fields.selectVerifyHex.textContent = data.selectVerifyHex || "无";
    fields.selectVerifyLength.textContent = `${data.selectVerifyLength} 字节`;
    fields.readAt.textContent = data.readAt;
    showDevice(data.device);
    fields.resultTitle.textContent = "读取成功";
    resultPanel.classList.add("has-result");
}

readButton.addEventListener("click", async () => {
    setReading(true);
    operationMessage.classList.remove("error");
    operationMessage.textContent = "请将卡片贴近读卡区域并保持不动";

    try {
        const response = await fetch("/api/read-card", {
            method: "POST",
            headers: {"Accept": "application/json"}
        });
        const data = await response.json();
        if (!response.ok || !data.success) {
            throw new Error(data.message || "读取失败");
        }
        showResult(data);
        operationMessage.textContent = "本次读取完成，可以移开卡片";
    } catch (error) {
        operationMessage.classList.add("error");
        operationMessage.textContent = error.message || "读取失败，请重试";
    } finally {
        setReading(false);
    }
});

statusCheckButton.addEventListener("click", checkStatus);
checkStatus();
