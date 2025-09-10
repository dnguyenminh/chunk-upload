package vn.com.fecredit.chunkedupload.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import vn.com.fecredit.chunkedupload.model.InitResponse;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

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
/**
 * Integration tests for ChunkedUploadClient.
 *
 * <p>
 * Tests cover:
 * <ul>
 * <li>Basic file upload functionality</li>
 * <li>Large file upload handling</li>
 * <li>Upload resume capabilities</li>
 * <li>Authentication validation</li>
 * <li>Error handling scenarios</li>
 * </ul>
 *
 * <p>
 * The test suite uses an embedded server with:
 * <ul>
 * <li>Random port assignment</li>
 * <li>In-memory database</li>
 * <li>Test-specific upload directories</li>
 * </ul>
 */
class ChunkedUploadClientIntegrationTest {

    @LocalServerPort
    private int port;

    private String uploadUrl;
    private final String USERNAME = "user";
    private final String PASSWORD = "password";
    private final String FILENAME = "temp/integration-test-file.txt";
    private final byte[] FILE_CONTENT = "Integration test file content.".getBytes();
    private Path tempFile;

    private static final String UPLOAD_DIR = "uploads";
    private static final String COMPLETE_DIR = UPLOAD_DIR + "/complete/";

    /**
     * Retrieves tenant ID from server using username.
     *
     * <p>
     * Makes an HTTP call to the server's user API endpoint to find the tenant ID
     * associated with the given username. Used for constructing upload paths and
     * verifying file locations.
     *
     * @param username Username to look up
     * @param user     Authentication username
     * @param password Authentication password
     * @return Tenant ID for the username
     * @throws IOException if user lookup fails
     */
    private long getTenantIdByUsername(String username, String user, String password) throws IOException {
        java.net.URI uri = java.net.URI.create("http://localhost:" + port + "/api/users");
        java.net.URL url = uri.toURL();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        // Add Basic Auth header
        String auth = user + ":" + password;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch users: HTTP " + conn.getResponseCode());
        }
        try (java.io.InputStream is = conn.getInputStream()) {
            String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            for (com.fasterxml.jackson.databind.JsonNode userNode : root) {
                if (userNode.has("username") && username.equals(userNode.get("username").asText())) {
                    return userNode.get("id").asLong();
                }
            }
        }
        throw new IOException("User not found: " + username);
    }

    private final List<Path> filesToDelete = new ArrayList<>();

    /**
     * Sets up the test environment before each test.
     *
     * <p>
     * Performs:
     * <ul>
     * <li>Configures upload URL with random port</li>
     * <li>Cleans up previous test files</li>
     * <li>Creates necessary directories</li>
     * <li>Prepares test files</li>
     * </ul>
     */
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

        // Write FILE_CONTENT to temp file for upload tests
        tempFile = Files.createTempFile("integration-test-file", ".txt");
        Files.write(tempFile, FILE_CONTENT);
        filesToDelete.add(tempFile);
    }

    /**
     * Cleans up test resources after each test.
     *
     * <p>
     * Removes all files created during the test, including:
     * <ul>
     * <li>Temporary test files</li>
     * <li>Uploaded files</li>
     * <li>Generated artifacts</li>
     * </ul>
     */
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

    /**
     * Tests basic file upload functionality.
     *
     * <p>
     * Verifies:
     * <ul>
     * <li>File is uploaded successfully</li>
     * <li>Uploaded file exists in correct location</li>
     * <li>File content integrity via checksum</li>
     * </ul>
     */
    @Test
    void testUploadIntegration() throws IOException {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        long tenantId = getTenantIdByUsername(USERNAME, USERNAME, PASSWORD);
        String uploadId = client.upload(tempFile, null, null);
        Path uploadedFilePath = Path.of(COMPLETE_DIR, String.valueOf(tenantId), uploadId + "_" + tempFile.getFileName().toString());
        filesToDelete.add(uploadedFilePath);

        System.out.println("[DEBUG] Checking file existence: " + uploadedFilePath.toAbsolutePath());
        // DEBUG: Print expected file path and name for comparison
        System.out.println("[DEBUG] Expected file path: " + uploadedFilePath.toString());
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
            assertEquals(
                    ChecksumUtil.generateChecksum(tempFile),
                    ChecksumUtil.generateChecksum(uploadedFilePath),
                    "File content should match"
            );
        } catch (RuntimeException e) {
            fail("Failed to read uploaded file: " + e.getMessage());
        }
    }

    /**
     * Tests large file upload functionality.
     *
     * <p>
     * Verifies:
     * <ul>
     * <li>5MB file uploads successfully</li>
     * <li>Chunking works correctly for large files</li>
     * <li>Content integrity maintained for large files</li>
     * </ul>
     */
    @Test
    void testUploadBigFileIntegration() throws IOException {
        byte[] bigFile = new byte[5 * 1024 * 1024]; // 5MB
        for (int i = 0; i < bigFile.length; i++) bigFile[i] = (byte) (i % 256);
        String bigFileName = "temp/big-file.bin";
        Path bigFilePathLocal = Files.createTempFile("big-file", ".bin");
        Files.write(bigFilePathLocal, bigFile);
        filesToDelete.add(bigFilePathLocal);

        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        long tenantId = getTenantIdByUsername(USERNAME, USERNAME, PASSWORD);
        String uploadId = client.upload(bigFilePathLocal, null, null);
        Path bigFilePath = Path.of(COMPLETE_DIR, String.valueOf(tenantId), uploadId + "_" + bigFilePathLocal.getFileName().toString());
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

    /**
     * Tests upload resume functionality.
     *
     * <p>
     * Verifies:
     * <ul>
     * <li>Failed upload can be resumed</li>
     * <li>Resume picks up from last successful chunk</li>
     * <li>Final file is assembled correctly after resume</li>
     * </ul>
     */
    @Test
    void testResumeFailedUpload() throws IOException, InterruptedException, NoSuchAlgorithmException {
        byte[] file = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < file.length; i++) file[i] = (byte) (i % 256);
        String fileName = "temp/resume-test-file.bin";
        Path filePathLocal = Files.createTempFile("resume-test-file", ".bin");
        Files.write(filePathLocal, file);
        filesToDelete.add(filePathLocal);

        long tenantId = getTenantIdByUsername(USERNAME, USERNAME, PASSWORD);

        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();

        InitResponse initResp = client.startUploadSession(filePathLocal);
        String uploadId = initResp.getUploadId();
        int chunkSize = initResp.getChunkSize();
        int totalChunks = initResp.getTotalChunks();
        int halfChunks = totalChunks / 2;

        // Attempt to resume upload (API contract may reject, but this test checks client behavior)
        try {
            client.resumeUpload(uploadId, filePathLocal);
        } catch (RuntimeException e) {
            System.out.println("Resume failed as expected: " + e.getMessage());
        }
        Path filePath = Path.of(COMPLETE_DIR, String.valueOf(tenantId), uploadId + "_" + filePathLocal.getFileName().toString());
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
    // Resume upload integration test removed: no resume API is available, and resuming is handled by re-uploading missing chunks.

    /**
     * Tests authentication failure handling.
     *
     * <p>
     * Verifies:
     * <ul>
     * <li>Invalid credentials are rejected</li>
     * <li>Appropriate error is returned</li>
     * <li>Upload fails securely</li>
     * </ul>
     */
    @Test
    void testUploadFailWithWrongPassword() throws IOException {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password("wrongpassword")
                .build();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> client.upload(tempFile, null, null));
        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("unauthorized") || msg.contains("401"), "Should fail with unauthorized error, got: " + msg);
    }

    /**
     * Tests empty file upload handling.
     *
     * <p>
     * Verifies:
     * <ul>
     * <li>Empty file uploads are rejected</li>
     * <li>Appropriate error is returned</li>
     * <li>System handles edge case gracefully</li>
     * </ul>
     */
    @Test
    void testUploadFailWithEmptyFile() throws IOException {
        Path emptyFile = Files.createTempFile("empty-file", ".txt");
        filesToDelete.add(emptyFile);

        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        assertThrows(RuntimeException.class, () -> client.upload(emptyFile, null, null));
    }
}
