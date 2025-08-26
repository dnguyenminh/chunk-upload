# Chunked Upload Service

This project implements a resumable file upload server in Java without using a database. 
It stores file chunks and maintains a simple header file to track uploaded chunks.

## Features
- Chunked upload with resume support
- File header (`.hdr`) to track uploaded chunks
- Resumable uploads without database
- Simple cache directory
- JUnit tests

## Build & Run
```bash
./gradlew build
./gradlew run
```
