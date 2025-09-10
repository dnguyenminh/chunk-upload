package vn.com.fecredit.chunkedupload.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;
import vn.com.fecredit.chunkedupload.port.impl.DefaultITenantAccountPort;
import vn.com.fecredit.chunkedupload.port.impl.DefaultIUploadInfoPort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.io.PrintWriter;

class DefaultChunkedUploadIntegratingTest {

    private static final Logger logger = Logger.getLogger(DefaultChunkedUploadIntegratingTest.class.getName());

    private DefaultIUploadInfoPort uploadInfoPort;
    private DefaultITenantAccountPort tenantAccountPort;
    private InMemoryChunkedUpload chunkedUpload;
    private Path inProgressDir;
    private Path completeDir;
    private final int CHUNK_SIZE = 1024; // 1 KB for testing
    private final String TEST_USERNAME = "test-user";
    private final long TEST_TENANT_ID = 1L;

    @BeforeEach
    void setUp() throws IOException {
        uploadInfoPort = new DefaultIUploadInfoPort();
        tenantAccountPort = new DefaultITenantAccountPort();

        inProgressDir = Files.createTempDirectory("inprogress-e2e");
        completeDir = Files.createTempDirectory("complete-e2e");

        chunkedUpload = new InMemoryChunkedUpload(
                uploadInfoPort,
                tenantAccountPort,
                inProgressDir.toString(),
                completeDir.toString(),
                CHUNK_SIZE
        );
        chunkedUpload.setUploadInfoPort(uploadInfoPort);

        // Setup a default tenant for tests
        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(TEST_TENANT_ID);
        tenant.setUsername(TEST_USERNAME);
        tenantAccountPort.addTenant(tenant);
    }

