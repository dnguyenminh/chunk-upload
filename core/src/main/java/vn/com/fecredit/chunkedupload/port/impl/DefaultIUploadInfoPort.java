package vn.com.fecredit.chunkedupload.port.impl;

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
    // Removed unused tenantUploadMap field

    @Override
    public <S extends DefaultUploadInfo> S save(S uploadInfo) {
        System.out.println("[DefaultIUploadInfoPort] save called with uploadId=" +
            (uploadInfo != null ? uploadInfo.getUploadId() : "null"));
        if (uploadInfo == null || uploadInfo.getUploadId() == null) {
            throw new IllegalArgumentException("UploadInfo or uploadId cannot be null");
        }
        uploadIdMap.put(uploadInfo.getUploadId(), uploadInfo);
        // No tenant support in UploadInfo, so skip tenant mapping
        return uploadInfo;
    }

    @Override
    public Optional<DefaultUploadInfo> findByUploadId(String uploadId) {
        DefaultUploadInfo found = uploadIdMap.get(uploadId);
        System.out.println("[DefaultIUploadInfoPort] findByUploadId called with uploadId=" + uploadId +
            ", found=" + (found != null));
        return Optional.ofNullable(found);
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