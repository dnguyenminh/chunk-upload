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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link ChunkedUploadController}.
 * <p>
 * This test class uses Spring's MockMvc to simulate HTTP requests and verify the behavior of the chunked upload REST API.
 * It covers normal flows, edge cases, error handling, and security scenarios for chunked file uploads.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ChunkedUploadControllerTest {
    /**
     * MockMvc is used to perform HTTP requests and assert responses in the test environment.
     */

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
    /**
     * Tests the entire upload flow, from initialization to completion, with a small file.
     * Ensures basic upload functionality is working correctly.
     * @throws Exception if any error occurs during the test.
     */
    String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"testfile.txt\"}";
    String res = mockMvc.perform(post("/api/upload/init")
        .with(httpBasic("user", "password"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(initJson))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    // extract sessionId (uploadId) using Jackson
    com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
    String uploadId = jsonNode.get("sessionId").asText();

        MockMultipartFile chunk0 = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());

        mockMvc.perform(multipart("/api/upload/chunk")
                .file(chunk0)
                .with(httpBasic("user", "password"))
                .param("uploadId", uploadId)
                .param("chunkNumber", "1")
                .param("totalChunks", "2")
                .param("chunkSize", "10")
                .param("fileSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextChunk").isNotEmpty());

        MockMultipartFile chunk1 = new MockMultipartFile("file", "chunk1", "application/octet-stream", "abcdefghij".getBytes());

        mockMvc.perform(multipart("/api/upload/chunk")
                .file(chunk1)
                .with(httpBasic("user", "password"))
                .param("uploadId", uploadId)
                .param("chunkNumber", "2")
                .param("totalChunks", "2")
                .param("chunkSize", "10")
                .param("fileSize", "20"))
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
    /**
     * Tests the upload of a large file to ensure the system can handle it.
     * Simulates the upload of a 100MB file, split into 100 chunks of 1MB each.
     * @throws Exception if any error occurs during the test.
     */
        final int totalChunks = 100;
        final int chunkSize = 1024 * 1024; // 1MB
        final long fileSize = (long) totalChunks * chunkSize;
        final String filename = "bigfile.dat";

        String initJson = String.format("{\"totalChunks\":%d, \"chunkSize\":%d, \"fileSize\":%d, \"filename\":\"%s\"}",
                totalChunks, chunkSize, fileSize, filename);

    String res = mockMvc.perform(post("/api/upload/init")
        .with(httpBasic("user", "password"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(initJson))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
    String uploadId = jsonNode.get("sessionId").asText();

        for (int i = 1; i <= totalChunks; i++) {
            byte[] chunkData = new byte[chunkSize];
            new java.util.Random().nextBytes(chunkData);

            MockMultipartFile chunk = new MockMultipartFile("file", "chunk" + i, "application/octet-stream", chunkData);

            var requestBuilder = multipart("/api/upload/chunk")
                    .file(chunk)
                    .with(httpBasic("user", "password"))
                    .param("uploadId", uploadId)
                    .param("chunkNumber", String.valueOf(i))
                    .param("totalChunks", String.valueOf(totalChunks))
                    .param("chunkSize", String.valueOf(chunkSize))
                    .param("fileSize", String.valueOf(fileSize));

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

    // --- Additional Test Cases ---

    @Test
    public void testResumeUploadWithValidIdAndFileSize() throws Exception {
    /**
     * Tests resuming an upload with a valid uploadId and matching fileSize.
     * Ensures resume logic returns correct session info.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"resume.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        String resumeJson = String.format("{\"uploadId\":\"%s\", \"fileSize\":20}", uploadId);
        mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(resumeJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(uploadId));
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testResumeUploadMissingFileSize() throws Exception {
    /**
     * Tests resume logic when fileSize is missing in the request.
     * Expects a 400 Bad Request error.
     * @throws Exception if any error occurs during the test.
     */
        String resumeJson = "{\"uploadId\":\"dummy-id\"}";
        mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(resumeJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void testInitMissingRequiredFields() throws Exception {
    /**
     * Tests initialization with missing required fields.
     * Expects a 400 Bad Request error.
     * @throws Exception if any error occurs during the test.
     */
        String badJson = "{\"chunkSize\":10, \"fileSize\":20}";
        mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(badJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void testChunkUploadInvalidUploadId() throws Exception {
    /**
     * Tests chunk upload with an invalid uploadId.
     * Expects a 5xx Server Error.
     * @throws Exception if any error occurs during the test.
     */
        MockMultipartFile chunk = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());
        mockMvc.perform(multipart("/api/upload/chunk")
            .file(chunk)
            .with(httpBasic("user", "password"))
            .param("uploadId", "invalid-id")
            .param("chunkNumber", "1")
            .param("totalChunks", "2")
            .param("chunkSize", "10")
            .param("fileSize", "20"))
            .andExpect(status().is5xxServerError());
    }

    @Test
    public void testChunkUploadInvalidChunkNumber() throws Exception {
    /**
     * Tests chunk upload with an invalid chunk number.
     * Expects a 5xx Server Error.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"test.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        MockMultipartFile chunk = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());
        mockMvc.perform(multipart("/api/upload/chunk")
            .file(chunk)
            .with(httpBasic("user", "password"))
            .param("uploadId", uploadId)
            .param("chunkNumber", "99")
            .param("totalChunks", "2")
            .param("chunkSize", "10")
            .param("fileSize", "20"))
            .andExpect(status().is5xxServerError());
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testChunkUploadInvalidChunkSize() throws Exception {
    /**
     * Tests chunk upload with an invalid chunk size.
     * Expects a 5xx Server Error.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"test.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        MockMultipartFile chunk = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());
        mockMvc.perform(multipart("/api/upload/chunk")
            .file(chunk)
            .with(httpBasic("user", "password"))
            .param("uploadId", uploadId)
            .param("chunkNumber", "1")
            .param("totalChunks", "2")
            .param("chunkSize", "99999")
            .param("fileSize", "20"))
            .andExpect(status().is5xxServerError());
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testChunkUploadMissingFile() throws Exception {
    /**
     * Tests chunk upload when the file part is missing.
     * Expects a 400 Bad Request error.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"test.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        mockMvc.perform(multipart("/api/upload/chunk")
            .with(httpBasic("user", "password"))
            .param("uploadId", uploadId)
            .param("chunkNumber", "1")
            .param("totalChunks", "2")
            .param("chunkSize", "10")
            .param("fileSize", "20"))
            .andExpect(status().isBadRequest());
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testStatusValidUploadId() throws Exception {
    /**
     * Tests status endpoint with a valid uploadId.
     * Expects a successful response.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"status.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        mockMvc.perform(get("/api/upload/" + uploadId + "/status")
            .with(httpBasic("user", "password")))
            .andExpect(status().isOk());
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testStatusInvalidUploadId() throws Exception {
    /**
     * Tests status endpoint with an invalid uploadId.
     * Expects a successful response (empty status).
     * @throws Exception if any error occurs during the test.
     */
        mockMvc.perform(get("/api/upload/invalid-id/status")
            .with(httpBasic("user", "password")))
            .andExpect(status().isOk());
    }

    @Test
    public void testAbortValidUploadId() throws Exception {
    /**
     * Tests aborting an upload with a valid uploadId.
     * Expects a 204 No Content response.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"abort.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        mockMvc.perform(delete("/api/upload/" + uploadId)
            .with(httpBasic("user", "password")))
            .andExpect(status().isNoContent());
    }

    @Test
    public void testAbortInvalidUploadId() throws Exception {
    /**
     * Tests aborting an upload with an invalid uploadId.
     * Expects a 204 No Content response.
     * @throws Exception if any error occurs during the test.
     */
        mockMvc.perform(delete("/api/upload/invalid-id")
            .with(httpBasic("user", "password")))
            .andExpect(status().isNoContent());
    }

    @Test
    public void testUnauthorizedAccess() throws Exception {
    /**
     * Tests unauthorized access to the upload API.
     * Expects a 401 Unauthorized response.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"unauth.txt\"}";
        mockMvc.perform(post("/api/upload/init")
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void testSecurityExceptionHandler() throws Exception {
    /**
     * Placeholder for testing SecurityException handler.
     * Typically tested via integration or controller advice.
     * @throws Exception if any error occurs during the test.
     */
        // Simulate SecurityException by throwing it from controller (not trivial with MockMvc)
        // This is usually tested via integration or controller advice
    }

    @Test
    public void testIllegalArgumentExceptionHandler() throws Exception {
    /**
     * Tests IllegalArgumentException handler by sending invalid init data.
     * Expects a 400 Bad Request response.
     * @throws Exception if any error occurs during the test.
     */
        String badJson = "{\"chunkSize\":-1, \"fileSize\":20, \"totalChunks\":2, \"filename\":\"bad.txt\"}";
        mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(badJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void testResumeUploadWrongFileSize() throws Exception {
    /**
     * Tests resume logic with a mismatched fileSize.
     * Expects a 400 Bad Request error.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"resume2.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        String resumeJson = String.format("{\"uploadId\":\"%s\", \"fileSize\":999}", uploadId);
        mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(resumeJson))
            .andExpect(status().isBadRequest());
        Files.deleteIfExists(Paths.get("uploads/in-progress/" + uploadId + ".part"));
    }

    @Test
    public void testResumeUploadChecksumMismatch() throws Exception {
    /**
     * Placeholder for checksum mismatch test.
     * Only relevant if checksum logic is implemented in the controller/model.
     * @throws Exception if any error occurs during the test.
     */
        // Only relevant if checksum is enforced in controller/model
        // Skipped unless checksum logic is implemented
    }

    @Test
    public void testChunkUploadAfterAbort() throws Exception {
    /**
     * Tests chunk upload after aborting the upload session.
     * Expects a 5xx Server Error.
     * @throws Exception if any error occurs during the test.
     */
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"abort2.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
            .with(httpBasic("user", "password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(initJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res);
        String uploadId = jsonNode.get("sessionId").asText();
        mockMvc.perform(delete("/api/upload/" + uploadId)
            .with(httpBasic("user", "password")))
            .andExpect(status().isNoContent());
        MockMultipartFile chunk = new MockMultipartFile("file", "chunk0", "application/octet-stream", "0123456789".getBytes());
        mockMvc.perform(multipart("/api/upload/chunk")
            .file(chunk)
            .with(httpBasic("user", "password"))
            .param("uploadId", uploadId)
            .param("chunkNumber", "1")
            .param("totalChunks", "2")
            .param("chunkSize", "10")
            .param("fileSize", "20"))
            .andExpect(status().is5xxServerError());
    }
}
