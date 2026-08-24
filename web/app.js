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
    cardType: document.querySelector("#cardType"),
    uidLength: document.querySelector("#uidLength"),
    readAt: document.querySelector("#readAt"),
    deviceName: document.querySelector("#deviceName"),
    deviceSerial: document.querySelector("#deviceSerial"),
    resultTitle: document.querySelector("#resultTitle")
};

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
            fields.deviceName.textContent = data.device.name || "USB 读卡器";
            fields.deviceSerial.textContent = `序列号 ${data.device.serialNumber} · VID ${data.device.vid} · PID ${data.device.pid}`;
            operationMessage.classList.remove("error");
            operationMessage.textContent = "连接正常，可以开始读取卡片";
        } else {
            fields.deviceName.textContent = "未连接";
            fields.deviceSerial.textContent = "";
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
    fields.cardType.textContent = data.cardType;
    fields.uidLength.textContent = `${data.uidLength} 字节`;
    fields.readAt.textContent = data.readAt;
    fields.deviceName.textContent = data.device.name || "USB 读卡器";
    fields.deviceSerial.textContent = `序列号 ${data.device.serialNumber} · VID ${data.device.vid} · PID ${data.device.pid}`;
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
