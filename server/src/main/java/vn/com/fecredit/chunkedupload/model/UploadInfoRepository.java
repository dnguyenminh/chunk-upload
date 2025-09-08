package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.fecredit.chunkedupload.port.intefaces.IUploadInfoPort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadInfoRepository extends JpaRepository<UploadInfo, Long>, IUploadInfoPort<UploadInfo> {
    Optional<UploadInfo> findByTenantAndUploadId(TenantAccount tenant, String uploadId);

    Optional<UploadInfo> findByUploadId(String uploadId);

    /**
     * Find upload info records that haven't been updated since the specified time and have the given status.
     * Used for timeout cleanup operations.
     * 
     * @param cutoffTime The cutoff time - uploads older than this will be returned
     * @param status The status to filter by
     * @return List of upload info records that match the criteria
     */
    List<UploadInfo> findByLastUpdateDateTimeBeforeAndStatus(LocalDateTime cutoffTime, String status);

    /**
     * Find upload info records by status.
     * 
     * @param status The status to filter by
     * @return List of upload info records with the specified status
     */
    List<UploadInfo> findByStatus(String status);

    /**
     * Find upload info records by tenant and status.
     * 
     * @param tenant The tenant account
     * @param status The status to filter by
     * @return List of upload info records for the tenant with the specified status
     */
    List<UploadInfo> findByTenantAndStatus(TenantAccount tenant, String status);
}
