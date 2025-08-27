package vn.com.fecredit.chunkedupload.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;

/**
 * A client for uploading files in chunks to a server.
 */
public class ChunkedUploadClient {

    private final String uploadUrl;
    private final byte[] fileContent;
    private final String fileName;
    private final String username;
    private final String password;
    private final int retryTimes;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new ChunkedUploadClient.
     *
     * @param uploadUrl   The URL to upload the file to.
     * @param fileContent The content of the file to upload.
     * @param fileName    The name of the file.
     * @param username    The username for authentication.
     * @param password    The password for authentication.
     * @param retryTimes  The number of times to retry a failed upload.
     */
    public ChunkedUploadClient(String uploadUrl, byte[] fileContent, String fileName, String username, String password, int retryTimes) {
        this.uploadUrl = uploadUrl;
        this.fileContent = fileContent;
        this.fileName = fileName;
        this.username = username;
        this.password = password;
        this.retryTimes = retryTimes;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Uploads the file to the server.
     *
     * @throws RuntimeException if the upload fails.
     */
    public void upload() {
        int attempts = 0;
        while (attempts < retryTimes) {
            try {
                InitResponse initResponse = initUpload();
                uploadChunks(initResponse.getUploadId());
                return;
            } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                attempts++;
                if (attempts >= retryTimes) {
                    throw new RuntimeException("Failed to upload file after " + retryTimes + " attempts", e);
                }
            }
        }
    }

    private InitResponse initUpload() throws IOException, InterruptedException {
        InitRequest initRequest = new InitRequest();
        initRequest.setFileName(fileName);
        initRequest.setFileSize(fileContent.length);
        initRequest.setPassword(password);

        String requestBody = objectMapper.writeValueAsString(initRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl + "/init"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to initialize upload: " + response.body());
        }

        return objectMapper.readValue(response.body(), InitResponse.class);
    }

    private void uploadChunks(String uploadId) throws IOException, InterruptedException, NoSuchAlgorithmException {
        int chunkSize = 1024 * 1024; // 1MB
        int totalChunks = (int) Math.ceil((double) fileContent.length / chunkSize);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, fileContent.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileContent, start, chunk, 0, chunk.length);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] chunkChecksum = digest.digest(chunk);
            String chunkChecksumBase64 = Base64.getEncoder().encodeToString(chunkChecksum);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl + "/upload/" + uploadId + "/" + i))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Chunk-Checksum", chunkChecksumBase64)
                    .header("X-Password", password)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(chunk))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to upload chunk " + i + ": " + response.body());
            }
        }
    }
}
