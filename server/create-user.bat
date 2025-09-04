@echo off
REM Usage: create-user.bat tenantId username password

if "%1"=="--help" (
    echo Usage: create-user.bat tenantId username password
    echo All parameters are required and must be non-empty.
    echo Example:
    echo   create-user.bat myTenant myUser myPassword
    exit /b 1
)

if "%1"=="" (
    echo Error: tenantId is required.
    echo Usage: create-user.bat tenantId username password
    exit /b 2
)
if "%2"=="" (
    echo Error: username is required.
    echo Usage: create-user.bat tenantId username password
    exit /b 2
)
if "%3"=="" (
    echo Error: password is required.
    echo Usage: create-user.bat tenantId username password
    exit /b 2
)

set tenantId=%1
set username=%2
set password=%3

REM Run the utility
java -cp "libs/spring-security-crypto-6.1.7.jar;libs/spring-boot-3.1.8.jar;libs/spring-boot-autoconfigure-3.1.8.jar;libs/spring-context-6.1.7.jar;libs/spring-data-jpa-3.1.8.jar;libs/jakarta.persistence-api-3.1.0.jar;build\classes\java\main" vn.com.fecredit.chunkedupload.util.CreateUserUtility %tenantId% %username% %password%

echo Done.