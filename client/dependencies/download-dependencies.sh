#!/bin/bash
mkdir -p libs


# Download all runtime dependencies
curl -L -o libs/jackson-databind-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar
curl -L -o libs/jackson-core-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar
curl -L -o libs/jackson-annotations-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar
