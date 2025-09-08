package vn.com.fecredit.chunkedupload.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UserDatabaseIntegrationTest {

    @Autowired
    private javax.sql.DataSource dataSource;

    private void logTablesAndUsers() throws Exception {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            java.sql.ResultSet rs = conn.createStatement().executeQuery("SHOW TABLES");
            System.out.println("=== H2 TABLES ===");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
            rs.close();
            rs = conn.createStatement().executeQuery("SELECT username FROM tenants");
            System.out.println("=== TENANTS TABLE USERS ===");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
            rs.close();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private vn.com.fecredit.chunkedupload.model.TenantAccountRepository tenantAccountRepository;

    @Autowired
    private vn.com.fecredit.chunkedupload.model.UploadInfoRepository uploadInfoRepository;

    @Autowired
    private vn.com.fecredit.chunkedupload.model.UploadInfoHistoryRepository uploadInfoHistoryRepository;

    @BeforeEach
    public void setupUser() {
        // Delete in order to respect foreign key constraints
        uploadInfoHistoryRepository.deleteAll();
        uploadInfoRepository.deleteAll();
        tenantAccountRepository.deleteAll();

        vn.com.fecredit.chunkedupload.model.TenantAccount user = new vn.com.fecredit.chunkedupload.model.TenantAccount();
        user.setTenantId("testTenant");
        user.setUsername("user");
        user.setPassword("{bcrypt}$2a$10$Lu4NwC5fbHT7kXV0o0PdDuX2NGsz0U/4ipCCa3GezK5hHSOguhtaG");
        tenantAccountRepository.save(user);
    }

    /**
     * Integration test: verify chunked upload controller works with real database.
     */
    @Test
    public void testControllerIntegrationWithDatabase() throws Exception {
        logTablesAndUsers();
        String initJson = "{\"totalChunks\":1, \"chunkSize\":10, \"fileSize\":10, \"filename\":\"integration.txt\", \"checksum\":\"testchecksum\"}";
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
        logTablesAndUsers();
        mockMvc.perform(get("/api/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").isNotEmpty());
    }
}
