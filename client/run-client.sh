#!/bin/bash
# Executes the client application JAR, handling --help and passing all other arguments.

# Check for --help argument
if [[ "$1" == "--help" ]]; then
    echo "Usage: ./run-client.sh [options]"
    echo ""
    echo "Options:"
    echo "  --filePath=<path>          : Required. Path to the file to upload."
    echo "  --uploadUrl=<url>          : Required. The server's base upload URL (e.g., http://localhost:8080/api/upload)."
    echo "  --username=<user>          : Required. Username for authentication."
    echo "  --password=<pass>          : Required. Password for authentication."
    echo "  --retryTimes=<num>         : Optional. Number of retries for failed chunks (default: 3)."
    echo "  --threadCounts=<num>       : Optional. Number of parallel upload threads (default: 4)."
    echo "  --help                     : Print this help message."
    exit 0
fi

# Find the client JAR file (with or without version)
JAR=""
# First try to find versioned JAR files
for jar_file in "build/libs/client-"*.jar; do
    if [ -f "$jar_file" ]; then
        JAR="$jar_file"
        break
    fi
done

# Fallback to non-versioned JAR if no versioned JAR found
if [ -z "$JAR" ]; then
    if [ -f "build/libs/client.jar" ]; then
        JAR="build/libs/client.jar"
    fi
fi

if [ ! -f "$JAR" ]; then
    echo "Error: Client JAR not found in build/libs/" >&2
    echo "Please build the project first by running './gradlew build'" >&2
    exit 1
fi

JAR_PATH="$JAR:libs/*.jar"

# Pass all script arguments to the java application
java -jar "$JAR_PATH" "$@"
