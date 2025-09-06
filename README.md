# Chunked Upload Service

This project implements a resumable file upload service in Java (Spring Boot). The project is divided into three modules:
- `server`: The main Spring Boot application providing the upload API.
- `client`: A Java client for interacting with the upload service.
- `model`: Shared data transfer objects (DTOs) used by both the server and client.

## Features

### Core Features
- Chunked upload with resume capability
- File header for tracking upload progress
- Database-free resumable uploads
- Configurable storage directories
- Comprehensive test suite

### Performance & Reliability
- Concurrent chunk uploads
- Automatic retry with backoff
- Memory-efficient bitset tracking
- Thread-safe operations
- Resource cleanup on completion

### Client Features
- Builder pattern configuration
- Configurable thread pool
- Automatic chunk size handling
- Progress tracking
- Error recovery
- Custom transport support

### Server Features
- RESTful API endpoints
- Basic authentication
- Tenant isolation
- Checksum validation
- Rolling log files

## Gradle Project Structure
The project uses a multi-module Gradle setup. The root `build.gradle` file configures shared settings for the subprojects: `server`, `client`, and `model`. Dependency versions are managed centrally using the `io.spring.dependency-management` plugin and the Spring Boot BOM, ensuring consistency across all modules.

**Logging:**
SLF4J logging is used throughout the project, with Logback as the backend.
Logback configuration is provided in [`server/src/main/resources/logback.xml`](server/src/main/resources/logback.xml:1).
Logback now uses a rolling file appender with daily log rotation (`logs/server.%d{yyyy-MM-dd}.log`, 30 days history).
Logback and SLF4J dependencies are managed by Spring Boot; do not declare explicit Logback versions in Gradle.

Each module (`server`, `client`, `model`) has its own `build.gradle` file defining its specific dependencies.

## Directory Structure
* Upload directories are configurable in `application.properties` within the `server` module:
    - `chunkedupload.inprogress-dir`: Temporary storage for in-progress uploads (default: `uploads/in-progress`)
    - `chunkedupload.complete-dir`: Final assembled files (default: `uploads/complete`), named as `<originalFilename>`

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

## Client Configuration

The `ChunkedUploadClient` supports extensive configuration through its builder:

```java
ChunkedUploadClient client = new ChunkedUploadClient.Builder()
    .uploadUrl("http://server/api/upload")
    .username("user")
    .password("pass")
    .retryTimes(3)         // Number of retry attempts
    .threadCounts(4)       // Concurrent upload threads
    .httpClient(custom)    // Optional custom HTTP client
    .transport(custom)     // Optional custom transport
    .build();

// Start new upload
String uploadId = client.upload(filePath, 3, 4);

// Resume broken upload
client.resumeUpload(brokenUploadId, filePath);
```

### Performance Tuning

- **Thread Count**: Set based on available cores and network capacity
- **Retry Count**: Adjust based on network reliability
- **Chunk Size**: Server-optimized, typically 1-5MB
- **Queue Size**: Set to 2x thread count for optimal throughput

## API Reference

### Upload Initialization
`POST /api/upload/init`
```json
Request:
{
  "filename": "myfile.txt",
  "fileSize": 123456
}

Response:
{
  "uploadId": "unique-id",
  "totalChunks": 10,
  "chunkSize": 1048576,
  "fileSize": 123456,
  "filename": "myfile.txt",
  "checksum": "sha256-hash"
}
```

### Chunk Upload
`POST /api/upload/chunk`
- Multipart form data
- Parameters: uploadId, chunkNumber, file
- Returns: 200 OK on success

### Status Check
`GET /api/upload/status/{uploadId}`
- Returns upload progress and state
- Used for resume operations

See the [User Guide](USER_GUIDE.md) for detailed API documentation.

## Error Handling

The system provides comprehensive error handling:

### Client-Side
- Network timeouts: Automatic retry with backoff
- Checksum mismatch: Validation before resume
- Thread interruption: Clean resource shutdown
- Memory constraints: Backpressure via bounded queue

### Server-Side
- Invalid requests: Clear error messages
- Disk full: Appropriate error codes
- Concurrent access: Thread-safe operations
- Resource cleanup: Automatic via session timeout

## Monitoring

- Rolling log files with daily rotation
- Debug logging for upload operations
- Thread state monitoring
- Upload progress tracking
- Error aggregation and reporting

See [`logback.xml`](server/src/main/resources/logback.xml:1) for logging configuration.
