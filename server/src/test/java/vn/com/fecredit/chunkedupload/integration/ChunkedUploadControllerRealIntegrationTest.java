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
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChunkedUploadControllerRealIntegrationTest {

    @Autowired
    private vn.com.fecredit.chunkedupload.model.TenantAccountRepository repo;

    @BeforeEach
    public void setupTestUsers() {
        repo.deleteAll();
        vn.com.fecredit.chunkedupload.model.TenantAccount user = new vn.com.fecredit.chunkedupload.model.TenantAccount();
        user.setTenantId("testTenant");
        user.setUsername("user");
        user.setPassword("{bcrypt}$2a$10$Lu4NwC5fbHT7kXV0o0PdDuX2NGsz0U/4ipCCa3GezK5hHSOguhtaG");
        repo.save(user);
        vn.com.fecredit.chunkedupload.model.TenantAccount user1 = new vn.com.fecredit.chunkedupload.model.TenantAccount();
        user1.setTenantId("testTenant1");
        user1.setUsername("user1");
        user1.setPassword("{bcrypt}$2a$10$/SwSpnLX3sJ5ahPfwvqtJuS2f0dq8ijPqxg0b2KSqAysvtffgpfpK");
        repo.save(user1);
        vn.com.fecredit.chunkedupload.model.TenantAccount user2 = new vn.com.fecredit.chunkedupload.model.TenantAccount();
        user2.setTenantId("testTenant2");
        user2.setUsername("user2");
        user2.setPassword("{bcrypt}$2a$10$sTgrnsgRjbsRzg8ZrVhq8.amnNiyNs5KV24DHWW/SAI6pTN53TvEa");
        repo.save(user2);
    }

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

    private JsonNode startUpload(Path filePath, String username, String password) throws IOException {
        return startResumeUpload(filePath, null, username, password);
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

//    private ResponseEntity<String> uploadChunk(String uploadId, int chunkNumber, ByteArrayResource fileResource, String username, String password) {
//        String chunkUrl = baseUrl() + "/chunk";
//        LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
//        params.add("uploadId", uploadId);
//        params.add("chunkNumber", String.valueOf(chunkNumber));
//        params.add("file", fileResource);
//        HttpHeaders headers = authHeaders(username, password);
//        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//        HttpEntity<LinkedMultiValueMap<String, Object>> entity = new HttpEntity<>(params, headers);
//        ResponseEntity<String> resp = restTemplate().postForEntity(chunkUrl, entity, String.class);
//        return resp;
//    }

    @Test
    public void testInitUpload() throws IOException {
        Path filePath = createTempFile(Path.of("integration.txt"), 800);
        JsonNode uploadJson = startUpload(filePath, "user", "password");
        String uploadId = uploadJson.get("uploadId").asText();
        assertNotNull(uploadId);
    }

    @Test
    public void testChunkUploadAndFileAssembly() throws Exception {
        Path filePath = createTempFile(Path.of("file.txt"), 524288 + 800);
        JsonNode uploadJson = startUpload(filePath, "user", "password");
        String uploadId = uploadJson.get("uploadId").asText();

        // Upload chunk 0
        String chunkUrl = baseUrl() + "/chunk";
        org.springframework.util.LinkedMultiValueMap<String, Object> params0 = new org.springframework.util.LinkedMultiValueMap<>();
        params0.add("uploadId", uploadId);
        params0.add("chunkNumber", "0");

        int defaultChunkSize = 524288;
        byte[] dummyChunk0 = new byte[defaultChunkSize];
        for (int i = 0; i < defaultChunkSize; i++) dummyChunk0[i] = (byte) ('R' + i % 26);
        ByteArrayResource fileResource0 = new ByteArrayResource(dummyChunk0) {
            @Override
            public String getFilename() {
                return "chunk0.bin";
            }
        };
        params0.add("file", fileResource0);
        HttpHeaders headers0 = authHeaders("user", "password");
        headers0.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params0, headers0);
        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
        assertEquals(HttpStatus.OK, resp0.getStatusCode());

        // Upload chunk 1
        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
        params1.add("uploadId", uploadId);
        params1.add("chunkNumber", "1");

        byte[] dummyChunk1 = new byte[800];
        for (int i = 0; i < 800; i++) dummyChunk1[i] = (byte) ('R' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
            @Override
            public String getFilename() {
                return "chunk1.bin";
            }
        };
        params1.add("file", fileResource1);
        HttpHeaders headers1 = authHeaders("user", "password");
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        // Check file assembled
        Path completeDir = Paths.get("uploads/complete");
        boolean found;
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(completeDir)) {
            found = stream.anyMatch(p -> p.getFileName().toString().contains(filePath.getFileName().toString()));
        }
        assertTrue(found, "Completed file should exist in complete folder");
    }

    private Path createTempFile(Path filePath, int expectedFileSize) throws IOException {
        int defaultChunkSize = 524288;
//        int fileSize = defaultChunkSize * 4 + 10;
        int buffCount = (int) Math.ceil((double) expectedFileSize / defaultChunkSize);
        int fileSizeLeft = expectedFileSize;
        try (OutputStream fos = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {
            for (int i = 0; i < buffCount; i++) {
                int buffFill = Math.min(defaultChunkSize, fileSizeLeft);
                fileSizeLeft = fileSizeLeft - defaultChunkSize;
                byte[] fileBuff = new byte[buffFill];
                for (int j = 0; j < buffFill; j++) {
                    fileBuff[j] = (byte) ('R' + j % 26);
                }
                fos.write(fileBuff);
            }
        }
        return filePath;
    }

    public int[] shuffleArray(int[] array) {
        Random rand = new Random(); // Use Random for better randomness than Math.random()
        for (int i = array.length - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1)); // Generate random index between 0 and i
            // Swap elements at i and j
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
        return array;
    }

    @Test
    public void testResumeUpload() throws IOException {
        // Prepare temp file and content for resume test
        final String FILE_NAME = "ResumeUpload.bin";
        int fileSize = 524288 * 4 + 10;
        Path filePath = createTempFile(Path.of(FILE_NAME), fileSize);
        // Start upload
        JsonNode uploadJson = startUpload(filePath, "user", "password");
        int chunkSize = uploadJson.get("chunkSize").asInt();
        String uploadId = uploadJson.get("uploadId").asText();
        System.out.println(uploadJson.get("bitsetBytes").toString());
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        int[] chunkIndices = new int[totalChunks];
        for (int i = 0; i < totalChunks; i++) chunkIndices[i] = i;
        chunkIndices = shuffleArray(chunkIndices);

        try (InputStream intputStream = Files.newInputStream(filePath, StandardOpenOption.READ)) {
            int halfChunks = totalChunks / 2;
            for (int i = 0; i < halfChunks / 2; i++) {
                int chunkIndex = chunkIndices[i];
                String chunkUrl = baseUrl() + "/chunk";
                int buffSize = chunkSize;
                if (chunkIndex > totalChunks - 2) {
                    buffSize = fileSize - (chunkIndex * chunkSize);
                }
                byte[] buff = new byte[buffSize];
                intputStream.read(buff);
                ByteArrayResource fileResource = new ByteArrayResource(buff) {
                    @Override
                    public String getFilename() {
                        return FILE_NAME;
                    }
                };
                ResponseEntity<String> resp0 = chunkUpload(fileResource, uploadId, chunkIndex, chunkUrl);
                assertEquals(HttpStatus.OK, resp0.getStatusCode());
            }

            JsonNode resumeUploadJson = startResumeUpload(filePath, uploadId, "user", "password");
            String resumeUploadId = uploadJson.get("uploadId").asText();
            assertEquals(uploadId, resumeUploadId);

            System.out.println(resumeUploadJson.get("bitsetBytes").toString());
        }

//
//        // Upload chunk 0 (first defaultChunkSize bytes)
////        params0.add("totalChunks", (int) Math.ceil((double) fileSize / defaultChunkSize));
////        params0.add("fileSize", String.valueOf(fileSize));
//
//        // Resume upload (simulate resume endpoint)
//        String initUrl = baseUrl() + "/init";
//        String resumeJson = String.format("{\"uploadId\":\"%s\", \"fileSize\":%d, \"filename\":\"%s\", \"checksum\":\"testchecksum\"}", uploadId, fileSize, filePath.getFileName());
//        HttpEntity<String> resumeEntity = new HttpEntity<>(resumeJson, authHeaders("user", "password"));
//        ResponseEntity<String> resumeResp = restTemplate().postForEntity(initUrl, resumeEntity, String.class);
//        assertEquals(HttpStatus.OK, resumeResp.getStatusCode());
//
//
//        byte[] chunk = new byte[defaultChunkSize];
//        for (int i = 0; i < 3; i++) {
//            org.springframework.core.io.ByteArrayResource fillBuff = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
//                @Override
//                public String getFilename() {
//                    return "chunk1.bin";
//                }
//            };
//        }
//        params0.add("file", fileResource0);
//        HttpHeaders headers0 = authHeaders("user", "password");
//        headers0.setContentType(MediaType.MULTIPART_FORM_DATA);
//        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params0, headers0);
//        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
//        assertEquals(HttpStatus.OK, resp0.getStatusCode());
//
//        // Resume upload (simulate resume endpoint)
//        // Duplicate resume upload logic removed; already handled above.
////        System.arraycopy(fileContent, 0, chunk0, 0, defaultChunkSize);
////        org.springframework.core.io.ByteArrayResource fileResource0 = new org.springframework.core.io.ByteArrayResource(chunk0) {
////            @Override
////            public String getFilename() {
////                return "chunk0.bin";
////            }
////        };
////        params0.add("file", fileResource0);
////        HttpHeaders headers0 = authHeaders("user", "password");
////        headers0.setContentType(MediaType.MULTIPART_FORM_DATA);
////        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params0, headers0);
////        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
////        assertEquals(HttpStatus.OK, resp0.getStatusCode());
////
////        // Resume upload (simulate resume endpoint)
////        String initUrl = baseUrl() + "/init";
////        String resumeJson = String.format("{\"uploadId\":\"%s\", \"fileSize\":%d, \"filename\":\"%s\", \"checksum\":\"testchecksum\"}", uploadId, fileSize, filePath.getFileName());
////        HttpEntity<String> resumeEntity = new HttpEntity<>(resumeJson, authHeaders("user", "password"));
////        ResponseEntity<String> resumeResp = restTemplate().postForEntity(initUrl, resumeEntity, String.class);
////        assertEquals(HttpStatus.OK, resumeResp.getStatusCode());
////
////        // Upload chunk 1 (remaining bytes)
////        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
////        params1.add("uploadId", uploadId);
////        params1.add("chunkNumber", "1");
////        params1.add("totalChunks", "2");
////        params1.add("fileSize", String.valueOf(fileSize));
////        int chunk1Size = fileSize - defaultChunkSize;
////        byte[] chunk1 = new byte[chunk1Size];
////        System.arraycopy(fileContent, defaultChunkSize, chunk1, 0, chunk1Size);
////        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(chunk1) {
////            @Override
////            public String getFilename() {
////                return "chunk1.bin";
////            }
////        };
////        params1.add("file", fileResource1);
////        HttpHeaders headers1 = authHeaders("user", "password");
////        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
////        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
////        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
////        assertEquals(HttpStatus.OK, resp1.getStatusCode());
////
////        // Optionally: verify file assembled
////        Path completeDir = Paths.get("uploads/complete");
////        boolean found;
////        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(completeDir)) {
////            found = stream.anyMatch(p -> p.getFileName().toString().contains(filePath.getFileName().toString()));
////        }
////        assertTrue(found, "Completed file should exist in complete folder");
    }

    private ResponseEntity<String> chunkUpload(ByteArrayResource fileResource, String uploadId, int chunkIndex, String chunkUrl) throws IOException {
        LinkedMultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
        params.add("file", fileResource);
        params.add("uploadId", uploadId);
        params.add("chunkNumber", String.valueOf(chunkIndex));
        HttpHeaders headers = authHeaders("user", "password");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity0 = new HttpEntity<>(params, headers);
        ResponseEntity<String> resp0 = restTemplate().postForEntity(chunkUrl, entity0, String.class);
        return resp0;
    }

    @Test
    public void testAbortUpload() throws IOException {
        Path filePath = createTempFile(Path.of("abort.txt"), 800);
        com.fasterxml.jackson.databind.JsonNode uploadJson = startUpload(filePath, "user", "password");
        String uploadId = uploadJson.get("uploadId").asText();

        // Abort upload
        String abortUrl = baseUrl() + "/" + uploadId;
        HttpEntity<String> abortEntity = new HttpEntity<>(authHeaders("user", "password"));
        ResponseEntity<String> abortResp = restTemplate().exchange(abortUrl, HttpMethod.DELETE, abortEntity, String.class);
        assertEquals(HttpStatus.NO_CONTENT, abortResp.getStatusCode());
    }

    @Test
    public void testMultiTenantIsolation() throws IOException {
        Path filePath1 = createTempFile(Path.of("tenant1.txt"), 600);
        Path filePath2 = createTempFile(Path.of("tenant2.txt"), 800);
        ;
        com.fasterxml.jackson.databind.JsonNode uploadJson1 = startUpload(filePath1, "user1", "password1");
        String uploadId1 = uploadJson1.get("uploadId").asText();
        com.fasterxml.jackson.databind.JsonNode uploadJson2 = startUpload(filePath2, "user2", "password2");
        String uploadId2 = uploadJson2.get("uploadId").asText();

        // Upload chunk for user1
        String chunkUrl = baseUrl() + "/chunk";
        org.springframework.util.LinkedMultiValueMap<String, Object> params1 = new org.springframework.util.LinkedMultiValueMap<>();
        params1.add("uploadId", uploadId1);
        params1.add("chunkNumber", "0");
        params1.add("totalChunks", "1");
        params1.add("fileSize", String.valueOf(Files.size(filePath1)));
        byte[] dummyChunk1 = new byte[(int) Files.size(filePath1)];
        for (int i = 0; i < dummyChunk1.length; i++) dummyChunk1[i] = (byte) ('R' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource1 = new org.springframework.core.io.ByteArrayResource(dummyChunk1) {
            @Override
            public String getFilename() {
                return "chunk1.bin";
            }
        };
        params1.add("file", fileResource1);
        HttpHeaders headers1 = authHeaders("user1", "password1");
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity1 = new HttpEntity<>(params1, headers1);
        ResponseEntity<String> resp1 = restTemplate().postForEntity(chunkUrl, entity1, String.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        // Upload chunk for user2
        org.springframework.util.LinkedMultiValueMap<String, Object> params2 = new org.springframework.util.LinkedMultiValueMap<>();
        params2.add("uploadId", uploadId2);
        params2.add("chunkNumber", "0");
        params2.add("totalChunks", "1");
        params2.add("fileSize", String.valueOf(Files.size(filePath2)));
        byte[] dummyChunk2 = new byte[(int) Files.size(filePath2)];
        for (int i = 0; i < dummyChunk2.length; i++) dummyChunk2[i] = (byte) ('R' + i % 26);
        org.springframework.core.io.ByteArrayResource fileResource2 = new org.springframework.core.io.ByteArrayResource(dummyChunk2) {
            @Override
            public String getFilename() {
                return "chunk2.bin";
            }
        };
        params2.add("file", fileResource2);
        HttpHeaders headers2 = authHeaders("user2", "password2");
        headers2.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity2 = new HttpEntity<>(params2, headers2);
        ResponseEntity<String> resp2 = restTemplate().postForEntity(chunkUrl, entity2, String.class);
        assertEquals(HttpStatus.OK, resp2.getStatusCode());

        // Check files are isolated
        Path completeDir = Paths.get("uploads/complete");
        boolean found1, found2;
        try (java.util.stream.Stream<Path> stream1 = java.nio.file.Files.walk(completeDir)) {
            found1 = stream1.anyMatch(p -> p.getFileName().toString().contains(filePath1.getFileName().toString()));
        }
        try (java.util.stream.Stream<Path> stream2 = java.nio.file.Files.walk(completeDir)) {
            found2 = stream2.anyMatch(p -> p.getFileName().toString().contains(filePath2.getFileName().toString()));
        }
        assertTrue(found1 && found2, "Both tenant files should exist and be isolated");
    }

    @Test
    public void testErrorHandling() throws IOException {
        Path filePath = createTempFile(Path.of("error.txt"), 800);
        com.fasterxml.jackson.databind.JsonNode uploadJson = startUpload(filePath, "user", "password");
        String uploadId = uploadJson.get("uploadId").asText();
        String chunkUrl = baseUrl() + "/chunk";

        org.springframework.util.LinkedMultiValueMap<String, Object> multipartErr = new org.springframework.util.LinkedMultiValueMap<>();
        multipartErr.add("uploadId", uploadId);
        multipartErr.add("chunkNumber", "-1");
        multipartErr.add("totalChunks", "1");
        multipartErr.add("fileSize", String.valueOf(Files.size(filePath)));
        multipartErr.add("file", new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3, 4, 5}) {
            @Override
            public String getFilename() {
                return "error.txt";
            }
        });

        HttpHeaders headers = authHeaders("user", "password");
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity = new HttpEntity<>(multipartErr, headers);
        try {
            ResponseEntity<String> resp = restTemplate().postForEntity(chunkUrl, entity, String.class);
            fail("Expected HttpClientErrorException$BadRequest");
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest ex) {
            assertTrue(ex.getResponseBodyAsString().contains("Invalid chunk size for chunk -1"));
        }

        // Unauthorized access
        HttpHeaders badHeaders = authHeaders("baduser", "badpass");
        badHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<org.springframework.util.MultiValueMap<String, Object>> badAuthEntity = new HttpEntity<>(multipartErr, badHeaders);
        try {
            restTemplate().postForEntity(chunkUrl, badAuthEntity, String.class);
            fail("Expected HttpClientErrorException$Unauthorized");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized ex) {
            assertEquals(401, ex.getStatusCode().value());
        }
    }
}