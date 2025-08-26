package vn.com.fecredit.chunkedupload.model;

/**
 * Represents the request to initialize a chunked upload.
 */
public class InitRequest {
    /**
     * The unique identifier for the upload. Can be null if a new upload is being started.
     */
    public String uploadId;
    /**
     * The total number of chunks the file will be split into.
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
     * The name of the file being uploaded.
     */
    public String filename;
}
