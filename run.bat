@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven was not found in PATH.
    exit /b 1
)
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME must point to a 64-bit JDK 17.
    exit /b 1
)

call mvn package
if errorlevel 1 exit /b 1

"%JAVA_HOME%\bin\java.exe" -Dhcusb.sdk.dir=..\lib -Dcardreader.web.root=web -jar target\card-reader-local-service.jar
endlocal
