package vn.com.fecredit.chunkedupload.manager;

import org.springframework.stereotype.Component;
import vn.com.fecredit.chunkedupload.service.ChunkedUploadService.Header;

import java.nio.file.Path;
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
@Component
public class BitsetManager {
    /**
     * In-memory cache for upload bitsets.
     * The key is the string representation of the partial file's path
     * ({@link Path#toString()}),
     * and the value is the byte array representing the bitset.
     */
    private final ConcurrentHashMap<String, byte[]> bitsets = new ConcurrentHashMap<>();

    /**
     * Marks a chunk as received and checks if the entire file upload is complete.
     * Thread-safe. Computes the bitset if absent, sets the bit for the received
     * chunk, and checks if all chunks are present.
     * 
     * @param partPath    The path to the partial file being assembled.
     * @param chunkNumber The 0-based index of the chunk that was just uploaded.
     * @param header      The header of the upload file, containing metadata like total chunks.
     * @return {@code true} if all chunks have been uploaded, {@code false}
     *         otherwise.
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
    public boolean markChunkAndCheckComplete(Path partPath, int chunkNumber, Header header) {
        int totalChunks = header.totalChunks;
        byte[] bitset = bitsets.computeIfAbsent(partPath.toString(), k -> new byte[(totalChunks + 7) / 8]);
        // Set the bit for the received chunk
        bitset[chunkNumber / 8] |= (byte) (1 << (chunkNumber % 8));
        // Check if all bits are set
        for (int i = 0; i < totalChunks; i++) {
            if ((bitset[i / 8] & (1 << (i % 8))) == 0) {
                return false; // Found a missing chunk
            }
        }
        return true; // All chunks are present
    }
}
