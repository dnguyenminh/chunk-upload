package vn.com.fecredit.chunkedupload.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;
import vn.com.fecredit.chunkedupload.model.util.FileNameValidator;
import vn.com.fecredit.chunkedupload.service.port.TenantAccountPort;
import vn.com.fecredit.chunkedupload.service.port.UploadInfoPort;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

public class AbstractChunkedUpload {

    private static final Logger log = LoggerFactory.getLogger(AbstractChunkedUpload.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    private final UploadInfoPort uploadInfoPort;
    private final TenantAccountPort tenantAccountPort;
    private final Path inProgressDir;
    private final Path completeDir;
    @Getter
    private final int defaultChunkSize;
    private final ConcurrentHashMap<String, String> uploadFilenames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> tenantIdCache = new ConcurrentHashMap<>();

    public AbstractChunkedUpload(
            UploadInfoPort uploadInfoPort,
            TenantAccountPort tenantAccountPort,
            String inProgressDirPath,
            String completeDirPath,
            int defaultChunkSize) throws IOException {
        this.uploadInfoPort = uploadInfoPort;
        this.tenantAccountPort = tenantAccountPort;
        this.inProgressDir = Paths.get(inProgressDirPath);
        this.completeDir = Paths.get(completeDirPath);
        this.defaultChunkSize = defaultChunkSize;
        Files.createDirectories(this.inProgressDir);
        Files.createDirectories(this.completeDir);
    }

    public TenantAccount findTenantByUsername(String username) {
        return tenantAccountPort.findByUsername(username).orElse(null);
    }

    public void saveUploadInfo(UploadInfo info) {
        uploadInfoPort.save(info);
    }

    public UploadInfo findUploadInfoByTenantAndUploadId(String username, String uploadId) {
        return tenantAccountPort.findByUsername(username)
                .flatMap(tenant -> uploadInfoPort.findByTenantAndUploadId(tenant, uploadId))
                .orElse(null);
    }

    public byte[] readBitsetBytesFromHeader(Path partPath) throws IOException {
        try (var ch = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ)) {
            var header = readHeader(ch);
            return header.bitset;
        }
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

    public abstract Path getPartPath(String tenantAccountId, String uploadId) ;

    public Path getFinalPath(String tenantAccountId, String uploadId) {
        Long dbId = tenantIdCache.computeIfAbsent(tenantAccountId, username -> {
            log.debug("Cache miss for tenantAccountId={}, querying DB for final path...", username);
            return tenantAccountPort.findByUsername(username)
                    .map(TenantAccount::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found for username: " + username));
        });
        String filename = getFilename(uploadId);
        Path finalPath = completeDir.resolve(String.valueOf(dbId))
                .resolve(uploadId + (FileNameValidator.isValidFileName(filename) ? ("_" + filename) : ""));
        log.debug("Final path for completed file: {}", finalPath);
        return finalPath;
    }

    public Header createOrValidateHeader(Path partPath, int totalChunks, int chunkSize, long fileSize) throws IOException {
        int bitsetBytes = (totalChunks + 7) / 8;
        int headerSize = PART_FILE_HEADER_FIXED_SIZE + bitsetBytes;

        createParentDirectory(partPath);
        log.debug("Creating or validating upload part file: {}", partPath);

        try (var ch = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.CREATE)) {
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
        var header = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
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
        ch.read(bits, PART_FILE_HEADER_FIXED_SIZE);
        return new Header(totalChunks, chunkSize, fileSize, bits.array());
    }

    public void writeChunk(Path partPath, int chunkNumber, int chunkSize, byte[] data) throws IOException {
        try (var ch = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE)) {
            var h = readHeader(ch);
            long headerSize = PART_FILE_HEADER_FIXED_SIZE + h.bitset.length;
            long offset = headerSize + (long) chunkNumber * h.chunkSize;
            ch.write(ByteBuffer.wrap(data), offset);
            BitsetUtil.setUsedBit(h.bitset, chunkNumber);
            writeHeader(ch, h);
        }
    }

    private void writeHeader(FileChannel ch, Header h) throws IOException {
        int bitsetBytes = h.bitset.length;
        int headerSize = PART_FILE_HEADER_FIXED_SIZE + bitsetBytes;
        var headerBuf = ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.BIG_ENDIAN);
        headerBuf.putInt(0xCAFECAFE);
        headerBuf.putInt(h.totalChunks);
        headerBuf.putInt(h.chunkSize);
        headerBuf.putLong(h.fileSize);
        headerBuf.put(h.bitset);
        headerBuf.flip();
        ch.write(headerBuf, 0);
    }

    public void assembleFile(Path partPath, Path finalPath, Header header) throws IOException {
        long fileSize = header.fileSize;
        long headerSize = PART_FILE_HEADER_FIXED_SIZE + header.bitset.length;
        createParentDirectory(finalPath);
        try (var src = FileChannel.open(partPath, java.nio.file.StandardOpenOption.READ);
             var dst = FileChannel.open(finalPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            src.transferTo(headerSize, fileSize, dst);
        }
        String uploadId = partPath.getFileName().toString().replace(".part", "");
        uploadInfoPort.findByUploadId(uploadId).ifPresent(info -> {
            try {
                String expectedChecksum = info.getChecksum();
                String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
                if (!expectedChecksum.equals(actualChecksum)) {
                    throw new IOException("Checksum mismatch after file assembly");
                }
                uploadInfoPort.delete(info);
                Files.deleteIfExists(partPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
