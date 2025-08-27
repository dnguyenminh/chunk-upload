package vn.com.fecredit.chunkedupload.client;


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import vn.com.fecredit.chunkedupload.UploadApplication;
import vn.com.fecredit.chunkedupload.model.InitResponse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = UploadApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
class ChunkedUploadClientIntegrationTest {

    @LocalServerPort
    private int port;

    private String uploadUrl;
    private final String USERNAME = "user";
    private final String PASSWORD = "password";
    private final String FILENAME = "integration-test-file.txt";
    private final byte[] FILE_CONTENT = "Integration test file content.".getBytes();
    private Path uploadedFilePath;

    @BeforeEach
    void setUp() {
        uploadUrl = "http://localhost:" + port + "/api/upload";
        // uploadedFilePath will be set after upload, since uploadId is not known yet
        uploadedFilePath = null;
    }

    @Test
    void testResumeFailedUpload() {
        // Simulate a partial upload by uploading only half the chunks, then resume
        byte[] file = new byte[1024 * 1024]; // 1MB
        for (int i = 0; i < file.length; i++) file[i] = (byte) (i % 256);
        String fileName = "resume-integration-test-file.bin";
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(2)
                .build();

        // Step 1: Start upload, but simulate interruption after half the chunks
        String uploadId = null;
        int chunkSize = 0;
        int totalChunks = 0;
        try {
            InitResponse initResp = client.startUploadSession(file, fileName);
            uploadId = initResp.getUploadId();
            chunkSize = initResp.getChunkSize();
            totalChunks = (int) Math.ceil((double) file.length / chunkSize);
            int halfChunks = totalChunks / 2;
            for (int i = 0; i < halfChunks; i++) {
                client.uploadChunk(uploadId, i, chunkSize, file);
            }
        } catch (Exception e) {
            // Ignore, simulating interruption
        }

    // Step 2: Resume upload using resumeUpload
    client.resumeUpload(uploadId, file);
        Path filePath = Path.of("uploads", "complete", uploadId + "_" + fileName);
        assertTrue(Files.exists(filePath), "Resumed uploaded file should exist");
        try {
            byte[] uploadedContent = Files.readAllBytes(filePath);
            assertArrayEquals(file, uploadedContent, "Resumed uploaded file content should match");
        } catch (Exception e) {
            fail("Failed to read resumed uploaded file: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        try {
            Files.deleteIfExists(uploadedFilePath);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void testUploadIntegration() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(2)
                .build();
        String uploadId = client.upload(FILE_CONTENT, FILENAME, null, null);
        uploadedFilePath = Path.of("uploads", "complete", uploadId + "_" + FILENAME);
        assertTrue(Files.exists(uploadedFilePath), "Uploaded file should exist");
        try {
            byte[] uploadedContent = Files.readAllBytes(uploadedFilePath);
            assertArrayEquals(FILE_CONTENT, uploadedContent, "Uploaded file content should match");
        } catch (Exception e) {
            fail("Failed to read uploaded file: " + e.getMessage());
        }
    }

    @Test
    void testUploadBigFileIntegration() {
        byte[] bigFile = new byte[5 * 1024 * 1024]; // 5MB
        for (int i = 0; i < bigFile.length; i++) bigFile[i] = (byte) (i % 256);
        String bigFileName = "big-integration-test-file.bin";
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(2)
                .build();
        String uploadId = client.upload(bigFile, bigFileName, null, null);
        Path bigFilePath = Path.of("uploads", "complete", uploadId + "_" + bigFileName);
        assertTrue(Files.exists(bigFilePath), "Big uploaded file should exist");
        try {
            byte[] uploadedContent = Files.readAllBytes(bigFilePath);
            assertArrayEquals(bigFile, uploadedContent, "Big uploaded file content should match");
        } catch (Exception e) {
            fail("Failed to read big uploaded file: " + e.getMessage());
        }
    }

    @Test
    void testUploadFailWithWrongPassword() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password("wrongpassword")
                .retryTimes(2)
                .build();
        try {
            client.upload(FILE_CONTENT, FILENAME, null, null);
            fail("Upload should fail with wrong password");
        } catch (RuntimeException e) {
            String msg = e.getMessage().toLowerCase();
            System.out.println("Error message: " + msg);
            assertTrue(msg.contains("unauthorized") || msg.contains("401") || msg.contains("failed to initialize upload"), "Should fail with unauthorized error, got: " + msg);
        }
    }

    @Test
    void testUploadFailWithEmptyFile() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(uploadUrl)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(2)
                .build();
        try {
            client.upload(new byte[0], FILENAME, null, null);
            fail("Upload should fail with empty file");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().toLowerCase().contains("filecontent is required"), "Should fail with fileContent is required");
        }
    }
}
