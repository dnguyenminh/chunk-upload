@echo off
setlocal

REM Set release tag, default to v1.1.0 if not provided
set TAG=%TAG%
if "%TAG%"=="" set TAG=v1.1.0

REM Create libs directory
if not exist libs mkdir libs

REM Download all runtime dependencies
curl -L -o libs\jackson-annotations-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar
curl -L -o libs\jackson-core-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar
curl -L -o libs\jackson-databind-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar

REM Download model jar from release artifact URL using dynamic tag
curl -L -o libs\model.jar https://github.com/dnguyenminh/chunk-upload/releases/download/%TAG%/model.jar

endlocal