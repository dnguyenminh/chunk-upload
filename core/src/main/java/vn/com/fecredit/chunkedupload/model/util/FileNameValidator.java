package vn.com.fecredit.chunkedupload.model.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public class FileNameValidator {

    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }

        try {
            // Try to create a path, will throw exception if invalid
            Paths.get(fileName);
        } catch (InvalidPathException e) {
            return false;
        }

        // Check for reserved characters in Windows
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String invalidChars = "<>:\"/\\|?*";
            for (char c : invalidChars.toCharArray()) {
                if (fileName.indexOf(c) >= 0) {
                    return false;
                }
            }

            // Windows also has reserved file names
            String[] reservedNames = {
                    "CON", "PRN", "AUX", "NUL",
                    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
            };
            for (String reserved : reservedNames) {
                if (fileName.equalsIgnoreCase(reserved) || fileName.matches("(?i)" + reserved + "\\..*")) {
                    return false;
                }
            }
        } else {
            // Unix/Linux: only '/' and '\0' are invalid
            if (fileName.contains("/") || fileName.contains("\0")) {
                return false;
            }
        }

        return true;
    }
}
