package vn.com.fecredit.chunkedupload.controller;

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
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Controller for handling chunked file uploads.
 * This controller provides endpoints for initializing an upload, uploading chunks,
 * checking the status of an upload, and aborting an upload.
 */
@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {
    @Autowired
    private ChunkedUploadService uploadService;
    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private BitsetManager bitsetManager;

    /**
     * Initialize an upload session.
     * @param req The initialization request containing upload parameters.
     * @return The initialization response containing uploadId and other parameters.
     * @throws IOException If there is a file access error.
     */
    @PostMapping("/init")
    public InitResponse initUpload(@RequestBody InitRequest req) throws IOException {
        validateInitRequest(req);
        String uploadId = getOrCreateUploadId(req.getUploadId());
        Path partPath = uploadService.getPartPath(uploadId);
        uploadService.storeFilename(uploadId, req.getFilename());
        uploadService.createOrValidateHeader(partPath, req.getTotalChunks(), req.getChunkSize(), req.getFileSize());
        sessionManager.startSession(uploadId, req.getFileSize());
        return new InitResponse(uploadId, req.getTotalChunks(), req.getChunkSize(), req.getFileSize(), req.getFilename());
    }

    /**
     * Upload a chunk of the file.
     * @param uploadId The upload session ID.
     * @param chunkNumber The chunk number being uploaded.
     * @param totalChunks The total number of chunks.
     * @param chunkSize The size of each chunk.
     * @param fileSize The total size of the file.
     * @param file The MultipartFile object containing the chunk data.
     * @return A response entity containing the status and nextChunk information.
     * @throws IOException If there is an error processing the upload.
     */
    @PostMapping("/chunk")
    public ResponseEntity<?> uploadChunk(@RequestParam String uploadId, @RequestParam int chunkNumber,
                                         @RequestParam int totalChunks, @RequestParam int chunkSize,
                                         @RequestParam long fileSize, @RequestParam("file") MultipartFile file) throws IOException {
        Path partPath = uploadService.getPartPath(uploadId);
        uploadService.writeChunk(partPath, chunkNumber, chunkSize, file.getBytes());
        boolean isLastChunk = bitsetManager.markChunkAndCheckComplete(partPath, chunkNumber, totalChunks);
        if (isLastChunk) {
            Path finalPath = uploadService.getFinalPath(uploadId);
            uploadService.assembleFile(partPath, finalPath, fileSize, bitsetManager.getBitset(partPath, totalChunks));
            uploadService.removeFilename(uploadId);
            sessionManager.endSession(uploadId);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("status", "completed");
            result.put("nextChunk", null);
            result.put("finalPath", finalPath.toString());
            return ResponseEntity.ok(result);
        }
        int nextChunk = findNextMissingChunk(bitsetManager.getBitset(partPath, totalChunks), totalChunks);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "ok");
        result.put("nextChunk", nextChunk);
        return ResponseEntity.ok(result);
    }

    /**
     * Get the status of an upload session.
     * @param uploadId The upload session ID.
     * @return Status information for the upload session.
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String uploadId) {
        return ResponseEntity.ok(sessionManager.getStatus(uploadId));
    }

    /**
     * Abort and clean up an in-progress upload session.
     * @param uploadId The upload session ID.
     * @return HTTP 204 No Content if successful.
     */
    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> abort(@PathVariable String uploadId) {
        uploadService.removeFilename(uploadId);
        sessionManager.endSession(uploadId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles SecurityException and returns a 401 Unauthorized response.
     * @param e The SecurityException that was thrown.
     * @return A response entity with a 401 status and the exception message.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    /**
     * Validate the init request for required fields and values.
     * @param req The init request object.
     * @throws IllegalArgumentException If any required field is missing or invalid.
     */
    private void validateInitRequest(InitRequest req) {
        if (req.getTotalChunks() <= 0 || req.getChunkSize() <= 0 || req.getFileSize() <= 0 || !StringUtils.hasText(req.getFilename())) {
            throw new IllegalArgumentException("totalChunks, chunkSize, fileSize, and filename must be provided");
        }
    }

    /**
     * Get or create a new upload session ID.
     * @param uploadId The provided uploadId, or null/empty for new.
     * @return The upload session ID.
     */
    private String getOrCreateUploadId(String uploadId) {
        return StringUtils.hasText(uploadId) ? uploadId : java.util.UUID.randomUUID().toString();
    }

    /**
     * Find the next missing chunk number in the bitset.
     * @param bitset The bitset representing received chunks.
     * @param totalChunks The total number of chunks.
     * @return The next missing chunk number, or -1 if all are present.
     */
    private int findNextMissingChunk(byte[] bitset, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            if ((bitset[i / 8] & (1 << (i % 8))) == 0) return i + 1;
        }
        return -1;
    }
}
