package vn.com.fecredit.chunkedupload.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import vn.com.fecredit.chunkedupload.manager.BitsetManager;
import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;
import vn.com.fecredit.chunkedupload.model.interfaces.IUploadInfo;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;
import vn.com.fecredit.chunkedupload.port.intefaces.ITenantAccountPort;
import vn.com.fecredit.chunkedupload.port.intefaces.IUploadInfoPort;

public abstract class AbstractChunkedUpload<T extends ITenantAccount, Y extends IUploadInfo, U extends IUploadInfoPort<Y>, V extends ITenantAccountPort<T>> {

    private static final Logger log = LoggerFactory.getLogger(AbstractChunkedUpload.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    @Getter
    private final U iUploadInfoPort;
    @Getter
    private final V iTenantAccountPort;
    @Getter
    private final int defaultChunkSize;
    private final Path inProgressDir;
    private final Path completeDir;
    private final ConcurrentHashMap<String, IUploadInfo> uploadInfoMap = new ConcurrentHashMap<>();
    // Locks for concurrent chunk uploads, one per uploadId
    private final ConcurrentHashMap<String, ReentrantLock> uploadLocks = new ConcurrentHashMap<>();

    public AbstractChunkedUpload(U iUploadInfoPort, V iTenantAccountPort,
                                 String inProgressDirPath, String completeDirPath,
                                 int defaultChunkSize) throws IOException {
        this.iUploadInfoPort = iUploadInfoPort;
        this.iTenantAccountPort = iTenantAccountPort;
        this.defaultChunkSize = defaultChunkSize;
        this.inProgressDir = Paths.get(inProgressDirPath);
        this.completeDir = Paths.get(completeDirPath);
        Files.createDirectories(this.inProgressDir);
        Files.createDirectories(this.completeDir);
    }

    public Y findUploadInfoByTenantAndUploadId(String username, String uploadId) {
        return iTenantAccountPort.findByUsername(username).flatMap(
                tenant -> iUploadInfoPort.findByTenantAndUploadId(tenant, uploadId)
        ).orElse(null);
    }

    public byte[] readBitsetBytesFromHeader(Path partPath) throws IOException {
        try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ)) {
            var header = readHeader(ch);
            return header.bitset;
        }
    }

    protected IUploadInfo getUploadInfo(String uploadId) {
        return uploadInfoMap.get(uploadId);
    }

    public void removeUploadInfo(String uploadId) {
        uploadInfoMap.remove(uploadId);
    }

    private Path getPartPath(String username, String uploadId) throws Throwable {
        T tenantAccount = iTenantAccountPort.findByUsername(username).orElseThrow(() -> new IllegalStateException("Tenant not found for username: " + username));
        Long dbId = tenantAccount.getId();
        return inProgressDir.resolve(String.valueOf(dbId)).resolve(uploadId + ".part");
    }

    private Path getFinalPath(String username, String uploadId) throws Throwable {
        T tenantAccount = iTenantAccountPort.findByUsername(username).orElseThrow(() -> new IllegalStateException("Tenant not found for username: " + username));
        Y uploadInfo = iUploadInfoPort.findByTenantAndUploadId(tenantAccount, uploadId).orElseThrow(() -> new IllegalStateException("UploadInfo not found for tenant id: " + tenantAccount.getId()));
        return completeDir.resolve(String.valueOf(tenantAccount.getId())).resolve(uploadId + "_" + uploadInfo.getFilename());
    }

    public Header createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize) throws IOException {
        int bitsetBytes = (totalChunks + 7) / 8;
        int headerSize = PART_FILE_HEADER_FIXED_SIZE + bitsetBytes;

        createParentDirectory(partPath);
        log.debug("Creating or validating upload part file: {}", partPath);

        try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            if (ch.size() == 0) {
                ch.truncate(headerSize + fileSize);
                return createHeader(ch, headerSize, totalChunks, chunkSize, fileSize, bitsetBytes);
            } else {
                return validateHeader(ch, totalChunks, chunkSize, fileSize);
            }
        }
    }

    private void createParentDirectory(Path partPath) throws IOException {
        Path parentDir = partPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            log.debug("Creating parent directory for upload part: {}", parentDir);
            Files.createDirectories(parentDir);
        }
    }

    private Header createHeader(FileChannel ch, int headerSize, int totalChunks, int chunkSize, long fileSize, int bitsetBytes) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        header.putInt(0xCAFECAFE); // Magic number
        header.putInt(totalChunks);
        header.putInt(chunkSize);
        header.putLong(fileSize);
        byte[] bitset = new byte[bitsetBytes];
        BitsetUtil.setUnusedBits(bitset, totalChunks);
        header.put(bitset);
        header.flip();
        ch.write(header, 0);
        return new Header(totalChunks, chunkSize, fileSize, bitset);
    }

    private Header validateHeader(FileChannel ch, int totalChunks, int chunkSize, long fileSize) throws IOException {
        var h = readHeader(ch);
        if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
            throw new IllegalStateException("Existing upload file has different parameters");
        }
        return h;
    }

    public Header readHeader(String usename, String uploadId) throws Throwable {
        Path filePath = getPartPath(usename, uploadId);
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            return readHeader(ch);
        }
    }

    private Header readHeader(FileChannel ch) throws IOException {
        ByteBuffer fixed = ByteBuffer.allocate(PART_FILE_HEADER_FIXED_SIZE).order(java.nio.ByteOrder.BIG_ENDIAN);
        ch.read(fixed, 0);
        fixed.flip();
        int magic = fixed.getInt();
        if (magic != 0xCAFECAFE) {
            throw new IOException("Bad magic in upload file header");
        }
        int totalChunks = fixed.getInt();
        int chunkSize = fixed.getInt();
        long fileSize = fixed.getLong();
        int bitsetBytes = (totalChunks + 7) / 8;
        ByteBuffer bits = ByteBuffer.allocate(bitsetBytes);
        ch.read(bits, PART_FILE_HEADER_FIXED_SIZE);
        return new Header(totalChunks, chunkSize, fileSize, bits.array());
    }

    private void validateChunkSize(int chunkNumber, Header header, byte[] file) {
        long actualChunkLength = file != null ? file.length : -1;
        boolean isLastChunk = (chunkNumber == header.totalChunks - 1);
        long expectedLastChunkSize = header.fileSize % header.chunkSize;
        if (expectedLastChunkSize == 0) {
            expectedLastChunkSize = header.chunkSize;
        }

        if (header.totalChunks > 1 || actualChunkLength != header.fileSize) {
            if (!isLastChunk && actualChunkLength != header.chunkSize) {
                throw new IllegalArgumentException("Invalid chunk size for chunk " + chunkNumber);
            }
            if (isLastChunk && actualChunkLength != expectedLastChunkSize) {
                throw new IllegalArgumentException("Invalid last chunk size");
            }
        }
    }

    public final Y registerUploadingFile(String username, String uploadId, String fileName, long fileSize, String checksum) throws Throwable {
        Header header = createOrValidateHeader(getPartPath(username, uploadId), (int) Math.ceil((double) fileSize / defaultChunkSize), defaultChunkSize, fileSize);
        Y uploadInfo = createUploadInfo(username, uploadId, header, fileName, checksum);
        try {
            log.debug("Saving upload info to file:" + uploadInfo.getClass().getName());
            // Use abstract save method to avoid interface conflicts
            saveUploadInfo(uploadInfo);
            log.debug("File upload info saved to file:" + uploadInfo.getClass().getName());
        } catch (Exception e) {
            log.debug("Could not save upload info to file: {}", e.getMessage());
        }
        this.uploadInfoMap.put(uploadId, uploadInfo);
        return uploadInfo;
    }

    abstract protected Y createUploadInfo(String username, String uploadId, Header header, String fileName, String checksum) throws Throwable;

    /**
     * Abstract method to save upload info.
     * Concrete implementations should provide the specific save logic.
     *
     * @param uploadInfo The upload info to save
     * @return The saved upload info
     */
    abstract protected Y saveUploadInfo(Y uploadInfo);

    /**
     * Update the lastUpdateDateTime for the upload session.
     * This method should be implemented by concrete classes to update the database.
     *
     * @param uploadId The upload ID to update
     */
    public void updateUploadInfoLastUpdateTime(String uploadId) {
        try {
            iUploadInfoPort.findByUploadId(uploadId).ifPresent(info -> {
                try {
                    Y uploadInfo = (Y) info;
                    // Update lastUpdateDateTime - concrete implementation should handle this
                    updateLastUpdateDateTime(uploadInfo);
                    // Use the abstract save method to avoid interface conflicts
                    saveUploadInfo(uploadInfo);
                    log.debug("Updated lastUpdateDateTime for uploadId={}", uploadId);
                } catch (Exception e) {
                    log.warn("Failed to update lastUpdateDateTime for uploadId={}: {}", uploadId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Error updating upload info last update time for uploadId={}: {}", uploadId, e.getMessage());
        }
    }

    /**
     * Abstract method to update the lastUpdateDateTime field.
     * Concrete implementations should provide the specific logic for their entity type.
     *
     * @param uploadInfo The upload info entity to update
     */
    protected abstract void updateLastUpdateDateTime(Y uploadInfo);

    /**
     * Abstract method to move completed upload to history.
     * Concrete implementations should provide the specific logic for their entity and history types.
     *
     * @param uploadInfo The completed upload info to move to history
     */
    protected abstract void moveToHistory(Y uploadInfo);

    public void writeChunk(String username, String uploadId, int chunkNumber, byte[] data) throws Throwable {
        Path partPath = getPartPath(username, uploadId);
        // Use a per-uploadId lock to serialize access to the file/channel
        ReentrantLock lock = uploadLocks.computeIfAbsent(uploadId, k -> new ReentrantLock());
        lock.lock();
        Header headerRef = null;
        boolean needAssemble = false;
        try {
            // Open and lock the part file, write the chunk, update header, then close channel/lock
            try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                log.debug("Attempting to acquire file lock for uploadId={}, chunkNumber={}, partPath={}", uploadId, chunkNumber, partPath);
                try (FileLock fileLock = ch.lock()) {
                    log.debug("Acquired file lock for uploadId={}, chunkNumber={}, partPath={}", uploadId, chunkNumber, partPath);
                    Header header = readHeader(ch);
                    // Validate chunk number bounds
                    if (chunkNumber < 0 || chunkNumber >= header.totalChunks) {
                        throw new IllegalArgumentException("Invalid chunk number: " + chunkNumber + ", totalChunks: " + header.totalChunks);
                    }
                    validateChunkSize(chunkNumber, header, data);

                    log.debug("Writing chunk: uploadId={}, chunkNumber={}, data.length={}, partPath={}", uploadId, chunkNumber, data != null ? data.length : -1, partPath);

                    long headerSize = PART_FILE_HEADER_FIXED_SIZE + header.bitset.length;
                    long offset = headerSize + (long) chunkNumber * header.chunkSize;
                    ch.write(ByteBuffer.wrap(data), offset);

                    boolean isCompleted = BitsetManager.markChunkAndCheckComplete(header, chunkNumber);
                    writeHeader(ch, header);

                    // Update lastUpdateDateTime in database
                    updateUploadInfoLastUpdateTime(uploadId);

                    // preserve header and assembly decision after channel/lock are closed
                    if (isCompleted) {
                        needAssemble = true;
                        headerRef = header;
                        log.debug("All chunks received for uploadId={}, will assemble after releasing file lock", uploadId);
                    }
                }
                // fileLock and ch are closed here (exiting try-with-resources for ch)
            }

            if (needAssemble) {
                // assemble while still holding the ReentrantLock to prevent concurrent writers
                try {
                    assembleFile(username, uploadId, partPath, headerRef);
                } finally {
                    // Clean up lock map after assembly completes
                    uploadLocks.remove(uploadId);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void writeHeader(FileChannel ch, Header h) throws IOException {
        int bitsetBytes = h.bitset.length;
        int headerSize = PART_FILE_HEADER_FIXED_SIZE + bitsetBytes;
        ByteBuffer headerBuf = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(0xCAFECAFE);
        headerBuf.putInt(h.totalChunks);
        headerBuf.putInt(h.chunkSize);
        headerBuf.putLong(h.fileSize);
        headerBuf.put(h.bitset);
        headerBuf.flip();
        ch.write(headerBuf, 0);
    }

    private void assembleFile(String username, String uploadId, Path partPath, Header header) throws Throwable {
        Path finalPath = getFinalPath(username, uploadId);

        long fileSize = header.fileSize;
        long headerSize = PART_FILE_HEADER_FIXED_SIZE + header.bitset.length;
        createParentDirectory(finalPath);

        Path lockPath = partPath.resolveSibling(partPath.getFileName() + ".lock");

        log.debug("Assembling file for uploadId={}, partPath={}, finalPath={}, lockPath={}, fileSize={}, thread={}, time={}",
                uploadId, partPath, finalPath, lockPath, fileSize, Thread.currentThread().getName(), System.currentTimeMillis());
        // DEBUG: Print actual final file path for comparison with test
        System.out.println("[DEBUG] Server assembled file path: " + finalPath.toAbsolutePath());

        // Acquire lock file for chunk assembly
        int maxAttempts = 30;
        int attempt = 0;
        while (true) {
            try {
                java.nio.file.Files.createFile(lockPath);
                log.debug("Acquired lock file {} for uploadId={}, attempt={}, thread={}, time={}",
                        lockPath, uploadId, attempt, Thread.currentThread().getName(), System.currentTimeMillis());
                break;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    log.error("Failed to acquire lock file {} for uploadId={} after {} attempts", lockPath, uploadId, attempt);
                    throw new IOException("Could not acquire lock file for chunk assembly: " + lockPath);
                }
                log.debug("Lock file {} exists, waiting... uploadId={}, attempt={}, thread={}, time={}",
                        lockPath, uploadId, attempt, Thread.currentThread().getName(), System.currentTimeMillis());
                Thread.sleep(100);
            }
        }

        try {
            try (FileChannel src = FileChannel.open(partPath, StandardOpenOption.READ); FileChannel dst = FileChannel.open(finalPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                log.debug("Opened partPath for assembly, uploadId={}, thread={}, time={}",
                        uploadId, Thread.currentThread().getName(), System.currentTimeMillis());
                src.transferTo(headerSize, fileSize, dst);
            }

            iUploadInfoPort.findByUploadId(uploadId).ifPresent(info -> {
                try {
                    String expectedChecksum = ((Y) info).getChecksum();
                    String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
                    log.debug("Verifying checksum for uploadId={}: expected={}, actual={}, thread={}, time={}",
                            uploadId, expectedChecksum, actualChecksum, Thread.currentThread().getName(), System.currentTimeMillis());
                    if (!expectedChecksum.equals(actualChecksum)) {
                        log.error("Checksum mismatch for uploadId={}: expected={}, actual={}", uploadId, expectedChecksum, actualChecksum);
                        throw new IOException("Checksum mismatch after file assembly");
                    }

                    // Move upload to history with COMPLETED status
                    Y uploadInfo = (Y) info;
                    moveToHistory(uploadInfo);

                    // Delete the original upload info and part file
                    iUploadInfoPort.delete(uploadInfo);
                    Files.deleteIfExists(partPath);

                    log.debug("Successfully moved uploadId={} to history and cleaned up files", uploadId);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            try {
                Files.deleteIfExists(lockPath);
                log.debug("Released lock file {} for uploadId={}, thread={}, time={}",
                        lockPath, uploadId, Thread.currentThread().getName(), System.currentTimeMillis());
            } catch (IOException e) {
                log.warn("Failed to delete lock file {} for uploadId={}: {}", lockPath, uploadId, e.getMessage());
            }
        }
    }

    public void deleteUploadFile(String username, String uploadId) throws Throwable {
        Path partPath = getPartPath(username, uploadId);
        Files.deleteIfExists(partPath);
        System.out.println("[DEBUG] deleteUploadFile called for uploadId=" + uploadId + ", tenant=" + username);
        System.out.println("[DEBUG] UploadInfoPort.findByUploadId(" + uploadId + ") exists: " + iUploadInfoPort.findByUploadId(uploadId).isPresent());
        Path completePath = getFinalPath(username, uploadId);
        Files.deleteIfExists(completePath);
        iUploadInfoPort.findByUploadId(uploadId).ifPresent(info -> {
            try {
                iUploadInfoPort.delete((Y) info);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        removeUploadInfo(uploadId);
// DEBUG: Print uploadId and UploadInfo existence before deletion
    }
}
