package vn.com.fecredit.chunkedupload.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "/test-users.sql")
public class ChunkedUploadImageTest {
    @LocalServerPort
    private int port;

    @Test
    public void testChunkedImageUploadAndRead() throws Exception {
        String filename = "big-image-2.jpg";
        Path imagePath = Paths.get("src/test/images/" + filename);
        byte[] imageBytes = Files.readAllBytes(imagePath);

        // Call init API to get uploadId and chunkSize
        HttpHeaders initHeaders = new HttpHeaders();
        initHeaders.setContentType(MediaType.APPLICATION_JSON);
        initHeaders.setBasicAuth("user", "password");
        String initUrl = baseUrl() + "/init";
        String initBody = String.format("{\"filename\":\"%s\",\"fileSize\":%d}", filename, imageBytes.length);
        HttpEntity<String> initEntity = new HttpEntity<>(initBody, initHeaders);
        ResponseEntity<String> initResp = new org.springframework.web.client.RestTemplate()
                .postForEntity(initUrl, initEntity, String.class);
        assertTrue(initResp.getStatusCode().is2xxSuccessful(), "Init API failed: " + initResp.getBody());
        String initJson = initResp.getBody();
        String uploadId = initJson.replaceAll(".*\"uploadId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        int chunkSize = Integer.parseInt(initJson.replaceAll(".*\"chunkSize\"\\s*:\\s*(\\d+).*", "$1"));
        int totalChunks = (int) Math.ceil((double) imageBytes.length / chunkSize);

        // Upload chunks
        for (int chunkNumber = 0; chunkNumber < totalChunks; chunkNumber++) {
            int start = chunkNumber * chunkSize;
            int end = (chunkNumber == totalChunks - 1) ? imageBytes.length : start + chunkSize;
            int actualChunkSize = end - start;
            byte[] chunkData = new byte[actualChunkSize];
            System.arraycopy(imageBytes, start, chunkData, 0, actualChunkSize);

            MultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
            params.add("uploadId", uploadId);
            params.add("chunkNumber", String.valueOf(chunkNumber));
            params.add("totalChunks", String.valueOf(totalChunks));
            params.add("fileSize", String.valueOf(imageBytes.length));
            params.add("file", new ByteArrayResource(chunkData) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBasicAuth("user", "password");

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<String> resp = new org.springframework.web.client.RestTemplate()
                    .postForEntity(baseUrl()+"/chunk", entity, String.class);

            assertTrue(resp.getStatusCode().is2xxSuccessful(), "Chunk upload failed: " + resp.getBody());
        }

        // Read assembled file
        Path assembledPath = Paths.get("uploads/complete/1/" + uploadId + "_" + filename);
        assertTrue(Files.exists(assembledPath), "Assembled file not found");

        byte[] assembledBytes = Files.readAllBytes(assembledPath);
        assertArrayEquals(imageBytes, assembledBytes, "Uploaded and assembled image do not match");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/upload";
    }
}