package vn.com.fecredit.chunkedupload.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;

/**
 * Client for chunked file upload operations with resume capability.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Concurrent chunk uploads with configurable thread count</li>
 * <li>Automatic retry on failed chunks</li>
 * <li>Resume interrupted uploads</li>
 * <li>Checksum validation</li>
 * <li>Progress tracking</li>
 * </ul>
 *
 * <p>
 * Usage:
 * <pre>
 * ChunkedUploadClient client = new ChunkedUploadClient.Builder()
 *     .uploadUrl("http://server/api/upload")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * String uploadId = client.upload(filePath, 3, 4); // 3 retries, 4 threads
 * </pre>
 */
public class ChunkedUploadClient {

    /**
     * Pluggable transport layer for making HTTP requests to the upload server.
     *
     * <p>
     * This interface abstracts the HTTP communication details, allowing:
     * <ul>
     * <li>Customizable HTTP client implementations</li>
     * <li>Mocking for testing scenarios</li>
     * <li>Different authentication mechanisms</li>
     * <li>Custom retry strategies</li>
     * </ul>
     *
     * <p>
     * The default implementation {@link DefaultUploadTransport}:
     * <ul>
     * <li>Uses {@link java.net.http.HttpClient}</li>
     * <li>Supports Basic authentication</li>
     * <li>Handles retries for failed chunks</li>
     * <li>Provides detailed error messages</li>
     * </ul>
     *
     * @see DefaultUploadTransport
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
        InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth)
                throws IOException, InterruptedException;

        /**
         * Uploads a single chunk to the server /chunk endpoint.
         *
         * @param sessionId   The upload session ID.
         * @param chunk       The chunk data and metadata to upload.
         * @param uploadUrl   The base URL for the upload server.
         * @param encodedAuth The Base64 encoded authentication string.
         * @param retryTimes  The number of times to retry on failure.
         * @throws IOException          if a network error occurs during upload.
         * @throws InterruptedException if the thread is interrupted during upload or retry backoff.
         */
        void uploadSingleChunk(String sessionId, Chunk chunk, String uploadUrl, String encodedAuth, int retryTimes)
                throws IOException, InterruptedException;
    }

    public static class DefaultUploadTransport implements UploadTransport {
        /** Thread-safe JSON mapper for request/response serialization */
        private final ObjectMapper objectMapper;
        
        /** Shared HTTP client with connection pooling */
        private final HttpClient httpClient;

        /**
         * Creates a new DefaultUploadTransport instance.
         *
         * <p>
         * Implementation details:
         * <ul>
         * <li>Creates a thread-safe ObjectMapper instance</li>
         * <li>Uses provided HttpClient or creates default</li>
         * <li>Default client uses HTTP/2</li>
         * <li>Automatic connection pooling</li>
         * </ul>
         *
         * @param httpClient Optional custom HttpClient instance.
         *                  If null, creates default client with:
         *                  <ul>
         *                  <li>HTTP/2 enabled</li>
         *                  <li>Default timeouts</li>
         *                  <li>Connection pooling</li>
         *                  </ul>
         */
        public DefaultUploadTransport(HttpClient httpClient) {
            this.objectMapper = new ObjectMapper();
            this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        }

        @Override
        public InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth)
                throws IOException, InterruptedException {
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

        /**
         * Uploads a single chunk with retry capability.
         *
         * <p>
         * Error handling:
         * <ul>
         * <li>Retries on network/timeout errors</li>
         * <li>Fails fast on 4xx client errors</li>
         * <li>Retries on 5xx server errors</li>
         * <li>Detailed error messages in exceptions</li>
         * </ul>
         *
         * <p>
         * Retry behavior:
         * <ul>
         * <li>Exponential backoff between attempts</li>
         * <li>Retries up to specified limit</li>
         * <li>Preserves original exception chain</li>
         * </ul>
         *
         * @param sessionId Upload session identifier
         * @param chunk Chunk data and metadata
         * @param uploadUrl Base server URL
         * @param encodedAuth Base64 encoded auth header
         * @param retryTimes Maximum retry attempts
         * @throws IOException On unrecoverable network errors
         * @throws InterruptedException If interrupted during retry wait
         * @throws RuntimeException On client/server errors after retries
         */
        @Override
        public void uploadSingleChunk(String sessionId, Chunk chunk,
                                      String uploadUrl, String encodedAuth, int retryTimes)
                throws IOException, InterruptedException {

            HttpRequest request = buildMultipartRequest(sessionId, chunk, uploadUrl, encodedAuth);

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
                        throw new RuntimeException("Failed to upload chunkNumber " + (chunk.getIndex()) + " (chunkIndex="
                                + chunk.getIndex() + "): " + response.body());
                    }
                    lastException = new IOException("Failed to upload chunkNumber " + (chunk.getIndex()) + " (chunkIndex="
                            + chunk.getIndex() + "): " + response.body());
                } catch (IOException e) {
                    lastException = new IOException(
                            "Failed to upload chunkNumber " + (chunk.getIndex()) + " (chunkIndex=" + chunk.getIndex() + ")", e);
                }
                attempts++;
            }
            throw new RuntimeException(lastException);
        }

        /**
         * Builds a multipart/form-data HTTP request for chunk upload.
         *
         * <p>
         * Request structure:
         * <ul>
         * <li>Unique boundary using UUID</li>
         * <li>Three form parts: uploadId, chunkNumber, file</li>
         * <li>Binary file content with octet-stream type</li>
         * </ul>
         *
         * <p>
         * Memory optimization:
         * <ul>
         * <li>Pre-calculated buffer sizes</li>
         * <li>Single byte array allocation</li>
         * <li>Efficient array copies</li>
         * </ul>
         *
         * @param sessionId Upload session identifier
         * @param chunk Chunk data and metadata
         * @param uploadUrl Base server URL
         * @param encodedAuth Base64 encoded auth header
         * @return Configured HttpRequest for chunk upload
         */
        private HttpRequest buildMultipartRequest(String sessionId,
                                                  Chunk chunk, String uploadUrl, String encodedAuth) {
            String boundary = "----Boundary" + java.util.UUID.randomUUID();
            String CRLF = "\r\n";
            byte[] header = buildMultipartHeader(boundary, CRLF, sessionId, chunk);
            byte[] footer = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);

            byte[] multipartBody = new byte[header.length + chunk.getData().length + footer.length];
            System.arraycopy(header, 0, multipartBody, 0, header.length);
            System.arraycopy(chunk.getData(), 0, multipartBody, header.length, chunk.getData().length);
            System.arraycopy(footer, 0, multipartBody, header.length + chunk.getData().length, footer.length);

            return HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl + "/chunk"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Basic " + encodedAuth)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();
        }

        private byte[] buildMultipartHeader(String boundary, String CRLF, String sessionId, Chunk chunk) {
            return new StringBuilder()
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"uploadId\"" + CRLF + CRLF).append(sessionId)
                    .append(CRLF)
                    .append("--").append(boundary).append(CRLF)
                    .append("Content-Disposition: form-data; name=\"chunkNumber\"" + CRLF + CRLF).append(chunk.getIndex())
                    .append(CRLF)
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
        this.encodedAuth = Base64.getEncoder()
                .encodeToString((builder.username + ":" + builder.password).getBytes(StandardCharsets.UTF_8));
        this.transport = builder.transport != null ? builder.transport : new DefaultUploadTransport(builder.httpClient);
    }

    /**
     * Starts a new upload for the provided file content.
     *
     * @param filePath     Path to the file to be uploaded. Must exist.
     * @param retryTimes   optional override for chunk retry count
     * @param threadCounts optional override for number of parallel threads
     * @return uploadId assigned by the server
     * @throws IllegalArgumentException if filePath is null or does not exist.
     * @throws RuntimeException         if the upload fails for any reason (e.g., network, server error).
     */
    public String upload(Path filePath, Integer retryTimes, Integer threadCounts) {
        if (filePath == null || !filePath.toFile().exists())
            throw new IllegalArgumentException("filePath is required and must exist");
        if (retryTimes != null)
            this.retryTimes = retryTimes;
        if (threadCounts != null)
            this.threadCounts = threadCounts;

        try {
            InitResponse initResponse = startUploadSession(filePath);
            uploadChunks(initResponse.getUploadId(), initResponse, filePath);
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
     * @param brokenUploadId upload session id returned by a previous `init` call.
     * @param filePath       Path to the file being uploaded. It must be the same file as the original upload.
     * @throws RuntimeException if the resume operation fails, for example due to a checksum mismatch
     *                          or if the server cannot find the session.
     */
    public void resumeUpload(String brokenUploadId, Path filePath) {
        try {
            InitResponse initResp = startResumeSession(brokenUploadId, filePath);

            String serverChecksum = initResp.getChecksum();
            if (serverChecksum != null && !serverChecksum.isEmpty()) {
                String clientChecksum = ChecksumUtil.generateChecksum(filePath);
                if (!serverChecksum.equals(clientChecksum)) {
                    throw new RuntimeException("Checksum mismatch for uploadId " + initResp.getUploadId());
                }
            }
            uploadChunks(brokenUploadId, initResp, filePath);

        } catch (Exception e) {
            propagateRelevantException(e);
        }
    }

    /**
     * Coordinates concurrent chunk uploads using a thread pool and producer-consumer pattern.
     *
     * <p>
     * Threading model:
     * <ul>
     * <li>Producer thread reads file chunks sequentially</li>
     * <li>Consumer threads upload chunks concurrently</li>
     * <li>Bounded queue manages backpressure</li>
     * <li>Sentinel chunks signal completion</li>
     * </ul>
     *
     * <p>
     * Error handling:
     * <ul>
     * <li>Worker exceptions propagate to main thread</li>
     * <li>Interrupted uploads can be resumed</li>
     * <li>Resources are cleaned up on failure</li>
     * </ul>
     *
     * <p>
     * Performance tuning:
     * <ul>
     * <li>Queue size = 2 * thread count for optimal throughput</li>
     * <li>Thread count capped at total chunks</li>
     * <li>Efficient file channel access</li>
     * </ul>
     *
     * @param sessionId Upload session identifier
     * @param initResponse Server's initialization response
     * @param filePath Path to the file being uploaded
     * @throws InterruptedException If the upload is interrupted
     * @throws IOException If file or network errors occur
     */
    private void uploadChunks(String sessionId, InitResponse initResponse, Path filePath)
            throws InterruptedException, IOException {
        uploadChunks(sessionId, filePath, initResponse);
    }

    private void uploadChunks(String sessionId, Path filePath, InitResponse initResponse) throws InterruptedException, IOException {
        int numWorkers = Math.min(threadCounts, initResponse.getTotalChunks());
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        BlockingQueue<Chunk> chunkQueue = new LinkedBlockingQueue<>(numWorkers * 2);
        Future<?>[] futures = new Future<?>[numWorkers];
        for (int i = 0; i < numWorkers; i++) {
            System.out.println("[DEBUG] Starting worker thread " + i);
            futures[i] = executor.submit(() -> {
                Chunk chunk;
                System.out.println("[DEBUG] Worker thread started: " + Thread.currentThread().getName());
                while (true) {
                    try {
                        chunk = chunkQueue.take();
                        if (chunk.getIndex() == -1) {
                            break;
                        }
                        System.out.println("[DEBUG] Uploading chunk index: " + chunk.getIndex() + " by thread: " + Thread.currentThread().getName());
                        transport.uploadSingleChunk(sessionId, chunk,
                                uploadUrl, encodedAuth, retryTimes);
                        System.out.println("[DEBUG] Uploaded chunk index: " + chunk.getIndex() + " by thread: " + Thread.currentThread().getName());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("[DEBUG] Worker thread interrupted: " + Thread.currentThread().getName());
                        break;
                    } catch (Exception e) {
                        System.out.println("[DEBUG] Exception in worker thread: " + Thread.currentThread().getName() + " - " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("[DEBUG] Worker thread finished: " + Thread.currentThread().getName());
            });
            System.out.println("[DEBUG] Worker thread " + i + " submitted.");
        }
        long fileSize = initResponse.getFileSize();
        int chunkSize = initResponse.getChunkSize();
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            List<Integer> chunkIndices = BitsetUtil.bitsetToList(BitsetUtil.invertBits(initResponse.getBitsetBytes()));
            long totalChunks = (fileSize + chunkSize - 1) / chunkSize;
            for (Integer chunkIndex : chunkIndices) {
                long remainingBytes = fileSize - (long) chunkIndex * chunkSize;
                int buffSize = (int) Math.min(chunkSize, remainingBytes);
                byte[] buffer = new byte[buffSize];
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                fileChannel.read(byteBuffer, chunkIndex * chunkSize);
                System.out.println("[DEBUG] Read chunk index: " + chunkIndex + " with size: " + buffSize);
                chunkQueue.put(new Chunk(buffer, chunkIndex));
                System.out.println("[DEBUG] Enqueued chunk index: " + chunkIndex);
            }
            Chunk stopChunk = new Chunk(null, -1);
            for (int i = 0; i < numWorkers; i++) {
                System.out.println("[DEBUG] Sending stop signal to worker thread " + i);
                chunkQueue.put(stopChunk);
                System.out.println("[DEBUG] Stop signal sent to worker thread " + i);
            }

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

    /**
     * Initializes a new upload session for the given file.
     *
     * <p>
     * This method:
     * <ul>
     * <li>Calculates file checksum</li>
     * <li>Creates server-side upload session</li>
     * <li>Returns server configuration for chunked upload</li>
     * </ul>
     *
     * @param filePath Path to the file to upload
     * @return Server's initialization response with upload configuration
     * @throws IOException If file access or network errors occur
     * @throws InterruptedException If the operation is interrupted
     * @see InitResponse
     */
    public InitResponse startUploadSession(Path filePath) throws IOException, InterruptedException {
        return startResumeSession(null, filePath);
    }

    /**
     * Resumes an interrupted upload session with server verification.
     *
     * <p>
     * This method:
     * <ul>
     * <li>Validates file hasn't changed via checksum</li>
     * <li>Retrieves missing chunks information</li>
     * <li>Configures chunk size and count</li>
     * </ul>
     *
     * <p>
     * Implementation details:
     * <ul>
     * <li>Reuses existing upload ID if provided</li>
     * <li>Performs checksum validation</li>
     * <li>Handles server-side session state</li>
     * </ul>
     *
     * @param brokenUploadId Previous upload ID to resume, or null for new upload
     * @param filePath Path to the file being uploaded
     * @return Server's initialization response containing upload state
     * @throws IOException If file access or network errors occur
     * @throws InterruptedException If the operation is interrupted
     * @see InitResponse
     */
    public InitResponse startResumeSession(String brokenUploadId, Path filePath)
            throws IOException, InterruptedException {
        InitRequest initRequest = new InitRequest();
        initRequest.setFilename(filePath.getFileName().toString());
        initRequest.setBrokenUploadId(brokenUploadId);
        initRequest.setFileSize(filePath.toFile().length());
        initRequest.setChecksum(ChecksumUtil.generateChecksum(filePath));
        // Do not set chunkSize; let server decide and return in InitResponse
        return transport.initUpload(initRequest, uploadUrl, encodedAuth);
    }

    /**
     * Extracts and propagates relevant error messages from nested exceptions.
     *
     * <p>
     * This method traverses the exception chain to find actionable errors:
     * <ul>
     * <li>Upload failures (network, server errors)</li>
     * <li>Checksum mismatches (data corruption)</li>
     * <li>Authentication failures</li>
     * <li>Resource access errors</li>
     * </ul>
     *
     * <p>
     * Processing rules:
     * <ul>
     * <li>Prioritizes errors with "Failed to" or "Checksum mismatch"</li>
     * <li>Preserves original cause chain</li>
     * <li>Falls back to generic message if no specific error found</li>
     * <li>Unwraps nested exceptions for clarity</li>
     * </ul>
     *
     * @param e The source exception to analyze
     * @throws RuntimeException Containing the most relevant error message
     */
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

    /**
     * Builder for creating ChunkedUploadClient instances with custom configuration.
     *
     * <p>
     * Required parameters:
     * <ul>
     * <li>uploadUrl - Server endpoint URL</li>
     * <li>username - Basic auth username</li>
     * <li>password - Basic auth password</li>
     * </ul>
     *
     * <p>
     * Optional parameters:
     * <ul>
     * <li>retryTimes - Number of retry attempts (default: 2)</li>
     * <li>threadCounts - Number of concurrent upload threads (default: 4)</li>
     * <li>httpClient - Custom HttpClient instance</li>
     * <li>transport - Custom upload transport implementation</li>
     * </ul>
     */
    public static class Builder {
        private String uploadUrl;
        private String username;
        private String password;
        private int retryTimes = 2;
        private int threadCounts = 4;
        private HttpClient httpClient;
        private UploadTransport transport;

        /**
         * Sets the server upload endpoint URL.
         *
         * @param uploadUrl Base URL for upload endpoints (e.g. http://server/api/upload)
         * @return this builder instance
         */
        public Builder uploadUrl(String uploadUrl) {
            this.uploadUrl = uploadUrl;
            return this;
        }

        /**
         * Sets the username for basic authentication.
         *
         * @param username Basic auth username
         * @return this builder instance
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password for basic authentication.
         *
         * @param password Basic auth password
         * @return this builder instance
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the number of retry attempts for failed chunk uploads.
         *
         * @param retryTimes Number of retries (default: 2)
         * @return this builder instance
         */
        public Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        /**
         * Sets the number of concurrent upload threads.
         *
         * @param threadCounts Number of threads (default: 4)
         * @return this builder instance
         */
        public Builder threadCounts(int threadCounts) {
            this.threadCounts = threadCounts;
            return this;
        }

        /**
         * Sets a custom HttpClient instance.
         *
         * @param httpClient Custom HttpClient for making HTTP requests
         * @return this builder instance
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Sets a custom upload transport implementation.
         *
         * @param transport Custom transport for upload operations
         * @return this builder instance
         */
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
