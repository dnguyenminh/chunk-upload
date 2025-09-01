package vn.com.fecredit.chunkedupload.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * JPA entity for tenant accounts.
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
}