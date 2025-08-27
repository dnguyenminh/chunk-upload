# Chunked Upload Service – User Guide

## Overview
This project provides a resumable, chunked file upload system in Java (Spring Boot) with a robust client (`ChunkedUploadClient`). It supports large file uploads, automatic assembly, and resume/retry logic.

## Directory Structure
* Upload directories are configurable in `application.properties`:
  - `chunkedupload.inprogress-dir`: Temporary storage for in-progress uploads (default: `uploads/in-progress`)
  - `chunkedupload.complete-dir`: Final assembled files (default: `uploads/complete`), named as `<uploadId>_<originalFilename>`

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

### Example
```java
ChunkedUploadClient client = new ChunkedUploadClient.Builder()
    .uploadUrl("http://localhost:8080/api/upload")
    .username("user")
    .password("password")
    .retryTimes(2)
    .threadCounts(4)
    .build();

String uploadId = client.upload(fileBytes, "myfile.txt", null, null);
// The final file will be saved as uploads/complete/{uploadId}_myfile.txt
```
- The client automatically splits the file into chunks, uploads in parallel, and retries failed chunks.
- The returned `uploadId` can be used to check status or locate the completed file.

---

## Integration Test Example
See `ChunkedUploadClientIntegrationTest.java` for a full integration test. It verifies upload and file content:

```java
String uploadId = client.upload(FILE_CONTENT, FILENAME, null, null);
Path uploadedFilePath = Path.of("uploads", "complete", uploadId + "_" + FILENAME);
assertTrue(Files.exists(uploadedFilePath), "Uploaded file should exist");
byte[] uploadedContent = Files.readAllBytes(uploadedFilePath);
assertArrayEquals(FILE_CONTENT, uploadedContent, "Uploaded file content should match");
```

---

## Architecture Notes
- No database required; header files track chunk status.
- Server assembles the file automatically when all chunks are received.
- Resume support: client can retry missing chunks.

---

## Troubleshooting
- If the upload fails, check server logs for errors.
- Ensure the client and server use matching authentication credentials.
- The completed file will be named `{uploadId}_{originalFilename}` in `uploads/complete/`.

---

For more details, see `README.md` and `architecture.md`.
