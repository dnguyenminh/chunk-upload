package vn.com.fecredit.chunkedupload.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ClientApp {

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("--help"))) {
            printHelp();
            return;
        }

        Map<String, String> params = parseArgs(args);

        if (!params.containsKey("filePath") || !params.containsKey("uploadUrl") || !params.containsKey("username") || !params.containsKey("password")) {
            System.err.println("Error: Missing required arguments: filePath, uploadUrl, username, password");
            printHelp();
            System.exit(1);
        }

        try {
            Path filePath = Paths.get(params.get("filePath"));
            String uploadUrl = params.get("uploadUrl");
            String username = params.get("username");
            String password = params.get("password");
            int retryTimes = Integer.parseInt(params.getOrDefault("retryTimes", "3"));
            int threadCounts = Integer.parseInt(params.getOrDefault("threadCounts", "4"));

            ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                    .uploadUrl(uploadUrl)
                    .username(username)
                    .password(password)
                    .retryTimes(retryTimes)
                    .threadCounts(threadCounts)
                    .build();

            System.out.println("Starting upload for file: " + filePath);
            String uploadId = client.upload(filePath, retryTimes, threadCounts);
            System.out.println("Upload completed successfully. Upload ID: " + uploadId);

        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            // For a CLI, printing the high-level error is more user-friendly than a stack trace.
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    params.put(parts[0], parts[1]);
                }
            }
        }
        return params;
    }

    private static void printHelp() {
        System.out.println("Usage: ./run-client.bat [options]");
        System.out.println("Options:");
        System.out.println("  --filePath=<path>          : Required. Path to the file to upload.");
        System.out.println("  --uploadUrl=<url>          : Required. The server's base upload URL (e.g., http://localhost:8080/api/upload).");
        System.out.println("  --username=<user>          : Required. Username for authentication.");
        System.out.println("  --password=<pass>          : Required. Password for authentication.");
        System.out.println("  --retryTimes=<num>         : Optional. Number of retries for failed chunks (default: 3).");
        System.out.println("  --threadCounts=<num>       : Optional. Number of parallel upload threads (default: 4).");
        System.out.println("  --help                     : Print this help message.");
    }
}
