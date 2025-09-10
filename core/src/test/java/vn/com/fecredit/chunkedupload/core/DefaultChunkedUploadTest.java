package vn.com.fecredit.chunkedupload.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.port.impl.DefaultITenantAccountPort;
import vn.com.fecredit.chunkedupload.port.impl.DefaultIUploadInfoPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultChunkedUploadTest {

    private DefaultIUploadInfoPort uploadInfoPort;
    private DefaultITenantAccountPort tenantAccountPort;
    private InMemoryChunkedUpload chunkedUpload;
    private Path inProgressDir;
    private Path completeDir;
    private final String TEST_USERNAME = "test-user";
    private final long TEST_TENANT_ID = 1L;

    @BeforeEach
    void setUp() throws IOException {
        uploadInfoPort = new DefaultIUploadInfoPort();
        tenantAccountPort = new DefaultITenantAccountPort();
        inProgressDir = Files.createTempDirectory("inprogress");
        completeDir = Files.createTempDirectory("complete");
        chunkedUpload = new InMemoryChunkedUpload(
                uploadInfoPort,
                tenantAccountPort,
                inProgressDir.toString(),
                completeDir.toString(),
                1024 // 1KB chunk size for tests
        );
        chunkedUpload.setUploadInfoPort(uploadInfoPort); // ensure synchronization

        // Setup a default tenant for tests
        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(TEST_TENANT_ID);
        tenant.setUsername(TEST_USERNAME);
        tenantAccountPort.addTenant(tenant);
    }

    @Test
    void testRegisterUploadingFileAndRetrieve() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/testfile.txt";
        String checksum = "dummychecksum";

        // Register file for upload
        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, filename, 1024L, checksum);

        DefaultUploadInfo info = uploadInfoPort.findByUploadId(uploadId).orElse(null);
        assertNotNull(info);
        assertEquals(uploadId, info.getUploadId());
        assertEquals(filename, info.getFilename());
        assertEquals(checksum, info.getChecksum());
        assertNotNull(info.getUploadDateTime());
    }

    @Test
    void testRegisterUploadingFile_TenantNotFound() {
        String username = "missing";
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/testfile.txt";
        String checksum = "dummychecksum";

        // No tenant added to tenantAccountPort
        Exception ex = assertThrows(IllegalStateException.class, () ->
                chunkedUpload.registerUploadingFile(username, uploadId, filename, 1024L, checksum));
        assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void testWriteChunk_InvalidChunkNumber() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        long fileSize = 2048; // 2 chunks
        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, "test.txt", fileSize, "checksum");

        byte[] data = new byte[1024];
        // Try to write chunk 2, which is out of bounds (0 and 1 are valid)
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                chunkedUpload.writeChunk(TEST_USERNAME, uploadId, 2, data));
        assertTrue(ex.getMessage().contains("Invalid chunk number"));
    }

    @Test
    void testWriteChunk_InvalidChunkSize() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        long fileSize = 2048; // 2 chunks
        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, "test.txt", fileSize, "checksum");

        // Chunk 0 should have size 1024, but we send 512
        byte[] data = new byte[512];
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                chunkedUpload.writeChunk(TEST_USERNAME, uploadId, 0, data));
        assertTrue(ex.getMessage().contains("Invalid chunk size"));
    }

    @Test
    void testWriteChunk_InvalidLastChunkSize() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        long fileSize = 1536; // 1.5 chunks (chunk 0: 1024, chunk 1: 512)
        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, "test.txt", fileSize, "checksum");

        // Last chunk (chunk 1) should have size 512, but we send 256
        byte[] data = new byte[256];
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                chunkedUpload.writeChunk(TEST_USERNAME, uploadId, 1, data));
        assertTrue(ex.getMessage().contains("Invalid last chunk size"));
    }
}
