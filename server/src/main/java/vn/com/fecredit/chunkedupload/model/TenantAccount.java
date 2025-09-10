package vn.com.fecredit.chunkedupload.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;

// Force update to resolve serialization issue
@Entity
@Table(name = "tenants")
@Data
public class TenantAccount implements ITenantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hash

    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private java.util.List<UploadInfo> uploads = new java.util.ArrayList<>();
}