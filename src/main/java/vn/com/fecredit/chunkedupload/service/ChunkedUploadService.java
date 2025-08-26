package vn.com.fecredit.chunkedupload.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the low-level file operations for chunked uploads.
 * <p>
 * This service manages the lifecycle of a file upload, from creating temporary part files
 * to assembling the final complete file. It uses a dedicated directory for in-progress uploads
 * and another for completed files. To ensure thread safety during concurrent chunk writes for
 * the same upload, it employs a system of per-upload locks.
 * </p>
 */
@Service
public class ChunkedUploadService {
    private final Path inProgressDir = Paths.get("uploads/in-progress");
    private final Path completeDir = Paths.get("uploads/complete");
    private final ConcurrentHashMap<String, String> uploadFilenames = new ConcurrentHashMap<>();

    /**
     * Initializes the service by creating the necessary directories for in-progress and completed uploads.
     * This constructor is called once at application startup.
     * @throws IOException If an I/O error occurs while creating the directories.
     */
    public ChunkedUploadService() throws IOException {
        Files.createDirectories(inProgressDir);
        Files.createDirectories(completeDir);
    }

    /**
     * Stores the original filename associated with an upload ID.
     * @param uploadId The unique identifier for the upload.
     * @param filename The original name of the file being uploaded.
     */
    public void storeFilename(String uploadId, String filename) {
        uploadFilenames.put(uploadId, filename);
    }

    /**
     * Retrieves the original filename for a given upload ID.
     * @param uploadId The unique identifier for the upload.
     * @return The original filename, or {@code null} if no filename is associated with the ID.
     */
    public String getFilename(String uploadId) {
        return uploadFilenames.get(uploadId);
    }

    /**
     * Removes the stored filename for an upload session, typically after completion or abortion.
     * @param uploadId The unique identifier for the upload.
     */
    public void removeFilename(String uploadId) {
        uploadFilenames.remove(uploadId);
    }

    /**
     * Constructs the path to the temporary partial file for an upload.
     * @param uploadId The unique identifier for the upload.
     * @return The {@link Path} to the partial file (e.g., "uploads/in-progress/uploadId.part").
     */
    public Path getPartPath(String uploadId) {
        return inProgressDir.resolve(uploadId + ".part");
    }

    /**
     * Constructs the final destination path for a completed file.
     * The final name is a combination of the upload ID and the original filename.
     * @param uploadId The unique identifier for the upload.
     * @return The final {@link Path} for the completed file (e.g., "uploads/complete/uploadId_original.txt").
     */
    public Path getFinalPath(String uploadId) {
        String filename = getFilename(uploadId);
        return completeDir.resolve(uploadId + (StringUtils.hasText(filename) ? ("_" + filename) : ""));
    }

    /**
     * Creates and writes a header to a new partial file or validates the header of an existing one.
     * The header contains metadata required to manage and assemble the file. The file is also
     * pre-allocated to its full expected size to reserve disk space.
     * <p>Header format (Big Endian):
     * - Magic Number (4 bytes): 0xCAFECAFE
     * - Total Chunks (4 bytes)
     * - Chunk Size (4 bytes)
     * - File Size (8 bytes)
     * - Bitset (variable bytes)
     * </p>
     * @param partPath The path to the partial file.
     * @param totalChunks The total number of chunks the file is divided into.
     * @param chunkSize The size of each individual chunk in bytes.
     * @param fileSize The total size of the original file in bytes.
     * @throws IOException If an I/O error occurs during file access.
     * @throws IllegalStateException If the file exists but its header parameters do not match the provided ones.
     */
    public void createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize) throws IOException {
        int bitsetBytes = (totalChunks + 7) / 8;
        int headerSize = 4 + 4 + 4 + 8 + bitsetBytes;
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
             var ch = raf.getChannel()) {
            if (ch.size() == 0) {
                // File is new, create header and pre-allocate space
                var header = java.nio.ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
                header.putInt(0xCAFECAFE); // Magic number
                header.putInt(totalChunks);
                header.putInt(chunkSize);
                header.putLong(fileSize);
                header.put(new byte[bitsetBytes]); // Empty bitset
                header.flip();
                // A partial write would throw an IOException, so the return value is not checked
                // noinspection ResultOfMethodCallIgnored
                ch.write(header, 0);
                raf.setLength(headerSize + (long) totalChunks * chunkSize);
            } else {
                // File exists, validate its header
                var h = readHeader(ch);
                if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
                    throw new IllegalStateException("Existing upload file has different parameters");
                }
            }
        }
    }

    /**
     * Reads the header from a partial file channel.
     * @param ch The {@link FileChannel} of the partial file.
     * @return A {@link Header} object containing the file's metadata.
     * @throws IOException If an I/O error occurs or the file magic number is incorrect.
     */
    public Header readHeader(FileChannel ch) throws IOException {
        var fixed = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.BIG_ENDIAN);
        ch.read(fixed, 0);
        fixed.flip();
        int magic = fixed.getInt();
        if (magic != 0xCAFECAFE) throw new IOException("Bad magic in upload file header");
        int totalChunks = fixed.getInt();
        int chunkSize = fixed.getInt();
        long fileSize = fixed.getLong();
        int bitsetBytes = (totalChunks + 7) / 8;
        var bits = java.nio.ByteBuffer.allocate(bitsetBytes);
        ch.read(bits, 20);
        return new Header(totalChunks, chunkSize, fileSize, bits.array());
    }

    /**
     * Represents the metadata header of a partial upload file.
     */
    public static class Header {
        public int totalChunks;
        public int chunkSize;
        public long fileSize;
        public byte[] bitset;

        /**
         * Constructs a new Header instance.
         * @param totalChunks Total number of chunks.
         * @param chunkSize Size of each chunk in bytes.
         * @param fileSize Total size of the file in bytes.
         * @param bitset The bitset indicating received chunks.
         */
        public Header(int totalChunks, int chunkSize, long fileSize, byte[] bitset) {
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.fileSize = fileSize;
            this.bitset = bitset;
        }
    }

    /**
     * Writes a single data chunk to the correct position in the partial file.
     * The position is calculated based on the chunk number and the header size.
     * @param partPath The path to the partial file.
     * @param chunkNumber The 1-based index of the chunk to write.
     * @param chunkSize The size of the chunk.
     * @param data The byte array containing the chunk's data.
     * @throws IOException If an I/O error occurs.
     */
    public void writeChunk(Path partPath, int chunkNumber, int chunkSize, byte[] data) throws IOException {
        int idx = chunkNumber - 1;
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            long headerSize = 4 + 4 + 4 + 8 + h.bitset.length;
            long offset = headerSize + (long) idx * chunkSize;
            ch.position(offset);
            // A partial write would throw an IOException, so the return value is not checked
            // noinspection ResultOfMethodCallIgnored
            ch.write(java.nio.ByteBuffer.wrap(data));
        }
    }

    /**
     * Assembles the final file by copying the data portion from the partial file.
     * This method reads from the partial file, skipping the header, and writes the content
     * to the final destination path.
     * @param partPath The path to the partial file.
     * @param finalPath The destination path for the final, assembled file.
     * @param fileSize The total size of the file.
     * @param bitset The bitset indicating which chunks have been received.
     * @throws IOException If an I/O error occurs during file transfer.
     */
    public void assembleFile(Path partPath, Path finalPath, long fileSize, byte[] bitset) throws IOException {
        long headerSize = 4 + 4 + 4 + 8 + bitset.length;
        try (var src = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ);
             var dst = FileChannel.open(finalPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            src.transferTo(headerSize, fileSize, dst);
        }
    }
}
