package vn.com.fecredit.chunkedupload.model;

import jakarta.persistence.*;
import lombok.Data;
import vn.com.fecredit.chunkedupload.model.interfaces.IUploadInfo;

import java.time.LocalDateTime;

/**
 * JPA entity representing metadata for a file upload.
 *
 * <p>
 * This entity tracks:
 * <ul>
 * <li>Upload session information</li>
 * <li>File metadata and integrity checks</li>
 * <li>Tenant ownership and upload timing</li>
 * </ul>
 *
 * <p>
 * Each upload is:
 * <ul>
 * <li>Associated with exactly one tenant</li>
 * <li>Identified by a unique upload ID</li>
 * <li>Tracked with checksum for integrity</li>
 * <li>Timestamped for audit purposes</li>
 * </ul>
 */
@Entity
@Table(name = "upload_info")
@Data
public class UploadInfo implements IUploadInfo {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_TIMED_OUT = "TIMED_OUT";

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the upload session.
     * Generated as UUID during upload initialization.
     */
    @Column(nullable = false, unique = true)
    private String uploadId;

    /**
     * SHA-256 checksum of the complete file.
     * Used for validating upload integrity.
     */
    @Column(nullable = false)
    private String checksum;

    /**
     * Original name of the uploaded file.
     * Preserved for user reference and download.
     */
    @Column(nullable = false)
    private String filename;

    /**
     * Timestamp of when the upload was initiated.
     * Used for tracking and audit purposes.
     */
    @Column(nullable = false)
    private LocalDateTime uploadDateTime;

    /**
     * Timestamp of the last update to this upload.
     * Used for session timeout detection.
     */
    @Column(nullable = false)
    private LocalDateTime lastUpdateDateTime;

    /**
     * Status of the upload session.
     * Values: IN_PROGRESS, COMPLETED, TIMED_OUT
     */
    @Column(nullable = false)
    private String status;

    /**
     * The tenant who owns this upload.
     * Lazy fetched to optimize performance.
     * Many uploads can belong to one tenant.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private TenantAccount tenant;
}
