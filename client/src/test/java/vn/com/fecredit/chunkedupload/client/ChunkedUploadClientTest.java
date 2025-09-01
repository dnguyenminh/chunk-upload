package vn.com.fecredit.chunkedupload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.fecredit.chunkedupload.model.InitResponse;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ChunkedUploadClientTest {

    private static final String UPLOAD_URL = "http://localhost:8080";
    private static final String FILENAME = "test-file.txt";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "password";
    private static final int RETRY_TIMES = 2;
    private static final byte[] FILE_CONTENT = new byte[524288 * 2]; // 2 chunks of 524288 bytes

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    // Removed unused field: httpRequestCaptor

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(httpResponse);
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
        int chunkSize = 10;
        long fileSize = FILE_CONTENT.length;
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        String initResponseBody = String.format(
            "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"%s\",\"status\":\"INIT\"}",
            sessionId, totalChunks, chunkSize, fileSize, FILENAME
        );

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(initResponseBody).thenReturn("{\"status\":\"OK\"}");

        client.upload(FILE_CONTENT, FILENAME, null, null);

        int expectedCalls = 1 + (int) Math.ceil((double) FILE_CONTENT.length / 10);
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

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));

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
        int chunkSize = 10;
        long fileSize = FILE_CONTENT.length;
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        String initResponseBody = String.format(
            "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"%s\",\"status\":\"INIT\"}",
            sessionId, totalChunks, chunkSize, fileSize, FILENAME
        );

        when(httpResponse.statusCode()).thenReturn(200).thenReturn(500);
        when(httpResponse.body()).thenReturn(initResponseBody).thenReturn("Chunk Error");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));

        assertTrue(exception.getMessage().contains("Failed to upload chunkNumber "));
        verify(httpClient, times(1 + 1 + 1 + RETRY_TIMES)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
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
        int chunkSize = 524288;
        long fileSize = 1048576;
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        String initResponseBody = String.format(
            "{\"uploadId\":\"%s\",\"totalChunks\":%d,\"chunkSize\":%d,\"fileSize\":%d,\"fileName\":\"test-file.txt\",\"status\":\"INIT\"}",
            sessionId, totalChunks, chunkSize, fileSize
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

        // Use file content at least 2 * chunkSize
        byte[] bigFileContent = new byte[524288 * 2];
        new java.util.Random().nextBytes(bigFileContent);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(bigFileContent, FILENAME, null, null));

        assertTrue(exception.getMessage().contains("Failed to upload chunkNumber 1"));
    }

    @Test
    void testUploadWithEmptyFileContentThrows() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
            .uploadUrl(UPLOAD_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .build();
        assertThrows(IllegalArgumentException.class, () -> client.upload(new byte[0], FILENAME, null, null));
    }

    @Test
    void testUploadWithNullFilenameThrows() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
            .uploadUrl(UPLOAD_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .build();
        assertThrows(IllegalArgumentException.class, () -> client.upload(FILE_CONTENT, null, null, null));
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
        int chunkSize = 10;
        int totalChunks = (int) Math.ceil((double) FILE_CONTENT.length / chunkSize);
        InitResponse resp = new InitResponse("sessionId", totalChunks, chunkSize, FILE_CONTENT.length, FILENAME);
        when(transport.initUpload(any(), any(), any())).thenReturn(resp);

        assertDoesNotThrow(() -> client.resumeUpload("sessionId", FILE_CONTENT));
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
        int chunkSize = 10;
        int totalChunks = (int) Math.ceil((double) FILE_CONTENT.length / chunkSize);
        InitResponse resp = new InitResponse("sessionId", totalChunks, chunkSize, FILE_CONTENT.length, FILENAME);
        resp.setChecksum("badchecksum");
        when(transport.initUpload(any(), any(), any())).thenReturn(resp);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> client.resumeUpload("sessionId", FILE_CONTENT));
        assertTrue(ex.getMessage().contains("Checksum mismatch"));
    }
}
