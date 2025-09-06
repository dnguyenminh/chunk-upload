package vn.com.fecredit.chunkedupload.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.com.fecredit.chunkedupload.core.AbstractChunkedUpload;
import vn.com.fecredit.chunkedupload.model.*;
import java.io.IOException;

@Service
public class ChunkedUploadService extends AbstractChunkedUpload<TenantAccount, UploadInfo, UploadInfoRepository, TenantAccountRepository> {
    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);
    private static final int PART_FILE_HEADER_FIXED_SIZE = 20; // Magic(4) + totalChunks(4) + chunkSize(4) + fileSize(8)

    public ChunkedUploadService(
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath,
            @Value("${chunkedupload.complete-dir:uploads/complete}") String completeDirPath,
            @Value("${chunkedupload.chunk-size:524288}") int defaultChunkSize,
            TenantAccountRepository tenantAccountRepository,
            UploadInfoRepository uploadInfoRepository) throws IOException {
        super(uploadInfoRepository, tenantAccountRepository, inProgressDirPath, completeDirPath, defaultChunkSize);
    }

    @Override
    protected UploadInfo createUploadInfo(String username, String uploadId, Header header, String fileName, String checksum) throws Throwable {
        UploadInfo info = new UploadInfo();
        info.setUploadId(uploadId);
        info.setChecksum(checksum);
        info.setUploadDateTime(java.time.LocalDateTime.now());
        info.setFilename(fileName);
        info.setTenant(
            (TenantAccount) getITenantAccountPort()
                .findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Tenant not found for username: " + username))
        );
        log.info("Persisting UploadInfo of class: " + info.getClass().getName());
        return info;
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
