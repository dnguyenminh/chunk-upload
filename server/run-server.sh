#!/bin/bash
# Run the server JAR with optional application property overrides.
# Usage:
#   ./run-server.sh                  -> runs with defaults
#   ./run-server.sh server.port=8081 -> sets server.port=8081
#   ./run-server.sh --server.port=8081 -> same as above

# Whitelist of allowed property keys (space-separated)
ALLOWED=(server.port chunkedupload.chunk-size chunkedupload.inprogress-dir chunkedupload.complete-dir)
ARGS=()

if [ "$#" -eq 0 ]; then
    echo "Starting server with default properties..."
else
    if [ "$1" = "--help" ]; then
        echo "Usage: $0 [options]"
        echo
        echo "Options (can be provided as key=value or --key=value):"
        echo
        echo "  server.port=PORT"
        echo "      Default: 8080"
        echo "      Purpose: TCP port the Spring Boot application will bind to."
        echo
        echo "  chunkedupload.chunk-size=BYTES"
        echo "      Default: 524288"
        echo "      Purpose: Default chunk size (in bytes) used by the server when creating upload sessions."
        echo
        echo "  chunkedupload.inprogress-dir=PATH"
        echo "      Default: uploads/in-progress"
        echo "      Purpose: Directory where partial (in-progress) upload .part files are stored."
        echo
        echo "  chunkedupload.complete-dir=PATH"
        echo "      Default: uploads/complete"
        echo "      Purpose: Where assembled, completed uploads are stored."
        echo
        echo "Special flags:"
        echo "  --help    Print this help and exit."
        echo "  --dry-run Show the resolved java command that would be executed, but do not launch the JVM."
        echo
        echo "Examples:"
        echo "  ./run-server.sh server.port=8085 chunkedupload.chunk-size=65536"
        echo "  ./run-server.sh --dry-run --server.port=9090"
        exit 0
    fi
    if [ "$1" = "--dry-run" ]; then
        DRYRUN=true
        shift
    fi
    for a in "$@"; do
        if [[ "$a" == --* ]]; then
            clean="${a:2}"
        else
            clean="$a"
        fi
        key="${clean%%=*}"
        allowed=false
        for k in "${ALLOWED[@]}"; do
            if [ "$k" = "$key" ]; then
                allowed=true
                break
            fi
        done
        if [ "$allowed" = true ]; then
            ARGS+=("--$clean")
        else
            echo "Skipping invalid or disallowed property: $a"
        fi
    done
fi

# Resolve directory of this script to make paths robust regardless of CWD
DIR="$(cd "$(dirname "$0")" && pwd)"
echo "Script directory: $DIR"

# Find the server JAR file (with or without version)
JAR=""
EXECUTABLE_JAR=""
# First try to find Spring Boot executable JAR relative to script directory (prefer non-plain JARs)
echo "Searching for executable JAR in $DIR/build/libs/server-*.jar"
for jar_file in "$DIR/build/libs/server-"*.jar; do
    if [ -f "$jar_file" ] && [[ "$jar_file" != *"server-plain"* ]]; then
        EXECUTABLE_JAR="$jar_file"
        echo "Found executable JAR: $EXECUTABLE_JAR"
        break
    fi
done
# If not found relative to script, try current directory
if [ -z "$EXECUTABLE_JAR" ]; then
    echo "Searching for executable JAR in build/libs/server-*.jar"
    for jar_file in "build/libs/server-"*.jar; do
        if [ -f "$jar_file" ] && [[ "$jar_file" != *"server-plain"* ]]; then
            EXECUTABLE_JAR="$jar_file"
            echo "Found executable JAR: $EXECUTABLE_JAR"
            break
        fi
    done
fi
# If executable JAR found, use it
if [ -n "$EXECUTABLE_JAR" ]; then
    JAR="$EXECUTABLE_JAR"
    JAR_CMD="java -jar"
    JAR_ARGS="$JAR"
else
    # Fallback to plain JAR with classpath (script dir first)
    echo "Searching for plain JAR in $DIR/build/libs/server.jar"
    if [ -f "$DIR/build/libs/server.jar" ]; then
        JAR="$DIR/build/libs/server.jar"
        echo "Found plain JAR: $JAR"
    elif [ -f "build/libs/server.jar" ]; then
        JAR="build/libs/server.jar"
        echo "Found plain JAR: $JAR"
    elif [ -f "$DIR/build/libs/server-plain.jar" ]; then
        JAR="$DIR/build/libs/server-plain.jar"
        echo "Found plain JAR: $JAR"
    elif [ -f "build/libs/server-plain.jar" ]; then
        JAR="build/libs/server-plain.jar"
        echo "Found plain JAR: $JAR"
    elif [ -f "$DIR/build/libs/server-plain-"*.jar ]; then
        JAR=$(ls "$DIR/build/libs/server-plain-"*.jar | head -n 1)
        echo "Found plain JAR: $JAR"
    elif [ -f "build/libs/server-plain-"*.jar ]; then
        JAR=$(ls "build/libs/server-plain-"*.jar | head -n 1)
        echo "Found plain JAR: $JAR"
    fi
    if [ -n "$JAR" ]; then
        # Build CLASSPATH with the found JAR and all libs/*.jar files
        CLASSPATH="$JAR"
        echo "Building CLASSPATH with $JAR and libs/*.jar"
        for lib_jar in "$DIR/libs/"*.jar; do
            if [ -f "$lib_jar" ]; then
                CLASSPATH="$CLASSPATH:$lib_jar"
                echo "Added to CLASSPATH: $lib_jar"
            fi
        done
        JAR_CMD="java -verbose:class -cp"  # Added -verbose:class for debugging
        JAR_ARGS="$CLASSPATH vn.com.fecredit.chunkedupload.UploadApplication"
    fi
fi

if [ -z "$JAR" ]; then
    echo "Error: Server JAR not found in the following locations:"
    echo "  - $DIR/build/libs/"
    echo "  - $PWD/build/libs/"
    echo "Please ensure you have built the project by running 'gradlew build'"
    echo "or that the JAR files are in the expected location."
    exit 1
fi

echo "Running: $JAR_CMD $JAR_ARGS ${ARGS[*]}"
if [ "$DRYRUN" = true ]; then
    echo "Dry run enabled, not launching JVM."
else
    exec $JAR_CMD $JAR_ARGS "${ARGS[@]}"
fi