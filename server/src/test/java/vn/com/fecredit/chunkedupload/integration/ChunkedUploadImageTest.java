package vn.com.fecredit.chunkedupload.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import vn.com.fecredit.chunkedupload.model.TenantAccount;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChunkedUploadImageTest {

    @Autowired
    private vn.com.fecredit.chunkedupload.model.TenantAccountRepository repo;

    @BeforeEach
    public void setupTestUsers() {
        repo.deleteAll();
        TenantAccount user = new TenantAccount();
        user.setTenantId("testTenant3");
        user.setUsername("user3");
        user.setPassword("{bcrypt}$2a$10$Lu4NwC5fbHT7kXV0o0PdDuX2NGsz0U/4ipCCa3GezK5hHSOguhtaG"); // bcrypt for "password"
        repo.save(user);
    }

    @LocalServerPort
    private int port;

    private JsonNode startUpload(Path filePath, String username, String password) throws IOException {
        return startResumeUpload(filePath, null, username, password);
    }

    private RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private JsonNode startResumeUpload(Path filePath, String uploadId, String username, String password) throws IOException {
        String url = baseUrl() + "/init";
        String initJson = String.format("{\"fileSize\":%d, \"filename\":\"%s\", \"checksum\":\"%s\", \"brokenUploadId\":\"%s\"}",
                Files.size(filePath), filePath.getFileName(), ChecksumUtil.generateChecksum(filePath), uploadId == null ? "" : uploadId);
        HttpHeaders headers = authHeaders(username, password);
        HttpEntity<String> entity = new HttpEntity<>(initJson, headers);
        ResponseEntity<String> response = restTemplate().postForEntity(url, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(response.getBody());
        assertTrue(json.has("uploadId"));
        assertFalse(json.get("uploadId").asText().isEmpty());
        return json;
    }

    private HttpHeaders authHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(username, password);
        return headers;
    }

    private ResponseEntity<String> uploadChunk(String uploadId, int chunkNumber, ByteArrayResource fileResource, String username, String password) {
        String chunkUrl = baseUrl() + "/chunk";
        LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        params.add("uploadId", uploadId);
        params.add("chunkNumber", String.valueOf(chunkNumber));
        params.add("file", fileResource);
        HttpHeaders headers = authHeaders(username, password);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(params, headers);
        return restTemplate().postForEntity(chunkUrl, entity, String.class);
    }

    @Test
    public void testChunkedImageUploadAndRead() throws Exception {
        String filename = "big-image-2.jpg";
        Path imagePath = Paths.get("src/test/images/" + filename);
        JsonNode uploadJson = startUpload(imagePath, "user3", "password");
        String uploadId = uploadJson.get("uploadId").asText();
        int chunkSize = uploadJson.get("chunkSize").asInt();
        long fileSize = Files.size(imagePath);
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        long restFileSize = fileSize;
        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
                int buffSize = restFileSize < chunkSize ? (int) restFileSize : chunkSize;
                restFileSize = restFileSize - chunkSize;
                byte[] buff = new byte[buffSize];
                int bytesRead = inputStream.read(buff);
                assertEquals(buffSize, bytesRead);
                ByteArrayResource fileResource = new ByteArrayResource(buff) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };

                ResponseEntity<String> resp = uploadChunk(uploadId, chunkNumber, fileResource, "user3", "password");

                assertTrue(resp.getStatusCode().is2xxSuccessful(), "Chunk upload failed: " + resp.getBody());
            }
        }

        // Read assembled file
        Optional<TenantAccount> userAccountOpt = repo.findByUsername("user3");
        assertTrue(userAccountOpt.isPresent(), "Test user 'user3' not found in database");
        Long userId = userAccountOpt.get().getId();
        Path assembledPath = Paths.get("uploads/complete/" + userId + "/" + filename);
        System.out.println("assembledPath ==> " + assembledPath.toAbsolutePath());

        assertTrue(Files.exists(assembledPath), "Assembled file not found");

    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/upload";
    }
}
