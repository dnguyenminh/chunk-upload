// Force update to resolve build issue
package vn.com.fecredit.chunkedupload.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.com.fecredit.chunkedupload.model.InitRequest;
import vn.com.fecredit.chunkedupload.model.InitResponse;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;
import vn.com.fecredit.chunkedupload.model.util.ChecksumUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;

public class ChunkedUploadClient {

    public interface UploadTransport {
        InitResponse initUpload(InitRequest initRequest, String uploadUrl, String encodedAuth)
                throws IOException, InterruptedException;

        void uploadSingleChunk(String sessionId, Chunk chunk, String uploadUrl, String encodedAuth, int retryTimes)
                throws IOException, InterruptedException;
    }

    public static class DefaultUploadTransport implements UploadTransport {
        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

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

        @Override
        public void uploadSingleChunk(String sessionId, Chunk chunk,
                                      String uploadUrl, String encodedAuth, int retryTimes)
                throws InterruptedException {
            HttpRequest request = buildMultipartRequest(sessionId, chunk, uploadUrl, encodedAuth);
            int attempts = 0;
            IOException lastException = null;
            while (attempts <= retryTimes) {
                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        return; // Success
                    }
                    if (response.statusCode() >= 400) {
                        throw new RuntimeException("Failed to upload chunkNumber " + (chunk.getIndex()) + ": " + response.body());
                    }
                    lastException = new IOException("Failed to upload chunkNumber " + (chunk.getIndex()) + ": " + response.body());
                } catch (IOException e) {
                    lastException = new IOException("Failed to upload chunkNumber " + (chunk.getIndex()), e);
                }
                attempts++;
            }
            throw new RuntimeException(lastException);
        }

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
            String headerStr = "--" + boundary + CRLF +
                    "Content-Disposition: form-data; name=\"uploadId\"" + CRLF + CRLF + sessionId + CRLF +
                    "--" + boundary + CRLF +
                    "Content-Disposition: form-data; name=\"chunkNumber\"" + CRLF + CRLF + chunk.getIndex() + CRLF +
                    "--" + boundary + CRLF +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"chunk.bin\"" + CRLF +
                    "Content-Type: application/octet-stream" + CRLF + CRLF;
            return headerStr.getBytes(StandardCharsets.UTF_8);
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

    public String upload(Path filePath, Integer retryTimes, Integer threadCounts) {
        if (filePath == null || !filePath.toFile().exists()) throw new IllegalArgumentException("filePath is required and must exist");
        if (retryTimes != null) this.retryTimes = retryTimes;
        if (threadCounts != null) this.threadCounts = threadCounts;
        try {
            InitResponse initResponse = startUploadSession(filePath);
            uploadChunks(initResponse.getUploadId(), filePath, initResponse);
            return initResponse.getUploadId();
        } catch (Exception e) {
            propagateRelevantException(e);
            return null; // Unreachable
        }
    }

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
            uploadChunks(brokenUploadId, filePath, initResp);
        } catch (Exception e) {
            propagateRelevantException(e);
        }
    }

    private void uploadChunks(String sessionId, Path filePath, InitResponse initResponse) throws InterruptedException, IOException {
        int numWorkers = Math.min(threadCounts, initResponse.getTotalChunks());
        try (ExecutorService executor = Executors.newFixedThreadPool(numWorkers)) {
            BlockingQueue<Chunk> chunkQueue = new LinkedBlockingQueue<>(numWorkers * 2);
            Future<?>[] futures = new Future<?>[numWorkers];
            for (int i = 0; i < numWorkers; i++) {
                futures[i] = executor.submit(() -> {
                    try {
                        while (true) {
                            Chunk chunk = chunkQueue.take();
                            if (chunk.getIndex() == -1) break;
                            transport.uploadSingleChunk(sessionId, chunk, uploadUrl, encodedAuth, retryTimes);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            long fileSize = initResponse.getFileSize();
            int chunkSize = initResponse.getChunkSize();
            try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
                List<Integer> chunkIndices = BitsetUtil.bitsetToList(BitsetUtil.invertBits(initResponse.getBitsetBytes()));
                for (Integer chunkIndex : chunkIndices) {
                    long remainingBytes = fileSize - (long) chunkIndex * chunkSize;
                    int buffSize = (int) Math.min(chunkSize, remainingBytes);
                    byte[] buffer = new byte[buffSize];
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                    fileChannel.read(byteBuffer, (long) chunkIndex * chunkSize);
                    chunkQueue.put(new Chunk(buffer, chunkIndex));
                }
                for (int i = 0; i < numWorkers; i++) {
                    chunkQueue.put(new Chunk(null, -1));
                }
            }
            try {
                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (Exception e) {
                executor.shutdownNow();
                propagateRelevantException(e);
            }
        }
    }

    public InitResponse startUploadSession(Path filePath) throws IOException, InterruptedException {
        return startResumeSession(null, filePath);
    }

    public InitResponse startResumeSession(String brokenUploadId, Path filePath) throws IOException, InterruptedException {
        InitRequest initRequest = new InitRequest();
        initRequest.setFilename(filePath.getFileName().toString());
        initRequest.setBrokenUploadId(brokenUploadId);
        initRequest.setFileSize(filePath.toFile().length());
        initRequest.setChecksum(ChecksumUtil.generateChecksum(filePath));
        return transport.initUpload(initRequest, uploadUrl, encodedAuth);
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
