#!/bin/bash

# Download all runtime dependencies
curl -L -o jackson-databind-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.19.2/jackson-databind-2.19.2.jar
curl -L -o jackson-core-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.19.2/jackson-core-2.19.2.jar
curl -L -o jackson-annotations-2.19.2.jar https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.19.2/jackson-annotations-2.19.2.jar
curl -L -o jakarta.validation-api-3.0.2.jar https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/3.0.2/jakarta.validation-api-3.0.2.jar
