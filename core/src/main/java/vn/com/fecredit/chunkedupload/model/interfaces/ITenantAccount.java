package vn.com.fecredit.chunkedupload.model.interfaces;

public interface ITenantAccount {
    Long getId();

    void setId(Long id);

    String getTenantId();

    void setTenantId(String tenantId);

    String getUsername();

    void setUsername(String username);

    String getPassword();

    void setPassword(String password);
}
