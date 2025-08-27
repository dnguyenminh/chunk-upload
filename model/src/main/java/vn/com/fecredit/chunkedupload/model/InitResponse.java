package vn.com.fecredit.chunkedupload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for upload initialization, containing uploadId and file parameters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitResponse {
    /**
     * The checksum of the file (optional, used for validation).
     */
    private String checksum;
    /**
     * The unique identifier for the upload session.
     */
    @JsonProperty("sessionId")
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
     * No-arg constructor for Jackson deserialization.
     */
    public InitResponse() {
    }

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
    this.checksum = null;
    }

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

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
