@echo off
chcp 65001 >nul
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "HikvisionCardReader" /f >nul 2>nul
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "CardReaderService" /f >nul 2>nul
echo [OK] Automatic startup was removed.
pause
