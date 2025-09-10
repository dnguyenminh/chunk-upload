package vn.com.fecredit.chunkedupload.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import vn.com.fecredit.chunkedupload.model.*;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UploadSessionManagementIntegrationTest {

    @Autowired
    private TenantAccountRepository tenantRepo;

    @Autowired
    private UploadInfoRepository uploadInfoRepo;

    @Autowired
    private UploadInfoHistoryRepository uploadInfoHistoryRepo;

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        uploadInfoHistoryRepo.deleteAll();
        uploadInfoRepo.deleteAll();
        tenantRepo.deleteAll();

        TenantAccount user = new TenantAccount();
        user.setTenantId("testTenant");
        user.setUsername("user");
        user.setPassword("{bcrypt}$2a$10$Lu4NwC5fbHT7kXV0o0PdDuX2NGsz0U/4ipCCa3GezK5hHSOguhtaG");
        tenantRepo.save(user);

        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        cleanupTestDirectories();
    }

    private void cleanupTestDirectories() {
        try {
            Path inProgressDir = Paths.get("uploads/in-progress");
            Path completeDir = Paths.get("uploads/complete");

            if (Files.exists(inProgressDir)) {
                Files.walk(inProgressDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            }

            if (Files.exists(completeDir)) {
                Files.walk(completeDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    private HttpHeaders authHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/upload";
    }

    private Path createTestFile(String filename, int size) throws IOException {
        Path filePath = Paths.get(filename);
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) ('A' + (i % 26));
        }
        Files.write(filePath, content);
        return filePath;
    }

    private JsonNode startUpload(Path filePath, String username, String password) throws IOException {
        String url = baseUrl() + "/init";
        String checksum = ChecksumUtil.generateChecksum(filePath);

        String initJson = String.format(
            "{\"filename\":\"%s\", \"fileSize\":%d, \"checksum\":\"%s\"}",
            filePath.getFileName().toString(),
            Files.size(filePath),
            checksum
        );

        HttpHeaders headers = authHeaders(username, password);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(initJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> uploadChunk(String uploadId, int chunkNumber, byte[] chunkData,
                                             String username, String password) {
        String chunkUrl = baseUrl() + "/chunk";

        LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        params.add("uploadId", uploadId);
        params.add("chunkNumber", String.valueOf(chunkNumber));

        ByteArrayResource fileResource = new ByteArrayResource(chunkData) {
            @Override
            public String getFilename() {
                return "chunk" + chunkNumber + ".bin";
            }
        };
        params.add("file", fileResource);

        HttpHeaders headers = authHeaders(username, password);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(params, headers);

        return restTemplate.postForEntity(chunkUrl, entity, String.class);
    }

    @Test
    public void testUploadSessionInitializationWithTimeoutTracking() throws IOException {
        Path testFile = createTestFile("session-test.txt", 1000);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();
        assertNotNull(uploadId);
        assertFalse(uploadId.isEmpty());

        Optional<UploadInfo> uploadInfoOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertTrue(uploadInfoOpt.isPresent());

        UploadInfo uploadInfo = uploadInfoOpt.get();
        assertEquals(UploadInfo.STATUS_IN_PROGRESS, uploadInfo.getStatus());
        assertNotNull(uploadInfo.getLastUpdateDateTime());
        assertNotNull(uploadInfo.getUploadDateTime());

        Optional<UploadInfoHistory> historyOpt = uploadInfoHistoryRepo.findByUploadId(uploadId);
        assertFalse(historyOpt.isPresent());

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testChunkUploadUpdatesLastUpdateTime() throws IOException {
        int fileSize = 600000;
        Path testFile = createTestFile("update-time-test.txt", fileSize);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();
        int chunkSize = uploadResponse.get("chunkSize").asInt();

        UploadInfo initialUploadInfo = uploadInfoRepo.findByUploadId(uploadId).get();
        LocalDateTime initialUpdateTime = initialUploadInfo.getLastUpdateDateTime();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        byte[] chunkData = new byte[chunkSize];
        for (int i = 0; i < chunkSize; i++) {
            chunkData[i] = (byte) ('A' + (i % 26));
        }

        ResponseEntity<String> chunkResponse = uploadChunk(uploadId, 0, chunkData, "user", "password");

        assertEquals(HttpStatus.OK, chunkResponse.getStatusCode());

        UploadInfo updatedUploadInfo = uploadInfoRepo.findByUploadId(uploadId).get();
        LocalDateTime updatedTime = updatedUploadInfo.getLastUpdateDateTime();

        assertTrue(updatedTime.isAfter(initialUpdateTime),
            "lastUpdateDateTime should be updated after chunk upload");

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testCompleteUploadMovesToHistory() throws IOException {
        Path testFile = createTestFile("complete-test.txt", 1000);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();

        byte[] chunkData = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            chunkData[i] = (byte) ('A' + (i % 26));
        }

        ResponseEntity<String> chunkResponse = uploadChunk(uploadId, 0, chunkData, "user", "password");

        assertEquals(HttpStatus.OK, chunkResponse.getStatusCode());

        Optional<UploadInfo> uploadInfoOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertFalse(uploadInfoOpt.isPresent(), "UploadInfo should be removed after completion");

        Optional<UploadInfoHistory> historyOpt = uploadInfoHistoryRepo.findByUploadId(uploadId);
        assertTrue(historyOpt.isPresent(), "UploadInfoHistory should be created after completion");

        UploadInfoHistory history = historyOpt.get();
        assertEquals(UploadInfoHistory.STATUS_COMPLETED, history.getStatus());
        assertNotNull(history.getCompletionDateTime());
        assertEquals(testFile.getFileName().toString(), history.getFilename());

        Path completeDir = Paths.get("uploads/complete");
        String expectedFilename = uploadId + "_" + testFile.getFileName().toString();
        Path tenantCompleteDir = completeDir.resolve(String.valueOf(history.getTenant().getId()));
        Path finalPath = tenantCompleteDir.resolve(expectedFilename);
        assertTrue(Files.exists(finalPath), "Completed file should exist in complete directory");

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testTimeoutDetectionAndCleanup() throws IOException {
        Path testFile = createTestFile("timeout-test.txt", 1000);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();

        UploadInfo uploadInfo = uploadInfoRepo.findByUploadId(uploadId).get();
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(35);
        uploadInfo.setLastUpdateDateTime(oldTime);
        ((JpaRepository)uploadInfoRepo).save(uploadInfo);

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<UploadInfo> timedOutUploads = uploadInfoRepo.findByLastUpdateDateTimeBeforeAndStatus(
            cutoffTime, UploadInfo.STATUS_IN_PROGRESS);

        assertFalse(timedOutUploads.isEmpty(), "Should find timed-out uploads");
        assertEquals(1, timedOutUploads.size());
        assertEquals(uploadId, timedOutUploads.get(0).getUploadId());

        for (UploadInfo timedOutUpload : timedOutUploads) {
            UploadInfoHistory history = UploadInfoHistory.fromUploadInfo(timedOutUpload, UploadInfoHistory.STATUS_TIMED_OUT);
            uploadInfoHistoryRepo.save(history);
            uploadInfoRepo.delete(timedOutUpload);
        }

        Optional<UploadInfo> uploadInfoOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertFalse(uploadInfoOpt.isPresent(), "UploadInfo should be removed after timeout cleanup");

        Optional<UploadInfoHistory> historyOpt = uploadInfoHistoryRepo.findByUploadId(uploadId);
        assertTrue(historyOpt.isPresent(), "UploadInfoHistory should be created after timeout cleanup");

        UploadInfoHistory history = historyOpt.get();
        assertEquals(UploadInfoHistory.STATUS_TIMED_OUT, history.getStatus());
        assertNotNull(history.getCompletionDateTime());

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testMultipleUploadsWithDifferentTenants() throws IOException {
        TenantAccount user2 = new TenantAccount();
        user2.setTenantId("testTenant2");
        user2.setUsername("user2");
        user2.setPassword("{bcrypt}$2a$10$sTgrnsgRjbsRzg8ZrVhq8.amnNiyNs5KV24DHWW/SAI6pTN53TvEa");
        tenantRepo.save(user2);

        Path testFile1 = createTestFile("tenant1-test.txt", 1000);
        Path testFile2 = createTestFile("tenant2-test.txt", 1000);

        JsonNode uploadResponse1 = startUpload(testFile1, "user", "password");
        JsonNode uploadResponse2 = startUpload(testFile2, "user2", "password2");

        String uploadId1 = uploadResponse1.get("uploadId").asText();
        String uploadId2 = uploadResponse2.get("uploadId").asText();

        byte[] chunkData = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            chunkData[i] = (byte) ('A' + (i % 26));
        }

        uploadChunk(uploadId1, 0, chunkData, "user", "password");
        uploadChunk(uploadId2, 0, chunkData, "user2", "password2");

        List<UploadInfoHistory> tenant1History = uploadInfoHistoryRepo.findByTenantEagerly(tenantRepo.findByUsername("user").get());
        List<UploadInfoHistory> tenant2History = uploadInfoHistoryRepo.findByTenantEagerly(tenantRepo.findByUsername("user2").get());

        assertEquals(1, tenant1History.size());
        assertEquals(1, tenant2History.size());
        assertEquals("testTenant", tenant1History.get(0).getTenant().getTenantId());
        assertEquals("testTenant2", tenant2History.get(0).getTenant().getTenantId());

        Path completeDir = Paths.get("uploads/complete");
        String expectedFilename1 = uploadId1 + "_" + testFile1.getFileName().toString();
        String expectedFilename2 = uploadId2 + "_" + testFile2.getFileName().toString();

        Path tenant1ExpectedFile = completeDir.resolve(String.valueOf(tenant1History.get(0).getTenant().getId())).resolve(expectedFilename1);
        Path tenant2ExpectedFile = completeDir.resolve(String.valueOf(tenant2History.get(0).getTenant().getId())).resolve(expectedFilename2);

        assertTrue(Files.exists(tenant1ExpectedFile), "Tenant 1 should have its completed file in its own directory");
        assertTrue(Files.exists(tenant2ExpectedFile), "Tenant 2 should have its completed file in its own directory");

        Files.deleteIfExists(testFile1);
        Files.deleteIfExists(testFile2);
    }

    @Test
    public void testSessionStatusTransitions() throws IOException {
        Path testFile = createTestFile("status-test.txt", 1000);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();

        UploadInfo uploadInfo = uploadInfoRepo.findByUploadId(uploadId).get();
        assertEquals(UploadInfo.STATUS_IN_PROGRESS, uploadInfo.getStatus());

        byte[] chunkData = new byte[1000];
        for (int i = 0; i < 1000; i++) {
            chunkData[i] = (byte) ('A' + (i % 26));
        }
        uploadChunk(uploadId, 0, chunkData, "user", "password");

        Optional<UploadInfo> completedUploadOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertFalse(completedUploadOpt.isPresent(), "UploadInfo should be removed after completion");

        Optional<UploadInfoHistory> historyOpt = uploadInfoHistoryRepo.findByUploadId(uploadId);
        assertTrue(historyOpt.isPresent(), "UploadInfoHistory should exist");
        assertEquals(UploadInfoHistory.STATUS_COMPLETED, historyOpt.get().getStatus());

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testUploadAbortAndCleanup() throws IOException {
        Path testFile = createTestFile("abort-test.txt", 1000);
        JsonNode uploadResponse = startUpload(testFile, "user", "password");
        String uploadId = uploadResponse.get("uploadId").asText();

        Optional<UploadInfo> uploadInfoOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertTrue(uploadInfoOpt.isPresent());

        String abortUrl = baseUrl() + "/" + uploadId;
        HttpEntity<String> abortEntity = new HttpEntity<>(authHeaders("user", "password"));
        ResponseEntity<String> abortResponse = restTemplate.exchange(abortUrl, HttpMethod.DELETE, abortEntity, String.class);

        assertEquals(HttpStatus.NO_CONTENT, abortResponse.getStatusCode());

        Optional<UploadInfo> abortedUploadOpt = uploadInfoRepo.findByUploadId(uploadId);
        assertFalse(abortedUploadOpt.isPresent(), "UploadInfo should be removed after abort");

        Optional<UploadInfoHistory> historyOpt = uploadInfoHistoryRepo.findByUploadId(uploadId);
        assertFalse(historyOpt.isPresent(), "No history should be created for aborted uploads");

        Files.deleteIfExists(testFile);
    }

    @Test
    public void testConcurrentUploadsWithTimeoutTracking() throws IOException {
        int fileSize = 600000;
        Path testFile1 = createTestFile("concurrent1.txt", fileSize);
        Path testFile2 = createTestFile("concurrent2.txt", fileSize);

        JsonNode uploadResponse1 = startUpload(testFile1, "user", "password");
        JsonNode uploadResponse2 = startUpload(testFile2, "user", "password");

        String uploadId1 = uploadResponse1.get("uploadId").asText();
        String uploadId2 = uploadResponse2.get("uploadId").asText();

        int chunkSize = uploadResponse1.get("chunkSize").asInt();
        int totalChunks1 = uploadResponse1.get("totalChunks").asInt();
        int totalChunks2 = uploadResponse2.get("totalChunks").asInt();

        byte[] file1Bytes = Files.readAllBytes(testFile1);
        byte[] file2Bytes = Files.readAllBytes(testFile2);

        for (int chunkNum = 0; chunkNum < totalChunks1; chunkNum++) {
            int offset = chunkNum * chunkSize;
            int length = Math.min(chunkSize, (int) (fileSize - offset));
            byte[] chunkBytes = new byte[length];
            System.arraycopy(file1Bytes, offset, chunkBytes, 0, length);
            uploadChunk(uploadId1, chunkNum, chunkBytes, "user", "password");
        }

        for (int chunkNum = 0; chunkNum < totalChunks2; chunkNum++) {
            int offset = chunkNum * chunkSize;
            int length = Math.min(chunkSize, (int) (fileSize - offset));
            byte[] chunkBytes = new byte[length];
            System.arraycopy(file2Bytes, offset, chunkBytes, 0, length);
            uploadChunk(uploadId2, chunkNum, chunkBytes, "user", "password");
        }

        Optional<UploadInfoHistory> history1 = uploadInfoHistoryRepo.findByUploadId(uploadId1);
        Optional<UploadInfoHistory> history2 = uploadInfoHistoryRepo.findByUploadId(uploadId2);

        assertTrue(history1.isPresent(), "First upload should be in history");
        assertTrue(history2.isPresent(), "Second upload should be in history");
        assertEquals(UploadInfoHistory.STATUS_COMPLETED, history1.get().getStatus());
        assertEquals(UploadInfoHistory.STATUS_COMPLETED, history2.get().getStatus());

        Files.deleteIfExists(testFile1);
        Files.deleteIfExists(testFile2);
    }
}
