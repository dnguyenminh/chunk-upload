package vn.com.fecredit.chunkedupload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ChunkedUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testInitAndUploadFlow() throws Exception {
        String initJson = "{\"totalChunks\":2, \"chunkSize\":10, \"fileSize\":20, \"filename\":\"testfile.txt\"}";
        String res = mockMvc.perform(post("/api/upload/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // extract uploadId
        String uploadId = res.replaceAll(".*\\\"uploadId\\\":\\s*\\\"([^\\\"]+)\\\".*", "$1"); // quick extract

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
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("uploads/in-progress/" + uploadId + ".part"));
        java.nio.file.Files.list(java.nio.file.Paths.get("uploads/complete")).forEach(path -> {
            if (path.getFileName().toString().startsWith(uploadId + "_")) {
                try { java.nio.file.Files.deleteIfExists(path); } catch (Exception ignored) {}
            }
        });
    }
}