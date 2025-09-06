@echo off
setlocal

REM Initialize variables
set "filePath="
set "uploadUrl="
set "username="
set "password="
set "missing="

REM Show help for no args or --help
if "%~1"=="" goto :missing_args
if /I "%~1"=="--help" goto :show_help

:parse_args
if "%~1"=="" goto :check_args
set "arg=%~1"
if "%arg:~0,11%"=="--filePath=" set "filePath=%arg:~11%"
if "%arg:~0,11%"=="--uploadUrl=" set "uploadUrl=%arg:~11%"
if "%arg:~0,11%"=="--username=" set "username=%arg:~11%"
if "%arg:~0,11%"=="--password=" set "password=%arg:~11%"
shift
goto :parse_args

:check_args
REM Debug: Show captured values
echo.
echo Parsed arguments:
echo FilePath: [%filePath%]
echo UploadURL: [%uploadUrl%]
echo Username: [%username%]
echo Password: [%password%]
echo.

REM Check for missing arguments
if not defined filePath set "missing=%missing% --filePath"
if not defined uploadUrl set "missing=%missing% --uploadUrl"
if not defined username set "missing=%missing% --username"
if not defined password set "missing=%missing% --password"

if defined missing (
    echo Error: Missing required arguments:%missing%
    echo.
    goto :show_help
)

REM Find the client JAR file (with or without version)
set "JAR_PATH="
if exist "build\libs\client-*.jar" (
    REM Use the first versioned JAR found
    for %%f in ("build\libs\client-*.jar") do (
        set "JAR_PATH=%%f"
        goto client_jar_found
    )
)
if not defined JAR_PATH (
    if exist "build\libs\client.jar" (
        REM Fallback to plain client.jar
        set "JAR_PATH=build\libs\client.jar"
        goto client_jar_found
    )
)
:client_jar_found
if not defined JAR_PATH (
    echo Error: Client JAR not found in build\libs\
    echo Please build the project first by running '..\gradlew.bat build'
    exit /b 1
)

REM Execute JAR with all arguments
java -jar "%JAR_PATH%" %*
exit /b 0

:missing_args
echo Error: No arguments provided. Required arguments: --filePath, --uploadUrl, --username, --password
echo.
goto :show_help

:show_help
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
exit /b 1
