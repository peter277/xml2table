@echo off
SETLOCAL

REM Determine the directory of the script
set SCRIPT_DIR=%~dp0

REM Remove trailing backslash if present
if "%SCRIPT_DIR:~-1%"=="\" set SCRIPT_DIR=%SCRIPT_DIR:~0,-1%

REM Set the module directory relative to the script directory
set MODULE_DIR=%SCRIPT_DIR%\modules

REM Execute the Java module with all passed arguments
java --module-path "%MODULE_DIR%" --module com.github.peter277.xml2table/com.github.peter277.xml2table.Main %*

ENDLOCAL
