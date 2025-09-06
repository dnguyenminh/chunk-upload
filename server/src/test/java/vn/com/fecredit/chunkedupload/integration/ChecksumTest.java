package vn.com.fecredit.chunkedupload.integration;

import org.junit.jupiter.api.Test;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChecksumTest {

    @Test
    public void testChecksumConsistency() throws IOException {
        // Create test file with 'R' pattern
        Path testFile = Files.createTempFile("checksum-test", ".bin");
        try (var output = Files.newOutputStream(testFile)) {
            for (int i = 0; i < 100; i++) {
                output.write((byte) ('R' + i % 26));
            }
        }

        // Calculate checksum
        String checksum = ChecksumUtil.generateChecksum(testFile);
        System.out.println("Generated checksum: " + checksum);

        // Verify file exists and checksum is not null
        assertEquals(100, Files.size(testFile));
        assertEquals("6ceb10914d3b6bf8b525c143e125e2aa805c25e15652cb3acba1a5c213c22f03", checksum);

        // Clean up
        Files.delete(testFile);
    }
}
