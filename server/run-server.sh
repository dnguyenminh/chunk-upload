#!/bin/bash
# Run the server JAR with optional application property overrides.
# Usage:
#   ./run-server.sh                         -> runs with defaults
#   ./run-server.sh server.port=8081       -> sets server.port=8081
#   ./run-server.sh --server.port=8081     -> same as above

ARGS=()

# Whitelist of allowed property keys (space-separated)
ALLOWED=(server.port chunkedupload.chunk-size chunkedupload.inprogress-dir chunkedupload.complete-dir)

if [ "$#" -eq 0 ]; then
	echo "Starting server with default properties..."
else
		if [ "$1" = "--help" ]; then
			echo "Usage: run-server.sh [options]"
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
			echo "      Purpose: Directory where assembled, completed uploads are stored."
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
# Prefer server.jar if present
JAR="$DIR/build/libs/server.jar"
if [ ! -f "$JAR" ]; then
  JAR="$DIR/build/libs/server-plain.jar"
fi

CLASSPATH="$JAR:libs/*"
echo "Running: java -cp $CLASSPATH vn.com.fecredit.chunkedupload.UploadApplication ${ARGS[*]}"
if [ "$DRYRUN" = true ]; then
	echo "Dry run enabled, not launching JVM."
else
	exec java -cp "$CLASSPATH" vn.com.fecredit.chunkedupload.UploadApplication "${ARGS[@]}"
fi