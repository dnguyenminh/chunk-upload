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
public class ChunkedUploadController {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadController.class);

    @Autowired
    private ChunkedUploadService uploadService;
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private BitsetManager bitsetManager;
    @Autowired
    private vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository;

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(tenantAccountRepository.findAll());
    }

    @PostMapping("/init")
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
    public ResponseEntity<?> uploadChunk(
            @RequestParam(value = "uploadId") String uploadId,
            @RequestParam(value = "chunkNumber") int chunkNumber,
            @RequestParam(value = "totalChunks") int totalChunks,
            @RequestParam(value = "fileSize") long fileSize,
            @RequestPart(value = "file") MultipartFile file,
            Principal principal
    ) throws IOException {
        // Use chunkSize from request parameter, not from service
        int chunkSize = uploadService.getDefaultChunkSize();
        String filename = uploadService.getFilename(uploadId);
        log.debug("Received chunk upload: uploadId={}, chunkNumber={}, totalChunks={}, chunkSize={}, fileSize={}, filename={}, file={}, file.length={}",
            uploadId, chunkNumber, totalChunks, chunkSize, fileSize, filename, (file != null ? file.getOriginalFilename() : "null"), (file != null ? file.getSize() : -1));

        // Use server-configured chunkSize for validation

        // Validate chunk size for each chunk
        long actualChunkLength = file != null ? file.getSize() : -1;
        // chunkNumber is 0-base index
        boolean isLastChunk = (chunkNumber == totalChunks - 1);
        long expectedLastChunkSize = fileSize % chunkSize;
        if (expectedLastChunkSize == 0) expectedLastChunkSize = chunkSize;

        // Accept single-chunk uploads where fileSize < chunkSize
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

        try {
            if (!sessionManager.isSessionActive(uploadId)) {
                log.debug("Upload session is not active or has been aborted for uploadId={}", uploadId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Upload session is not active or has been aborted.");
            }
            try {
                // Extract tenantAccountId from authenticated user/session
                String tenantAccountId = principal != null ? principal.getName() : "unknown";
                validateChunkRequest(chunkNumber, totalChunks, uploadId, tenantAccountId);
            } catch (IllegalArgumentException e) {
                log.debug("Chunk validation failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Chunk upload failed: " + e.getMessage());
            }

            // Extract tenantAccountId from authenticated user/session
            String tenantAccountId = principal != null ? principal.getName() : "unknown";
            Path partPath = uploadService.getPartPath(tenantAccountId, uploadId);
            log.debug("Calling writeChunk with: partPath={}, chunkNumber={}, chunkSize={}, file.length={}",
                partPath, chunkNumber, chunkSize, (file != null ? file.getSize() : -1));
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
            isLastChunk = bitsetManager.markChunkAndCheckComplete(partPath, chunkNumber, totalChunks);

            // Always return "ok" for chunk upload response, even for last chunk
            if (isLastChunk) {
                Path finalPath = uploadService.getFinalPath(tenantAccountId, uploadId);
                uploadService.assembleFile(partPath, finalPath, fileSize, bitsetManager.getBitset(partPath, totalChunks));
                uploadService.removeFilename(uploadId);
                sessionManager.endSession(uploadId);
                log.debug("Upload completed for uploadId={}", uploadId);
            }

            log.debug("Chunk upload successful for uploadId={}, chunkNumber={}", uploadId, chunkNumber);
            return ResponseEntity.ok(Map.of("status", "ok", "uploadId", uploadId));
        } catch (Exception e) {
            log.debug("Chunk upload failed: {}", e.getMessage());
            throw e;
        }
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
