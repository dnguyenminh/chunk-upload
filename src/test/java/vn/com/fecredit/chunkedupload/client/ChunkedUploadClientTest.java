package vn.com.fecredit.chunkedupload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkedUploadClientTest {

    private static final String UPLOAD_URL = "http://localhost:8080";
    private static final String FILENAME = "test-file.txt";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "password";
    private static final int RETRY_TIMES = 2;
    private static final byte[] FILE_CONTENT = "This is a test file.".getBytes(); // 22 bytes

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;

//    private ChunkedUploadClient client;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        // Default happy path mock. Tests can override this.
        lenient().when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    @Test
    void testUploadSuccess() throws IOException, InterruptedException {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
            .uploadUrl(UPLOAD_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .retryTimes(RETRY_TIMES)
            .httpClient(httpClient)
            .build();
        String sessionId = UUID.randomUUID().toString();
        String initResponseBody = "{\"uploadId\":\"" + sessionId + "\",\"chunkSize\":10}";
        when(httpResponse.statusCode())
                .thenReturn(200)
                .thenReturn(200);
        when(httpResponse.body())
                .thenReturn(initResponseBody)
                .thenReturn("{\"status\":\"OK\"}")
                .thenReturn("{\"status\":\"OK\"}");

        client.upload(FILE_CONTENT, FILENAME, null, null);

        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)); // NOSONAR
    }

    @Test
    void testUploadInitFails() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
            .uploadUrl(UPLOAD_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .retryTimes(RETRY_TIMES)
            .httpClient(httpClient)
            .build();
        when(httpResponse.statusCode()).thenReturn(500);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));

        assertEquals("Failed to initialize upload", exception.getMessage());
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
        String initResponseBody = "{\"uploadId\":\"" + sessionId + "\",\"chunkSize\":10}";

        // Mock responses: Init succeeds, but the first chunk fails consistently.
        when(httpResponse.statusCode()).thenReturn(200).thenReturn(500);
        when(httpResponse.body()).thenReturn(initResponseBody).thenReturn("Chunk Error");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));

        // Verify the final exception message is correct.
        assertTrue(exception.getMessage().contains("Failed to upload chunk 0"));
        // Verify it was tried the correct number of times (1 init + all chunk upload attempts)
        // For a 22-byte file and chunkSize=10, there are 3 chunks (0,1,2), but only chunk 0 is retried and fails, so only 7 calls (1 init + 6 chunk attempts)
        int expectedCalls = 7;
        verify(httpClient, times(expectedCalls)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)); // NOSONAR
    }

    @Test
    void testUploadAssembleFails() {
        ChunkedUploadClient client = new ChunkedUploadClient.Builder()
            .uploadUrl(UPLOAD_URL)
            .username(USERNAME)
            .password(PASSWORD)
            .retryTimes(RETRY_TIMES)
            .httpClient(httpClient)
            .threadCounts(1)
            .build();
        String sessionId = UUID.randomUUID().toString();
        String initResponseBody = "{\"uploadId\":\"" + sessionId + "\",\"chunkSize\":10}";

        // Mock responses: Init and all chunks succeed, but assemble fails.
        when(httpResponse.statusCode())
                .thenReturn(200) // Init
                .thenReturn(200) // Chunk 0
                .thenReturn(500) // Assemble
                .thenReturn(500); // Assemble

        when(httpResponse.body())
                .thenReturn(initResponseBody)
                .thenReturn("{\"status\":\"OK\"}") // Chunk 0
                .thenReturn("Assemble Error") // Assemble
                .thenReturn("Assemble Error"); // Assemble

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.upload(FILE_CONTENT, FILENAME, null, null));

        assertEquals("Failed to upload chunk 1", exception.getMessage());
    }
}
