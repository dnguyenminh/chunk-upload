package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadInfoRepository extends JpaRepository<UploadInfo, Long> {
    Optional<UploadInfo> findByTenantAndUploadId(TenantAccount tenant, String uploadId);
    Optional<UploadInfo> findByUploadId(String uploadId);
}