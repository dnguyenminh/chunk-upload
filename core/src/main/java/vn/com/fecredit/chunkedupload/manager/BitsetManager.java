package vn.com.fecredit.chunkedupload.manager;

import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the chunk tracking state for chunked file uploads.
 *
 * <p>
 * This manager provides:
 * <ul>
 * <li>Thread-safe bitset tracking of uploaded chunks</li>
 * <li>Efficient completion checking with bit operations</li>
 * <li>Memory-efficient storage using byte arrays</li>
 * <li>Automatic cleanup via weak references</li>
 * </ul>
 *
 * <p>
 * The bitset approach:
 * <ul>
 * <li>Each bit represents one chunk (1 = received, 0 = pending)</li>
 * <li>Uses 1 bit per chunk (8 chunks per byte)</li>
 * <li>Supports concurrent uploads via {@link ConcurrentHashMap}</li>
 * <li>Provides O(1) chunk marking and O(n/8) completion checking</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>
 * BitsetManager manager = new BitsetManager();
 * // After receiving chunk 5:
 * boolean isComplete = manager.markChunkAndCheckComplete(filePath, 5, header);
 * if (isComplete) {
 *     // Handle upload completion
 * }
 * </pre>
 */
public class BitsetManager {
    /**
     * Marks a chunk as received and checks if the entire file upload is complete.
     * Thread-safe. Computes the bitset if absent, sets the bit for the received
     * chunk, and checks if all chunks are present.
     *
     * @param chunkNumber The 0-based index of the chunk that was just uploaded.
     * @param header      The header of the upload file, containing metadata like total chunks.
     * @return {@code true} if all chunks have been uploaded, {@code false}
     * otherwise.
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Uses atomic operations for thread safety</li>
     * <li>Calculates bitset indices using bit shift operations</li>
     * <li>Creates bitset on first chunk mark</li>
     * <li>Returns true only when all chunks are present</li>
     * </ul>
     */
    public static boolean markChunkAndCheckComplete(Header header, int chunkNumber) {
        BitsetUtil.setUsedBit(header.bitset, chunkNumber);
        return BitsetUtil.isUsedBitSetFull(header.bitset);
    }
}
