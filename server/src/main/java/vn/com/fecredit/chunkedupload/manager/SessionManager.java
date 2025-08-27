package vn.com.fecredit.chunkedupload.manager;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active upload sessions, tracking their state and metadata.
 * <p>
 * Maintains a record of all ongoing chunked uploads using a thread-safe in-memory map.
 * Maps a unique upload ID to the total file size for quick lookups and status checks.
 * </p>
 */
@Component
public class SessionManager {
    /**
     * In-memory store for active upload sessions.
     * The key is the unique upload ID, and the value is the total size of the file in bytes.
     * Using {@link ConcurrentHashMap} ensures thread-safe access.
     */
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    /**
     * Starts and registers a new upload session.
     * If a session with the same upload ID already exists, it will be overwritten.
     * @param uploadId The unique identifier for the upload session.
     * @param fileSize The total size of the file being uploaded in bytes.
     */
    public void startSession(String uploadId, long fileSize) {
        sessions.put(uploadId, fileSize);
    }

    /**
     * Ends and removes an upload session from tracking.
     * Should be called when an upload is completed or aborted to free up resources.
     * @param uploadId The unique identifier for the upload session to be ended.
     */
    public void endSession(String uploadId) {
        sessions.remove(uploadId);
    }

    /**
     * Retrieves the status of a specific upload session.
     * @param uploadId The unique identifier for the upload session.
     * @return A {@link SessionStatus} object containing details about the session.
     *         If the session is not found, the fileSize in the returned status will be 0.
     */
    public SessionStatus getStatus(String uploadId) {
        Long fileSize = sessions.get(uploadId);
        return new SessionStatus(uploadId, fileSize != null ? fileSize : 0);
    }

    /**
     * Checks if an upload session is currently active.
     * @param uploadId The unique identifier for the upload session.
     * @return true if the session is active, false otherwise.
     */
    public boolean isSessionActive(String uploadId) {
        return sessions.containsKey(uploadId);
    }

    /**
     * Represents the status and metadata of an upload session.
     * Typically returned to the client to provide information about an ongoing upload.
     */
    public static class SessionStatus {
        /**
         * The unique identifier for the upload session.
         */
        public String uploadId;
        /**
         * The total size of the file in bytes.
         */
        public long fileSize;

        /**
         * Constructs a new SessionStatus.
         *
         * @param uploadId The unique identifier for the upload.
         * @param fileSize The total size of the file in bytes.
         */
        public SessionStatus(String uploadId, long fileSize) {
            this.uploadId = uploadId;
            this.fileSize = fileSize;
        }
    }
}
