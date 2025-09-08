package vn.com.fecredit.chunkedupload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.fecredit.chunkedupload.model.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChunkedUploadService focusing on session management functionality.
 */
@ExtendWith(MockitoExtension.class)
public class ChunkedUploadServiceTest {

    @Mock
    private UploadInfoRepository uploadInfoRepository;

    @Mock
    private TenantAccountRepository tenantAccountRepository;

    @Mock
    private UploadInfoHistoryRepository uploadInfoHistoryRepository;

    private ChunkedUploadService chunkedUploadService;
    private Path tempDir;
    private TenantAccount testTenant;
    private UploadInfo testUploadInfo;


    @BeforeEach
    public void setup() throws IOException {
        // Create temporary directory for testing
        tempDir = Files.createTempDirectory("chunked-upload-test");

        // Setup test tenant
        testTenant = new TenantAccount();
        testTenant.setId(1L);
        testTenant.setTenantId("testTenant");
        testTenant.setUsername("testuser");

        // Setup test upload info
        testUploadInfo = new UploadInfo();
        testUploadInfo.setId(1L);
        testUploadInfo.setUploadId("test-upload-123");
        testUploadInfo.setFilename("test-file.txt");
        testUploadInfo.setChecksum("test-checksum");
        testUploadInfo.setStatus(UploadInfo.STATUS_IN_PROGRESS);
        testUploadInfo.setUploadDateTime(LocalDateTime.now());
        testUploadInfo.setLastUpdateDateTime(LocalDateTime.now());
        testUploadInfo.setTenant(testTenant);

        chunkedUploadService = new ChunkedUploadService(
            tempDir.resolve("in-progress").toString(),
            tempDir.resolve("complete").toString(),
            524288, // chunk size
            tenantAccountRepository,
            uploadInfoRepository,
            uploadInfoHistoryRepository
        );
    }

    @Test
    public void testUpdateLastUpdateDateTime() {
        // Given
        LocalDateTime beforeUpdate = LocalDateTime.now();

        // When
        chunkedUploadService.updateLastUpdateDateTime(testUploadInfo);

        // Then
        LocalDateTime afterUpdate = LocalDateTime.now();
        assertTrue(testUploadInfo.getLastUpdateDateTime().isAfter(beforeUpdate) ||
                  testUploadInfo.getLastUpdateDateTime().equals(beforeUpdate));
        assertTrue(testUploadInfo.getLastUpdateDateTime().isBefore(afterUpdate) ||
                  testUploadInfo.getLastUpdateDateTime().equals(afterUpdate));
    }

    @Test
    public void testMoveToHistory_Successful() {
        // Given
        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        // When
        assertDoesNotThrow(() -> chunkedUploadService.moveToHistory(testUploadInfo));

        // Then
        verify(uploadInfoHistoryRepository).save(argThat(history -> {
            return history.getUploadId().equals(testUploadInfo.getUploadId()) &&
                   history.getStatus().equals(UploadInfoHistory.STATUS_COMPLETED) &&
                   history.getCompletionDateTime() != null &&
                   history.getOriginalUploadInfoId().equals(testUploadInfo.getId()) &&
                   history.getTenant().equals(testUploadInfo.getTenant());
        }));
    }

    @Test
    public void testMoveToHistory_DatabaseError() {
        // Given
        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            chunkedUploadService.moveToHistory(testUploadInfo));

