@echo off
title Railway Reservation & Route Management System
echo ==================================================================
echo   Compiling Railway Reservation and Route Management System...
echo ==================================================================
if not exist bin mkdir bin

javac -encoding UTF-8 -d bin src\com\railway\dsa\*.java src\com\railway\model\*.java src\com\railway\service\*.java src\com\railway\ui\*.java src\com\railway\web\*.java src\com\railway\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed! Please check Java installation.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [SUCCESS] Compilation complete. Launching application...
echo.
java -cp bin com.railway.Main
pause
