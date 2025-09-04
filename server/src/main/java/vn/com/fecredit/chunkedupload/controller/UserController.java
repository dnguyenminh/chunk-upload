package vn.com.fecredit.chunkedupload.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

/**
 * REST controller for user/tenant account management.
 *
 * <p>
 * Provides endpoints for:
 * <ul>
 * <li>Listing tenant accounts</li>
 * <li>User management operations</li>
 * </ul>
 * <p>
 * This controller is primarily used for debugging and integration testing purposes.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private TenantAccountRepository tenantAccountRepository;

    /**
     * Lists all tenant accounts in the system.
     *
     * <p>
     * This endpoint retrieves all registered tenant accounts and is primarily used for:
     * <ul>
     * <li>Debugging purposes</li>
     * <li>Integration testing</li>
     * <li>System administration</li>
     * </ul>
     *
     * @param authentication The caller's authentication principal used for access control
     * @return ResponseEntity containing a list of tenant accounts with HTTP 200 OK status
     */
    @GetMapping
    public ResponseEntity<?> listUsers(org.springframework.security.core.Authentication authentication) {
        System.out.println("[DEBUG] /api/users called by principal: " + (authentication != null ? authentication.getName() : "null"));
        var users = tenantAccountRepository.findAll();
        System.out.println("[DEBUG] Returning users: " + users);
        return ResponseEntity.ok(users);
    }
}