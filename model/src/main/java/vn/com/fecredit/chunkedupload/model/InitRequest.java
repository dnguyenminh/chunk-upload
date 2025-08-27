package vn.com.fecredit.chunkedupload.model;

/**
 * Represents the request to initialize a chunked upload.
 */
public class InitRequest {
    /**
     * The unique identifier for the upload. Can be null if a new upload is being started.
     */
    private String uploadId;
    /**
     * The total number of chunks the file will be split into.
     */
    private int totalChunks;
    /**
     * The size of each chunk in bytes.
     */
    private int chunkSize;
    /**
     * The total size of the file in bytes.
     */
    private long fileSize;
    /**
     * The name of the file being uploaded.
     */
    private String filename;

    /**
     * Gets the upload ID.
     * @return The upload ID, or null if starting a new upload.
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * Sets the upload ID.
     * @param uploadId The upload ID to set.
     */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /**
     * Gets the total number of chunks.
     * @return The total number of chunks.
     */
    public int getTotalChunks() {
        return totalChunks;
    }

    /**
     * Sets the total number of chunks.
     * @param totalChunks The total number of chunks.
     */
    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    /**
     * Gets the chunk size in bytes.
     * @return The chunk size in bytes.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Sets the chunk size in bytes.
     * @param chunkSize The chunk size in bytes.
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Gets the total file size in bytes.
     * @return The total file size in bytes.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the total file size in bytes.
     * @param fileSize The total file size in bytes.
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets the filename being uploaded.
     * @return The filename.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the filename being uploaded.
     * @param filename The filename.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }
}
