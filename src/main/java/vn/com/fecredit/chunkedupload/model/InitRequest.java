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

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
