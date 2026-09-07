@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
call scripts\build.bat
if errorlevel 1 exit /b 1
python scripts\validate_exact.py
if errorlevel 1 exit /b 1
python scripts\validate_randomized.py
