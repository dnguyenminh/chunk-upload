package vn.com.fecredit.chunkedupload.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing the history of completed upload sessions.
 * This table stores information about uploads that have been completed or timed out.
 */
@Entity
@Table(name = "upload_info_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadInfoHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, unique = true)
    private String uploadId;

    @Column(name = "checksum", nullable = false)
    private String checksum;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "upload_date_time", nullable = false)
    private LocalDateTime uploadDateTime;

    @Column(name = "last_update_date_time", nullable = false)
    private LocalDateTime lastUpdateDateTime;

    @Column(name = "completion_date_time", nullable = false)
    private LocalDateTime completionDateTime;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "original_upload_info_id")
    private Long originalUploadInfoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private TenantAccount tenant;

    // Status constants
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_TIMED_OUT = "TIMED_OUT";

    /**
     * Creates an UploadInfoHistory instance from an UploadInfo instance.
     * 
     * @param uploadInfo The UploadInfo to convert
     * @param completionStatus The final status (COMPLETED or TIMED_OUT)
     * @return A new UploadInfoHistory instance
     */
    public static UploadInfoHistory fromUploadInfo(UploadInfo uploadInfo, String completionStatus) {
        UploadInfoHistory history = new UploadInfoHistory();
        history.setUploadId(uploadInfo.getUploadId());
        history.setChecksum(uploadInfo.getChecksum());
        history.setFilename(uploadInfo.getFilename());
        history.setUploadDateTime(uploadInfo.getUploadDateTime());
        history.setLastUpdateDateTime(uploadInfo.getLastUpdateDateTime());
        history.setCompletionDateTime(LocalDateTime.now());
        history.setStatus(completionStatus);
        history.setTenant(uploadInfo.getTenant());
        history.setOriginalUploadInfoId(uploadInfo.getId());
        return history;
    }
}
