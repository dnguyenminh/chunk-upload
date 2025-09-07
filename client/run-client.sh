#!/bin/bash
# Executes the client application JAR with advanced argument parsing and validation

# Function to show help
show_help() {
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
}

# Initialize variables
filePath=""
uploadUrl=""
username=""
password=""
retryTimes=3
threadCounts=4
missing=""

# Show help for no args or --help
if [ $# -eq 0 ]; then
    echo "Error: No arguments provided. Required arguments: --filePath, --uploadUrl, --username, --password"
    echo ""
    show_help
    exit 1
fi

if [ "$1" = "--help" ]; then
    show_help
    exit 0
fi

# Parse arguments
while [ $# -gt 0 ]; do
    arg="$1"
    echo "Debugging: Processing arg=[$arg]"

    # Check if the argument is a key (starts with --)
    if [[ "$arg" == --* ]]; then
        key="$arg"
        value=""

        # Check if the key has a value attached (contains =)
        echo "Debugging: Checking if arg [$arg] contains ="
        if [[ "$arg" == *"="* ]]; then
            # Value is after = in the same argument
            echo "Debugging: = found, extracting value"
            IFS='=' read -r key value <<< "$arg"
            key="${key#--}"  # Remove -- prefix
            value="${value%\"}"  # Remove trailing quotes if any
            value="${value#\"}"  # Remove leading quotes if any
            echo "Debugging: Extracted key=[$key], value=[$value]"
        else
            # No =, value is the next argument
            echo "Debugging: No = found, using next arg as value"
            if [ $# -gt 1 ]; then
                value="$2"
                key="${key#--}"  # Remove -- prefix
                shift
            fi
        fi

        # Assign the value to the corresponding variable
        case "$key" in
            "filePath")
                filePath="$value"
                echo "Debugging: Set filePath=[$filePath]"
                ;;
            "uploadUrl")
                uploadUrl="$value"
                echo "Debugging: Set uploadUrl=[$uploadUrl]"
                ;;
            "username")
                username="$value"
                echo "Debugging: Set username=[$username]"
                ;;
            "password")
                password="$value"
                echo "Debugging: Set password=[$password]"
                ;;
            "retryTimes")
                retryTimes="$value"
                echo "Debugging: Set retryTimes=[$retryTimes]"
                ;;
            "threadCounts")
                threadCounts="$value"
                echo "Debugging: Set threadCounts=[$threadCounts]"
                ;;
        esac
    fi
    shift
done

# Debug: Show captured values
echo ""
echo "Parsed arguments:"
echo "FilePath: [$filePath]"
echo "UploadURL: [$uploadUrl]"
echo "Username: [$username]"
echo "Password: [$password]"
echo "RetryTimes: [$retryTimes]"
echo "ThreadCounts: [$threadCounts]"
echo ""

# Check for missing required arguments
if [ -z "$filePath" ]; then
    missing="$missing --filePath"
fi
if [ -z "$uploadUrl" ]; then
    missing="$missing --uploadUrl"
fi
if [ -z "$username" ]; then
    missing="$missing --username"
fi
if [ -z "$password" ]; then
    missing="$missing --password"
fi

if [ -n "$missing" ]; then
    echo "Error: Missing required arguments:$missing"
    echo ""
    show_help
    exit 1
fi

# Find the client JAR file (with or without version)
JAR_PATH=""
if compgen -G "build/libs/client-*.jar" > /dev/null; then
    # Use the first versioned JAR found
    for jar_file in build/libs/client-*.jar; do
        if [ -f "$jar_file" ]; then
            JAR_PATH="$jar_file"
            break
        fi
    done
fi

if [ -z "$JAR_PATH" ]; then
    if [ -f "build/libs/client.jar" ]; then
        # Fallback to plain client.jar
        JAR_PATH="build/libs/client.jar"
    fi
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Client JAR not found in build/libs/"
    echo "Please build the project first by running './gradlew build'"
    exit 1
fi

# Execute JAR with all arguments
echo "Running: java -jar \"$JAR_PATH\" --filePath=\"$filePath\" --uploadUrl=\"$uploadUrl\" --username=\"$username\" --password=\"$password\" --retryTimes=$retryTimes --threadCounts=$threadCounts"
java -jar "$JAR_PATH" --filePath="$filePath" --uploadUrl="$uploadUrl" --username="$username" --password="$password" --retryTimes=$retryTimes --threadCounts=$threadCounts
