package vn.com.fecredit.chunkedupload.port.impl;

import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.port.intefaces.ITenantAccountPort;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory implementation of TenantAccountPort.
 */
public class DefaultITenantAccountPort implements ITenantAccountPort<DeafultTenantAccount> {

    private final Map<String, DeafultTenantAccount> tenantMap = new ConcurrentHashMap<>();

    @Override
    public Optional<DeafultTenantAccount> findByUsername(String username) {
        return Optional.ofNullable(tenantMap.get(username));
    }

    // For testing/demo: add a tenant
    public void addTenant(DeafultTenantAccount tenant) {
        if (tenant != null && tenant.getUsername() != null) {
            tenantMap.put(tenant.getUsername(), tenant);
        }
    }
}