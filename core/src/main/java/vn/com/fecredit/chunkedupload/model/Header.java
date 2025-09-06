package vn.com.fecredit.chunkedupload.model;

/**
 * Represents the metadata header of a partial upload file.
 */
public class Header {
    /** The total number of chunks for this upload */
    public final int totalChunks;
    /** The size of each chunk in bytes */
    public final int chunkSize;
    /** The total size of the file in bytes */
    public final long fileSize;
    /** Bitset tracking uploaded chunks (1 = received, 0 = pending) */
    public final byte[] bitset;

    /**
     * Creates a new header with the given parameters.
     *
     * @param totalChunks Number of chunks for the upload
     * @param chunkSize   Size of each chunk in bytes
     * @param fileSize    Total file size in bytes
     * @param bitset      Byte array for tracking uploaded chunks
     */
    public Header(int totalChunks, int chunkSize, long fileSize, byte[] bitset) {
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.fileSize = fileSize;
        this.bitset = bitset;
    }
}
