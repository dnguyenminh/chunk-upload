package vn.com.fecredit.chunkedupload.service;

import java.io.IOException;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import vn.com.fecredit.chunkedupload.core.AbstractChunkedUpload;
import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;
import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.model.UploadInfoHistory;
import vn.com.fecredit.chunkedupload.model.UploadInfoHistoryRepository;
import vn.com.fecredit.chunkedupload.model.UploadInfoRepository;

@Service
public class ChunkedUploadService extends AbstractChunkedUpload<TenantAccount, UploadInfo, UploadInfoRepository, TenantAccountRepository> {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    private final UploadInfoHistoryRepository uploadInfoHistoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ChunkedUploadService(
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath,
            @Value("${chunkedupload.complete-dir:uploads/complete}") String completeDirPath,
            @Value("${chunkedupload.chunk-size:524288}") int defaultChunkSize,
            TenantAccountRepository tenantAccountRepository,
            UploadInfoRepository uploadInfoRepository,
            UploadInfoHistoryRepository uploadInfoHistoryRepository) throws IOException {
        super(uploadInfoRepository, tenantAccountRepository, inProgressDirPath, completeDirPath, defaultChunkSize);
        this.uploadInfoHistoryRepository = uploadInfoHistoryRepository;
    }

    @Override
    protected UploadInfo createUploadInfo(String username, String uploadId, Header header, String fileName, String checksum) throws Throwable {
        UploadInfo info = new UploadInfo();
        info.setUploadId(uploadId);
        info.setChecksum(checksum);
        info.setUploadDateTime(LocalDateTime.now());
        info.setLastUpdateDateTime(LocalDateTime.now());
        info.setStatus(UploadInfo.STATUS_IN_PROGRESS);
        info.setFilename(fileName);
        info.setTenant(
            (TenantAccount) getITenantAccountPort()
                .findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Tenant not found for username: " + username))
        );
        log.info("Persisting UploadInfo of class: " + info.getClass().getName());
        return info;
    }

    @Override
    protected UploadInfo saveUploadInfo(UploadInfo uploadInfo) {
        // Cast to JpaRepository to resolve method ambiguity with IUploadInfoPort
        return ((JpaRepository<UploadInfo, Long>) getIUploadInfoPort()).save(uploadInfo);
    }

    @Override
    protected void updateLastUpdateDateTime(UploadInfo uploadInfo) {
        uploadInfo.setLastUpdateDateTime(LocalDateTime.now());
        log.debug("Updated lastUpdateDateTime for uploadId={}", uploadInfo.getUploadId());
    }

    @Override
    protected void moveToHistory(UploadInfo uploadInfo) {
        try {
            // Create history record with COMPLETED status
            uploadInfo.setStatus(UploadInfo.STATUS_COMPLETED);
            UploadInfoHistory history = UploadInfoHistory.fromUploadInfo(uploadInfo, UploadInfoHistory.STATUS_COMPLETED);
            uploadInfoHistoryRepository.save(history);
            log.info("Moved upload to history: uploadId={}, status={}", uploadInfo.getUploadId(), UploadInfoHistory.STATUS_COMPLETED);
        } catch (Exception e) {
            log.error("Failed to move upload to history: uploadId={}, error={}", uploadInfo.getUploadId(), e.getMessage(), e);
            throw new RuntimeException("Failed to move upload to history", e);
        }
    }

    // Idempotent abort/delete for upload session
    public void deleteUploadFile(String username, String uploadId) {
        try {
            UploadInfo info = findUploadInfoByTenantAndUploadId(username, uploadId);
            if (info != null) {
                getIUploadInfoPort().delete(info);
                log.info("Deleted upload info for uploadId={} user={}", uploadId, username);
            } else {
                log.info("No upload info found for uploadId={} user={}, abort is idempotent", uploadId, username);
            }
        } catch (Exception e) {
            log.warn("Exception during abort/delete for uploadId={} user={}: {}", uploadId, username, e.getMessage());
        }
    }
}
