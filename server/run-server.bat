@echo off
REM Run the server JAR with optional application property overrides.
REM Usage:
REM   run-server.bat [options]
REM   Example: run-server.bat server.port=8081 chunkedupload.chunk-size=524288

setlocal EnableDelayedExpansion

REM Whitelist of allowed property keys
set "ALLOWED=server.port chunkedupload.chunk-size chunkedupload.inprogress-dir chunkedupload.complete-dir"

REM Check for help first and exit immediately
if /I "%~1"=="--help" (
    echo Usage: run-server.bat [options]
    echo.
    echo Options ^(can be provided as key=value or --key=value^):
    echo.
    echo   server.port=PORT
    echo       Default: 8080
    echo       Purpose: TCP port the Spring Boot application will bind to.
    echo.
    echo   chunkedupload.chunk-size=BYTES
    echo       Default: 524288
    echo       Purpose: Default chunk size ^(in bytes^) used by the server when creating upload sessions.
    echo.
    echo   chunkedupload.inprogress-dir=PATH
    echo       Default: uploads/in-progress
    echo       Purpose: Directory where partial ^(in-progress^) upload .part files are stored.
    echo.
    echo   chunkedupload.complete-dir=PATH
    echo       Default: uploads/complete
    echo       Purpose: Directory where assembled, completed uploads are stored.
    echo.
    echo Special flags:
    echo   --help    Print this help and exit.
    echo   --dry-run Show the resolved java command that would be executed, but do not launch the JVM.
    exit /b 0
)

REM Dry run support
set "DRYRUN=0"
if /I "%~1"=="--dry-run" (
    set "DRYRUN=1"
    shift /1
)

REM Collect allowed args
set "ARGS="
:parse_args
if "%~1"=="" goto after_parse
set "ARG=%~1"
set "CLEAN=%ARG:--=%"
REM Skip arguments that don't contain '=' to avoid processing flags like --help
echo %CLEAN% | findstr /C:"=" >nul
if errorlevel 1 (
    echo Skipping invalid or disallowed property: %ARG%
    goto next_arg
)
for %%K in (%ALLOWED%) do (
    echo %CLEAN% | findstr /B /C:"%%K=" >nul
    if not errorlevel 1 (
        set "ARGS=!ARGS! %ARG%"
        goto next_arg
    )
)
echo Skipping invalid or disallowed property: %ARG%
:next_arg
shift /1
goto parse_args

:after_parse
set "JAR=build\libs\server.jar"
REM Build classpath from all jars in libs plus the main jar
set "CLASSPATH=%JAR%;libs\"
set "JAVA_CMD=java -cp %CLASSPATH% vn.com.fecredit.chunkedupload.UploadApplication !ARGS!"
echo [DEBUG] JAVA_CMD=%JAVA_CMD%

if "%DRYRUN%"=="1" (
    echo Dry run enabled, not launching JVM.
    exit /b 0
)

echo Running: %JAVA_CMD%
%JAVA_CMD%

endlocal