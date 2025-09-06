package vn.com.fecredit.chunkedupload.port.impl;

import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;
import vn.com.fecredit.chunkedupload.model.interfaces.IUploadInfo;
import vn.com.fecredit.chunkedupload.port.intefaces.IUploadInfoPort;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of UploadInfoPort.
 */
public class DefaultIUploadInfoPort implements IUploadInfoPort<DefaultUploadInfo> {

    private final Map<String, DefaultUploadInfo> uploadIdMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, DefaultUploadInfo>> tenantUploadMap = new ConcurrentHashMap<>();

    @Override
    public DefaultUploadInfo save(DefaultUploadInfo uploadInfo) {
        if (uploadInfo == null || uploadInfo.getUploadId() == null) {
            throw new IllegalArgumentException("UploadInfo or uploadId cannot be null");
        }
        uploadIdMap.put(uploadInfo.getUploadId(), uploadInfo);
        // No tenant support in UploadInfo, so skip tenant mapping
        return uploadInfo;
    }

    @Override
    public Optional<DefaultUploadInfo> findByUploadId(String uploadId) {
        return Optional.ofNullable(uploadIdMap.get(uploadId));
    }

    @Override
    public Optional<DefaultUploadInfo> findByTenantAndUploadId(ITenantAccount tenant, String uploadId) {
        return findByUploadId(uploadId);
    }

    @Override
    public <Y extends IUploadInfo> void delete(Y uploadInfo) {
        if (uploadInfo == null || uploadInfo.getUploadId() == null) return;
        uploadIdMap.remove(uploadInfo.getUploadId());
    }
}