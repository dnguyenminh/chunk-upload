package vn.com.fecredit.chunkedupload.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import vn.com.fecredit.chunkedupload.model.InitResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@TestPropertySource(properties = {
//        "chunkedupload.inprogress-dir=build/test-uploads/in-progress",
//        "chunkedupload.complete-dir=build/test-uploads/complete",
//        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
//        "spring.datasource.driverClassName=org.h2.Driver",
//        "spring.datasource.username=sa",
//        "spring.datasource.password=",
//        "spring.jpa.hibernate.ddl-auto=create-drop"
//})
@org.springframework.test.context.jdbc.Sql(scripts = "/test-users.sql")
class ChunkedUploadClientIntegrationTest {

    @LocalServerPort
    private int port;

    private String uploadUrl;
    private final String USERNAME = "user";
    private final String PASSWORD = "password";
    private final String FILENAME = "integration-test-file.txt";
    private final byte[] FILE_CONTENT = "Integration test file content.".getBytes();

    private static final String UPLOAD_DIR = "uploads";
    private static final String COMPLETE_DIR = UPLOAD_DIR + "/complete/1";

    private final List<Path> filesToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        uploadUrl = "http://localhost:" + port + "/api/upload";
        filesToDelete.clear();

        Path uploadPath = Path.of(UPLOAD_DIR);
        if (Files.exists(uploadPath)) {
            try (var stream = Files.walk(uploadPath)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
            }
        }
        Files.createDirectories(Path.of(COMPLETE_DIR));
    }

    @AfterEach
    void tearDown() {
        filesToDelete.forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // Ignore
            }
        });
    }

    @Test
    void testUploadIntegration() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        String uploadId = client.upload(FILE_CONTENT, FILENAME, null, null);
        Path uploadedFilePath = Path.of(COMPLETE_DIR, uploadId + "_" + FILENAME);
        filesToDelete.add(uploadedFilePath);

        // Wait and retry to ensure file is assembled before checking existence
        System.out.println("[DEBUG] Checking file existence: " + uploadedFilePath.toAbsolutePath());
        boolean exists = false;
        int retries = 5;
        for (int i = 0; i < retries; i++) {
            exists = Files.exists(uploadedFilePath);
            if (exists) break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(exists, "Uploaded file should exist");
        try {
            assertArrayEquals(FILE_CONTENT, Files.readAllBytes(uploadedFilePath), "File content should match");
        } catch (IOException e) {
            fail("Failed to read uploaded file: " + e.getMessage());
        }
    }

    @Test
    void testUploadBigFileIntegration() {
        byte[] bigFile = new byte[5 * 1024 * 1024]; // 5MB
        for (int i = 0; i < bigFile.length; i++) bigFile[i] = (byte) (i % 256);
        String bigFileName = "big-file.bin";
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        String uploadId = client.upload(bigFile, bigFileName, null, null);
        Path bigFilePath = Path.of(COMPLETE_DIR, uploadId + "_" + bigFileName);
        filesToDelete.add(bigFilePath);

        // Wait and retry to ensure big file is assembled before checking existence
        System.out.println("[DEBUG] Checking big file existence: " + bigFilePath.toAbsolutePath());
        boolean exists = false;
        int retries = 5;
        for (int i = 0; i < retries; i++) {
            exists = Files.exists(bigFilePath);
            if (exists) break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(exists, "Big file should exist");
        try {
            assertArrayEquals(bigFile, Files.readAllBytes(bigFilePath), "Big file content should match");
        } catch (IOException e) {
            fail("Failed to read big file: " + e.getMessage());
        }
    }

    @Test
    void testResumeFailedUpload() throws IOException, InterruptedException, NoSuchAlgorithmException {
        byte[] file = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < file.length; i++) file[i] = (byte) (i % 256);
        String fileName = "resume-test-file.bin";
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();

        InitResponse initResp = client.startUploadSession(file, fileName);
        String uploadId = initResp.getUploadId();
        int chunkSize = initResp.getChunkSize();
        int totalChunks = initResp.getTotalChunks();
        int halfChunks = totalChunks / 2;

        for (int i = 0; i < halfChunks; i++) {
            client.uploadChunk(uploadId, i, chunkSize, totalChunks, file);
        }

        client.resumeUpload(uploadId, file);

        Path filePath = Path.of(COMPLETE_DIR, uploadId + "_" + fileName);
        filesToDelete.add(filePath);

        System.out.println("[DEBUG] Checking file existence: " + filePath.toAbsolutePath());
        boolean exists = false;
        int retries = 5;
        for (int i = 0; i < retries; i++) {
            exists = Files.exists(filePath);
            if (exists) break;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(exists, "Resumed file should exist");
        try {
            assertArrayEquals(file, Files.readAllBytes(filePath), "Resumed file content should match");
        } catch (IOException e) {
            fail("Failed to read resumed file: " + e.getMessage());
        }
    }

    @Test
    void testUploadFailWithWrongPassword() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password("wrongpassword")
                .build();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("unauthorized") || msg.contains("401"), "Should fail with unauthorized error, got: " + msg);
    }

    @Test
    void testUploadFailWithEmptyFile() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        assertThrows(IllegalArgumentException.class, () -> client.upload(new byte[0], FILENAME, null, null));
    }
}
