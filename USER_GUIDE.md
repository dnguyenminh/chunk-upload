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

### Release Artifacts Structure
Each release zip contains:
- `libs/chunked-upload-*.jar`
- `dependencies/download-dependencies.bat`
- `dependencies/download-dependencies.sh`
- `run-*.bat`
- `run-*.sh`

See `.github/workflows/build-and-release.yml` for packaging details.

## API Endpoints

### 1. Initialize Upload
**POST** `/api/upload/init`

**Request Body:**
```json
{
  "totalChunks": 2,
  "chunkSize": 10,
  "fileSize": 20,
  "filename": "myfile.txt"
}
```
**Response:**
```json
{
  "uploadId": "...",
  "totalChunks": 2,
  "chunkSize": 10,
  "fileSize": 20,
  "filename": "myfile.txt"
}
```

### 2. Upload Chunk
**POST** `/api/upload/chunk`

**Form Data:**
- `uploadId` (String)
- `chunkNumber` (int, 1-based)
- `totalChunks` (int)
- `chunkSize` (int)
- `fileSize` (long)
- `file` (chunk data, binary)

**Response:**
- On last chunk, server assembles the file as `<uploadId>_<filename>` in `uploads/complete/`
- Response includes `nextChunk` (null if completed) and `uploadId`

### 3. Get Upload Status
**GET** `/api/upload/{uploadId}/status`
Returns upload status and chunk progress.

### 4. Abort Upload
**DELETE** `/api/upload/{uploadId}`
Aborts and cleans up an in-progress upload.

---

## Java Client Usage
The `ChunkedUploadClient` is available in the `client` module.

### Example
```java
// Make sure the 'client' and 'model' modules are on the classpath
import vn.com.fecredit.chunkedupload.client.ChunkedUploadClient;

// ...

ChunkedUploadClient client = new ChunkedUploadClient.Builder()
    .uploadUrl("http://localhost:8080/api/upload")
    .username("user")
    .password("password")
    .retryTimes(2)
    .threadCounts(4)
    .build();

String uploadId = client.upload(fileBytes, "myfile.txt", null, null);
// The final file will be saved as uploads/complete/{uploadId}_myfile.txt on the server
```
- The client automatically splits the file into chunks, uploads in parallel, and retries failed chunks.
- The returned `uploadId` can be used to check status or locate the completed file on the server.

---

## Integration Test Example
See `client/src/test/java/vn/com/fecredit/chunkedupload/client/ChunkedUploadClientIntegrationTest.java` for a full integration test. It verifies upload and file content:

<!-- Example integration test (Java, for documentation only) -->
```java
String uploadId = client.upload(FILE_CONTENT, FILENAME, null, null);
Path uploadedFilePath = Path.of("uploads", "complete", uploadId + "_" + FILENAME);
// The following assertions are for illustrative purposes only, not executable in Markdown:
assertTrue(Files.exists(uploadedFilePath), "Uploaded file should exist");
byte[] uploadedContent = Files.readAllBytes(uploadedFilePath);
assertArrayEquals(FILE_CONTENT, uploadedContent, "Uploaded file content should match");
```

---

## Architecture Notes
- The project is split into `server`, `client`, and `model` modules for a clean separation of concerns.
- The Gradle build is structured as a multi-module project, with dependency versions managed by the Spring Boot BOM.
- No database required; header files track chunk status.
- Server assembles the file automatically when all chunks are received.
- Resume support: client can retry missing chunks.

---

## Build & Run

```bash
# Clean and build all modules
./gradlew clean build

# Run the server application
./gradlew :server:bootRun

# Run the client's integration tests
./gradlew :client:test
```

---

## Troubleshooting
- If the upload fails, check server logs for errors.
- Ensure the client and server use matching authentication credentials.
- The completed file will be named `{uploadId}_{originalFilename}` in the server's `uploads/complete/` directory.

---

For more details, see `README.md` and `architecture.md`.
