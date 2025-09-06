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
- **Logging**: SLF4J logging is used throughout, with Logback as the backend. Logback configuration is in [`server/src/main/resources/logback.xml`](server/src/main/resources/logback.xml:1). Logback now uses a rolling file appender with daily log rotation (`logs/server.%d{yyyy-MM-dd}.log`, 30 days history). Logback dependencies are managed by Spring Boot; do not declare explicit Logback versions in Gradle.

This setup ensures a clean, maintainable, and consistent build process across the entire project.

## Release Packaging & Artifacts

Each release zip (client/server) is structured as follows:
- `libs/chunked-upload-*.jar`
- `dependencies/download-dependencies.bat`
- `dependencies/download-dependencies.sh`
- `run-*.bat`
- `run-*.sh`

Packaging is automated via `.github/workflows/build-and-release.yml`.

## Server Module Components

- **`ChunkedUploadController`**: The entry point for all API requests. It handles HTTP requests for initializing, chunking, checking status, and aborting uploads. It delegates business logic to the service and manager layers.
- **`ChunkedUploadService`**: Manages all low-level file system operations. Its responsibilities include creating temporary part files, writing a metadata header, writing individual chunks to the correct offset, and assembling the final file upon completion.
- **`SessionManager`**: An in-memory component that tracks active upload sessions using a thread-safe `ConcurrentHashMap`. It provides:
  - Fast O(1) lookups for session validation
  - Automatic resource cleanup on session end
  - Thread-safe state management
  - Memory-efficient storage using upload IDs as keys

- **`BitsetManager`**: A sophisticated chunk tracking system that uses bitsets for efficient state management:
  - Uses 1 bit per chunk (8 chunks per byte) for memory efficiency
  - Thread-safe operations via `ConcurrentHashMap`
  - O(1) chunk marking and O(n/8) completion checking
  - Automatic cleanup via weak references
  - Supports concurrent uploads with atomic operations

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

- **Stateless Architecture**:
  - In-memory state management via thread-safe components
  - On-disk headers for persistence and recovery
  - No database dependency for simplified deployment
  - Automatic resource cleanup and memory management

- **Resumability**:
  - Chunk-level granularity for efficient resume
  - Server-controlled chunk size for optimal performance
  - Checksum validation for data integrity
  - Bitset tracking for upload progress

- **Modularity**:
  - Clean separation of concerns across modules
  - Independent versioning and deployment
  - Shared DTOs in model module
  - Consistent dependency management

- **Client Performance**:
  - Producer-consumer pattern for chunk processing
  - Configurable thread pool for parallel uploads
  - Bounded queue for backpressure management
  - Automatic retry with exponential backoff
  - Efficient resource cleanup on completion/failure

- **Thread Safety**:
  - Immutable data transfer objects
  - Thread-safe managers using `ConcurrentHashMap`
  - Atomic operations for state updates
  - Clear ownership boundaries for shared resources

## Security Architecture

### Authentication & Authorization
- HTTP Basic Authentication with Spring Security
- BCrypt password hashing with salt
- Delegating password encoder supporting multiple hash formats
- Database-backed user details service
- Tenant-based access control

### Database Schema
- **Tenants Table**:
  - `id`: Primary key (auto-generated)
  - `tenant_id`: Unique business identifier
  - `username`: Unique login name
  - `password`: BCrypt hashed password (with {bcrypt} prefix)

### File Access Control
- Each file upload is associated with a tenant
- Files are stored in tenant-specific directories
- Cross-tenant access is prevented
- Upload sessions are tenant-scoped

### Security Best Practices
- Automatic password hashing
- No plaintext password storage
- Secure session management
- Resource isolation between tenants
- Input validation and sanitization
