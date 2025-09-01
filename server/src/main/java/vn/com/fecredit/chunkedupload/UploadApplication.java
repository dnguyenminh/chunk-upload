package vn.com.fecredit.chunkedupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("vn.com.fecredit.chunkedupload.model")
@EnableJpaRepositories("vn.com.fecredit.chunkedupload.model")
public class UploadApplication {
    /**
     * The main method which serves as the entry point for the Java application.
     * It delegates to Spring Boot's {@link SpringApplication} to run the application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
        // DEBUG: Print all beans of type TenantAccountRepository at startup
        org.springframework.context.ApplicationContext ctx = org.springframework.boot.SpringApplication.run(UploadApplication.class, args);
        String[] beanNames = ctx.getBeanNamesForType(vn.com.fecredit.chunkedupload.model.TenantAccountRepository.class);
        System.out.println("[DEBUG] TenantAccountRepository beans: " + java.util.Arrays.toString(beanNames));
    }
}
