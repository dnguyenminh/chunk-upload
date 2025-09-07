@echo off
REM Run the server JAR with optional application property overrides.
REM Usage:
REM run-server.bat [options]
REM Example: run-server.bat server.port=8081 chunkedupload.chunk-size=524288

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
REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "JAR_PATH="
REM Remove trailing backslash if present
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
REM Try to find JAR files relative to script directory
REM Prefer Spring Boot executable JAR (server-*.jar) over plain JAR (server-plain-*.jar)
if exist "%SCRIPT_DIR%\build\libs\server-*.jar" (
    for %%f in ("%SCRIPT_DIR%\build\libs\server-*.jar") do (
        set "JAR_PATH=%%f"
        goto found_jar
    )
)
REM If not found relative to script, try current directory
if not defined JAR_PATH (
    if exist "build\libs\server-*.jar" (
        for %%f in ("build\libs\server-*.jar") do (
            set "JAR_PATH=%%f"
            goto found_jar
        )
    )
)
REM If no executable JAR found, try plain server.jar (script dir)
if not defined JAR_PATH (
    if exist "%SCRIPT_DIR%\build\libs\server.jar" (
        set "JAR_PATH=%SCRIPT_DIR%\build\libs\server.jar"
        goto found_jar
    )
)
REM Try plain server.jar (current dir)
if not defined JAR_PATH (
    if exist "build\libs\server.jar" (
        set "JAR_PATH=build\libs\server.jar"
        goto found_jar
    )
)
REM Last resort: server-plain.jar (script dir)
if not defined JAR_PATH (
    if exist "%SCRIPT_DIR%\build\libs\server-plain.jar" (
        set "JAR_PATH=%SCRIPT_DIR%\build\libs\server-plain.jar"
        goto found_jar
    )
)
REM Last resort: server-plain.jar (current dir)
if not defined JAR_PATH (
    if exist "build\libs\server-plain.jar" (
        set "JAR_PATH=build\libs\server-plain.jar"
        goto found_jar
    )
)
REM No JAR found
echo Error: Server JAR not found in the following locations:
echo - %SCRIPT_DIR%\build\libs\
echo - %CD%\build\libs\
echo Please ensure you have built the project by running 'gradlew build'
echo or that the JAR files are in the expected location.
exit /b 1
:found_jar
REM Build CLASSPATH with the found JAR and all libs/*.jar files
set "CLASSPATH=!JAR_PATH!"
for %%f in (.\libs\*.jar) do set "CLASSPATH=!CLASSPATH!;%%f"
REM Determine if it's a Spring Boot executable JAR (check for Spring Boot loader)
set "IS_SPRING_BOOT=0"
for /f "tokens=*" %%i in ('jar tf "!JAR_PATH!" ^| findstr /C:"org.springframework.boot.loader"') do set "IS_SPRING_BOOT=1"
if "!IS_SPRING_BOOT!"=="1" (
    REM Run as Spring Boot executable JAR
    set "JAVA_CMD=java -jar !JAR_PATH! !ARGS!"
) else (
    REM Run as plain JAR with main class
    set "JAVA_CMD=java -cp !CLASSPATH! vn.com.fecredit.chunkedupload.UploadApplication !ARGS!"
)
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
echo server.port=PORT
echo Default: 8080
echo Purpose: TCP port the Spring Boot application will bind to.
echo.
echo chunkedupload.chunk-size=BYTES
echo Default: 524288
echo Purpose: Default chunk size (in bytes) used by the server when creating upload sessions.
echo.
echo chunkedupload.inprogress-dir=PATH
echo Default: uploads/in-progress
echo Purpose: Directory where partial (in-progress) upload .part files are stored.
echo.
echo chunkedupload.complete-dir=PATH
echo Default: uploads/complete
echo Purpose: Directory where assembled, completed uploads are stored.
echo.
echo Special flags:
echo --help Print this help and exit.
echo --dry-run Show the resolved java command that would be executed, but do not launch the JVM.
exit /b 1