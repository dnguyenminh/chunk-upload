package vn.com.fecredit.chunkedupload.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tenants")
@Data
public class TenantAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hash
}