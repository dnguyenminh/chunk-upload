package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.fecredit.chunkedupload.port.intefaces.IUploadInfoPort;

import java.util.Optional;

public interface UploadInfoRepository extends JpaRepository<UploadInfo, Long>, IUploadInfoPort<UploadInfo> {
    Optional<UploadInfo> findByTenantAndUploadId(TenantAccount tenant, String uploadId);

    Optional<UploadInfo> findByUploadId(String uploadId);
}