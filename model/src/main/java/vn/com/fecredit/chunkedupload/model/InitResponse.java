package vn.com.fecredit.chunkedupload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response returned by the server when initializing or resuming an upload.
 * Contains session metadata and (optionally) missing chunk numbers for resume.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitResponse {

    /** Upload session identifier assigned by the server. */
    private String uploadId;
    /** Total number of chunks expected for the file. */
    private int totalChunks;
    /** Chunk size in bytes used by the server for this session. */
    private int chunkSize;
    /** Total file size in bytes. */
    private long fileSize;
    /** Original filename, if provided. */
    private String filename;

    /** Optional checksum (e.g. SHA-256) reported by server for integrity checks. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String checksum;

    /**
     * Optional list of missing chunk indices when resuming an upload. Clients
     * should only re-upload the chunks listed here.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Integer> missingChunkNumbers;

    public InitResponse() {
    }

    public InitResponse(String uploadId, int totalChunks, int chunkSize, long fileSize, String filename) {
        this.uploadId = uploadId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.filename = filename;
    }

    // Getters / setters omitted for brevity but remain functionally identical
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public List<Integer> getMissingChunkNumbers() { return missingChunkNumbers; }
    public void setMissingChunkNumbers(List<Integer> missingChunkNumbers) { this.missingChunkNumbers = missingChunkNumbers; }
}
