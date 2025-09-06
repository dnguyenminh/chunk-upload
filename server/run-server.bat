@echo off
REM Run the server JAR with optional application property overrides.
REM Usage:
REM   run-server.bat [options]
REM   Example: run-server.bat server.port=8081 chunkedupload.chunk-size=524288

setlocal EnableDelayedExpansion

REM Whitelist of allowed property keys
set "ALLOWED=server.port chunkedupload.chunk-size chunkedupload.inprogress-dir chunkedupload.complete-dir"

REM Check for help first and exit immediately
if "%~1"=="--help" goto show_help

REM Dry run support
set "DRYRUN=0"
if /I "%~1"=="--dry-run" (
    set "DRYRUN=1"
    shift /1
)

REM Collect allowed args and format them for Spring Boot (--key=value)
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
        set "ARGS=!ARGS! --%CLEAN%"
        goto next_arg
    )
)
echo Skipping invalid or disallowed property: %ARG%
:next_arg
shift /1
goto parse_args

:after_parse
REM Find the server JAR file (with or without version)
set "JAR_PATH="

REM Try versioned JARs first
if exist "build\libs\server-*.jar" (
    for %%f in ("build\libs\server-*.jar") do (
        set "JAR_PATH=%%f"
        goto found_jar
    )
)

REM Fallback to plain server.jar
if exist "build\libs\server.jar" (
    set "JAR_PATH=build\libs\server.jar"
    goto found_jar
)

REM Fallback to server-plain.jar
if exist "build\libs\server-plain.jar" (
    set "JAR_PATH=build\libs\server-plain.jar"
    goto found_jar
)

REM No JAR found
echo Error: Server JAR not found in build\libs\
echo Please build the project first by running '..\gradlew.bat build'
exit /b 1

:found_jar

set "JAVA_CMD=java -jar !JAR_PATH! !ARGS!"

if "%DRYRUN%"=="1" (
    echo Dry run enabled. Command that would be executed:
    echo !JAVA_CMD!
    exit /b 0
)

echo Running: !JAVA_CMD!
!JAVA_CMD!

endlocal

:show_help
echo Usage: run-server.bat [options]
echo.
echo Options (can be provided as key=value or --key=value):
echo.
echo   server.port=PORT
echo       Default: 8080
echo       Purpose: TCP port the Spring Boot application will bind to.
echo.
echo   chunkedupload.chunk-size=BYTES
echo       Default: 524288
echo       Purpose: Default chunk size (in bytes) used by the server when creating upload sessions.
echo.
echo   chunkedupload.inprogress-dir=PATH
echo       Default: uploads/in-progress
echo       Purpose: Directory where partial (in-progress) upload .part files are stored.
echo.
echo   chunkedupload.complete-dir=PATH
echo       Default: uploads/complete
echo       Purpose: Directory where assembled, completed uploads are stored.
echo.
echo Special flags:
echo   --help    Print this help and exit.
echo   --dry-run Show the resolved java command that would be executed, but do not launch the JVM.
exit /b 1
