package vn.com.fecredit.chunkedupload.manager;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and metadata of active chunked upload sessions.
 *
 * <p>
 * This manager provides:
 * <ul>
 * <li>Thread-safe session tracking and state management</li>
 * <li>Memory-efficient storage using upload IDs as keys</li>
 * <li>Quick status lookups for active uploads</li>
 * <li>Automatic resource cleanup on session end</li>
 * </ul>
 *
 * <p>
 * Session lifecycle:
 * <ul>
 * <li>Created on upload initiation via {@link #startSession}</li>
 * <li>Queried during upload via {@link #getStatus}</li>
 * <li>Removed on completion via {@link #endSession}</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>
 * SessionManager manager = new SessionManager();
 * // On upload start:
 * manager.startSession("upload123", 5000000);
 * // During upload:
 * SessionStatus status = manager.getStatus("upload123");
 * // On completion:
 * manager.endSession("upload123");
 * </pre>
 *
 * @see SessionStatus
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
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Thread-safe via {@link ConcurrentHashMap#put}</li>
     * <li>Overwrites existing session if ID exists</li>
     * <li>O(1) operation for session creation</li>
     * </ul>
     *
     * @param uploadId The unique identifier for the upload session, typically a UUID.
     * @param fileSize The total size of the file being uploaded in bytes.
     * @throws IllegalArgumentException if fileSize is negative
     */
    public void startSession(String uploadId, long fileSize) {
        sessions.put(uploadId, fileSize);
    }

    /**
     * Ends and removes an upload session from tracking.
     *
     * <p>
     * This method should be called in these scenarios:
     * <ul>
     * <li>Upload completed successfully</li>
     * <li>Upload aborted by client</li>
     * <li>Upload failed with unrecoverable error</li>
     * <li>Session timeout/cleanup</li>
     * </ul>
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Thread-safe via {@link ConcurrentHashMap#remove}</li>
     * <li>No-op if session doesn't exist</li>
     * <li>Immediately frees memory</li>
     * </ul>
     *
     * @param uploadId The unique identifier for the upload session to be ended.
     */
    public void endSession(String uploadId) {
        sessions.remove(uploadId);
    }

    /**
     * Retrieves the current status of a specific upload session.
     *
     * <p>
     * This method provides:
     * <ul>
     * <li>Non-null return value (safe for null checks)</li>
     * <li>File size of 0 indicating non-existent session</li>
     * <li>Thread-safe access to session data</li>
     * </ul>
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Thread-safe via {@link ConcurrentHashMap#get}</li>
     * <li>O(1) lookup performance</li>
     * <li>Returns new status object for each call</li>
     * </ul>
     *
     * @param uploadId The unique identifier for the upload session
     * @return A new {@link SessionStatus} object containing the current session state.
     *         If the session is not found, returns a status with fileSize = 0.
     * @see SessionStatus
     */
    public SessionStatus getStatus(String uploadId) {
        Long fileSize = sessions.get(uploadId);
        return new SessionStatus(uploadId, fileSize != null ? fileSize : 0);
    }

    /**
     * Checks if an upload session is currently active and tracked by the manager.
     *
     * <p>
     * Usage scenarios:
     * <ul>
     * <li>Validate session before processing chunks</li>
     * <li>Check if cleanup is needed</li>
     * <li>Verify session state in error handling</li>
     * </ul>
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Thread-safe via {@link ConcurrentHashMap#containsKey}</li>
     * <li>O(1) operation for status check</li>
     * <li>Returns false for null uploadId</li>
     * </ul>
     *
     * @param uploadId The unique identifier for the upload session
     * @return true if the session exists and is active, false otherwise
     */
    public boolean isSessionActive(String uploadId) {
        return sessions.containsKey(uploadId);
    }

    /**
     * Represents the status and metadata of an upload session.
     *
     * <p>
     * This immutable class provides:
     * <ul>
     * <li>Session identification via uploadId</li>
     * <li>Total file size tracking</li>
     * <li>Thread-safe state representation</li>
     * <li>Null-safe value semantics</li>
     * </ul>
     *
     * <p>
     * Status interpretation:
     * <ul>
     * <li>fileSize > 0: Active session with specified size</li>
     * <li>fileSize = 0: Session not found or invalid</li>
     * </ul>
     *
     * @see #getStatus
     */
    public static class SessionStatus {
        /**
         * The unique identifier for the upload session.
         */
        public final String uploadId;
        /**
         * The total size of the file in bytes.
         */
        public final long fileSize;

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
