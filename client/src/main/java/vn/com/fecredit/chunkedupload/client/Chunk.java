package vn.com.fecredit.chunkedupload.client;

/**
 * Represents a single chunk of a file being uploaded.
 *
 * <p>
 * A chunk contains:
 * <ul>
 * <li>The actual bytes of a file segment (raw data)</li>
 * <li>The chunk's zero-based index in the overall file</li>
 * <li>Thread-safe immutable state management</li>
 * </ul>
 *
 * <p>
 * Thread safety:
 * <ul>
 * <li>All fields are final for thread-safe publication</li>
 * <li>Byte array data is not defensively copied (for performance)</li>
 * <li>Callers must not modify the byte array after chunk creation</li>
 * </ul>
 *
 * <p>
 * Special index values:
 * <ul>
 * <li>-1: Sentinel value indicating end of processing</li>
 * <li>≥0: Normal chunk indices in file order</li>
 * </ul>
 *
 * <p>
 * Usage note: Since byte array data is not copied, callers must ensure
 * they do not modify the array after creating a chunk to maintain
 * thread safety guarantees.
 *
 * @see ChunkedUploadClient#uploadChunks
 */
public class Chunk {
    /**
     * Raw byte data for this chunk.
     * Not defensively copied for performance - callers must not modify after chunk creation.
     */
    private final byte[] data;
    
    /**
     * Zero-based index indicating this chunk's position in the file.
     * Special value -1 indicates a sentinel chunk for worker termination.
     */
    private final int index;

    /**
     * Creates a new chunk with the given data and index.
     *
     * @param data  The raw byte data for this chunk
     * @param index The zero-based index of this chunk in the file
     */
    public Chunk(byte[] data, int index) {
        this.data = data;
        this.index = index;
    }

    /**
     * Gets the raw byte data for this chunk.
     *
     * @return The chunk's data as a byte array
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Gets this chunk's index in the file.
     *
     * @return The zero-based chunk index
     */
    public int getIndex() {
        return index;
    }


}
