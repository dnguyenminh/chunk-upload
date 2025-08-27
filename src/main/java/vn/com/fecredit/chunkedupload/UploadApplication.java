package vn.com.fecredit.chunkedupload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the chunked file upload service.
 * <p>
 * This class serves as the entry point for the Spring Boot application.
 * It enables auto-configuration and component scanning to bootstrap the
 * chunked upload RESTful service.
 * </p>
 */
/**
 * Main application class for the chunked file upload service.
 * <p>
 * This class serves as the entry point for the Spring Boot application.
 * It enables auto-configuration and component scanning to bootstrap the
 * chunked upload RESTful service.
 * </p>
 */
@SpringBootApplication
public class UploadApplication {
    /**
     * The main method which serves as the entry point for the Java application.
     * It delegates to Spring Boot's {@link SpringApplication} to run the application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
    }
}
