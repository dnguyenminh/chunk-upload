package vn.com.fecredit.chunkedupload.model.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Utility class for generating and validating file checksums.
 *
 * <p>
 * This class provides methods for:
 * <ul>
 * <li>Generating SHA-256 checksums of files</li>
 * <li>Computing checksums in a streaming fashion for large files</li>
 * <li>Using efficient buffer sizes for performance</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * <pre>
 * Path file = Paths.get("myfile.txt");
 * String checksum = ChecksumUtil.generateChecksum(file);
 * </pre>
 */
public final class ChecksumUtil {

    /** Size of buffer used for reading file data (8KB) */
    private static final int BUFFER_SIZE = 8192;

    private ChecksumUtil() {
        // Utility class, no instances allowed
    }

    /**
     * Generates a SHA-256 checksum for a file.
     *
     * <p>
     * The file is read in chunks to efficiently handle large files.
     * The resulting checksum is returned as a lowercase hexadecimal string.
     *
     * @param filePath Path to the file to checksum
     * @return SHA-256 checksum as a hex string
     * @throws IOException If the file cannot be read or if SHA-256 algorithm is unavailable
     */
    public static String generateChecksum(Path filePath) throws IOException {
        try (var fis = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to compute SHA-256 checksum", e);
        }
    }
}