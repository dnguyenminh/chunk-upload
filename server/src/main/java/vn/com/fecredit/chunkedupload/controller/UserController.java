package vn.com.fecredit.chunkedupload.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private TenantAccountRepository tenantAccountRepository;

    @GetMapping
    public ResponseEntity<List<TenantAccount>> getAllUsers() {
        List<TenantAccount> users = tenantAccountRepository.findAll();
        return ResponseEntity.ok(users);
    }
}