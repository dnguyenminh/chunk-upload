# Chunked Upload Service – User Guide

## Overview
This project provides a resumable, chunked file upload system in Java (Spring Boot). The project is divided into three modules:
- `server`: The main Spring Boot application providing the upload API.
- `client`: A Java client (`ChunkedUploadClient`) for interacting with the upload service.
- `model`: Shared data transfer objects (DTOs) used by both the server and client.

## Directory Structure
* Upload directories are configurable in `server/src/main/resources/application.properties`:
  - `chunkedupload.inprogress-dir`: Temporary storage for in-progress uploads (default: `uploads/in-progress`)
  - `chunkedupload.complete-dir`: Final assembled files (default: `uploads/complete`), named as `<uploadId>_<originalFilename>`

## Logging
- SLF4J logging is used throughout the project, with Logback as the backend.
- Logback configuration is provided in [`server/src/main/resources/logback.xml`](server/src/main/resources/logback.xml:1).
- Logback now uses a rolling file appender with daily log rotation (`logs/server.%d{yyyy-MM-dd}.log`, 30 days history).
- Logback and SLF4J dependencies are managed by Spring Boot; do not declare explicit Logback versions in Gradle.

### Release Artifacts Structure

**server.zip** contains:
- libs/download-dependencies.bat
- libs/download-dependencies.sh
- libs/model.jar
- run-server.bat
- run-server.sh
- build/libs/server.jar
- create-user.bat
- create-user.sh

**client.zip** contains:
- libs/download-dependencies.bat
- libs/download-dependencies.sh
- libs/model.jar
- run-server.bat
- run-server.sh
- build/libs/client.jar

**model.jar** is also published as a separate artifact.

See `.github/workflows/build-and-release.yml` for packaging details.

## Usage Guide

### Client Setup and Configuration

1. **Basic Usage**
```java
// Create client with minimal configuration
ChunkedUploadClient client = new ChunkedUploadClient.Builder()
    .uploadUrl("http://server/api/upload")
    .username("user")
    .password("pass")
    .build();

// Upload a file
String uploadId = client.upload(Paths.get("largefile.zip"), null, null);
```

2. **Advanced Configuration**
```java
// Create client with performance tuning
ChunkedUploadClient client = new ChunkedUploadClient.Builder()
    .uploadUrl("http://server/api/upload")
    .username("user")
    .password("pass")
    .retryTimes(5)              // More retries for unreliable networks
    .threadCounts(8)            // More threads for high bandwidth
    .httpClient(customClient)   // Custom HTTP client config
    .transport(customTransport) // Custom transport layer
    .build();

// Upload with custom retry/thread settings
client.upload(filePath, 3, 6);
```

3. **Resume Handling**
```java
try {
    client.upload(filePath, 3, 4);
} catch (RuntimeException e) {
    if (e.getMessage().contains("network error")) {
        // Resume the upload
        client.resumeUpload(brokenUploadId, filePath);
    }
}
```

### API Reference

#### 1. Upload Initialization
`POST /api/upload/init`

Starts a new upload session.

**Request:**
```json
{
  "filename": "myfile.txt",
  "fileSize": 123456,
  "checksum": "optional-sha256-hash"
}
```

**Response:**
```json
{
  "uploadId": "unique-session-id",
  "totalChunks": 10,
  "chunkSize": 1048576,
  "fileSize": 123456,
  "filename": "myfile.txt",
  "checksum": "server-computed-hash",
  "bitsetBytes": "base64-encoded-progress"
}
```

**Status Codes:**
- 200: Success
- 400: Invalid request
- 401: Unauthorized
- 500: Server error

#### 2. Chunk Upload
`POST /api/upload/chunk`

Uploads a single chunk using multipart/form-data.

**Request Fields:**
- uploadId (form field): Session identifier
- chunkNumber (form field): Zero-based chunk index
- file (form file): Binary chunk data

**Response:**
- 200: Chunk accepted
- 400: Invalid chunk
- 404: Session not found
- 409: Chunk already uploaded

#### 3. Status Check
`GET /api/upload/status/{uploadId}`

Retrieves current upload status.

**Response:**
```json
{
  "uploadId": "session-id",
  "completedChunks": 5,
  "totalChunks": 10,
  "status": "IN_PROGRESS"
}
```

### Performance Optimization

1. **Thread Count Guidelines:**
   - CPU-bound: threads = CPU cores
   - Network-bound: threads = 2-4x cores
   - Memory-bound: reduce thread count

2. **Retry Strategy:**
   - Network issues: 3-5 retries
   - Server errors: 2-3 retries
   - Client errors: no retry

3. **Memory Management:**
   - Monitor heap usage
   - Adjust queue size
   - Use file streaming

### Error Handling

1. **Network Errors:**
   - Automatic retry with backoff
   - Session resumption
   - Connection pooling

2. **Data Integrity:**
   - Checksum validation
   - Chunk verification
   - Session state checks

3. **Resource Cleanup:**
   - Automatic thread shutdown
   - Memory release
   - File handle closure

### Monitoring and Debugging

1. **Log Analysis:**
   - Check `logs/server.%d{yyyy-MM-dd}.log`
   - Monitor error patterns
   - Track performance metrics

2. **Performance Metrics:**
   - Upload throughput
   - Chunk success rate
   - Thread pool stats
   - Memory usage

3. **Debug Mode:**
   - Enable verbose logging
   - Track chunk progress
   - Monitor thread states
   - Validate checksums

### Security Considerations

1. **Authentication:**
   - Basic auth required
   - HTTPS recommended
   - Token expiration

2. **Data Protection:**
   - Tenant isolation
   - File validation
   - Size limits
   - Type checking

3. **Resource Limits:**
   - Max file size
   - Chunk size bounds
   - Session timeouts
   - Thread limits
