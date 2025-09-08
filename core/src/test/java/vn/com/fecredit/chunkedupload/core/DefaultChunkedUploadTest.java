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
                1024
        );
        chunkedUpload.setUploadInfoPort(uploadInfoPort); // ensure synchronization
    }

    @Test
    void testRegisterUploadingFileAndRetrieve() throws Throwable {
        String username = "user1";
        String uploadId = UUID.randomUUID().toString();
        String filename = "testfile.txt";
        String checksum = "dummychecksum";

        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(42L);
        tenant.setUsername(username);
        tenantAccountPort.addTenant(tenant);

        // Register file for upload
        // Correct parameter order: username, uploadId, fileName, fileSize, checksum
        System.out.println("[DefaultChunkedUploadTest] Registering file: username=" + username + ", uploadId=" + uploadId + ", filename=" + filename);
        chunkedUpload.registerUploadingFile(username, uploadId, filename, 1024L, checksum);

        // There is no direct public API to get part/final path, but we can check that the upload info is stored
        DefaultUploadInfo info = uploadInfoPort.findByUploadId(uploadId).orElse(null);
        System.out.println("[DefaultChunkedUploadTest] Retrieved info for uploadId=" + uploadId + ": " + (info != null ? "found" : "not found"));
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
        String filename = "testfile.txt";
        String checksum = "dummychecksum";
        long tenantId = 999L;

        // No tenant added to tenantAccountPort
        Exception ex = assertThrows(IllegalStateException.class, () ->
                chunkedUpload.registerUploadingFile(username, uploadId, filename, 1024L, checksum));
        assertTrue(ex.getMessage().contains("Tenant not found"));
    }
}