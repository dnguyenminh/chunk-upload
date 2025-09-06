package vn.com.fecredit.chunkedupload.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO representing a tenant account in the multi-tenant system.
 * This is the core domain object, free of persistence-specific annotations.
 */
@Data
public class TenantAccount {

    private Long id;
    private String tenantId;
    private String username;
    private String password; // BCrypt hash
    private List<UploadInfo> uploads = new ArrayList<>();

}
