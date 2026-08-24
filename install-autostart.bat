@echo off
chcp 65001 >nul
setlocal
set "APP_EXE=%~dp0海康IC读卡器.exe"
if not exist "%APP_EXE%" (
    echo [ERROR] 海康IC读卡器.exe was not found.
    pause
    exit /b 1
)
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "HikvisionCardReader" /t REG_SZ /d "\"%APP_EXE%\"" /f >nul
start "" "%APP_EXE%"
echo [OK] The card reader service will start automatically after sign-in.
pause
