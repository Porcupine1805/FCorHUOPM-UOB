@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
call scripts\run_check.bat
if errorlevel 1 exit /b 1
call scripts\run_smoke.bat
echo.
echo Correctness and smoke checks finished.
echo The full selected-9 journal run is long; use scripts\run_selected9.bat
