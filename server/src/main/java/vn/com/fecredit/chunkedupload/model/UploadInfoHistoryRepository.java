package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UploadInfoHistory entity.
 * Provides database operations for completed upload history records.
 */
@Repository
public interface UploadInfoHistoryRepository extends JpaRepository<UploadInfoHistory, Long> {

    /**
     * Find upload history by upload ID.
     * 
     * @param uploadId The upload ID to search for
     * @return Optional containing the history record if found
     */
    Optional<UploadInfoHistory> findByUploadId(String uploadId);

    @Query("SELECT h FROM UploadInfoHistory h JOIN FETCH h.tenant t WHERE t = :tenant")
    List<UploadInfoHistory> findByTenantEagerly(TenantAccount tenant);

    /**
     * Find upload history by status.
     * 
     * @param status The status to filter by
     * @return List of upload history records with the specified status
     */
    List<UploadInfoHistory> findByStatus(String status);

    /**
     * Find upload history records completed before a specific date.
     * Useful for cleanup operations.
     * 
     * @param dateTime The cutoff date/time
     * @return List of upload history records completed before the specified date
     */
    List<UploadInfoHistory> findByCompletionDateTimeBefore(LocalDateTime dateTime);

    /**
     * Find upload history records by tenant and status.
     * 
     * @param tenant The tenant account
     * @param status The status to filter by
     * @return List of upload history records for the tenant with the specified status
     */
    List<UploadInfoHistory> findByTenantAndStatus(TenantAccount tenant, String status);
}
