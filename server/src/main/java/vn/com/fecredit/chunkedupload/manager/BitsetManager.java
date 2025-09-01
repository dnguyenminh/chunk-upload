package vn.com.fecredit.chunkedupload.manager;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of chunked uploads using bitsets.
 * <p>
 * Tracks which chunks of a file have been successfully uploaded using a bitset (byte array) for each session.
 * Each bit corresponds to a single chunk, allowing efficient tracking of upload progress.
 * </p>
 */
@Component
public class BitsetManager {
    /**
     * In-memory cache for upload bitsets.
     * The key is the string representation of the partial file's path ({@link Path#toString()}),
     * and the value is the byte array representing the bitset.
     */
    private final ConcurrentHashMap<String, byte[]> bitsets = new ConcurrentHashMap<>();

    /**
     * Marks a chunk as received and checks if the entire file upload is complete.
     * Thread-safe. Computes the bitset if absent, sets the bit for the received chunk, and checks if all chunks are present.
     * @param partPath The path to the partial file being assembled.
     * @param chunkNumber The 0-based index of the chunk that was just uploaded.
     * @param totalChunks The total number of chunks for the file.
     * @return {@code true} if all chunks have been uploaded, {@code false} otherwise.
     */
    public boolean markChunkAndCheckComplete(Path partPath, int chunkNumber, int totalChunks) {
        byte[] bitset = bitsets.computeIfAbsent(partPath.toString(), k -> new byte[(totalChunks + 7) / 8]);
        int idx = chunkNumber;
        // Set the bit for the received chunk
        bitset[idx / 8] |= (byte) (1 << (idx % 8));
        // Check if all bits are set
        for (int i = 0; i < totalChunks; i++) {
            if ((bitset[i / 8] & (1 << (i % 8))) == 0) {
                return false; // Found a missing chunk
            }
        }
        return true; // All chunks are present
    }

    /**
     * Retrieves the bitset for a given upload.
     * If no bitset exists for the given path, a new, empty bitset is created and returned.
     * @param partPath The path to the partial file.
     * @param totalChunks The total number of chunks for the file.
     * @return The byte array representing the bitset for the upload.
     */
    public byte[] getBitset(Path partPath, int totalChunks) {
        return bitsets.getOrDefault(partPath.toString(), new byte[(totalChunks + 7) / 8]);
    }
}
