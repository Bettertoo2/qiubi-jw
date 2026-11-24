@echo off
chcp 65001 >nul
if not defined JAVA_HOME if exist "D:\microsoft jdk" set "JAVA_HOME=D:\microsoft jdk"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
if exist "%~dp0app\build\outputs\apk\debug\app-debug.apk" del /f /q "%~dp0app\build\outputs\apk\debug\app-debug.apk"
call "%~dp0gradlew.bat" testDebugUnitTest assembleDebug --no-daemon
if %errorlevel% neq 0 (
  echo Build failed with exit code %errorlevel%
  exit /b %errorlevel%
)
echo.
echo APK ready at: %~dp0app\build\outputs\apk\debug\app-debug.apk
