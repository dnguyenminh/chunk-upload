package vn.com.fecredit.chunkedupload.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * This class contains unit tests for the ChunkedUploadController.
 * It uses Spring's MockMvc to simulate HTTP requests and verify the controller's behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ChunkedUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests the entire upload flow, from initialization to completion, with a small file.
     * This test ensures that the basic upload functionality is working correctly.
     *
     * @throws Exception if any error occurs during the test.
     */
    @Test
    public void testInitAndUploadFlow() throws Exception {
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"testfile.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // extract uploadId
        String uploadId = res.replaceAll(".*\"uploadId\":\\s*\"([^\"]+)\".*", "$1"); // quick extract

        MockMultipartFile chunk0 = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());
        // compute SHA-256 base64
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String chunk0Hash = java.util.Base64.getEncoder().encodeToString(md.digest("0123456789".getBytes()));

        mockMvc.perform(multipart("/api/upload/chunk")
                .file(chunk0)
                .param("uploadId", uploadId)
                .param("chunkNumber", "1")
                .param("totalChunks", "2")
                .param("chunkSize", "10")
                .param("fileSize", "20")
                .param("chunkChecksum", chunk0Hash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextChunk").isNotEmpty());

        MockMultipartFile chunk1 = new MockMultipartFile("file", "chunk1", "application/octet-stream", "abcdefghij".getBytes());
        String chunk1Hash = java.util.Base64.getEncoder().encodeToString(md.digest("abcdefghij".getBytes()));

        mockMvc.perform(multipart("/api/upload/chunk")
                .file(chunk1)
                .param("uploadId", uploadId)
                .param("chunkNumber", "2")
                .param("totalChunks", "2")
                .param("chunkSize", "10")
                .param("fileSize", "20")
                .param("chunkChecksum", chunk1Hash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        // Clean up files after test
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
        try (Stream<Path> stream = Files.list(Paths.get("uploads/complete"))) {
            stream.filter(path -> path.getFileName().toString().startsWith(uploadId + "_")).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    /**
     * Tests the upload of a large file to ensure the system can handle it.
     * This test simulates the upload of a 100MB file, split into 100 chunks of 1MB each.
     *
     * @throws Exception if any error occurs during the test.
     */
    @Test
    public void testBigFileUpload() throws Exception {
        final int totalChunks = 100;
        final int chunkSize = 1024 * 1024; // 1MB
        final long fileSize = (long) totalChunks * chunkSize;
        final String filename = "bigfile.dat";

        String initJson = String.format("{\"totalChunks\":%d, \"chunkSize\":%d, \"fileSize\":%d, \"filename\":\"%s\"}",
                totalChunks, chunkSize, fileSize, filename);

        String res = mockMvc.perform(post("/api/upload/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String uploadId = res.replaceAll(".*\"uploadId\":\\s*\"([^\"]+)\".*", "$1");

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");

        for (int i = 1; i <= totalChunks; i++) {
            byte[] chunkData = new byte[chunkSize];
            new java.util.Random().nextBytes(chunkData);
            String chunkHash = java.util.Base64.getEncoder().encodeToString(md.digest(chunkData));

            MockMultipartFile chunk = new MockMultipartFile("file", "chunk" + i, "application/octet-stream", chunkData);

            var requestBuilder = multipart("/api/upload/chunk")
                    .file(chunk)
                    .param("uploadId", uploadId)
                    .param("chunkNumber", String.valueOf(i))
                    .param("totalChunks", String.valueOf(totalChunks))
                    .param("chunkSize", String.valueOf(chunkSize))
                    .param("fileSize", String.valueOf(fileSize))
                    .param("chunkChecksum", chunkHash);

            if (i == totalChunks) {
                mockMvc.perform(requestBuilder)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("completed"));
            } else {
                mockMvc.perform(requestBuilder)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.nextChunk").isNotEmpty());
            }
        }

        // Clean up files after test
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
        try (Stream<Path> stream = Files.list(Paths.get("uploads/complete"))) {
            stream.filter(path -> path.getFileName().toString().startsWith(uploadId + "_")).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }
}
