package vn.com.fecredit.chunkedupload.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantAccountRepository extends JpaRepository<TenantAccount, Long> {
    Optional<TenantAccount> findByUsername(String username);
}
