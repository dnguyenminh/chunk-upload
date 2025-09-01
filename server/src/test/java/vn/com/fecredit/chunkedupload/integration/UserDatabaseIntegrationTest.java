package vn.com.fecredit.chunkedupload.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "/test-users.sql") // Optional: preload test users if needed
public class UserDatabaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Integration test: verify chunked upload controller works with real database.
     */
    @Test
    public void testControllerIntegrationWithDatabase() throws Exception {
        String initJson = "{\"totalChunks\":1, \"chunkSize\":10, \"fileSize\":10, \"filename\":\"integration.txt\"}";
        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/upload/init")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("user", "password"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(initJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        // Optionally, verify DB state or follow up with chunk upload
    }

    /**
     * Integration test: verify users in database can be loaded successfully.
     */
    @Test
    public void testLoadUsersFromDatabase() throws Exception {
        mockMvc.perform(get("/api/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").isNotEmpty());
    }
}