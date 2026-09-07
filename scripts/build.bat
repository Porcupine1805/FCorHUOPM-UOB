@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
javac --release 17 -encoding UTF-8 -d build\classes src\main\java\org\fcorhuopm\*.java
if errorlevel 1 exit /b 1
echo Built classes in build\classes
