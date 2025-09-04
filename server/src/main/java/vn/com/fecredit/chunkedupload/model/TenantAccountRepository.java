package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for tenant account persistence operations.
 * Provides a lookup method by username used for authentication.
 */
public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long> {
    Optional<TenantAccount> findByUsername(String username);
}
