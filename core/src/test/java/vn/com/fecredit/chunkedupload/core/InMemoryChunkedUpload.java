package vn.com.fecredit.chunkedupload.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.port.impl.DefaultIUploadInfoPort;
import vn.com.fecredit.chunkedupload.port.impl.DefaultITenantAccountPort;

public class InMemoryChunkedUpload extends DefaultChunkedUpload {
    public InMemoryChunkedUpload(DefaultIUploadInfoPort uploadInfoPort, DefaultITenantAccountPort tenantAccountPort, String inProgressDir, String completeDir, int chunkSize) throws java.io.IOException {
        super(uploadInfoPort, tenantAccountPort, inProgressDir, completeDir, chunkSize);
    }

    private final Map<String, DefaultUploadInfo> uploadInfoStore = new ConcurrentHashMap<>();

    private DefaultIUploadInfoPort uploadInfoPort;

    public void setUploadInfoPort(DefaultIUploadInfoPort uploadInfoPort) {
        this.uploadInfoPort = uploadInfoPort;
    }

    @Override
    protected DefaultUploadInfo saveUploadInfo(DefaultUploadInfo uploadInfo) {
        if (uploadInfo != null && uploadInfo.getUploadId() != null) {
            uploadInfoStore.put(uploadInfo.getUploadId(), uploadInfo);
            if (uploadInfoPort != null) {
                uploadInfoPort.save(uploadInfo);
            }
        }
        return uploadInfo;
    }

    public DefaultUploadInfo findUploadInfoByUploadId(String uploadId) {
        return uploadInfoStore.get(uploadId);
    }
}