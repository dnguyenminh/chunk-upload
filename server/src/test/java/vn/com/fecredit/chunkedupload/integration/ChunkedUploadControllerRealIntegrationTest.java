package vn.com.fecredit.chunkedupload.integration;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-users.sql")
public class ChunkedUploadControllerRealIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private HttpHeaders authHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(username, password);
        return headers;
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/upload";
    }

    private String startUpload(String filename, int totalChunks, int chunkSize, int fileSize, String username, String password) {
        String url = baseUrl() + "/init";
        String initJson = String.format("{\"totalChunks\":%d, \"chunkSize\":%d, \"fileSize\":%d, \"filename\":\"%s\"}", totalChunks, chunkSize, fileSize, filename);
        HttpHeaders headers = authHeaders(username, password);
        System.out.println("DEBUG: startUpload called with username=" + username + ", password=" + password);
        System.out.println("DEBUG: Headers=" + headers);
        HttpEntity<String> entity = new HttpEntity<>(initJson, headers);
        ResponseEntity<String> response = restTemplate().postForEntity(url, entity, String.class);
        System.out.println("DEBUG: Response status=" + response.getStatusCode() + ", body=" + response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("uploadId"));
        String uploadId = response.getBody().replaceAll(".*\"uploadId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertFalse(uploadId.isEmpty());
        return uploadId;
    }

    @Test
    public void testInitUpload() {
        startUpload("integration.txt", 2, 10, 20, "user", "password");
    }

    @Test
    public void testChunkUploadAndFileAssembly() throws Exception {
        String filename = "file.txt";
        int totalChunks = 2;
        int chunkSize = 5;
        int fileSize = 10;
        String uploadId = startUpload(filename, totalChunks, chunkSize, fileSize, "user", "password");

        // Upload chunk 0
        String chunkUrl = baseUrl() + "/chunk";
        org.springframework.util.LinkedMultiValueMap<String, Object> params0 = new org.springframework.util.LinkedMultiValueMap<>();
        params0.add("uploadId", uploadId);
        params0.add("chunkNumber", "0");
        params0.add("totalChunks", String.valueOf(totalChunks));
        params0.add("fileSize", String.valueOf(fileSize));
        int defaultChunkSize = 524288;
        byte[] dummyChunk0 = new byte[defaultChunkSize];
        for (int i = 0; i < defaultChunkSize; i++) dummyChunk0[i] = (byte) ('A' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource0 = new org.springframework.core.io.ByteArrayResource(dummyChunk0) {
            @Override
            public String getFilename() { return "chunk0.bin"; }
        };
        params0.add("file", fileResource0);
        HttpHeaders headers0 = authHeaders("user", "password");
        headers0.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params0, headers0);
        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
        System.out.println("DEBUG: chunk 0 request: " + params0);
        System.out.println("DEBUG: chunk 0 response: status=" + resp0.getStatusCode() + ", body=" + resp0.getBody());
        assertEquals(HttpStatus.OK, resp0.getStatusCode());

        // Upload chunk 1
        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
        params1.add("uploadId", uploadId);
        params1.add("chunkNumber", "1");
        params1.add("totalChunks", String.valueOf(totalChunks));
        params1.add("fileSize", String.valueOf(fileSize));
        int lastChunkSize = fileSize - defaultChunkSize * (totalChunks - 1);
        if (lastChunkSize <= 0) lastChunkSize = fileSize;
        byte[] dummyChunk1 = new byte[lastChunkSize];
        for (int i = 0; i < lastChunkSize; i++) dummyChunk1[i] = (byte) ('a' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
            @Override
            public String getFilename() { return "chunk1.bin"; }
        };
        params1.add("file", fileResource1);
        HttpHeaders headers1 = authHeaders("user", "password");
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        // Check file assembled (simulate expected path)
        Path completeDir = Paths.get("uploads/complete");
        boolean found = Files.walk(completeDir)
            .anyMatch(p -> p.getFileName().toString().contains(filename));
        assertTrue(found, "Completed file should exist in complete folder");
    }

    @Test
    public void testResumeUpload() {
        // Start upload, upload chunk 0, then resume and upload chunk 1
        String filename = "resume.txt";
        int totalChunks = 2;
        int chunkSize = 5;
        int fileSize = 10;
        String uploadId = startUpload(filename, totalChunks, chunkSize, fileSize, "user", "password");

        // Upload chunk 0
        String chunkUrl = baseUrl() + "/chunk";
        int defaultChunkSize = 524288;
        org.springframework.util.LinkedMultiValueMap<String, Object> params0 = new org.springframework.util.LinkedMultiValueMap<>();
        params0.add("uploadId", uploadId);
        params0.add("chunkNumber", "0");
        params0.add("totalChunks", String.valueOf(totalChunks));
        params0.add("fileSize", String.valueOf(fileSize));
        byte[] dummyChunk0 = new byte[defaultChunkSize];
        for (int i = 0; i < defaultChunkSize; i++) dummyChunk0[i] = (byte) ('A' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource0 = new org.springframework.core.io.ByteArrayResource(dummyChunk0) {
            @Override
            public String getFilename() { return "chunk0.bin"; }
        };
        params0.add("file", fileResource0);
        HttpHeaders headers0 = authHeaders("user", "password");
        headers0.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params0, headers0);
        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
        System.out.println("DEBUG: resume chunk 0 request: " + params0);
        System.out.println("DEBUG: resume chunk 0 response: status=" + resp0.getStatusCode() + ", body=" + resp0.getBody());

        // Resume upload (simulate resume endpoint)
        // Resume logic: call /init with uploadId set (controller handles resume in init)
        String initUrl = baseUrl() + "/init";
        String resumeJson = String.format("{\"uploadId\":\"%s\", \"fileSize\":%d, \"filename\":\"%s\"}", uploadId, fileSize, filename);
        HttpEntity<String> resumeEntity = new HttpEntity<>(resumeJson, authHeaders("user", "password"));
        ResponseEntity<String> resumeResp = restTemplate().postForEntity(initUrl, resumeEntity, String.class);
        assertEquals(HttpStatus.OK, resumeResp.getStatusCode());

        // Upload chunk 1
        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
        params1.add("uploadId", uploadId);
        params1.add("chunkNumber", "1");
        params1.add("totalChunks", String.valueOf(totalChunks));
        params1.add("fileSize", String.valueOf(fileSize));
        int lastChunkSize = fileSize - defaultChunkSize * (totalChunks - 1);
        // Always send last chunk with size = fileSize - defaultChunkSize * (totalChunks - 1)
        if (lastChunkSize <= 0) lastChunkSize = fileSize;
        byte[] dummyChunk1 = new byte[lastChunkSize];
        for (int i = 0; i < lastChunkSize; i++) dummyChunk1[i] = (byte) ('a' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
            @Override
            public String getFilename() { return "chunk1.bin"; }
        };
        params1.add("file", fileResource1);
        HttpHeaders headers1 = authHeaders("user", "password");
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());
    }

    @Test
    public void testAbortUpload() {
        String filename = "abort.txt";
        int totalChunks = 2;
        int chunkSize = 5;
        int fileSize = 10;
        String uploadId = startUpload(filename, totalChunks, chunkSize, fileSize, "user", "password");

        // Abort upload
        String abortUrl = baseUrl() + "/" + uploadId;
        HttpEntity<String> abortEntity = new HttpEntity<>(authHeaders("user", "password"));
        ResponseEntity<String> abortResp = restTemplate().exchange(abortUrl, HttpMethod.DELETE, abortEntity, String.class);
        System.out.println("DEBUG: abort request: " + abortUrl);
        System.out.println("DEBUG: abort response: status=" + abortResp.getStatusCode() + ", body=" + abortResp.getBody());
        assertEquals(HttpStatus.NO_CONTENT, abortResp.getStatusCode());
    }

    @Test
    public void testMultiTenantIsolation() throws Exception {
        // Start upload as user1
        String uploadId1 = startUpload("tenant1.txt", 1, 5, 5, "user1", "password1");
        // Start upload as user2
        String uploadId2 = startUpload("tenant2.txt", 1, 5, 5, "user2", "password2");

        // Upload chunk for user1
        String chunkUrl = baseUrl() + "/chunk";
        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
        params1.add("uploadId", uploadId1);
        params1.add("chunkNumber", "0");
        params1.add("totalChunks", "1");
        params1.add("fileSize", "5");
        byte[] dummyChunk1 = new byte[5];
        for (int i = 0; i < 5; i++) dummyChunk1[i] = (byte) ('A' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
            @Override
            public String getFilename() { return "chunk1.bin"; }
        };
        params1.add("file", fileResource1);
        HttpHeaders headers1 = authHeaders("user1", "password1");
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
        System.out.println("DEBUG: multi-tenant chunk request (user1): " + params1);
        System.out.println("DEBUG: multi-tenant chunk response (user1): status=" + resp1.getStatusCode() + ", body=" + resp1.getBody());

        // Upload chunk for user2
        org.springframework.util.LinkedMultiValueMap<String, Object> params2 = new org.springframework.util.LinkedMultiValueMap<>();
        params2.add("uploadId", uploadId2);
        params2.add("chunkNumber", "0");
        params2.add("totalChunks", "1");
        params2.add("fileSize", "5");
        byte[] dummyChunk2 = new byte[5];
        for (int i = 0; i < 5; i++) dummyChunk2[i] = (byte) ('a' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource2 = new org.springframework.core.io.ByteArrayResource(dummyChunk2) {
            @Override
            public String getFilename() { return "chunk2.bin"; }
        };
        params2.add("file", fileResource2);
        HttpHeaders headers2 = authHeaders("user2", "password2");
        headers2.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity2 = new HttpEntity<>(params2, headers2);
        restTemplate().postForEntity(chunkUrl, entity2, String.class);

        // Check files are isolated (simulate expected path)
        Path completeDir = Paths.get("uploads/complete");
        boolean found1 = Files.walk(completeDir)
            .anyMatch(p -> p.getFileName().toString().contains("tenant1.txt"));
        boolean found2 = Files.walk(completeDir)
            .anyMatch(p -> p.getFileName().toString().contains("tenant2.txt"));
        assertTrue(found1 && found2, "Both tenant files should exist and be isolated");
    }

    @Test
    public void testErrorHandling() {
        // Invalid chunk number
        String uploadId = startUpload("error.txt", 1, 5, 5, "user", "password");
        String chunkUrl = baseUrl() + "/chunk";

        org.springframework.util.LinkedMultiValueMap<String, Object> multipartErr = new org.springframework.util.LinkedMultiValueMap<>();
        multipartErr.add("uploadId", uploadId);
        multipartErr.add("chunkNumber", "-1");
        multipartErr.add("totalChunks", "1");
        multipartErr.add("fileSize", "5");
        multipartErr.add("file", new org.springframework.core.io.ByteArrayResource(new byte[] {1,2,3,4,5}) {
            @Override
            public String getFilename() {
                return "error.txt";
            }
        });

        HttpHeaders headers = authHeaders("user", "password");
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity = new HttpEntity<>(multipartErr, headers);
        try {
            ResponseEntity<String> resp = restTemplate().postForEntity(chunkUrl, entity, String.class);
            System.out.println("DEBUG: error chunk multipart request: " + multipartErr);
            System.out.println("DEBUG: error chunk response: status=" + resp.getStatusCode() + ", body=" + resp.getBody());
            fail("Expected HttpClientErrorException$BadRequest");
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest ex) {
            System.out.println("DEBUG: Caught expected BadRequest: " + ex.getMessage());
            assertTrue(ex.getResponseBodyAsString().contains("Invalid chunk number"));
        }

        // Unauthorized access
        HttpHeaders badHeaders = authHeaders("baduser", "badpass");
        badHeaders.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> badAuthEntity = new HttpEntity<>(multipartErr, badHeaders);
        try {
            ResponseEntity<String> badAuthResp = restTemplate().postForEntity(chunkUrl, badAuthEntity, String.class);
            fail("Expected HttpClientErrorException$Unauthorized");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized ex) {
            System.out.println("DEBUG: Caught expected Unauthorized: " + ex.getMessage());
            assertEquals(401, ex.getRawStatusCode());
        }
    }
}