    @Test
    void testRegisterAndRetrieveUploadInfo() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/integration.txt";
        String checksum = "integrationchecksum";

        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, filename, 1024L, checksum);

        DefaultUploadInfo info = uploadInfoPort.findByUploadId(uploadId).orElse(null);
        assertNotNull(info, "UploadInfo should not be null after registration");
        assertEquals(uploadId, info.getUploadId());
        assertEquals(filename, info.getFilename());
        assertEquals(checksum, info.getChecksum());
        assertNotNull(info.getUploadDateTime());
    }

    @Test
    void testRegisterUploadingFile_TenantNotFound() {
        String username = "missingIntegration";
        String uploadId = UUID.randomUUID().toString();

        Exception ex = assertThrows(IllegalStateException.class, () ->
                chunkedUpload.registerUploadingFile(username, uploadId, "file.txt", 1024L, "checksum"));
        assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void testFullUpload_SingleChunk() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/single-chunk-file.txt";
        Path sourceFile = Files.createTempFile("single-chunk-", ".bin");
        byte[] fileData = new byte[500]; // Smaller than CHUNK_SIZE
        Files.write(sourceFile, fileData);
        String expectedChecksum = ChecksumUtil.generateChecksum(sourceFile);

        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, filename, fileData.length, expectedChecksum);
        chunkedUpload.writeChunk(TEST_USERNAME, uploadId, 0, fileData);

        Path finalPath = completeDir.resolve(String.valueOf(TEST_TENANT_ID)).resolve(uploadId + "_" + filename);
        assertTrue(Files.exists(finalPath), "Assembled file should exist for single chunk upload.");
        assertEquals(fileData.length, Files.size(finalPath), "Assembled file size should match original.");
        assertEquals(expectedChecksum, ChecksumUtil.generateChecksum(finalPath), "Checksum of assembled file should match.");

        // Verify cleanup
        assertTrue(uploadInfoPort.findByUploadId(uploadId).isEmpty(), "UploadInfo should be deleted after successful assembly.");
    }

    @Test
    void testDeleteUploadFile() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/to-be-deleted.txt";
        long fileSize = 2048; // 2 chunks

        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, filename, fileSize, "checksum");

        // Verify part file and DB record exist
        Path partPath = inProgressDir.resolve(String.valueOf(TEST_TENANT_ID)).resolve(uploadId + ".part");
        assertTrue(Files.exists(partPath), "Part file should be created upon registration.");
        assertTrue(uploadInfoPort.findByUploadId(uploadId).isPresent(), "UploadInfo should exist in DB after registration.");

        // Delete the upload file before removing UploadInfo
        chunkedUpload.deleteUploadFile(TEST_USERNAME, uploadId);

        // Now remove UploadInfo if needed (simulate cleanup order)
        // uploadInfoPort.removeByUploadId(uploadId); // Only if explicit removal is needed elsewhere
        // Verify cleanup
        assertFalse(Files.exists(partPath), "Part file should be deleted.");
        assertTrue(uploadInfoPort.findByUploadId(uploadId).isEmpty(), "UploadInfo should be deleted from DB.");
    }

    @Test
    void testFullUpload_ChecksumMismatch() throws Throwable {
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/checksum-mismatch.txt";
        Path sourceFile = Files.createTempFile("checksum-mismatch-", ".bin");
        byte[] fileData = "This is the real data".getBytes();
        Files.write(sourceFile, fileData);

        // Register with a deliberately incorrect checksum
        chunkedUpload.registerUploadingFile(TEST_USERNAME, uploadId, filename, fileData.length, "invalid-checksum");

        // Upload the file
        Exception ex = assertThrows(RuntimeException.class, () ->
                chunkedUpload.writeChunk(TEST_USERNAME, uploadId, 0, fileData));

        assertTrue(ex.getCause().getMessage().contains("Checksum mismatch"), "Exception should indicate a checksum mismatch.");

        // Verify that the final file was NOT created and the part file still exists
        Path finalPath = completeDir.resolve(String.valueOf(TEST_TENANT_ID)).resolve(filename);
        assertFalse(Files.exists(finalPath), "Final file should not be created on checksum mismatch.");

        Path partPath = inProgressDir.resolve(String.valueOf(TEST_TENANT_ID)).resolve(uploadId + ".part");
        assertTrue(Files.exists(partPath), "Part file should not be deleted on checksum mismatch.");
    }

    @Test
    void testConcurrentChunkUploads() throws Throwable {
        // This test remains the same as it's a good validation of the file lock
        String username = "concurrentUser";
        String uploadId = UUID.randomUUID().toString();
        String filename = "temp/concurrent-test-file.bin";

        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(456L);
        tenant.setUsername(username);
        tenantAccountPort.addTenant(tenant);

        Path sourceFile = Files.createTempFile("test-file-", ".bin");
        int fileSize = CHUNK_SIZE * 100; // 100 KB file
        byte[] fileData = new byte[fileSize];
        for (int i = 0; i < fileSize; i++) {
            fileData[i] = (byte) (i % 256);
        }
        Files.write(sourceFile, fileData);
        String expectedChecksum = ChecksumUtil.generateChecksum(sourceFile);

        chunkedUpload.registerUploadingFile(username, uploadId, filename, fileSize, expectedChecksum);

        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        List<byte[]> chunks = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(sourceFile)) {
            for (int i = 0; i < totalChunks; i++) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead = inputStream.read(buffer);
                if (bytesRead < CHUNK_SIZE) {
                    byte[] smallerBuffer = new byte[bytesRead];
                    System.arraycopy(buffer, 0, smallerBuffer, 0, bytesRead);
                    chunks.add(smallerBuffer);
                } else {
                    chunks.add(buffer);
                }
            }
        }

        for (int i = 0; i < totalChunks; i++) {
            final int chunkNumber = i;
            final byte[] chunkData = chunks.get(i);
            executor.submit(() -> {
                try {
                    chunkedUpload.writeChunk(username, uploadId, chunkNumber, chunkData);
                } catch (Throwable e) {
                    fail("Concurrent chunk upload failed for chunk " + chunkNumber, e);
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Upload did not complete in time");

        Path finalPath = completeDir.resolve(String.valueOf(tenant.getId())).resolve(uploadId + "_" + filename);
        assertTrue(Files.exists(finalPath), "Assembled file does not exist.");
        assertEquals(fileSize, Files.size(finalPath), "Assembled file size does not match original.");

        String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
        assertEquals(expectedChecksum, actualChecksum, "Checksum of assembled file does not match original.");

        Files.delete(sourceFile);
    }
}
