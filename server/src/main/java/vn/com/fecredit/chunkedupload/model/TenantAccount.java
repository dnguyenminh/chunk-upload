package vn.com.fecredit.chunkedupload.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA entity representing a tenant account in the multi-tenant system.
 *
 * <p>
 * A tenant account represents an organization or user that can:
 * <ul>
 * <li>Authenticate with the system</li>
 * <li>Upload files in isolated storage</li>
 * <li>Manage their own uploads</li>
 * </ul>
 *
 * <p>
 * Each tenant has:
 * <ul>
 * <li>A unique identifier separate from their username</li>
 * <li>Authentication credentials (username/password)</li>
 * <li>A collection of associated uploads</li>
 * </ul>
 */
@Entity
@Table(name = "tenants")
@Data
public class TenantAccount {

    /**
     * Primary key for the tenant account (auto-generated).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * Unique identifier for the tenant (not necessarily the username).
     */
    @Column(nullable = false)
    private String tenantId;


    /**
     * Username for login (must be unique).
     */
    @Column(nullable = false, unique = true)
    private String username;


    /**
     * Password hash (BCrypt encoded).
     */
    @Column(nullable = false)
    private String password; // BCrypt hash
    /**
     * Collection of uploads associated with this tenant.
     *
     * <p>
     * This bi-directional relationship enables:
     * <ul>
     * <li>Tracking all uploads for a tenant</li>
     * <li>Cascade operations on upload records</li>
     * <li>Automatic cleanup of orphaned uploads</li>
     * </ul>
     */
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<UploadInfo> uploads = new java.util.ArrayList<>();
}