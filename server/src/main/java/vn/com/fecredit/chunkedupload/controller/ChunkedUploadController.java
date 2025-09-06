package vn.com.fecredit.chunkedupload.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;
import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.service.ChunkedUploadService;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

/**
 * REST controller for chunked file uploads.
 *
 * <p>
 * Exposes endpoints for:
 * <ul>
 * <li>Initializing or resuming upload sessions</li>
 * <li>Uploading file chunks</li>
 * <li>Checking upload status</li>
 * <li>Aborting uploads</li>
 * <li>Listing users (for demo/multi-tenant support)</li>
 * </ul>
 * <p>
 * Relies on service and manager classes for session, chunk, and file
 * operations.
 */
@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadController.class);

    @Autowired
    private ChunkedUploadService uploadService;
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

    /**
     * Initializes a new upload session or resumes a broken upload.
     *
     * @param req       The initialization request containing file details and optional broken upload ID
     * @param principal The authenticated user principal
     * @return ResponseEntity containing InitResponse with session details or error message
     * @throws IOException If there is an error creating upload session
     */
    @PostMapping("/init")
    public ResponseEntity<?> initUpload(@Valid @RequestBody InitRequest req, Principal principal) throws Throwable {
        log.debug("Received InitRequest: filename={}, fileSize={}", req.getFilename(), req.getFileSize());
        try {
            return ResponseEntity.ok(newUpload(req, principal));
        } catch (IllegalArgumentException e) {
            log.debug("Init validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Throwable e) {
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
    private InitResponse newUpload(InitRequest req, Principal principal) throws Throwable {
        String username = getTenantAccountId(principal);
        String brokenUploadId = req.getBrokenUploadId();

        if (brokenUploadId != null && !brokenUploadId.isEmpty()) {
            UploadInfo info = uploadService.findUploadInfoByTenantAndUploadId(username, brokenUploadId);
            if (info != null && info.getChecksum().equals(req.getChecksum())) {
                Header header = uploadService.readHeader(username, brokenUploadId);
                InitResponse resp = new InitResponse(brokenUploadId, header.totalChunks, header.chunkSize, header.fileSize,
                        info.getFilename());
                resp.setBitsetBytes(header.bitset);
                return resp;
            }
        }

        String uploadId = java.util.UUID.randomUUID().toString();
        uploadService.registerUploadingFile(username, uploadId, req.getFilename(), req.getFileSize(), req.getChecksum());
        Header header = uploadService.readHeader(username, uploadId);
        return new InitResponse(uploadId, header.totalChunks, header.chunkSize, header.fileSize, req.getFilename(), header.bitset);
    }

    /**
     * Uploads a single chunk for an active upload session.
     *
     * @param uploadId    Upload session ID
     * @param chunkNumber Chunk index (0-based)
     * @param file        Chunk data
     * @param principal   Authenticated user principal
     * @return ResponseEntity with status or error
     * @throws IOException on I/O error
     */
    @PostMapping("/chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam(value = "uploadId") String uploadId,
            @RequestParam(value = "chunkNumber") int chunkNumber,
            @RequestPart(value = "file") MultipartFile file,
            Principal principal) throws Throwable {
        String username = getTenantAccountId(principal);
        try {
            uploadService.writeChunk(username, uploadId, chunkNumber, file.getBytes());
        } catch (IOException ioe) {
            log.debug("Chunk IO error: {}", ioe.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Chunk IO error: " + ioe.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("Chunk validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk validation failed: " + e.getMessage());
        } catch (Throwable throwable) {
            log.debug("Chunk upload system Fail: {}", throwable.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk upload system failed: " + throwable.getMessage());
        }
        log.debug("Chunk upload successful for uploadId={}, chunkNumber={}", uploadId, chunkNumber);
        return ResponseEntity.ok(Map.of("status", "ok", "uploadId", uploadId));
    }

    /**
     * Gets the tenant account ID from the principal, or "unknown" if
     * unauthenticated.
     *
     * @param principal Authenticated user principal
     * @return Tenant account ID or "unknown"
     */
    private String getTenantAccountId(Principal principal) {
        return principal != null ? principal.getName() : "unknown";
    }

    /**
     * Gets the current status of an upload session.
     *
     * @param uploadId The upload session ID
     * @return ResponseEntity containing session status information
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<?> getStatus(@PathVariable("uploadId") String uploadId, Principal principal) throws Throwable {
        UploadInfo uploadInfo = uploadService.findUploadInfoByTenantAndUploadId(uploadId, principal.getName());
        if (uploadInfo != null) {
            return ResponseEntity.ok("No file uploading in progress for the request: %s".formatted(uploadId));
        }
        return ResponseEntity.ok("The uploading file is in progress for the request: %s".formatted(uploadId));
    }

    /**
     * Aborts an active upload session.
     *
     * @param uploadId The upload session ID to abort
     * @return ResponseEntity with no content on success
     */
    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> abort(@PathVariable("uploadId") String uploadId, Principal principal) {
        try {
            uploadService.deleteUploadFile(principal.getName(), uploadId);
            return ResponseEntity.noContent().build();
        } catch (Throwable e) {
            return ResponseEntity.notFound().build();
        }
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

}
