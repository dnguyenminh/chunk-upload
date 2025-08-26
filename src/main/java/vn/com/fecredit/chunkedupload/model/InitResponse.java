package vn.com.fecredit.chunkedupload.model;

/**
 * Response for upload initialization, containing uploadId and file parameters.
 */
public class InitResponse {
    /**
     * The unique identifier for the upload session.
     */
    public String uploadId;
    /**
     * The total number of chunks the file is divided into.
     */
    public int totalChunks;
    /**
     * The size of each chunk in bytes.
     */
    public int chunkSize;
    /**
     * The total size of the file in bytes.
     */
    public long fileSize;
    /**
     * The name of the file.
     */
    public String filename;

    /**
     * Constructs a new InitResponse.
     * @param uploadId The unique identifier for the upload session.
     * @param totalChunks The total number of chunks.
     * @param chunkSize The size of each chunk.
     * @param fileSize The total size of the file.
     * @param filename The name of the file.
     */
    public InitResponse(String uploadId, int totalChunks, int chunkSize, long fileSize, String filename) {
        this.uploadId = uploadId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.filename = filename;
    }
}
