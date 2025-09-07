@echo off
setlocal EnableDelayedExpansion
REM Initialize variables
set "filePath="
set "uploadUrl="
set "username="
set "password="
set "retryTimes=3"
set "threadCounts=4"
set "missing="

REM Show help for no args or --help
if "%~1"=="" goto :missing_args
if /I "%~1"=="--help" goto :show_help

:parse_args
if "%~1"=="" goto :check_args
set "arg=%~1"
echo Debugging: Processing arg=[%arg%]
REM Check if the argument is a key (starts with --)
if "!arg:~0,2!"=="--" (
    set "key=!arg!"
    set "value="
    REM Check if the key has a value attached (contains =)
    echo Debugging: Checking if arg [%arg%] contains =
    echo !arg! | findstr "=" >nul
    if errorlevel 1 (
        REM No =, value is the next argument
        echo Debugging: No = found, using next arg as value
        if not "%~2"=="" (
            set "value=%~2"
            set "key=!key:--=!"
            shift
        )
    ) else (
        REM Value is after = in the same argument
        echo Debugging: = found, extracting value
        for /f "tokens=1,2 delims==" %%a in ("!arg!") do (
            set "key=%%a"
            set "value=%%b"
        )
        set "key=!key:--=!"
        set "value=!value:"=!"
        echo Debugging: Extracted key=[!key!], value=[!value!]
    )
    REM Assign the value to the corresponding variable
    if "!key!"=="filePath" set "filePath=!value!" && echo Debugging: Set filePath=[!filePath!]
    if "!key!"=="uploadUrl" set "uploadUrl=!value!" && echo Debugging: Set uploadUrl=[!uploadUrl!]
    if "!key!"=="username" set "username=!value!" && echo Debugging: Set username=[!username!]
    if "!key!"=="password" set "password=!value!" && echo Debugging: Set password=[!password!]
    if "!key!"=="retryTimes" set "retryTimes=!value!" && echo Debugging: Set retryTimes=[!retryTimes!]
    if "!key!"=="threadCounts" set "threadCounts=!value!" && echo Debugging: Set threadCounts=[!threadCounts!]
)
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
echo RetryTimes: [%retryTimes%]
echo ThreadCounts: [%threadCounts%]
echo.

REM Check for missing required arguments
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
        goto :client_jar_found
    )
)
if not defined JAR_PATH (
    if exist "build\libs\client.jar" (
        REM Fallback to plain client.jar
        set "JAR_PATH=build\libs\client.jar"
        goto :client_jar_found
    )
)
:client_jar_found
if not defined JAR_PATH (
    echo Error: Client JAR not found in build\libs\
    echo Please build the project first by running '..\gradlew.bat build'
    exit /b 1
)

REM Execute JAR with all arguments
java -jar "%JAR_PATH%" --filePath="%filePath%" --uploadUrl="%uploadUrl%" --username="%username%" --password="%password%" --retryTimes=%retryTimes% --threadCounts=%threadCounts%
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
echo   --help                      : Print this help message.
exit /b 1
