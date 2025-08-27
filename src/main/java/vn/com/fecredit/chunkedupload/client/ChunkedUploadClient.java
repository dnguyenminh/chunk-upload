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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ChunkedUploadClient provides a robust, multi-threaded client for uploading large files in chunks to a remote server using Basic Authentication.
 * <p>
 * Features:
 * <ul>
 *   <li>Builder pattern for flexible configuration and validation of required/optional properties.</li>
 *   <li>Optimized chunked upload using worker threads and a blocking queue for efficient parallelism.</li>
 *   <li>Customizable retry logic and thread count for uploads.</li>
 *   <li>Robust error propagation with exact messages for unit test compatibility.</li>
 *   <li>All HTTP requests use the provided or default HttpClient instance.</li>
 * </ul>
 * <p>
 * Usage Example:
 * <pre>
 * ChunkedUploadClient client = new ChunkedUploadClient.Builder()
 *     .uploadUrl("https://example.com/upload")
 *     .username("user")
 *     .password("pass")
 *     .retryTimes(3)
 *     .threadCounts(8)
 *     .build();
 * client.upload(fileBytes, "myfile.txt", null, null);
 * </pre>
 * <p>
 * Error Handling:
 * <ul>
 *   <li>Throws RuntimeException with exact error messages for chunk upload and initialization failures.</li>
 *   <li>Worker threads and main thread propagate errors consistently.</li>
 * </ul>
 * <p>
 * Thread Safety: This class is not thread-safe for concurrent uploads; create a new instance per upload.
 */
/**
 * ChunkedUploadClient provides a robust, multi-threaded client for uploading large files in chunks to a remote server using Basic Authentication.
 * <p>
 * Features:
 * <ul>
 *   <li>Builder pattern for flexible configuration and validation of required/optional properties.</li>
 *   <li>Optimized chunked upload using worker threads and a blocking queue for efficient parallelism.</li>
 *   <li>Customizable retry logic and thread count for uploads.</li>
 *   <li>Robust error propagation with exact messages for unit test compatibility.</li>
 *   <li>All HTTP requests use the provided or default HttpClient instance.</li>
 * </ul>
 * <p>
 * Usage Example:
 * <pre>
 * ChunkedUploadClient client = new ChunkedUploadClient.Builder()
 *     .uploadUrl("https://example.com/upload")
 *     .username("user")
 *     .password("pass")
 *     .retryTimes(3)
 *     .threadCounts(8)
 *     .build();
 * client.upload(fileBytes, "myfile.txt", null, null);
 * </pre>
 * <p>
 * Error Handling:
 * <ul>
 *   <li>Throws RuntimeException with exact error messages for chunk upload and initialization failures.</li>
 *   <li>Worker threads and main thread propagate errors consistently.</li>
 * </ul>
 * <p>
 * Thread Safety: This class is not thread-safe for concurrent uploads; create a new instance per upload.
 */
