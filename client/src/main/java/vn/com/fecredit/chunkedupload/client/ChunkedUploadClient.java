package vn.com.fecredit.chunkedupload.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.*;

public class ChunkedUploadClient {

    /**
     * Pluggable transport used to perform HTTP calls. Default implementation uses
     * java.net.http.HttpClient. Tests can provide a mock implementation.
     */
    public interface UploadTransport {
        /**
         * Calls the server /init endpoint to create or resume an upload session.
         *
         * @param initRequest init request payload
         * @param uploadUrl   base upload URL (e.g. http://host:port/api/upload)
         * @param encodedAuth Basic auth credentials encoded in Base64
         * @return InitResponse from server
         */
        InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth) throws IOException, InterruptedException;

        /**
         * Uploads a single chunk to the server /chunk endpoint.
         */
        void uploadSingleChunk(String sessionId, int chunkIndex, int chunkSize, int totalChunks, byte[] fileContent, String uploadUrl, String encodedAuth, int retryTimes) throws IOException, InterruptedException, NoSuchAlgorithmException;
    }

    public static class DefaultUploadTransport implements UploadTransport {
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

    /**
     * Default transport constructor.
     *
     * @param httpClient Optional HttpClient instance; if null, a default client is created.
     */
    public DefaultUploadTransport(HttpClient httpClient) {
            this.objectMapper = new ObjectMapper();
            this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        }

    @Override
    public InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth) throws IOException, InterruptedException {
            String requestBody = objectMapper.writeValueAsString(initRequest);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl + "/init"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + encodedAuth)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to initialize upload: " + response.body());
            }
            return objectMapper.readValue(response.body(), InitResponse.class);
        }

    @Override
    public void uploadSingleChunk(String sessionId, int chunkIndex, int chunkSize, int totalChunks, byte[] fileContent, String uploadUrl, String encodedAuth, int retryTimes) throws IOException, InterruptedException {
            int start = chunkIndex * chunkSize;
            int end = Math.min(start + chunkSize, fileContent.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileContent, start, chunk, 0, chunk.length);

            HttpRequest request = buildMultipartRequest(sessionId, chunkIndex, chunk.length, totalChunks, fileContent.length, chunk, uploadUrl, encodedAuth);
            System.out.println("[DEBUG] Sending chunk upload: sessionId=" + sessionId +
                    ", chunkNumber=" + (chunkIndex) +
                    ", chunkIndex=" + chunkIndex +
                    ", chunkSize=" + chunk.length +
                    ", totalChunks=" + totalChunks +
                    ", fileSize=" + fileContent.length);

            int attempts = 0;
            IOException lastException = null;
            while (attempts <= retryTimes) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("[DEBUG] HttpClient instance: " + httpClient);
                    System.out.println("[DEBUG] Response status code: " + response.statusCode());
                    System.out.println("[DEBUG] Response body: " + response.body());
                    if (response.statusCode() == 200) {
                        return; // Success
                    }
                    // Immediately throw for client/server errors
                    if (response.statusCode() >= 400) {
                        throw new RuntimeException("Failed to upload chunkNumber " + (chunkIndex) + " (chunkIndex=" + chunkIndex + "): " + response.body());
                    }
                    lastException = new IOException("Failed to upload chunkNumber " + (chunkIndex) + " (chunkIndex=" + chunkIndex + "): " + response.body());
                } catch (IOException e) {
                    lastException = new IOException("Failed to upload chunkNumber " + (chunkIndex) + " (chunkIndex=" + chunkIndex + ")", e);
                }
                attempts++;
            }
            throw new RuntimeException(lastException);
        }

    /**
     * Builds a multipart/form-data HttpRequest for uploading a chunk.
     */
    private HttpRequest buildMultipartRequest(String sessionId, int chunkIndex, int chunkSize, int totalChunks, int fileSize, byte[] chunk, String uploadUrl, String encodedAuth) {
            String boundary = "----Boundary" + java.util.UUID.randomUUID();
            String CRLF = "\r\n";
            byte[] header = buildMultipartHeader(boundary, CRLF, sessionId, chunkIndex, chunkSize, totalChunks, fileSize);
            byte[] footer = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);

            byte[] multipartBody = new byte[header.length + chunk.length + footer.length];
            System.arraycopy(header, 0, multipartBody, 0, header.length);
            System.arraycopy(chunk, 0, multipartBody, header.length, chunk.length);
            System.arraycopy(footer, 0, multipartBody, header.length + chunk.length, footer.length);

            return HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl + "/chunk"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Basic " + encodedAuth)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
        }

        private byte[] buildMultipartHeader(String boundary, String CRLF, String sessionId, int chunkIndex, int chunkSize, int totalChunks, int fileSize) {
            return new StringBuilder()
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"uploadId\"" + CRLF + CRLF).append(sessionId).append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"chunkNumber\"" + CRLF + CRLF).append(chunkIndex).append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"totalChunks\"" + CRLF + CRLF).append(totalChunks).append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"chunkSize\"" + CRLF + CRLF).append(chunkSize).append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"fileSize\"" + CRLF + CRLF).append(fileSize).append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"" + CRLF)
                    .append("Content-Type: application/octet-stream" + CRLF + CRLF)
                    .toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private final String uploadUrl;
    private final String encodedAuth;
    private int retryTimes;
    private int threadCounts;
    private final UploadTransport transport;

    private ChunkedUploadClient(Builder builder) {
        this.uploadUrl = builder.uploadUrl;
        this.retryTimes = builder.retryTimes;
        this.threadCounts = builder.threadCounts;
        this.encodedAuth = Base64.getEncoder().encodeToString((builder.username + ":" + builder.password).getBytes(StandardCharsets.UTF_8));
        this.transport = builder.transport != null ? builder.transport : new DefaultUploadTransport(builder.httpClient);
    }

    /**
     * Starts a new upload for the provided file content.
     *
     * @param fileContent file bytes
     * @param fileName    original filename
     * @param retryTimes  optional override for chunk retry count
     * @param threadCounts optional override for number of parallel threads
     * @return uploadId assigned by the server
     */
    public String upload(byte[] fileContent, String fileName, Integer retryTimes, Integer threadCounts) {
        if (fileContent == null || fileContent.length == 0)
            throw new IllegalArgumentException("fileContent is required");
        if (fileName == null || fileName.isEmpty()) throw new IllegalArgumentException("fileName is required");
        if (retryTimes != null) this.retryTimes = retryTimes;
        if (threadCounts != null) this.threadCounts = threadCounts;

        try {
            InitResponse initResponse = startUploadSession(fileContent, fileName);
            uploadChunks(initResponse.getUploadId(), initResponse.getChunkSize(), initResponse.getTotalChunks(), fileContent);
            return initResponse.getUploadId();
        } catch (Exception e) {
            propagateRelevantException(e);
            return null; // Unreachable but required by compiler
        }
    }

    /**
     * Resumes an existing upload by querying the server for missing chunks
     * and re-uploading only those chunks.
     *
     * @param uploadId   upload session id returned by previous init
     * @param fileContent entire file bytes used to re-send missing chunks
     */
    public void resumeUpload(String uploadId, byte[] fileContent) {
        try {
            InitRequest initRequest = new InitRequest();
            initRequest.setUploadId(uploadId);
            initRequest.setFileSize(fileContent.length);

            InitResponse initResp = transport.initUpload(initRequest, uploadUrl, encodedAuth);

            String serverChecksum = initResp.getChecksum();
            if (serverChecksum != null && !serverChecksum.isEmpty()) {
                String clientChecksum = computeSHA256(fileContent);
                if (!serverChecksum.equals(clientChecksum)) {
                    throw new RuntimeException("Checksum mismatch for uploadId " + uploadId);
                }
            }

            if (initResp.getMissingChunkNumbers() != null && !initResp.getMissingChunkNumbers().isEmpty()) {
                uploadChunks(initResp.getUploadId(), initResp.getChunkSize(), initResp.getTotalChunks(), fileContent, initResp.getMissingChunkNumbers());
            } else {
                uploadChunks(initResp.getUploadId(), initResp.getChunkSize(), initResp.getTotalChunks(), fileContent);
            }

        } catch (Exception e) {
            propagateRelevantException(e);
        }
    }

    private void uploadChunks(String sessionId, int chunkSize, int totalChunks, byte[] fileContent) throws InterruptedException {
        java.util.List<Integer> allChunks = new java.util.ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) allChunks.add(i);
        uploadChunks(sessionId, chunkSize, totalChunks, fileContent, allChunks);
    }

    private void uploadChunks(String sessionId, int chunkSize, int totalChunks, byte[] fileContent, java.util.List<Integer> chunksToUpload) throws InterruptedException {
        if (chunksToUpload.isEmpty()) return;
        int numWorkers = Math.min(threadCounts, chunksToUpload.size());
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        BlockingQueue<Integer> chunkQueue = new LinkedBlockingQueue<>(chunksToUpload);
        Future<?>[] futures = new Future<?>[numWorkers];

        for (int i = 0; i < numWorkers; i++) {
            futures[i] = executor.submit(() -> {
                Integer chunkIndex;
                while ((chunkIndex = chunkQueue.poll()) != null) {
                    try {
                        transport.uploadSingleChunk(sessionId, chunkIndex, chunkSize, totalChunks, fileContent, uploadUrl, encodedAuth, retryTimes);
                    } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            executor.shutdownNow();
            propagateRelevantException(e);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    public InitResponse startUploadSession(byte[] fileContent, String fileName) throws IOException, InterruptedException {
        InitRequest initRequest = new InitRequest();
        initRequest.setFilename(fileName);
        initRequest.setFileSize(fileContent.length);
        // Do not set chunkSize; let server decide and return in InitResponse
        return transport.initUpload(initRequest, uploadUrl, encodedAuth);
    }

    public void uploadChunk(String uploadId, int chunkIndex, int chunkSize, int totalChunks, byte[] fileContent) throws IOException, InterruptedException, NoSuchAlgorithmException {
        transport.uploadSingleChunk(uploadId, chunkIndex, chunkSize, totalChunks, fileContent, uploadUrl, encodedAuth, this.retryTimes);
    }

    private String computeSHA256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private void propagateRelevantException(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Failed to") || msg.contains("Checksum mismatch"))) {
                throw new RuntimeException(msg, cause.getCause());
            }
            cause = cause.getCause();
        }
        throw new RuntimeException("An unknown error occurred during upload", e);
    }

    public static class Builder {
        private String uploadUrl;
        private String username;
        private String password;
        private int retryTimes = 2;
        private int threadCounts = 4;
        private HttpClient httpClient;
        private UploadTransport transport;

        public Builder uploadUrl(String uploadUrl) {
            this.uploadUrl = uploadUrl;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public Builder threadCounts(int threadCounts) {
            this.threadCounts = threadCounts;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder transport(UploadTransport transport) {
            this.transport = transport;
            return this;
        }

        public ChunkedUploadClient build() {
            if (uploadUrl == null || username == null || password == null) {
                throw new IllegalStateException("uploadUrl, username, and password are required");
            }
            return new ChunkedUploadClient(this);
        }
    }
}
