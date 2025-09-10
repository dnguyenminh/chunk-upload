package vn.com.fecredit.chunkedupload.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import vn.com.fecredit.chunkedupload.model.interfaces.IUploadInfo;

import java.time.LocalDateTime;

// Force update to resolve serialization issue
@Entity
@Table(name = "upload_info")
@Data
public class UploadInfo implements IUploadInfo {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_TIMED_OUT = "TIMED_OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uploadId;

    @Column(nullable = false)
    private String checksum;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private LocalDateTime uploadDateTime;

    @Column(nullable = false)
    private LocalDateTime lastUpdateDateTime;

    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    @JsonBackReference
    private TenantAccount tenant;
}