package vn.com.fecredit.chunkedupload.model.impl;

import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO representing a tenant account in the multi-tenant system.
 * This is the core domain object, free of persistence-specific annotations.
 */
public class DeafultTenantAccount implements ITenantAccount {

    private Long id;
    private String tenantId;
    private String username;
    private String password; // BCrypt hash
    private List<DefaultUploadInfo> uploads = new ArrayList<>();

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }
}
