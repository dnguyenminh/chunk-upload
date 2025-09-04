package vn.com.fecredit.chunkedupload.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;
import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.model.UploadInfoRepository;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChunkedUploadService {
    @Autowired
    private UploadInfoRepository uploadInfoRepository;

    @Autowired
    private TenantAccountRepository tenantAccountRepository;
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    public TenantAccount findTenantByUsername(String username) {
        return tenantAccountRepository.findByUsername(username).orElse(null);
    }

    public void saveUploadInfo(UploadInfo info) {
        uploadInfoRepository.save(info);
    }

    private final Path inProgressDir;
    private final Path completeDir;
    @Getter
    private final int defaultChunkSize;
    private final ConcurrentHashMap<String, String> uploadFilenames = new ConcurrentHashMap<>();

    public UploadInfo findUploadInfoByTenantAndUploadId(String username, String uploadId) {
        TenantAccount tenant = tenantAccountRepository.findByUsername(username).orElse(null);
        if (tenant == null) return null;
        return uploadInfoRepository.findByTenantAndUploadId(tenant, uploadId).orElse(null);
    }

    public byte[] readBitsetBytesFromHeader(Path partPath) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "r");
             var ch = raf.getChannel()) {
            var header = readHeader(ch);
            return header.bitset;
        }
    }

    public ChunkedUploadService(
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath,
            @Value("${chunkedupload.complete-dir:uploads/complete}") String completeDirPath,
            @Value("${chunkedupload.chunk-size:524288}") int defaultChunkSize,
            TenantAccountRepository tenantAccountRepository) throws IOException {
        this.inProgressDir = Paths.get(inProgressDirPath);
        this.completeDir = Paths.get(completeDirPath);
        this.defaultChunkSize = defaultChunkSize;
        this.tenantAccountRepository = tenantAccountRepository;
        Files.createDirectories(this.inProgressDir);
        Files.createDirectories(this.completeDir);
    }

    public void storeFilename(String uploadId, String filename) {
        uploadFilenames.put(uploadId, filename);
    }

    public String getFilename(String uploadId) {
        return uploadFilenames.get(uploadId);
    }

    public void removeFilename(String uploadId) {
        uploadFilenames.remove(uploadId);
    }

    private final ConcurrentHashMap<String, Long> tenantIdCache = new ConcurrentHashMap<>();

    public Path getPartPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            log.debug("Cache miss for tenantAccountId={}, querying DB...", username);
            return tenantAccountRepository.findByUsername(username)
                    .map(TenantAccount::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        log.debug("Using tenant DB id={} for username={}", dbId, tenantAccountId);
        return inProgressDir.resolve(String.valueOf(dbId)).resolve(uploadId + ".part");
    }

    public Path getFinalPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            log.debug("Cache miss for tenantAccountId={}, querying DB for final path...", username);
            return tenantAccountRepository.findByUsername(username)
                    .map(TenantAccount::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        String filename = getFilename(uploadId);
        Path finalPath = completeDir.resolve(String.valueOf(dbId))
                .resolve(uploadId + (StringUtils.hasText(filename) ? ("_" + filename) : ""));
        log.debug("Final path for completed file: {}", finalPath);
        return finalPath;
    }

    public Header createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize) throws IOException {
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

    private void createParentDirectory(Path partPath) throws IOException {
        Path parentDir = partPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            log.debug("Creating parent directory for upload part: {}", parentDir);
            Files.createDirectories(parentDir);
        }
    }

    private Header createHeader(FileChannel ch, int headerSize, int totalChunks, int chunkSize, long fileSize, int bitsetBytes) throws IOException {
        var header = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        header.putInt(0xCAFECAFE); // Magic number
        header.putInt(totalChunks);
        header.putInt(chunkSize);
        header.putLong(fileSize);
        byte[] bitset = new byte[bitsetBytes];
        BitsetUtil.setUnusedBits(bitset, totalChunks);
        header.put(bitset);
        header.flip();
        int bytesWritten = ch.write(header, 0);
        if (bytesWritten != headerSize) {
            log.warn("Not all header bytes written: expected={}, actual={}", headerSize, bytesWritten);
        }
        return new Header(totalChunks, chunkSize, fileSize, bitset);
    }

    private Header validateHeader(FileChannel ch, int totalChunks, int chunkSize, long fileSize) throws IOException {
        var h = readHeader(ch);
        if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
            log.error("Existing upload file header mismatch: header.totalChunks={}, expected={}; header.chunkSize={}, expected={}; header.fileSize={}, expected={}",
                    h.totalChunks, totalChunks, h.chunkSize, chunkSize, h.fileSize, fileSize);
            throw new IllegalStateException("Existing upload file has different parameters");
        }
        return h;
    }

    public Header readHeader(FileChannel ch) throws IOException {
        var fixed = ByteBuffer.allocate(PART_FILE_HEADER_FIXED_SIZE).order(java.nio.ByteOrder.BIG_ENDIAN);
        ch.read(fixed, 0);
        fixed.flip();
        int magic = fixed.getInt();
        if (magic != 0xCAFECAFE) throw new IOException("Bad magic in upload file header");
        int totalChunks = fixed.getInt();
        int chunkSize = fixed.getInt();
        long fileSize = fixed.getLong();
        int bitsetBytes = (totalChunks + 7) / 8;
        var bits = ByteBuffer.allocate(bitsetBytes);
        ch.read(bits, 20);
        return new Header(totalChunks, chunkSize, fileSize, bits.array());
    }

    public static class Header {
        public final int totalChunks;
        public final int chunkSize;
        public final long fileSize;
        public final byte[] bitset;

        public Header(int totalChunks, int chunkSize, long fileSize, byte[] bitset) {
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.fileSize = fileSize;
            this.bitset = bitset;
        }
    }

    public void writeChunk(Path partPath, int chunkNumber, int chunkSize, byte[] data) throws IOException {
        try (var raf = new java.io.RandomAccessFile(partPath.toFile(), "rw");
             var ch = raf.getChannel()) {
            var h = readHeader(ch);
            long headerSize = PART_FILE_HEADER_FIXED_SIZE + h.bitset.length;
            long offset = headerSize + (long) chunkNumber * h.chunkSize;
            ch.position(offset);
            log.debug("writeChunk: chunkNumber={}, data[0]={}, data.length={}", chunkNumber, (data.length > 0 ? data[0] : "empty"), data.length);
            int bytesWritten = ch.write(ByteBuffer.wrap(data, 0, data.length));
            if (bytesWritten != data.length) {
                log.warn("Not all bytes written for chunk {}: expected={}, actual={}", chunkNumber, data.length, bytesWritten);
            }
            BitsetUtil.setUsedBit(h.bitset, chunkNumber);
            ch.position(0);
            writeHeader(ch, h);
            long partFileSize = raf.length();
            log.debug("writeChunk: partPath={}, chunkNumber={}, chunkSize={}, data.length={}, offset={}, headerSize={}, originalFileSize={}, partFileSize={} ==> partFileSize should equal originalFileSize + headerSize",
                    partPath, chunkNumber, chunkSize, data.length, offset, headerSize, h.fileSize, partFileSize);
        } catch (Exception e) {
            log.error("Exception in writeChunk: {}: {}", e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
    }

    private void writeHeader(FileChannel ch, Header h) throws IOException {
        int bitsetBytes = h.bitset.length;
        int headerSize = 4 + 4 + 4 + 8 + bitsetBytes;
        var headerBuf = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(0xCAFECAFE);
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

    public void assembleFile(Path partPath, Path finalPath, Header header) throws IOException {
        long fileSize = header.fileSize;
        byte[] bitset = header.bitset;
        long headerSize = 20 + bitset.length;
        log.debug("assembleFile: partPath={}, finalPath={}, expected fileSize={}, headerSize={}", partPath, finalPath, fileSize, headerSize);
        Path parentDir = finalPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            log.debug("Creating parent directory for completed file: {}", parentDir);
            Files.createDirectories(parentDir);
        }
        log.debug("Assembling file: partPath={}, finalPath={}", partPath, finalPath);
        try (var src = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ);
             var dst = FileChannel.open(finalPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            src.position(headerSize);
            long bytesCopied = dst.transferFrom(src, 0, src.size() - headerSize);
            log.debug("Bytes copied to completed file: {}, expected: {}", bytesCopied, fileSize);
            log.debug("Completed file exists: {}, size: {}", Files.exists(finalPath), Files.size(finalPath));
            if (Files.size(finalPath) != fileSize) {
                log.error("File size mismatch after assembly: actual={}, expected={}", Files.size(finalPath), fileSize);
            }
        }
        try {
            UploadInfo info = uploadInfoRepository.findByUploadId(partPath.getFileName().toString()).orElse(null);
            if (info != null) {
                String expectedChecksum = info.getChecksum();
                String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
                log.debug("Checksum validation: expected={}, actual={}", expectedChecksum, actualChecksum);
                if (!expectedChecksum.equals(actualChecksum)) {
                    log.error("Checksum mismatch after file assembly: expected={}, actual={}", expectedChecksum, actualChecksum);
                    throw new IOException("Checksum mismatch after file assembly");
                }
            }
        } catch (Exception e) {
            log.error("Checksum validation failed: {}", e.getMessage());
            throw new IOException("Checksum validation failed", e);
        }
        try {
            Files.deleteIfExists(partPath);
            log.debug("Deleted part file after assembly: {}", partPath);
        } catch (IOException e) {
            log.error("Failed to delete part file: {} - {}", partPath, e.getMessage());
        }
        try {
            UploadInfo info = uploadInfoRepository.findByUploadId(partPath.getFileName().toString()).orElse(null);
            if (info != null) {
                uploadInfoRepository.delete(info);
                log.debug("Deleted UploadInfo entity for uploadId: {}", partPath.getFileName().toString());
            }
        } catch (Exception e) {
            log.error("Failed to delete UploadInfo entity for uploadId {}: {}", partPath.getFileName().toString(), e.getMessage());
        }
    }
}