public class ChunkedUploadClient {
    public interface UploadTransport {
        InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth) throws IOException, InterruptedException;
    void uploadSingleChunk(String sessionId, int chunkIndex, int chunkSize, byte[] fileContent, String uploadUrl, String encodedAuth, int retryTimes) throws IOException, InterruptedException, NoSuchAlgorithmException;
    }

    public static class DefaultUploadTransport implements UploadTransport {
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;
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
                throw new IOException("Failed to initialize upload");
            }
            return objectMapper.readValue(response.body(), InitResponse.class);
        }
        @Override
    public void uploadSingleChunk(String sessionId, int chunkIndex, int chunkSize, byte[] fileContent, String uploadUrl, String encodedAuth, int retryTimes) throws IOException, InterruptedException, NoSuchAlgorithmException {
            int start = chunkIndex * chunkSize;
            int end = Math.min(start + chunkSize, fileContent.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(fileContent, start, chunk, 0, chunk.length);
            String boundary = "----Boundary" + java.util.UUID.randomUUID();
            String CRLF = "\r\n";
            StringBuilder sb = new StringBuilder();
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"uploadId\"" + CRLF + CRLF).append(sessionId).append(CRLF);
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"chunkNumber\"" + CRLF + CRLF).append(chunkIndex + 1).append(CRLF);
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"totalChunks\"" + CRLF + CRLF).append((int) Math.ceil((double) fileContent.length / chunkSize)).append(CRLF);
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"chunkSize\"" + CRLF + CRLF).append(chunkSize).append(CRLF);
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"fileSize\"" + CRLF + CRLF).append(fileContent.length).append(CRLF);
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"" + CRLF);
            sb.append("Content-Type: application/octet-stream" + CRLF + CRLF);
            byte[] headerBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            byte[] footerBytes = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
            byte[] multipartBody = new byte[headerBytes.length + chunk.length + footerBytes.length];
            System.arraycopy(headerBytes, 0, multipartBody, 0, headerBytes.length);
            System.arraycopy(chunk, 0, multipartBody, headerBytes.length, chunk.length);
            System.arraycopy(footerBytes, 0, multipartBody, headerBytes.length + chunk.length, footerBytes.length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl + "/chunk"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Basic " + encodedAuth)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
            int attempts = 0;
            IOException lastException = null;
            int maxAttempts = retryTimes + 1;
            while (attempts < maxAttempts) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return;
                    } else {
                        lastException = new IOException("Failed to upload chunk " + chunkIndex);
                    }
                } catch (IOException e) {
                    lastException = new IOException("Failed to upload chunk " + chunkIndex);
                }
                attempts++;
            }
            throw lastException != null ? lastException : new IOException("Failed to upload chunk " + chunkIndex);
        }
    }
    /**
     * Resumes a broken upload by continuing to upload missing chunks for the given uploadId.
     * @param uploadId The upload session ID to resume.
     * @param fileContent The file content as byte array.
     * @throws RuntimeException if resume fails.
     */
    public void resumeUpload(String uploadId, byte[] fileContent) {
        try {
            // Get session info from server (init with uploadId)
            InitRequest initRequest = new InitRequest();
            initRequest.setUploadId(uploadId);
            initRequest.setFileSize(fileContent.length);
            InitResponse initResp = transport.initUpload(initRequest, uploadUrl, encodedAuth);
            int chunkSize = initResp.getChunkSize();

            // Checksum comparison
            String previousChecksum = null;
            try {
                previousChecksum = (String) initResp.getClass().getMethod("getChecksum").invoke(initResp);
            } catch (Exception ignore) {}
            String currentChecksum = computeSHA256(fileContent);
            if (previousChecksum != null && !previousChecksum.isEmpty() && !previousChecksum.equals(currentChecksum)) {
                throw new RuntimeException("Checksum mismatch: cannot resume upload. Previous=" + previousChecksum + ", Current=" + currentChecksum);
            }

            uploadChunks(uploadId, chunkSize, fileContent);
        } catch (Exception e) {
            propagateRelevantException(e);
        }
    }

    private String computeSHA256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 checksum", e);
        }
    }
    private final String uploadUrl;
    private final String username;
    private final String password;
    private int retryTimes;
    private int threadCounts;
    private final String encodedAuth;
    private final UploadTransport transport;

    private ChunkedUploadClient(Builder builder) {
        if (builder.uploadUrl == null || builder.uploadUrl.isEmpty()) {
            throw new IllegalArgumentException("uploadUrl is required");
        }
        if (builder.username == null || builder.username.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        if (builder.password == null || builder.password.isEmpty()) {
            throw new IllegalArgumentException("password is required");
        }
    this.uploadUrl = builder.uploadUrl;
    this.username = builder.username;
    this.password = builder.password;
    this.retryTimes = builder.retryTimes;
    this.threadCounts = builder.threadCounts;
    String auth = username + ":" + password;
    this.encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    this.transport = builder.transport != null ? builder.transport : new DefaultUploadTransport(builder.httpClient);
    }

    public static class Builder {
    private String uploadUrl;
    private String username;
    private String password;
    private int retryTimes = 2;
    private int threadCounts = Runtime.getRuntime().availableProcessors() * 2;
    private HttpClient httpClient;
    private UploadTransport transport;
        public Builder transport(UploadTransport transport) {
            this.transport = transport;
            return this;
        }

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
            if (threadCounts <= 0) throw new IllegalArgumentException("threadCounts must be > 0");
            this.threadCounts = threadCounts;
            return this;
        }
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }
        public ChunkedUploadClient build() {
            return new ChunkedUploadClient(this);
        }
    }

    /**
     * Uploads the file to the server in chunks using multi-threaded workers and blocking queue.
     * <p>
     * Required parameters: fileContent, fileName. Optional: retryTimes, threadCounts (overwrites client config).
     * <p>
     * Throws RuntimeException with exact error messages for chunk upload and initialization failures.
     *
     * @param fileContent the file content as byte array (required)
     * @param fileName the name of the file (required)
     * @param retryTimes number of retry attempts for each chunk (optional, overrides client config)
     * @param threadCounts number of worker threads (optional, overrides client config)
     * @throws RuntimeException if upload fails with a relevant error message
     */
    public String upload(byte[] fileContent, String fileName, Integer retryTimes, Integer threadCounts) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("fileContent is required");
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("fileName is required");
        }
        if (retryTimes != null) this.retryTimes = retryTimes;
        if (threadCounts != null) {
            if (threadCounts <= 0) throw new IllegalArgumentException("threadCounts must be > 0");
            this.threadCounts = threadCounts;
        }
        try {
            InitResponse initResponse = initUpload(fileContent, fileName);
            String uploadId = initResponse.getUploadId();
            int chunkSize = initResponse.getChunkSize();
            uploadChunks(uploadId, chunkSize, fileContent);
            System.out.println("File uploaded successfully!");
            return uploadId;
        } catch (Exception e) {
            propagateRelevantException(e);
            return null;
        }
    }

    /**
     * Initializes the upload session with the server and retrieves session ID and chunk size.
     *
     * @param fileContent the file content as byte array
     * @param fileName the name of the file
     * @return InitResponse containing session ID and chunk size
     * @throws IOException if initialization fails
     * @throws InterruptedException if thread is interrupted
     */
    private InitResponse initUpload(byte[] fileContent, String fileName) throws IOException, InterruptedException {
    InitRequest initRequest = new InitRequest();
    initRequest.setFilename(fileName);
    initRequest.setFileSize(fileContent.length);
    // Do not set chunkSize or totalChunks; let server use its config
    return transport.initUpload(initRequest, uploadUrl, encodedAuth);
    }

    /**
     * Uploads all chunks in parallel using worker threads and a blocking queue.
     *
     * @param sessionId the upload session ID
     * @param chunkSize the size of each chunk
     * @param fileContent the file content as byte array
     * @throws IOException if chunk upload fails
     * @throws InterruptedException if thread is interrupted
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    private void uploadChunks(String sessionId, int chunkSize, byte[] fileContent) throws IOException, InterruptedException, NoSuchAlgorithmException {
        int totalChunks = (int) Math.ceil((double) fileContent.length / chunkSize);
        int numWorkers = Math.min(threadCounts, totalChunks);
        BlockingQueue<Integer> chunkQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < totalChunks; i++) {
            chunkQueue.add(i);
        }
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        Future<?>[] workerFutures = new Future<?>[numWorkers];
        for (int w = 0; w < numWorkers; w++) {
            workerFutures[w] = executor.submit(() -> {
                try {
                    while (true) {
                        Integer chunkIndex = chunkQueue.poll();
                        if (chunkIndex == null) break;
                        transport.uploadSingleChunk(sessionId, chunkIndex, chunkSize, fileContent, uploadUrl, encodedAuth, this.retryTimes);
                    }
                } catch (Exception e) {
                    propagateRelevantException(e);
                }
            });
        }
        for (Future<?> future : workerFutures) {
            try {
                future.get(); // Only throws if worker failed
            } catch (Exception e) {
                propagateRelevantException(e);
            }
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }
    }

    // Public methods for testing (no reflection needed)
    public InitResponse startUploadSession(byte[] fileContent, String fileName) throws IOException, InterruptedException {
        InitRequest initRequest = new InitRequest();
        initRequest.setFilename(fileName);
        initRequest.setFileSize(fileContent.length);
        return transport.initUpload(initRequest, uploadUrl, encodedAuth);
    }

    public void uploadChunk(String uploadId, int chunkIndex, int chunkSize, byte[] fileContent) throws IOException, InterruptedException, NoSuchAlgorithmException {
    transport.uploadSingleChunk(uploadId, chunkIndex, chunkSize, fileContent, uploadUrl, encodedAuth, this.retryTimes);
    }


    /**
     * Propagates relevant exceptions with exact error messages for chunk upload and initialization failures.
     * Throws RuntimeException with the correct message for unit test compatibility.
     *
     * @param e the exception to propagate
     */
    private void propagateRelevantException(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Failed to upload chunk") || msg.contains("Failed to initialize upload"))) {
                // Throw a plain RuntimeException with only the message, not the cause or type
                throw new RuntimeException(msg.replace("java.lang.RuntimeException: ", ""));
            }
            if (t instanceof java.util.concurrent.ExecutionException && t.getCause() != null) {
                t = t.getCause();
                continue;
            }
            t = t.getCause();
        }
        throw new RuntimeException("Unknown error");
    }
}
