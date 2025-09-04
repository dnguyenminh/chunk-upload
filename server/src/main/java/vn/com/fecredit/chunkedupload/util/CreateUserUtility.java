package vn.com.fecredit.chunkedupload.util;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

@SpringBootApplication(scanBasePackages = "vn.com.fecredit.chunkedupload")
public class CreateUserUtility {
    public static void main(String[] args) {
        if (args.length != 3 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.out.println("Usage: java -cp <classpath> vn.com.fecredit.chunkedupload.util.CreateUserUtility <tenantId> <username> <password>");
            System.out.println("All parameters are required and must be non-empty.");
            System.out.println("Example:");
            System.out.println("  java -cp \"<classpath>\" vn.com.fecredit.chunkedupload.util.CreateUserUtility myTenant myUser myPassword");
            System.exit(1);
        }
        String tenantId = args[0].trim();
        String username = args[1].trim();
        String rawPassword = args[2].trim();

        if (tenantId.isEmpty() || username.isEmpty() || rawPassword.isEmpty()) {
            System.err.println("Error: All parameters must be non-empty.");
            System.out.println("Usage: java -cp <classpath> vn.com.fecredit.chunkedupload.util.CreateUserUtility <tenantId> <username> <password>");
            System.exit(2);
        }

        String encodedPassword = "{bcrypt}" + new BCryptPasswordEncoder().encode(rawPassword);

        ConfigurableApplicationContext ctx = SpringApplication.run(CreateUserUtility.class, args);
        TenantAccountRepository repo = ctx.getBean(TenantAccountRepository.class);

        if (repo.findByUsername(username).isPresent()) {
            System.out.println("User already exists: " + username);
        } else {
            TenantAccount user = new TenantAccount();
            user.setTenantId(tenantId);
            user.setUsername(username);
            user.setPassword(encodedPassword);
            repo.save(user);
            System.out.println("User created: " + username);
        }
        ctx.close();
    }
}