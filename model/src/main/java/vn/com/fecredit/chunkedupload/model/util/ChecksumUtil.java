package vn.com.fecredit.chunkedupload.model.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class ChecksumUtil {
    /**
     * Generates a SHA-256 checksum from the given byte array.
     * @param data the byte array to checksum
     * @return the checksum as a hex string
     */
    public static String generateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a SHA-256 checksum from the file at the given path.
     * @param filePath the path to the file
     * @return the checksum as a hex string
     */
    public static String generateChecksum(Path filePath) {
        try {
            byte[] data = Files.readAllBytes(filePath);
            return generateChecksum(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file for checksum: " + filePath, e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
