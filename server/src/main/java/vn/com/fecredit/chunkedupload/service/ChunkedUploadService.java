package vn.com.fecredit.chunkedupload.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;

/**
 * Service for handling low-level file operations for chunked uploads.
 * <p>
 * Manages the lifecycle of a file upload, including creating temporary part files,
 * assembling the final file, and managing directories for in-progress and completed uploads.
 * </p>
 */
@Service
public class ChunkedUploadService {
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)
    /**
     * Reads chunkSize from the header of the partial file.
     * @param partPath Path to the partial file.
     * @return The chunk size in bytes.
     * @throws IOException If an I/O error occurs.
     */
    public int getChunkSizeFromHeader(Path partPath) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "r");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            return h.chunkSize;
        }
    }

    /**
     * Reads totalChunks from the header of the partial file.
     * @param partPath Path to the partial file.
     * @return The total number of chunks.
     * @throws IOException If an I/O error occurs.
     */
    public int getTotalChunksFromHeader(Path partPath) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "r");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            return h.totalChunks;
        }
    }

    /**
     * Reads fileSize from the header of the partial file.
     * @param partPath Path to the partial file.
     * @return The file size in bytes.
     * @throws IOException If an I/O error occurs.
     */
    public long getFileSizeFromHeader(Path partPath) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "r");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            return h.fileSize;
        }
    }

    private final Path inProgressDir;
    private final Path completeDir;
    private final int defaultChunkSize;
    private final ConcurrentHashMap<String, String> uploadFilenames = new ConcurrentHashMap<>();

    /**
     * Initializes the service by creating the necessary directories for in-progress and completed uploads.
     * @param inProgressDirPath Directory path for in-progress uploads.
     * @param completeDirPath Directory path for completed uploads.
     * @param defaultChunkSize Default chunk size in bytes.
     * @throws IOException If an I/O error occurs while creating the directories.
     */
    public ChunkedUploadService(
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath,
            @Value("${chunkedupload.complete-dir:uploads/complete}") String completeDirPath,
            @Value("${chunkedupload.chunk-size:524288}") int defaultChunkSize,
            vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository
    ) throws IOException {
        this.inProgressDir = Paths.get(inProgressDirPath);
        this.completeDir = Paths.get(completeDirPath);
        this.defaultChunkSize = defaultChunkSize;
        this.tenantAccountRepository = tenantAccountRepository;
        Files.createDirectories(this.inProgressDir);
        Files.createDirectories(this.completeDir);
    }

    public int getDefaultChunkSize() {
        return defaultChunkSize;
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
     * Removes the stored filename for an upload session.
     * @param uploadId The unique identifier for the upload.
     */
    public void removeFilename(String uploadId) {
        uploadFilenames.remove(uploadId);
    }

    /**
     * Constructs the path to the temporary partial file for an upload.
     * @param uploadId The unique identifier for the upload.
     * @return The {@link Path} to the partial file.
     */
    /**
     * Constructs the path to the temporary partial file for an upload, under the tenant account folder.
     * @param tenantAccountId The tenant account ID.
     * @param uploadId The unique identifier for the upload.
     * @return The {@link Path} to the partial file.
     */
    private final vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> tenantIdCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /* Removed legacy constructor: all final fields are now always initialized via the main constructor */
    
    public Path getPartPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            System.out.println("[DEBUG] Cache miss for tenantAccountId=" + username + ", querying DB...");
            return tenantAccountRepository.findByUsername(username)
                .map(vn.com.fecredit.chunkedupload.model.TenantAccount::getId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        System.out.println("[DEBUG] Using tenant DB id=" + dbId + " for username=" + tenantAccountId);
        return inProgressDir.resolve(String.valueOf(dbId)).resolve(uploadId + ".part");
    }

    /**
     * Constructs the final destination path for a completed file.
     * @param uploadId The unique identifier for the upload.
     * @return The final {@link Path} for the completed file.
     */
    public Path getFinalPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            System.out.println("[DEBUG] Cache miss for tenantAccountId=" + username + ", querying DB for final path...");
            return tenantAccountRepository.findByUsername(username)
                .map(vn.com.fecredit.chunkedupload.model.TenantAccount::getId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        String filename = getFilename(uploadId);
        Path finalPath = completeDir.resolve(String.valueOf(dbId)).resolve(uploadId + (StringUtils.hasText(filename) ? ("_" + filename) : ""));
        System.out.println("[DEBUG] Final path for completed file: " + finalPath);
        return finalPath;
    }

    /**
     * Creates and writes a header to a new partial file or validates the header of an existing one.
     * The file is also pre-allocated to its full expected size.
     * <p>Header format (Big Endian):
     * - Magic Number (4 bytes): 0xCAFECAFE
     * - Total Chunks (4 bytes)
     * - Chunk Size (4 bytes)
     * - File Size (8 bytes)
     * - Bitset (variable bytes)
     * </p>
     * @param partPath The path to the partial file.
     * @param totalChunks The total number of chunks.
     * @param chunkSize The size of each chunk in bytes.
     * @param fileSize The total size of the original file.
     * @throws IOException If an I/O error occurs.
     * @throws IllegalStateException If the file exists but its header parameters do not match.
     */
    public void createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize) throws IOException {
        int bitsetBytes = (totalChunks + 7) / 8;
        int headerSize = 4 + 4 + 4 + 8 + bitsetBytes;
        java.nio.file.Path parentDir = partPath.getParent();
        if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
            System.out.println("[DEBUG] Creating parent directory for upload part: " + parentDir);
            java.nio.file.Files.createDirectories(parentDir);
        }
        System.out.println("[DEBUG] Creating or validating upload part file: " + partPath);
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
                ch.write(header, 0);
                raf.setLength(headerSize + fileSize);
            } else {
                // File exists, validate its header
                var h = readHeader(ch);
                if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
                    System.out.println("[ERROR] Existing upload file header mismatch: " +
                        "header.totalChunks=" + h.totalChunks + ", expected=" + totalChunks +
                        "; header.chunkSize=" + h.chunkSize + ", expected=" + chunkSize +
                        "; header.fileSize=" + h.fileSize + ", expected=" + fileSize);
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
        var fixed = java.nio.ByteBuffer.allocate(PART_FILE_HEADER_FIXED_SIZE).order(java.nio.ByteOrder.BIG_ENDIAN);
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

        public Header(int totalChunks, int chunkSize, long fileSize, byte[] bitset) {
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.fileSize = fileSize;
            this.bitset = bitset;
        }
    }

    /**
     * Writes a single data chunk to the correct position in the partial file.
     * @param partPath The path to the partial file.
     * @param chunkNumber The 0-based index of the chunk to write.
     * @param chunkSize The size of the chunk.
     * @param data The byte array containing the chunk's data.
     * @throws IOException If an I/O error occurs.
     */
    public void writeChunk(Path partPath, int chunkNumber, int chunkSize, byte[] data) throws IOException {
        int idx = chunkNumber;
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            long headerSize = PART_FILE_HEADER_FIXED_SIZE + h.bitset.length;
            long offset = headerSize + (long) idx * h.chunkSize;
            ch.position(offset);
            int bytesWritten = ch.write(java.nio.ByteBuffer.wrap(data, 0, data.length));
            long partFileSize = raf.length();
            System.out.println("[DEBUG] writeChunk: partPath=" + partPath +
                ", chunkNumber=" + chunkNumber +
                ", chunkSize=" + chunkSize +
                ", data.length=" + (data != null ? data.length : "null") +
                ", offset=" + offset +
                ", headerSize=" + headerSize +
                ", originalFileSize=" + h.fileSize +
                ", partFileSize=" + partFileSize +
                " ==> partFileSize should equal originalFileSize + headerSize");
        } catch (Exception e) {
            System.out.println("[ERROR] Exception in writeChunk: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
            throw e;
        }
    }

    /**
     * Assembles the final file by copying the data portion from the partial file.
     * @param partPath The path to the partial file.
     * @param finalPath The destination path for the final, assembled file.
     * @param fileSize The total size of the file.
     * @param bitset The bitset indicating which chunks have been received.
     * @throws IOException If an I/O error occurs during file transfer.
     */
    public void assembleFile(Path partPath, Path finalPath, long fileSize, byte[] bitset) throws IOException {
        long headerSize = 20 + bitset.length;
        Path parentDir = finalPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            System.out.println("[DEBUG] Creating parent directory for completed file: " + parentDir);
            Files.createDirectories(parentDir);
        }
        System.out.println("[DEBUG] Assembling file: partPath=" + partPath + ", finalPath=" + finalPath);
        try (var src = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ);
             var dst = FileChannel.open(finalPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                 long bytesCopied = 0;
                 long position = headerSize;
                 long remaining = fileSize;
                 int bufferSize = 8192;
                 ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                 while (remaining > 0) {
                     buffer.clear();
                     int bytesToRead = (int) Math.min(bufferSize, remaining);
                     int read = src.read(buffer, position);
                     if (read <= 0) break;
                     buffer.flip();
                     dst.write(buffer);
                     position += read;
                     remaining -= read;
                     bytesCopied += read;
                 }
                 System.out.println("[DEBUG] Bytes copied to completed file: " + bytesCopied + ", expected: " + fileSize);
                 System.out.println("[DEBUG] Completed file exists: " + Files.exists(finalPath) + ", size: " + Files.size(finalPath));
             }
        // Delete part file after successful assembly
        try {
            Files.deleteIfExists(partPath);
            System.out.println("[DEBUG] Deleted part file after assembly: " + partPath);
        } catch (IOException e) {
            System.out.println("[ERROR] Failed to delete part file: " + partPath + " - " + e.getMessage());
        }
    }
}
