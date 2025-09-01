package vn.com.fecredit.chunkedupload.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.com.fecredit.chunkedupload.service.ChunkedUploadService;
import vn.com.fecredit.chunkedupload.manager.BitsetManager;
import vn.com.fecredit.chunkedupload.manager.SessionManager;
import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/upload")
/**
 * REST controller for chunked file uploads.
 *
 * <p>Exposes endpoints for:
 * <ul>
 *   <li>Initializing or resuming upload sessions</li>
 *   <li>Uploading file chunks</li>
 *   <li>Checking upload status</li>
 *   <li>Aborting uploads</li>
 *   <li>Listing users (for demo/multi-tenant support)</li>
 * </ul>
 * <p>Relies on service and manager classes for session, chunk, and file operations.
 */
public class ChunkedUploadController {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadController.class);

    /**
     * Service for handling chunked file uploads and file storage operations.
     */
    @Autowired
    private ChunkedUploadService uploadService;
    /**
     * Manages upload session lifecycle and state.
     */
    @Autowired
    private SessionManager sessionManager;
    /**
     * Manages bitset operations for tracking uploaded chunks.
     */
    @Autowired
    private BitsetManager bitsetManager;
    /**
     * Repository for accessing tenant account/user data.
     */
    @Autowired
    private vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository;

    /**
     * Lists all users (for demo or multi-tenant support).
     *
     * @return List of users in the system.
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(tenantAccountRepository.findAll());
    }

    @PostMapping("/init")
    /**
     * Initializes a new upload session or resumes an existing one.
     * <p>If uploadId is present, resumes the session; otherwise, starts a new session.
     *
     * @param req       Initialization request (filename, fileSize, chunkSize, uploadId)
     * @param principal Authenticated user principal
     * @return ResponseEntity with InitResponse or error message
     * @throws IOException on I/O error
     */
    public ResponseEntity<?> initUpload(@RequestBody InitRequest req, Principal principal) throws IOException {
        log.debug("Received InitRequest: filename={}, fileSize={}, uploadId={}", req.getFilename(), req.getFileSize(), req.getUploadId());
        try {
            if (StringUtils.hasText(req.getUploadId())) {
                return ResponseEntity.ok(resumeUpload(req, principal));
            }
            return ResponseEntity.ok(newUpload(req, principal));
        } catch (IllegalArgumentException e) {
            log.debug("Init validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.debug("Upload initialization failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Starts a new upload session and returns session details.
     *
     * @param req       Initialization request
     * @param principal Authenticated user principal
     * @return InitResponse with session info
     * @throws IOException on I/O error
     */
    private InitResponse newUpload(InitRequest req, Principal principal) throws IOException {
        // Always use server-configured chunkSize
        int chunkSize = uploadService.getDefaultChunkSize();
        long fileSize = req.getFileSize();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        // Explicitly validate chunkSize in request
        if (req.getChunkSize() < 0) {
            log.debug("Invalid chunkSize in request: {}", req.getChunkSize());
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }

        validateNewUploadRequest(req);

        String uploadId = java.util.UUID.randomUUID().toString();
        String tenantAccountId = principal != null ? principal.getName() : "unknown";
        Path partPath = uploadService.getPartPath(tenantAccountId, uploadId);
        uploadService.storeFilename(uploadId, req.getFilename());
        uploadService.createOrValidateHeader(partPath, totalChunks, chunkSize, fileSize);
        sessionManager.startSession(uploadId, fileSize);

        return new InitResponse(uploadId, totalChunks, chunkSize, fileSize, req.getFilename());
    }

    /**
     * Resumes an existing upload session and returns session details.
     *
     * @param req       Initialization request (must include uploadId)
     * @param principal Authenticated user principal
     * @return InitResponse with session info and missing chunk numbers
     * @throws IOException on I/O error
     */
    private InitResponse resumeUpload(InitRequest req, Principal principal) throws IOException {
        if (req.getFileSize() <= 0) {
            throw new IllegalArgumentException("fileSize must be provided for resume");
        }
        String uploadId = req.getUploadId();
        String tenantAccountId = principal != null ? principal.getName() : "unknown";
        Path partPath = uploadService.getPartPath(tenantAccountId, uploadId);

        int chunkSize = uploadService.getChunkSizeFromHeader(partPath);
        int totalChunks = uploadService.getTotalChunksFromHeader(partPath);
        long fileSize = uploadService.getFileSizeFromHeader(partPath);
        if (req.getFileSize() != fileSize) {
            throw new IllegalArgumentException("Resume fileSize does not match session fileSize");
        }
        String filename = uploadService.getFilename(uploadId);

        InitResponse resp = new InitResponse(uploadId, totalChunks, chunkSize, fileSize, filename);
        byte[] bitset = bitsetManager.getBitset(partPath, totalChunks);
        List<Integer> missingChunks = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if ((bitset[i / 8] & (1 << (i % 8))) == 0) {
                missingChunks.add(i);
            }
        }
        resp.setMissingChunkNumbers(missingChunks);
        // resp.setChecksum(uploadService.getChecksum(uploadId)); // If checksum logic is implemented

        return resp;
    }

    @PostMapping("/chunk")
    /**
     * Uploads a single chunk for an active upload session.
     *
     * @param uploadId      Upload session ID
     * @param chunkNumber   Chunk index (0-based)
     * @param totalChunks   Total number of chunks
     * @param fileSize      Total file size
     * @param file          Chunk data
     * @param principal     Authenticated user principal
     * @return ResponseEntity with status or error
     * @throws IOException on I/O error
     */
    public ResponseEntity<?> uploadChunk(
            @RequestParam(value = "uploadId") String uploadId,
            @RequestParam(value = "chunkNumber") int chunkNumber,
            @RequestParam(value = "totalChunks") int totalChunks,
            @RequestParam(value = "fileSize") long fileSize,
            @RequestPart(value = "file") MultipartFile file,
            Principal principal
    ) throws IOException {
        int chunkSize = uploadService.getDefaultChunkSize();
        String filename = uploadService.getFilename(uploadId);
        log.debug("Received chunk upload: uploadId={}, chunkNumber={}, totalChunks={}, chunkSize={}, fileSize={}, filename={}, file={}, file.length={}",
            uploadId, chunkNumber, totalChunks, chunkSize, fileSize, filename, (file != null ? file.getOriginalFilename() : "null"), (file != null ? file.getSize() : -1));

        ResponseEntity<?> validationResponse = validateChunk(chunkNumber, totalChunks, fileSize, file, chunkSize, uploadId, principal);
        if (validationResponse != null) return validationResponse;

        Path partPath = uploadService.getPartPath(getTenantAccountId(principal), uploadId);
        validationResponse = writeChunk(partPath, chunkNumber, chunkSize, file);
        if (validationResponse != null) return validationResponse;

        if (bitsetManager.markChunkAndCheckComplete(partPath, chunkNumber, totalChunks)) {
            handleLastChunk(partPath, getTenantAccountId(principal), uploadId, fileSize, totalChunks);
        }

        log.debug("Chunk upload successful for uploadId={}, chunkNumber={}", uploadId, chunkNumber);
        return ResponseEntity.ok(Map.of("status", "ok", "uploadId", uploadId));
    }

    /**
     * Validates the chunk upload request for correctness and session state.
     *
     * @param chunkNumber   Chunk index
     * @param totalChunks   Total number of chunks
     * @param fileSize      Total file size
     * @param file          Chunk data
     * @param chunkSize     Expected chunk size
     * @param uploadId      Upload session ID
     * @param principal     Authenticated user principal
     * @return ResponseEntity with validation error or null if valid
     */
    private ResponseEntity<?> validateChunk(int chunkNumber, int totalChunks, long fileSize, MultipartFile file, int chunkSize, String uploadId, Principal principal) {
        ResponseEntity<?> validationResponse = validateChunkSize(chunkNumber, totalChunks, fileSize, file, chunkSize);
        if (validationResponse != null) return validationResponse;

        validationResponse = validateSessionActive(uploadId);
        if (validationResponse != null) return validationResponse;

        String tenantAccountId = getTenantAccountId(principal);
        return tryValidateChunkRequest(chunkNumber, totalChunks, uploadId, tenantAccountId);
    }

    /**
     * Gets the tenant account ID from the principal, or "unknown" if unauthenticated.
     *
     * @param principal Authenticated user principal
     * @return Tenant account ID or "unknown"
     */
    private String getTenantAccountId(Principal principal) {
        return principal != null ? principal.getName() : "unknown";
    }

    /**
     * Writes the uploaded chunk to the file system.
     *
     * @param partPath    Path to file part
     * @param chunkNumber Chunk index
     * @param chunkSize   Expected chunk size
     * @param file        Chunk data
     * @return ResponseEntity with write result or null if successful
     */
    private ResponseEntity<?> writeChunk(Path partPath, int chunkNumber, int chunkSize, MultipartFile file) {
        return writeChunkToFile(partPath, chunkNumber, chunkSize, file);
    }

    /**
     * Validates the size of the uploaded chunk against expected values.
     *
     * @param chunkNumber   Chunk index
     * @param totalChunks   Total number of chunks
     * @param fileSize      Total file size
     * @param file          Chunk data
     * @param chunkSize     Expected chunk size
     * @return ResponseEntity with validation error or null if valid
     */
    private ResponseEntity<?> validateChunkSize(int chunkNumber, int totalChunks, long fileSize, MultipartFile file, int chunkSize) {
        long actualChunkLength = file != null ? file.getSize() : -1;
        boolean isLastChunk = (chunkNumber == totalChunks - 1);
        long expectedLastChunkSize = fileSize % chunkSize;
        if (expectedLastChunkSize == 0) expectedLastChunkSize = chunkSize;
        if (totalChunks > 1 || actualChunkLength != fileSize) {
            if (!isLastChunk && actualChunkLength != chunkSize) {
                log.debug("Invalid chunk size: expected={}, actual={}", chunkSize, actualChunkLength);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid chunk size for chunk " + chunkNumber);
            }
            if (isLastChunk && actualChunkLength != expectedLastChunkSize) {
                log.debug("Invalid last chunk size: expected={}, actual={}", expectedLastChunkSize, actualChunkLength);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid last chunk size");
            }
        }
        return null;
    }

    /**
     * Checks if the upload session is active.
     *
     * @param uploadId Upload session ID
     * @return ResponseEntity with error or null if active
     */
    private ResponseEntity<?> validateSessionActive(String uploadId) {
        if (!sessionManager.isSessionActive(uploadId)) {
            log.debug("Upload session is not active or has been aborted for uploadId={}", uploadId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Upload session is not active or has been aborted.");
        }
        return null;
    }

    /**
     * Attempts to validate the chunk upload request and handles exceptions.
     *
     * @param chunkNumber     Chunk index
     * @param totalChunks     Total number of chunks
     * @param uploadId        Upload session ID
     * @param tenantAccountId Tenant account ID
     * @return ResponseEntity with validation error or null if valid
     */
    private ResponseEntity<?> tryValidateChunkRequest(int chunkNumber, int totalChunks, String uploadId, String tenantAccountId) {
        try {
            try {
                validateChunkRequest(chunkNumber, totalChunks, uploadId, tenantAccountId);
            } catch (IOException ioe) {
                log.debug("Chunk validation IO error: {}", ioe.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chunk validation IO error: " + ioe.getMessage());
            }
        } catch (IllegalArgumentException e) {
            log.debug("Chunk validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk upload failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Writes the uploaded chunk to the file system (internal helper).
     *
     * @param partPath    Path to file part
     * @param chunkNumber Chunk index
     * @param chunkSize   Expected chunk size
     * @param file        Chunk data
     * @return ResponseEntity with write result or null if successful
     */
    private ResponseEntity<?> writeChunkToFile(Path partPath, int chunkNumber, int chunkSize, MultipartFile file) {
        try {
            if (file == null) {
                throw new IllegalArgumentException("Multipart file must not be null");
            }
            uploadService.writeChunk(partPath, chunkNumber, chunkSize, file.getBytes());
        } catch (IllegalArgumentException e) {
            log.debug("writeChunk validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk upload failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception during writeChunk: {}: {}", e.getClass().getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chunk upload failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finalizes the upload process when the last chunk is received.
     *
     * @param partPath        Path to file part
     * @param tenantAccountId Tenant account ID
     * @param uploadId        Upload session ID
     * @param fileSize        Total file size
     * @param totalChunks     Total number of chunks
     * @throws IOException on I/O error
     */
    private void handleLastChunk(Path partPath, String tenantAccountId, String uploadId, long fileSize, int totalChunks) throws IOException {
        Path finalPath = uploadService.getFinalPath(tenantAccountId, uploadId);
        uploadService.assembleFile(partPath, finalPath, fileSize, bitsetManager.getBitset(partPath, totalChunks));
        uploadService.removeFilename(uploadId);
        sessionManager.endSession(uploadId);
        log.debug("Upload completed for uploadId={}", uploadId);
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<?> getStatus(@PathVariable("uploadId") String uploadId) {
        return ResponseEntity.ok(sessionManager.getStatus(uploadId));
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> abort(@PathVariable("uploadId") String uploadId) {
        uploadService.removeFilename(uploadId);
        sessionManager.endSession(uploadId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    /**
     * Validates the initialization request for a new upload session.
     *
     * @param req Initialization request
     * @throws IllegalArgumentException if request is invalid
     */
    private void validateNewUploadRequest(InitRequest req) {
        int chunkSize = uploadService.getDefaultChunkSize();
        if (chunkSize <= 0) {
            log.debug("Invalid chunkSize: {}", chunkSize);
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (req.getFileSize() <= 0 || !StringUtils.hasText(req.getFilename())) {
            throw new IllegalArgumentException("fileSize and filename must be provided for a new upload");
        }
    }

    /**
     * Validates the chunk request for an upload session.
     *
     * @param chunkNumber     Chunk index
     * @param totalChunks     Total number of chunks
     * @param uploadId        Upload session ID
     * @param tenantAccountId Tenant account ID
     * @throws IOException if chunk request is invalid or session does not exist
     */
    private void validateChunkRequest(int chunkNumber, int totalChunks, String uploadId, String tenantAccountId) throws IOException {
        if (chunkNumber < 0 || chunkNumber > totalChunks) {
            throw new IllegalArgumentException("Invalid chunk number: " + chunkNumber);
        }
        Path partPath = uploadService.getPartPath(tenantAccountId, uploadId);
        if (!partPath.toFile().exists()) {
            throw new IOException("Upload has been aborted or does not exist.");
        }
    }
}
