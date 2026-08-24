@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME must point to a 64-bit JDK 17.
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
    echo [ERROR] JDK 17 jpackage was not found.
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found in PATH.
    exit /b 1
)

call mvn clean package
if errorlevel 1 exit /b 1

set "PACKAGE_INPUT=target\package-input"
if exist "%PACKAGE_INPUT%" rmdir /s /q "%PACKAGE_INPUT%"
mkdir "%PACKAGE_INPUT%\sdk"
copy /y "target\card-reader-local-service.jar" "%PACKAGE_INPUT%\" >nul
for %%F in (HCUSBSDK.dll hpr.dll libcrypto-3-x64.dll libssl-3-x64.dll libusb-1.0.dll SystemTransform.dll zlib1.dll) do (
    copy /y "..\lib\%%F" "%PACKAGE_INPUT%\sdk\" >nul || exit /b 1
)

if exist "dist\海康IC读卡器" rmdir /s /q "dist\海康IC读卡器"
"%JAVA_HOME%\bin\jpackage.exe" ^
  --type app-image ^
  --name "海康IC读卡器" ^
  --dest dist ^
  --input "%PACKAGE_INPUT%" ^
  --main-jar card-reader-local-service.jar ^
  --main-class com.hikvision.cardreader.CardReaderWebApplication ^
  --java-options "-Dcardreader.openBrowser=false" ^
  --java-options "-Dcardreader.allowed.origins=*"
if errorlevel 1 exit /b 1

copy /y "install-autostart.bat" "dist\海康IC读卡器\" >nul
copy /y "uninstall-autostart.bat" "dist\海康IC读卡器\" >nul
echo [OK] dist\海康IC读卡器 is ready. Client computers do not need Java.
endlocal
