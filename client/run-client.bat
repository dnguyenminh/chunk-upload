@echo off
REM Executes the client application JAR, handling --help and passing all other arguments.

setlocal

REM Check for --help argument
if /I "%~1"=="--help" (
    echo Usage: run-client.bat [options]
    echo.
    echo Options:
    echo   --filePath=^<path^>          : Required. Path to the file to upload.
    echo   --uploadUrl=^<url^>          : Required. The server's base upload URL (e.g., http://localhost:8080/api/upload).
    echo   --username=^<user^>          : Required. Username for authentication.
    echo   --password=^<pass^>          : Required. Password for authentication.
    echo   --retryTimes=^<num^>         : Optional. Number of retries for failed chunks (default: 3).
    echo   --threadCounts=^<num^>       : Optional. Number of parallel upload threads (default: 4).
    echo   --help                     : Print this help message.
    exit /b 0
)

set "JAR_PATH=build\libs\client.jar;libs\*.jar"

if not exist "%JAR_PATH%" (
    echo Error: Client JAR not found at %JAR_PATH%
    echo Please build the project first by running '..\gradlew.bat build'
    exit /b 1
)

REM Pass all script arguments to the java application
java -jar "%JAR_PATH%" %*

endlocal
