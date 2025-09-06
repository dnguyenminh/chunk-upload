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
        return new DefaultUploadInfo() {
            {
                setUploadId(uploadId);
                setFilename(fileName);
                setChecksum(checksum);
                setUploadDateTime(java.time.LocalDateTime.now());
            }
        };
    }
}
