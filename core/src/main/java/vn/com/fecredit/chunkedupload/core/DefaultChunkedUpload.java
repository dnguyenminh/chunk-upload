package vn.com.fecredit.chunkedupload.core;

import org.slf4j.Logger;
import vn.com.fecredit.chunkedupload.model.Header;
import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.port.impl.DefaultITenantAccountPort;
import vn.com.fecredit.chunkedupload.port.impl.DefaultIUploadInfoPort;

import java.io.IOException;

public class DefaultChunkedUpload extends AbstractChunkedUpload<DeafultTenantAccount, DefaultUploadInfo, DefaultIUploadInfoPort, DefaultITenantAccountPort> {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(DefaultChunkedUpload.class);

    public DefaultChunkedUpload(DefaultIUploadInfoPort defaultUploadInfo, DefaultITenantAccountPort tenantAccountPort, String inProgressDirPath, String completeDirPath, int defaultChunkSize) throws IOException {
        super(defaultUploadInfo, tenantAccountPort, inProgressDirPath, completeDirPath, defaultChunkSize);
    }

    @Override
    protected DefaultUploadInfo createUploadInfo(String username, String uploadId, Header header, String fileName, String checksum) throws Throwable {
        log.debug("Creating DefaultUploadInfo: username={}, uploadId={}, fileName={}, checksum={}", username, uploadId, fileName, checksum);
        DefaultUploadInfo info = new DefaultUploadInfo() {
            {
                setUploadId(uploadId);
                setFilename(fileName);
                setChecksum(checksum);
                setUploadDateTime(java.time.LocalDateTime.now());
            }
        };
        log.debug("Created DefaultUploadInfo: {}", info);
        return info;
    }

    @Override
    protected DefaultUploadInfo saveUploadInfo(DefaultUploadInfo uploadInfo) {
        log.debug("saveUploadInfo called for DefaultUploadInfo: {}", uploadInfo);
        // Default implementation - return as-is since no persistence in core module
        return uploadInfo;
    }

    @Override
    protected void updateLastUpdateDateTime(DefaultUploadInfo uploadInfo) {
        // Default implementation - may not have lastUpdateDateTime field
        log.debug("updateLastUpdateDateTime called for DefaultUploadInfo - no-op implementation");
    }

    @Override
    protected void moveToHistory(DefaultUploadInfo uploadInfo) {
        // Default implementation - no history functionality in core module
        log.debug("moveToHistory called for DefaultUploadInfo - no-op implementation");
    }
}
