package vn.com.fecredit.chunkedupload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.fecredit.chunkedupload.model.InitResponse;
import vn.com.fecredit.chunkedupload.model.util.BitsetUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ChunkedUploadClientTest {

    private static final String UPLOAD_URL = "http://localhost:8080";
    private static final String FILENAME = "test-file.txt";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "password";
    private static final int RETRY_TIMES = 2;
    private static final int CHUNK_SIZE = 524288;
    private static final byte[] FILE_CONTENT = new byte[CHUNK_SIZE * 2]; // 2 chunks of 524288 bytes
    private java.nio.file.Path tempFile;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    // Removed unused field: httpRequestCaptor

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        // Write FILE_CONTENT to temp file for upload tests
        tempFile = java.nio.file.Files.createTempFile("test-file", ".txt");
        java.nio.file.Files.write(tempFile, FILE_CONTENT);
    }

    @Test
    void testUploadSuccess() throws IOException, InterruptedException {
        // [DEBUG] Confirm injected HttpClient and mock responses
        System.out.println("[DEBUG] Injected HttpClient: " + httpClient);
        System.out.println("[DEBUG] Mocked HttpResponse: " + httpResponse);

        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .httpClient(httpClient)
                .build();
        String sessionId = UUID.randomUUID().toString();
        long fileSize = FILE_CONTENT.length;
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        byte[] bitSetBytes = new byte[(int) Math.ceil((double) fileSize / CHUNK_SIZE /8)];
        BitsetUtil.setUnusedBits(bitSetBytes, totalChunks);
        String bitsetBase64 = Base64.getEncoder().encodeToString(bitSetBytes);
        String initResponseBody = String.format(
                "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"%s\",\"status\":\"INIT\",\"bitsetBytes\":\"" + bitsetBase64 + "\"}",
                sessionId, totalChunks, CHUNK_SIZE, fileSize, FILENAME
        );

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(initResponseBody).thenReturn("{\"status\":\"OK\"}");

        client.upload(tempFile, null, null);

        int expectedCalls = 1 + (int) Math.ceil((double) FILE_CONTENT.length / CHUNK_SIZE);
        verify(httpClient, times(expectedCalls)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void testUploadInitFails() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .httpClient(httpClient)
                .build();
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Init Error");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(tempFile, null, null));

        assertTrue(exception.getMessage().contains("Failed to initialize upload"));
    }

    @Test
    void testUploadChunkFailsAfterRetries() throws IOException, InterruptedException {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(RETRY_TIMES)
                .httpClient(httpClient)
                .build();
        String sessionId = UUID.randomUUID().toString();
        long fileSize = FILE_CONTENT.length;
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        byte[] bitSetBytes = new byte[(int) Math.ceil((double) fileSize / CHUNK_SIZE /8)];
        BitsetUtil.setUnusedBits(bitSetBytes, totalChunks);
        String bitsetBase64 = Base64.getEncoder().encodeToString(bitSetBytes);
        String initResponseBody = String.format(
                "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"%s\",\"status\":\"INIT\",\"bitsetBytes\":\"" + bitsetBase64 + "\"}",
                sessionId, totalChunks, CHUNK_SIZE, fileSize, FILENAME
        );

        when(httpResponse.statusCode()).thenReturn(200).thenReturn(500);
        when(httpResponse.body()).thenReturn(initResponseBody).thenReturn("Chunk Error");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(tempFile, RETRY_TIMES, null));

        assertTrue(exception.getMessage().contains("Failed to upload chunkNumber "));
        verify(httpClient, times(1 + RETRY_TIMES)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void testUploadSecondChunkFails() throws Exception {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .retryTimes(RETRY_TIMES)
                .httpClient(httpClient)
                .threadCounts(1)
                .build();
        String sessionId = UUID.randomUUID().toString();
        long fileSize = 1048576;
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
        byte[] bitSetBytes = new byte[(int) Math.ceil((double) fileSize / CHUNK_SIZE /8)];
        BitsetUtil.setUnusedBits(bitSetBytes, totalChunks);
        String bitsetBase64 = Base64.getEncoder().encodeToString(bitSetBytes);
        String initResponseBody = String.format(
                "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"test-file.txt\",\"status\":\"INIT\",\"bitsetBytes\":\"" + bitsetBase64 + "\"}",
                sessionId, totalChunks, CHUNK_SIZE, fileSize
        );

        HttpResponse<String> initResponse = mock(HttpResponse.class);
        HttpResponse<String> chunk1Response = mock(HttpResponse.class);
        HttpResponse<String> chunk2Response = mock(HttpResponse.class);

        when(initResponse.statusCode()).thenReturn(200);
        when(initResponse.body()).thenReturn(initResponseBody);
        when(chunk1Response.statusCode()).thenReturn(200);
        when(chunk1Response.body()).thenReturn("{\"status\":\"OK\"}");
        when(chunk2Response.statusCode()).thenReturn(500);
        when(chunk2Response.body()).thenReturn("Chunk 1 Error");

        doReturn(initResponse)
                .doReturn(chunk1Response)
                .doReturn(chunk2Response)
                .doReturn(chunk2Response)
                .doReturn(chunk2Response)
                .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Use file content at least 2 * CHUNK_SIZE
        byte[] bigFileContent = new byte[524288 * 2];
        for (int i = 0; i < bigFileContent.length; i++) {
            bigFileContent[i] = (byte) ('A' + (i % 26));
        }
//        new java.util.Random().nextBytes(bigFileContent);
        java.nio.file.Path bigFilePath = java.nio.file.Files.createTempFile("big-file", ".bin");
        java.nio.file.Files.write(bigFilePath, bigFileContent);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(bigFilePath, null, null));

        assertTrue(exception.getMessage().contains("Failed to upload chunkNumber 1"));
    }

    @Test
    void testUploadWithEmptyFileContentThrows() throws IOException {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        java.nio.file.Path emptyFile = java.nio.file.Files.createTempFile("empty-file", ".txt");
        assertThrows(RuntimeException.class, () -> client.upload(emptyFile, null, null));
    }

    @Test
    void testUploadWithNullFilenameThrows() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .build();
        assertThrows(RuntimeException.class, () -> client.upload(tempFile, null, null));
    }

    @Test
    void testResumeUploadHappyPath() throws IOException, InterruptedException {
        ChunkedUploadClient.UploadTransport transport = mock(ChunkedUploadClient.UploadTransport.class);
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .transport(transport)
                .build();
        int totalChunks = (int) Math.ceil((double) FILE_CONTENT.length / CHUNK_SIZE);
        InitResponse resp = new InitResponse("sessionId", totalChunks, CHUNK_SIZE, FILE_CONTENT.length, FILENAME);
        when(transport.initUpload(any(), any(), any())).thenReturn(resp);

        assertDoesNotThrow(() -> client.resumeUpload("sessionId", tempFile));
    }

    @Test
    void testResumeUploadChecksumMismatchThrows() throws IOException, InterruptedException {
        ChunkedUploadClient.UploadTransport transport = mock(ChunkedUploadClient.UploadTransport.class);
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
                .uploadUrl(UPLOAD_URL)
                .username(USERNAME)
                .password(PASSWORD)
                .transport(transport)
                .build();
        int totalChunks = (int) Math.ceil((double) FILE_CONTENT.length / CHUNK_SIZE);
        InitResponse resp = new InitResponse("sessionId", totalChunks, CHUNK_SIZE, FILE_CONTENT.length, FILENAME);
        resp.setChecksum("badchecksum");
        when(transport.initUpload(any(), any(), any())).thenReturn(resp);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> client.resumeUpload("sessionId", tempFile));
        assertTrue(ex.getMessage().contains("Checksum mismatch"));
    }
}
