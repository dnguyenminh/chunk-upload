package vn.com.fecredit.chunkedupload.port.intefaces;

import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;

import java.util.Optional;

/**
 * Port interface for interacting with the persistence layer for TenantAccount entities.
 */
public interface TenantAccountPort {

    /**
     * Finds a tenant account by its username.
     *
     * @param username The username to search for.
     * @return An Optional containing the TenantAccount if found, otherwise empty.
     */
    Optional<DeafultTenantAccount> findByUsername(String username);
}
