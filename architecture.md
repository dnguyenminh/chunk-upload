# Architecture Design

## High-Level Architecture

The project is designed with a modular architecture, separating concerns into three distinct Gradle modules:

- **`server`**: A Spring Boot application that provides the REST API for chunked file uploads. It contains all the server-side logic for handling requests, managing file operations, and tracking upload state.
- **`client`**: A Java library containing the `ChunkedUploadClient`, a multi-threaded client designed to interact with the server's API. It handles file chunking, parallel uploads, and retry logic.
- **`model`**: A lightweight module containing the shared Data Transfer Objects (DTOs), such as `InitRequest` and `InitResponse`, used for communication between the `server` and `client`.

## Gradle Build System

The project is configured as a multi-module Gradle build. The root `build.gradle` file orchestrates the build, applying common plugins and configurations to all subprojects. Key aspects include:

- **`io.spring.dependency-management`**: This plugin is applied to all subprojects to manage dependency versions centrally.
- **Spring Boot BOM**: The `spring-boot-dependencies` BOM (Bill of Materials) is imported in the root project, ensuring that all modules use a consistent set of dependency versions that are known to work well together.
- **Module-Specific Dependencies**: Each module (`server`, `client`, `model`) has its own `build.gradle` file where its specific dependencies are declared (e.g., `spring-boot-starter-web` for the server, `jackson-databind` for the client).

This setup ensures a clean, maintainable, and consistent build process across the entire project.

## Server Module Components

- **`ChunkedUploadController`**: The entry point for all API requests. It handles HTTP requests for initializing, chunking, checking status, and aborting uploads. It delegates business logic to the service and manager layers.
- **`ChunkedUploadService`**: Manages all low-level file system operations. Its responsibilities include creating temporary part files, writing a metadata header, writing individual chunks to the correct offset, and assembling the final file upon completion.
- **`SessionManager`**: An in-memory component that tracks active upload sessions. It maps a unique `uploadId` to file metadata, allowing for quick status checks and session validation.
- **`BitsetManager`**: An in-memory component that tracks the completion status of chunks for each upload. It uses a bitset (represented as a `byte[]`) to efficiently mark received chunks and determine when an upload is complete.

## On-Disk Format (In-Progress Files)

To manage resumability without a database, the `server` uses a temporary `.part` file for each upload. This file has a custom binary header followed by the pre-allocated space for all file chunks.

**Header Format (Big Endian):**
1.  **Magic Number** (4 bytes): `0xCAFECAFE` to identify the file type.
2.  **Total Chunks** (4 bytes): The total number of chunks for the file.
3.  **Chunk Size** (4 bytes): The size of each chunk.
4.  **File Size** (8 bytes): The total size of the original file.
5.  **Bitset** (variable bytes): A bitset where each bit represents a chunk, used to track received chunks.

When a chunk is received, it is written directly to its calculated offset in the `.part` file (`headerSize + (chunkIndex * chunkSize)`).

## Design Choices

- **Stateless (No Database)**: The server's state is managed through in-memory components and the on-disk header files. This simplifies deployment and reduces external dependencies.
- **Resumability**: If an upload is interrupted, the client can re-initialize the upload with the same `uploadId`. The server uses the header in the `.part` file to determine which chunks are missing and the upload can be resumed.
- **Modularity**: The separation into `server`, `client`, and `model` modules promotes clean code, easier maintenance, and independent development of the client and server.
- **Client-Side Parallelism**: The `ChunkedUploadClient` uses a thread pool to upload multiple chunks in parallel, significantly improving performance for large files over high-latency networks.
