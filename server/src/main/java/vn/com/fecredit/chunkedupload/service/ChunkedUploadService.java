package vn.com.fecredit.chunkedupload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import vn.com.fecredit.chunkedupload.model.UploadInfoRepository;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;
import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling low-level file operations for chunked uploads.
 * <p>
 * Manages the lifecycle of a file upload, including creating temporary part
 * files,
 * assembling the final file, and managing directories for in-progress and
 * completed uploads.
 * </p>
 */
@Service

public class ChunkedUploadService {
    @Autowired
    private UploadInfoRepository uploadInfoRepository;

    @Autowired
    private TenantAccountRepository tenantAccountRepository;
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    /**
     * Finds a tenant account by username.
     *
     * @param username The username to search for
     * @return The TenantAccount if found, otherwise null
     */
    /**
     * Finds a tenant account by username.
     *
     * @param username the username to search for
     * @return the TenantAccount if found, otherwise null
     */
    public TenantAccount findTenantByUsername(String username) {
        return tenantAccountRepository.findByUsername(username).orElse(null);
    }

    /**
     * Saves the given upload information to the repository.
     *
     * @param info the UploadInfo object to save
     */
    public void saveUploadInfo(UploadInfo info) {
        uploadInfoRepository.save(info);
    }

    /**
     * Reads chunkSize from the header of the partial file.
     *
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
     *
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
     *
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
     * Finds UploadInfo by tenant username and uploadId.
     */
    public UploadInfo findUploadInfoByTenantAndUploadId(String username, String uploadId) {
        TenantAccount tenant = tenantAccountRepository.findByUsername(username).orElse(null);
        if (tenant == null)
            return null;
        return uploadInfoRepository.findByTenantAndUploadId(tenant, uploadId).orElse(null);
    }

