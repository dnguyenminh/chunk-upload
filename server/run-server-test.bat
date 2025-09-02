@echo off
REM Quick test helper to show how arguments are normalized by run-server.bat
echo Raw args: %*
setlocal enabledelayedexpansion
set "OUT="
:loop
if "%~1"=="" goto :done
  set "a=%~1"
  REM split on first '=' if present
  for /f "tokens=1* delims==" %%B in ("%a%") do (
    set "left=%%B"
    set "right=%%C"
  )
  if defined right (
    REM already key=value
    set "OUT=!OUT! --%a%"
  ) else (
    REM maybe key and value split into two tokens (key value)
    if not "%~2"=="" (
      set "next=%~2"
      REM don't treat --flags as value
      if not "%next:~0,2%"=="--" (
        REM join key and next as key=value
        set "OUT=!OUT! --%a%=%next%"
        shift
      ) else (
        set "OUT=!OUT! --%a%"
      )
    ) else (
      set "OUT=!OUT! --%a%"
    )
  )
  shift
  goto :loop
:done
echo Normalized args:%OUT%
endlocal
