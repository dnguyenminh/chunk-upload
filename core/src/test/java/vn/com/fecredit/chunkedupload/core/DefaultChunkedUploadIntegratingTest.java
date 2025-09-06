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
    private DefaultChunkedUpload chunkedUpload;
    private Path inProgressDir;
    private Path completeDir;
    private final int CHUNK_SIZE = 1024; // 1 KB for testing

    @BeforeEach
    void setUp() throws IOException {
        uploadInfoPort = new DefaultIUploadInfoPort();
        tenantAccountPort = new DefaultITenantAccountPort();

        inProgressDir = Files.createTempDirectory("inprogress-e2e");
        completeDir = Files.createTempDirectory("complete-e2e");

        chunkedUpload = new DefaultChunkedUpload(
                uploadInfoPort,
                tenantAccountPort,
                inProgressDir.toString(),
                completeDir.toString(),
                CHUNK_SIZE
        );
    }

    @Test
    void testRegisterAndRetrieveUploadInfo() throws Throwable {
        String username = "integrationUser";
        String uploadId = UUID.randomUUID().toString();
        String filename = "integration.txt";
        String checksum = "integrationchecksum";

        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(123L);
        tenant.setUsername(username);
        tenantAccountPort.addTenant(tenant);

        chunkedUpload.registerUploadingFile(username, uploadId, filename, 1024L, checksum);

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
        String filename = "integration.txt";
        String checksum = "integrationchecksum";

        Exception ex = assertThrows(IllegalStateException.class, () ->
                chunkedUpload.registerUploadingFile(username, uploadId, filename, 1024L, checksum));
        assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void testConcurrentChunkUploads() throws Throwable {
        // 1. Setup
        String username = "concurrentUser";
        String uploadId = UUID.randomUUID().toString();
        String filename = "concurrent-test-file.bin";


        DeafultTenantAccount tenant = new DeafultTenantAccount();
        tenant.setId(456L);
        tenant.setUsername(username);
        tenantAccountPort.addTenant(tenant);

        // 2. Create a test file
        Path sourceFile = Files.createTempFile("test-file-", ".bin");

        int fileSize = CHUNK_SIZE * 100; // 100 KB file
        byte[] fileData = new byte[fileSize];
        for (int i = 0; i < fileSize; i++) {
            fileData[i] = (byte) (i % 256);
        }
        Files.write(sourceFile, fileData);
        String expectedChecksum = ChecksumUtil.generateChecksum(sourceFile);

        // 3. Register the file for upload
        chunkedUpload.registerUploadingFile(username, uploadId, filename, fileSize, expectedChecksum);

        // 4. Upload chunks concurrently
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
                    logger.info("Uploading chunk " + chunkNumber + " for uploadId=" + uploadId);
                    chunkedUpload.writeChunk(username, uploadId, chunkNumber, chunkData);
                    logger.info("Finished uploading chunk " + chunkNumber + " for uploadId=" + uploadId);
                } catch (Throwable e) {
                    logger.severe("Concurrent chunk upload failed for chunk " + chunkNumber + ": " + e);
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    logger.severe(sw.toString());
                    fail("Concurrent chunk upload failed for chunk " + chunkNumber + ": " + e, e);
                }
            });
        }

        // 5. Wait for completion and verify
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Upload did not complete in time");

        Path finalPath = completeDir.resolve(String.valueOf(tenant.getId())).resolve(filename);
        logger.info("Checking existence of assembled file at: " + finalPath);
        assertTrue(Files.exists(finalPath), "Assembled file does not exist.");
        logger.info("Assembled file exists: " + Files.exists(finalPath));
        assertEquals(fileSize, Files.size(finalPath), "Assembled file size does not match original.");

        String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
        assertEquals(expectedChecksum, actualChecksum, "Checksum of assembled file does not match original.");

        // Clean up
        Files.delete(sourceFile);
    }

    @Test
    void testConcurrentUploadsOfManyFiles() throws Throwable {
        int fileCount = 5;
        int chunksPerFile = 20;
        int fileSize = CHUNK_SIZE * chunksPerFile;
        ExecutorService executor = Executors.newFixedThreadPool(fileCount * 2);

        List<String> uploadIds = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        List<String> usernames = new ArrayList<>();
        List<Long> tenantIds = new ArrayList<>();
        List<Path> sourceFiles = new ArrayList<>();
        List<String> checksums = new ArrayList<>();

        // Prepare tenants, files, and register uploads
        for (int i = 0; i < fileCount; i++) {
            String username = "multiUser" + i;
            String uploadId = UUID.randomUUID().toString();
            String filename = "multi-file-" + i + ".bin";
            DeafultTenantAccount tenant = new DeafultTenantAccount();
            tenant.setId(1000L + i);
            tenant.setUsername(username);
            tenantAccountPort.addTenant(tenant);

            Path sourceFile = Files.createTempFile("multi-test-file-" + i + "-", ".bin");
            byte[] fileData = new byte[fileSize];
            for (int j = 0; j < fileSize; j++) {
                fileData[j] = (byte) ((i + j) % 256);
            }
            Files.write(sourceFile, fileData);
            String checksum = ChecksumUtil.generateChecksum(sourceFile);

            chunkedUpload.registerUploadingFile(username, uploadId, filename, fileSize, checksum);

            uploadIds.add(uploadId);
            filenames.add(filename);
            usernames.add(username);
            tenantIds.add(tenant.getId());
            sourceFiles.add(sourceFile);
            checksums.add(checksum);
        }

        // Upload all files' chunks concurrently
        for (int i = 0; i < fileCount; i++) {
            String username = usernames.get(i);
            String uploadId = uploadIds.get(i);
            String filename = filenames.get(i);
            long tenantId = tenantIds.get(i);
            Path sourceFile = sourceFiles.get(i);

            List<byte[]> chunks = new ArrayList<>();
            try (InputStream inputStream = Files.newInputStream(sourceFile)) {
                for (int j = 0; j < chunksPerFile; j++) {
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

            for (int j = 0; j < chunksPerFile; j++) {
                final int chunkNumber = j;
                final byte[] chunkData = chunks.get(j);
                final int fileIdx = i;
                executor.submit(() -> {
                    try {
                        logger.info("Uploading chunk " + chunkNumber + " for uploadId=" + uploadId + " (fileIdx=" + fileIdx + ")");
                        chunkedUpload.writeChunk(username, uploadId, chunkNumber, chunkData);
                        logger.info("Finished uploading chunk " + chunkNumber + " for uploadId=" + uploadId + " (fileIdx=" + fileIdx + ")");
                    } catch (Throwable e) {
                        logger.severe("Concurrent chunk upload failed for chunk " + chunkNumber + " of fileIdx=" + fileIdx + ": " + e);
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        logger.severe(sw.toString());
                        fail("Concurrent chunk upload failed for chunk " + chunkNumber + " of fileIdx=" + fileIdx + ": " + e, e);
                    }
                });
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS), "Uploads did not complete in time");

        // Verify all files
        for (int i = 0; i < fileCount; i++) {
            Path finalPath = completeDir.resolve(String.valueOf(tenantIds.get(i))).resolve(filenames.get(i));
            logger.info("Checking existence of assembled file at: " + finalPath);
            assertTrue(Files.exists(finalPath), "Assembled file does not exist for fileIdx=" + i);
            assertEquals(fileSize, Files.size(finalPath), "Assembled file size does not match original for fileIdx=" + i);
            String actualChecksum = ChecksumUtil.generateChecksum(finalPath);
            assertEquals(checksums.get(i), actualChecksum, "Checksum of assembled file does not match original for fileIdx=" + i);
            Files.delete(sourceFiles.get(i));
        }
    }
}