    /**
     * Reads bitsetBytes from the header of the partial file.
     */
    public byte[] readBitsetBytesFromHeader(Path partPath) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "r");
                var ch = raf.getChannel()) {
            var header = readHeader(ch);
            return header.bitset;
        }
    }

    /**
     * Initializes the service by creating the necessary directories for in-progress
     * and completed uploads.
     *
     * @param inProgressDirPath Directory path for in-progress uploads.
     * @param completeDirPath   Directory path for completed uploads.
     * @param defaultChunkSize  Default chunk size in bytes.
     * @throws IOException If an I/O error occurs while creating the directories.
     */
    public ChunkedUploadService(
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath,
            @Value("${chunkedupload.complete-dir:uploads/complete}") String completeDirPath,
            @Value("${chunkedupload.chunk-size:524288}") int defaultChunkSize,
            vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository) throws IOException {
        this.inProgressDir = Paths.get(inProgressDirPath);
        this.completeDir = Paths.get(completeDirPath);
        this.defaultChunkSize = defaultChunkSize;
        this.tenantAccountRepository = tenantAccountRepository;
        Files.createDirectories(this.inProgressDir);
        Files.createDirectories(this.completeDir);
    }

    /**
     * Gets the configured default chunk size for uploads.
     *
     * @return The default chunk size in bytes
     */
    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    /**
     * Stores the original filename associated with an upload ID.
     *
     * @param uploadId The unique identifier for the upload.
     * @param filename The original name of the file being uploaded.
     */
    public void storeFilename(String uploadId, String filename) {
        uploadFilenames.put(uploadId, filename);
    }

    /**
     * Retrieves the original filename for a given upload ID.
     *
     * @param uploadId The unique identifier for the upload.
     * @return The original filename, or {@code null} if no filename is associated
     *         with the ID.
     */
    public String getFilename(String uploadId) {
        return uploadFilenames.get(uploadId);
    }

    /**
     * Removes the stored filename for an upload session.
     *
     * @param uploadId The unique identifier for the upload.
     */
    public void removeFilename(String uploadId) {
        uploadFilenames.remove(uploadId);
    }

    /**
     * Constructs the path to the temporary partial file for an upload, under the
     * tenant account folder.
     */

    // private final vn.com.fecredit.chunkedupload.model.TenantAccountRepository
    // tenantAccountRepository; // Remove duplicate
    private final java.util.concurrent.ConcurrentHashMap<String, Long> tenantIdCache = new java.util.concurrent.ConcurrentHashMap<>();

    /*
     * Removed legacy constructor: all final fields are now always initialized via
     * the main constructor
     */

    public Path getPartPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            log.debug("Cache miss for tenantAccountId={}, querying DB...", username);
            return tenantAccountRepository.findByUsername(username)
                    .map(vn.com.fecredit.chunkedupload.model.TenantAccount::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        log.debug("Using tenant DB id={} for username={}", dbId, tenantAccountId);
        return inProgressDir.resolve(String.valueOf(dbId)).resolve(uploadId + ".part");
    }

    /**
     * Constructs the final destination path for a completed file.
     *
     * @param uploadId The unique identifier for the upload.
     * @return The final {@link Path} for the completed file.
     */
    public Path getFinalPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            log.debug("Cache miss for tenantAccountId={}, querying DB for final path...", username);
            return tenantAccountRepository.findByUsername(username)
                    .map(vn.com.fecredit.chunkedupload.model.TenantAccount::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        String filename = getFilename(uploadId);
        Path finalPath = completeDir.resolve(String.valueOf(dbId))
                .resolve(uploadId + (StringUtils.hasText(filename) ? ("_" + filename) : ""));
        log.debug("Final path for completed file: {}", finalPath);
        return finalPath;
    }

    /**
     * Creates and writes a header to a new partial file or validates the header of
     * an existing one.
     * The file is also pre-allocated to its full expected size.
     * <p>
     * Header format (Big Endian):
     * - Magic Number (4 bytes): 0xCAFECAFE
     * - Total Chunks (4 bytes)
     * - Chunk Size (4 bytes)
     * - File Size (8 bytes)
     * - Bitset (variable bytes)
     * </p>
     *
     * @param partPath    The path to the partial file.
     * @param totalChunks The total number of chunks.
     * @param chunkSize   The size of each chunk in bytes.
     * @param fileSize    The total size of the original file.
     * @throws IOException           If an I/O error occurs.
     * @throws IllegalStateException If the file exists but its header parameters do
     *                               not match.
     */
    public Header createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize)
            throws IOException {
        int bitsetBytes = (totalChunks + 7) / 8;
        int headerSize = 4 + 4 + 4 + 8 + bitsetBytes;

        createParentDirectory(partPath);
        log.debug("Creating or validating upload part file: {}", partPath);

        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
                var ch = raf.getChannel()) {
            if (ch.size() == 0) {
                raf.setLength(headerSize + fileSize);
                return createHeader(ch, headerSize, totalChunks, chunkSize, fileSize, bitsetBytes);
            } else {
                return validateHeader(ch, totalChunks, chunkSize, fileSize);
            }
        }
    }

    /**
     * Creates the parent directory for a part file if it doesn't exist.
     *
     * @param partPath Path to the part file
     * @throws IOException If directory creation fails
     */
    private void createParentDirectory(Path partPath) throws IOException {
        java.nio.file.Path parentDir = partPath.getParent();
        if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
            log.debug("Creating parent directory for upload part: {}", parentDir);
            java.nio.file.Files.createDirectories(parentDir);
        }
    }

    /**
     * Creates and writes a new header to a file channel.
     *
     * @param ch          The file channel to write to
     * @param headerSize  Total size of header in bytes
     * @param totalChunks Total number of chunks
     * @param chunkSize   Size of each chunk in bytes
     * @param fileSize    Total file size in bytes
     * @param bitsetBytes Size of bitset in bytes
     * @return The created header object
     * @throws IOException If header write fails
     */
    private Header createHeader(FileChannel ch, int headerSize, int totalChunks, int chunkSize, long fileSize,
            int bitsetBytes) throws IOException {
        var header = java.nio.ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        header.putInt(0xCAFECAFE); // Magic number
        header.putInt(totalChunks);
        header.putInt(chunkSize);
        header.putLong(fileSize);
        byte[] bitset = new byte[bitsetBytes];
        // Fill bits greater than totalChunks with 1
        BitsetUtil.setUnusedBits(bitset, totalChunks);
        header.put(bitset); // Bitset with extra bits set to 1
        header.flip();
        int bytesWritten = ch.write(header, 0);
        if (bytesWritten != headerSize) {
            log.warn("Not all header bytes written: expected={}, actual={}", headerSize, bytesWritten);
        }
        // Return Header instance
        return new Header(totalChunks, chunkSize, fileSize, bitset);
    }

    /**
     * Validates an existing header matches expected values.
     *
     * @param ch          The file channel to read from
     * @param totalChunks Expected total number of chunks
     * @param chunkSize   Expected chunk size
     * @param fileSize    Expected total file size
     * @return The validated header object
     * @throws IOException If validation fails
     */
    private Header validateHeader(FileChannel ch, int totalChunks, int chunkSize, long fileSize) throws IOException {
        var h = readHeader(ch);
        if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
            log.error(
                    "Existing upload file header mismatch: header.totalChunks={}, expected={}; header.chunkSize={}, expected={}; header.fileSize={}, expected={}",
                    h.totalChunks, totalChunks, h.chunkSize, chunkSize, h.fileSize, fileSize);
            throw new IllegalStateException("Existing upload file has different parameters");
        }
        return h;
    }

    /**
     * Reads the header from a partial file channel.
     *
     * @param ch The {@link FileChannel} of the partial file.
     * @return A {@link Header} object containing the file's metadata.
     * @throws IOException If an I/O error occurs or the file magic number is
     *                     incorrect.
     */
    public Header readHeader(FileChannel ch) throws IOException {
        var fixed = java.nio.ByteBuffer.allocate(PART_FILE_HEADER_FIXED_SIZE).order(java.nio.ByteOrder.BIG_ENDIAN);
        ch.read(fixed, 0);
        fixed.flip();
        int magic = fixed.getInt();
        if (magic != 0xCAFECAFE)
            throw new IOException("Bad magic in upload file header");
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

    /**
     * Writes a single data chunk to the correct position in the partial file.
     *
     * @param partPath    The path to the partial file.
     * @param chunkNumber The 0-based index of the chunk to write.
     * @param chunkSize   The size of the chunk.
     * @param data        The byte array containing the chunk's data.
     * @throws IOException If an I/O error occurs.
     */
    public void writeChunk(Path partPath, int chunkNumber, int chunkSize, byte[] data) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
                var ch = raf.getChannel()) {
            var h = readHeader(ch);
            long headerSize = PART_FILE_HEADER_FIXED_SIZE + h.bitset.length;
            // Use actual chunkSize for single-chunk uploads
            long offset = headerSize + chunkNumber * h.chunkSize;
            ch.position(offset);
            log.debug("writeChunk: chunkNumber={}, data[0]={}, data.length={}", chunkNumber,
                    (data.length > 0 ? data[0] : "empty"), data.length);
            int bytesWritten = ch.write(ByteBuffer.wrap(data, 0, data.length));
            if (bytesWritten != data.length) {
                log.warn("Not all bytes written for chunk {}: expected={}, actual={}", chunkNumber, data.length,
                        bytesWritten);
            }
            // Update bitset for this chunk
            BitsetUtil.setUsedBit(h.bitset, chunkNumber);
            // Write updated header back to file
            ch.position(0);
            writeHeader(ch, h);
            long partFileSize = raf.length();
            log.debug(
                    "writeChunk: partPath={}, chunkNumber={}, chunkSize={}, data.length={}, offset={}, headerSize={}, originalFileSize={}, partFileSize={} ==> partFileSize should equal originalFileSize + headerSize",
                    partPath, chunkNumber, chunkSize, data.length, offset, headerSize, h.fileSize, partFileSize);
        } catch (Exception e) {
            log.error("Exception in writeChunk: {}: {}", e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Writes the header (including bitset) to the start of the file channel.
     */
    /**
     * Writes a header back to the file channel.
     *
     * @param ch The file channel to write to
     * @param h  The header to write
     * @throws IOException If header write fails
     */
    private void writeHeader(java.nio.channels.FileChannel ch, Header h) throws IOException {
        int bitsetBytes = h.bitset.length;
        int headerSize = 4 + 4 + 4 + 8 + bitsetBytes;
        var headerBuf = java.nio.ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(0xCAFECAFE); // Magic number
        headerBuf.putInt(h.totalChunks);
        headerBuf.putInt(h.chunkSize);
        headerBuf.putLong(h.fileSize);
        headerBuf.put(h.bitset);
        headerBuf.flip();
        int bytesWritten = ch.write(headerBuf, 0);
        if (bytesWritten != headerSize) {
            log.warn("Not all header bytes written: expected={}, actual={}", headerSize, bytesWritten);
        }
    }
    /**
     * Assembles the final file by copying the data portion from the partial file.
     *
     * @param partPath  The path to the partial file.
     * @param finalPath The destination path for the final, assembled file.
     * @param fileSize  The total size of the file.
     * @param bitset    The bitset indicating which chunks have been received.
     * @throws IOException If an I/O error occurs during file transfer.
     */
    public void assembleFile(Path partPath, Path finalPath, Header header) throws IOException {
        long fileSize = header.fileSize;
        byte[] bitset = header.bitset;
        long headerSize = 20 + bitset.length;
        log.debug("assembleFile: partPath={}, finalPath={}, expected fileSize={}, headerSize={}", partPath, finalPath,
                fileSize, headerSize);
        Path parentDir = finalPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            log.debug("Creating parent directory for completed file: {}", parentDir);
            Files.createDirectories(parentDir);
        }
        log.debug("Assembling file: partPath={}, finalPath={}", partPath, finalPath);
        try (var src = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ);
                var dst = FileChannel.open(finalPath, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {                            
            src.position(headerSize);
            long bytesCopied = dst.transferFrom(src, 0, src.size() - headerSize);
            log.debug("Bytes copied to completed file: {}, expected: {}", bytesCopied, fileSize);
            log.debug("Completed file exists: {}, size: {}", Files.exists(finalPath), Files.size(finalPath));
            if (Files.size(finalPath) != fileSize) {
                log.error("File size mismatch after assembly: actual={}, expected={}", Files.size(finalPath), fileSize);
            }
        }
        // Validate checksum after file assembly
        try {
            UploadInfo info = uploadInfoRepository.findByUploadId(partPath.getFileName().toString()).orElse(null);
            if (info != null) {
                String expectedChecksum = info.getChecksum();
                String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
                log.debug("Checksum validation: expected={}, actual={}", expectedChecksum, actualChecksum);
                if (!expectedChecksum.equals(actualChecksum)) {
                    log.error("Checksum mismatch after file assembly: expected={}, actual={}", expectedChecksum,
                            actualChecksum);
                    throw new IOException("Checksum mismatch after file assembly");
                }
            }
        } catch (Exception e) {
            log.error("Checksum validation failed: {}", e.getMessage());
            throw new IOException("Checksum validation failed", e);
        }
        // Delete part file after successful assembly
        try {
            Files.deleteIfExists(partPath);
            log.debug("Deleted part file after assembly: {}", partPath);
        } catch (IOException e) {
            log.error("Failed to delete part file: {} - {}", partPath, e.getMessage());
        }
        // Delete UploadInfo entity after successful assembly
        try {
            UploadInfo info = uploadInfoRepository.findByUploadId(partPath.getFileName().toString()).orElse(null);
            if (info != null) {
                uploadInfoRepository.delete(info);
                log.debug("Deleted UploadInfo entity for uploadId: {}", partPath.getFileName().toString());
            }
        } catch (Exception e) {
            log.error("Failed to delete UploadInfo entity for uploadId {}: {}", partPath.getFileName().toString(),
                    e.getMessage());
        }
    }


}
