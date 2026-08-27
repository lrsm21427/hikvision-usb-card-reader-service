@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JPACKAGE_EXE=%JAVA_HOME%\bin\jpackage.exe"
set "WIX_DIR=%CD%\tools\wix314"
set "APP_IMAGE=%CD%\dist\海康IC读卡器"
set "INSTALLER_DIR=%CD%\installer"
set "INSTALLER_EXE=%INSTALLER_DIR%\海康IC读卡器-1.0.5.exe"

if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME must point to a 64-bit JDK 17.
    exit /b 1
)
if not exist "%JPACKAGE_EXE%" (
    echo [ERROR] JDK 17 jpackage was not found.
    exit /b 1
)
if not exist "%WIX_DIR%\candle.exe" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%CD%\setup-wix.ps1"
    if errorlevel 1 exit /b 1
)
if not exist "%APP_IMAGE%\海康IC读卡器.exe" (
    echo [ERROR] Run package-windows.bat first.
    exit /b 1
)

if not exist "%INSTALLER_DIR%" mkdir "%INSTALLER_DIR%"
if exist "%INSTALLER_EXE%" del /f /q "%INSTALLER_EXE%"
set "PATH=%WIX_DIR%;%PATH%"

"%JPACKAGE_EXE%" ^
  --type exe ^
  --name "海康IC读卡器" ^
  --app-version 1.0.5 ^
  --vendor Yaxin ^
  --description "海康 USB 读卡器本地服务" ^
  --app-image "%APP_IMAGE%" ^
  --dest "%INSTALLER_DIR%" ^
  --win-per-user-install ^
  --win-dir-chooser ^
  --win-menu ^
  --win-menu-group "亚新门禁工具" ^
  --win-shortcut ^
  --win-upgrade-uuid "d9b0b5a2-e9b6-4a0b-8f6c-1ea883693ed1"

if errorlevel 1 exit /b 1
echo [OK] %INSTALLER_EXE%
endlocal
