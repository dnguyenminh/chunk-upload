package vn.com.fecredit.chunkedupload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response returned by the server when initializing or resuming an upload.
 *
 * <p>
 * This class encapsulates:
 * <ul>
 * <li>Upload session identification and configuration</li>
 * <li>File metadata and properties</li>
 * <li>Chunk tracking information</li>
 * <li>Resume state for interrupted uploads</li>
 * </ul>
 *
 * <p>
 * Usage examples:
 * <pre>
 * // New upload response
 * InitResponse resp = new InitResponse(uploadId, totalChunks, chunkSize, fileSize, filename);
 *
 * // Resume response with missing chunks
 * resp.setMissingChunkNumbers(Arrays.asList(1, 4, 7));
 * </pre>
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

    /** Bitset bytes for chunk status tracking. */
    private byte[] bitsetBytes;

    /** Optional checksum (e.g. SHA-256) reported by server for integrity checks. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String checksum;


    /**
     * Optional list of missing chunk indices when resuming an upload. Clients
     * should only re-upload the chunks listed here.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Integer> missingChunkNumbers;

    /**
     * Creates a new upload session response with bitset tracking.
     *
     * @param uploadId    Unique identifier for this upload session
     * @param totalChunks Total number of chunks expected
     * @param chunkSize   Size of each chunk in bytes
     * @param fileSize    Total size of the file in bytes
     * @param filename    Original name of the file
     * @param bitsetBytes Byte array tracking uploaded chunks (1=received, 0=pending)
     */
    public InitResponse(String uploadId, int totalChunks, int chunkSize, long fileSize, String filename, byte[] bitsetBytes) {
        this.uploadId = uploadId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.filename = filename;
        this.bitsetBytes = bitsetBytes;
    }
    /**
     * Default constructor for JSON deserialization.
     */
    public InitResponse() {
    }

    /**
     * Creates a new upload session response without bitset tracking.
     *
     * <p>
     * This constructor is typically used for:
     * <ul>
     * <li>New upload sessions that haven't received any chunks</li>
     * <li>Lightweight responses that don't need chunk tracking</li>
     * </ul>
     *
     * @param uploadId    Unique identifier for this upload session
     * @param totalChunks Total number of chunks expected
     * @param chunkSize   Size of each chunk in bytes
     * @param fileSize    Total size of the file in bytes
     * @param filename    Original name of the file
     */
    public InitResponse(String uploadId, int totalChunks, int chunkSize, long fileSize, String filename) {
        this.uploadId = uploadId;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.filename = filename;
    }

    /**
     * Gets the unique identifier for this upload session.
     *
     * @return The upload session ID
     */
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    /**
     * Gets the byte array tracking which chunks have been uploaded.
     * Each bit represents one chunk (1=received, 0=pending).
     *
     * @return The bitset byte array for chunk tracking
     */
    public byte[] getBitsetBytes() { return bitsetBytes; }
    public void setBitsetBytes(byte[] bitsetBytes) { this.bitsetBytes = bitsetBytes; }
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
