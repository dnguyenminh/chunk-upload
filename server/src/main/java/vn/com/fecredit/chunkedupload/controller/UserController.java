package vn.com.fecredit.chunkedupload.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private TenantAccountRepository tenantAccountRepository;

    /**
     * List all users in the database (for integration test).
     */
    @GetMapping
    public ResponseEntity<?> listUsers(org.springframework.security.core.Authentication authentication) {
        System.out.println("[DEBUG] /api/users called by principal: " + (authentication != null ? authentication.getName() : "null"));
        var users = tenantAccountRepository.findAll();
        System.out.println("[DEBUG] Returning users: " + users);
        return ResponseEntity.ok(users);
    }
}