        assertEquals("Failed to move upload to history", exception.getMessage());
        assertTrue(exception.getCause().getMessage().contains("Database connection failed"));
    }

    @Test
    public void testCreateUploadInfo_WithTimeoutFields() throws Throwable {
        // Given
        when(tenantAccountRepository.findByUsername("testuser"))
            .thenReturn(Optional.of(testTenant));

        Header testHeader = new Header(1, 524288, 1000, new byte[1]);

        // When
        UploadInfo createdUploadInfo = chunkedUploadService.createUploadInfo(
            "testuser", "test-upload-123", testHeader, "test-file.txt", "test-checksum");

        // Then
        assertNotNull(createdUploadInfo);
        assertEquals("test-upload-123", createdUploadInfo.getUploadId());
        assertEquals("test-file.txt", createdUploadInfo.getFilename());
        assertEquals("test-checksum", createdUploadInfo.getChecksum());
        assertEquals(UploadInfo.STATUS_IN_PROGRESS, createdUploadInfo.getStatus());
        assertNotNull(createdUploadInfo.getUploadDateTime());
        assertNotNull(createdUploadInfo.getLastUpdateDateTime());
        assertEquals(testTenant, createdUploadInfo.getTenant());

        // Verify the dates are set correctly
        assertTrue(createdUploadInfo.getLastUpdateDateTime().isAfter(
            createdUploadInfo.getUploadDateTime().minusSeconds(1)));
        assertTrue(createdUploadInfo.getLastUpdateDateTime().isBefore(
            createdUploadInfo.getUploadDateTime().plusSeconds(1)));
    }

    @Test
    public void testCreateUploadInfo_TenantNotFound() {
        // Given
        when(tenantAccountRepository.findByUsername("nonexistent"))
            .thenReturn(Optional.empty());

        Header testHeader = new Header(1, 524288, 1000, new byte[1]);

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            chunkedUploadService.createUploadInfo(
                "nonexistent", "test-upload-123", testHeader, "test-file.txt", "test-checksum"));

        assertTrue(exception.getMessage().contains("Tenant not found for username: nonexistent"));
    }

    @Test
    public void testUpdateUploadInfoLastUpdateTime_Successful() {
        // Given
        when(uploadInfoRepository.findByUploadId("test-upload-123"))
            .thenReturn(Optional.of(testUploadInfo));

        LocalDateTime beforeUpdate = testUploadInfo.getLastUpdateDateTime();

        // When
        chunkedUploadService.updateUploadInfoLastUpdateTime("test-upload-123");

        // Then
        verify(uploadInfoRepository).findByUploadId("test-upload-123");
        verify(uploadInfoRepository).save(testUploadInfo);

        // Verify lastUpdateDateTime was updated
        assertTrue(testUploadInfo.getLastUpdateDateTime().isAfter(beforeUpdate));
    }

    @Test
    public void testUpdateUploadInfoLastUpdateTime_UploadNotFound() {
        // Given
        when(uploadInfoRepository.findByUploadId("nonexistent"))
            .thenReturn(Optional.empty());

        // When
        chunkedUploadService.updateUploadInfoLastUpdateTime("nonexistent");

        // Then
        verify(uploadInfoRepository).findByUploadId("nonexistent");
        verify(uploadInfoRepository, never()).save(any(UploadInfo.class));
    }

    @Test
    public void testUpdateUploadInfoLastUpdateTime_SaveError() {
        // Given
        when(uploadInfoRepository.findByUploadId("test-upload-123"))
            .thenReturn(Optional.of(testUploadInfo));

        doThrow(new RuntimeException("Save failed")).when(uploadInfoRepository).save(testUploadInfo);

        // When
        chunkedUploadService.updateUploadInfoLastUpdateTime("test-upload-123");

        // Then
        verify(uploadInfoRepository).findByUploadId("test-upload-123");
        verify(uploadInfoRepository).save(testUploadInfo);
        // Should not throw exception - error is logged but not propagated
    }

    @Test
    public void testMoveToHistory_HistoryRecordFields() {
        // Given
        UploadInfoHistory savedHistory = new UploadInfoHistory();
        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(savedHistory);

        // When
        chunkedUploadService.moveToHistory(testUploadInfo);

        // Then
        verify(uploadInfoHistoryRepository).save(argThat(history -> {
            return history.getUploadId().equals(testUploadInfo.getUploadId()) &&
                   history.getChecksum().equals(testUploadInfo.getChecksum()) &&
                   history.getFilename().equals(testUploadInfo.getFilename()) &&
                   history.getUploadDateTime().equals(testUploadInfo.getUploadDateTime()) &&
                   history.getLastUpdateDateTime().equals(testUploadInfo.getLastUpdateDateTime()) &&
                   history.getStatus().equals(UploadInfoHistory.STATUS_COMPLETED) &&
                   history.getCompletionDateTime() != null &&
                   history.getOriginalUploadInfoId().equals(testUploadInfo.getId()) &&
                   history.getTenant().equals(testUploadInfo.getTenant());
        }));
    }

    @Test
    public void testSessionManagementIntegration() throws Throwable {
        // Given
        when(tenantAccountRepository.findByUsername("testuser"))
            .thenReturn(Optional.of(testTenant));

        Header testHeader = new Header(1, 524288, 1000, new byte[1]);

        // When - Create upload info
        UploadInfo createdUploadInfo = chunkedUploadService.createUploadInfo(
            "testuser", "test-upload-123", testHeader, "test-file.txt", "test-checksum");

        // Update last update time (simulating chunk upload)
        LocalDateTime beforeUpdate = createdUploadInfo.getLastUpdateDateTime();
        Thread.sleep(1); // Ensure clock tick for timestamp comparison
        chunkedUploadService.updateLastUpdateDateTime(createdUploadInfo);

        // Move to history (simulating completion)
        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        chunkedUploadService.moveToHistory(createdUploadInfo);

        // Then - Verify the complete session lifecycle
        assertEquals(UploadInfo.STATUS_COMPLETED, createdUploadInfo.getStatus());
        assertTrue(createdUploadInfo.getLastUpdateDateTime().isAfter(beforeUpdate));

        verify(uploadInfoHistoryRepository).save(argThat(history ->
            history.getStatus().equals(UploadInfoHistory.STATUS_COMPLETED) &&
            history.getCompletionDateTime() != null
        ));
    }
}
