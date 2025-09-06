package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;
import vn.com.fecredit.chunkedupload.port.intefaces.ITenantAccountPort;
import vn.com.fecredit.chunkedupload.port.intefaces.IUploadInfoPort;

import java.util.Optional;

/**
 * Repository for tenant account persistence operations.
 * Provides a lookup method by username used for authentication.
 */
public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long>, ITenantAccountPort<TenantAccount> {
    Optional<TenantAccount> findByUsername(String username);
}
