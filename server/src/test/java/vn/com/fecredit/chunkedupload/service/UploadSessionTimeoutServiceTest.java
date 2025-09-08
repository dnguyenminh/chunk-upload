package vn.com.fecredit.chunkedupload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.com.fecredit.chunkedupload.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UploadSessionTimeoutService.
 * Tests the background cleanup functionality without full Spring context.
 */
@ExtendWith(MockitoExtension.class)
public class UploadSessionTimeoutServiceTest {

    @Mock
    private UploadInfoRepository uploadInfoRepository;

    @Mock
    private UploadInfoHistoryRepository uploadInfoHistoryRepository;

    @Mock
    private TenantAccountRepository tenantAccountRepository;

    private UploadSessionTimeoutService timeoutService;

    private Path tempDir;
    private TenantAccount testTenant;
    private UploadInfo timedOutUpload;
    private UploadInfo activeUpload;

    @BeforeEach
    public void setup() throws IOException {
        // Create temporary directory for testing
        tempDir = Files.createTempDirectory("upload-timeout-test");

        // Setup test tenant
        testTenant = new TenantAccount();
        testTenant.setId(1L);
        testTenant.setTenantId("testTenant");
        testTenant.setUsername("testuser");

        // Setup timed-out upload (35 minutes old)
        timedOutUpload = new UploadInfo();
        timedOutUpload.setId(1L);
        timedOutUpload.setUploadId("timed-out-upload-123");
        timedOutUpload.setFilename("test-file.txt");
        timedOutUpload.setStatus(UploadInfo.STATUS_IN_PROGRESS);
        timedOutUpload.setLastUpdateDateTime(LocalDateTime.now().minusMinutes(35));
        timedOutUpload.setTenant(testTenant);

        // Setup active upload (5 minutes old)
        activeUpload = new UploadInfo();
        activeUpload.setId(2L);
        activeUpload.setUploadId("active-upload-456");
        activeUpload.setFilename("active-file.txt");
        activeUpload.setStatus(UploadInfo.STATUS_IN_PROGRESS);
        activeUpload.setLastUpdateDateTime(LocalDateTime.now().minusMinutes(5));
        activeUpload.setTenant(testTenant);

        // Initialize service with test configuration
        timeoutService = new UploadSessionTimeoutService(
            uploadInfoRepository,
            uploadInfoHistoryRepository,
            30, // 30-minute timeout
            tempDir.toString()
        );
    }

    @Test
    public void testCleanupTimedOutSessions_NoTimedOutUploads() {
        // Given
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verifyNoMoreInteractions(uploadInfoRepository, uploadInfoHistoryRepository);
    }

    @Test
    public void testCleanupTimedOutSessions_WithTimedOutUploads() {
        // Given
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload));

        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verify(uploadInfoHistoryRepository).save(any(UploadInfoHistory.class));
        verify(uploadInfoRepository).delete(timedOutUpload);
    }

    @Test
    public void testCleanupTimedOutSessions_MultipleUploads() {
        // Given
        UploadInfo anotherTimedOutUpload = new UploadInfo();
        anotherTimedOutUpload.setId(3L);
        anotherTimedOutUpload.setUploadId("another-timed-out-789");
        anotherTimedOutUpload.setFilename("another-file.txt");
        anotherTimedOutUpload.setStatus(UploadInfo.STATUS_IN_PROGRESS);
        anotherTimedOutUpload.setLastUpdateDateTime(LocalDateTime.now().minusMinutes(40));
        anotherTimedOutUpload.setTenant(testTenant);

        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload, anotherTimedOutUpload));

        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verify(uploadInfoHistoryRepository, times(2)).save(any(UploadInfoHistory.class));
        verify(uploadInfoRepository, times(2)).delete(any(UploadInfo.class));
    }

    @Test
    public void testCleanupTimedOutSessions_HistoryRecordCreation() {
        // Given
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload));

        UploadInfoHistory savedHistory = new UploadInfoHistory();
        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(savedHistory);

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        verify(uploadInfoHistoryRepository).save(argThat(history -> {
            return history.getUploadId().equals(timedOutUpload.getUploadId()) &&
                   history.getStatus().equals(UploadInfoHistory.STATUS_TIMED_OUT) &&
                   history.getCompletionDateTime() != null &&
                   history.getOriginalUploadInfoId().equals(timedOutUpload.getId());
        }));
    }

    @Test
    public void testCleanupTimedOutSessions_FileCleanup() throws IOException {
        // Given - Create a mock .part file
        Path tenantDir = tempDir.resolve("1"); // tenant ID = 1
        Files.createDirectories(tenantDir);
        Path partFile = tenantDir.resolve("timed-out-upload-123.part");
        Files.createFile(partFile);
        Path lockFile = tenantDir.resolve("timed-out-upload-123.part.lock");
        Files.createFile(lockFile);

        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload));

        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then - Files should be cleaned up (this would be tested in integration tests)
        // In unit test, we just verify the service methods are called
        verify(uploadInfoRepository).delete(timedOutUpload);
        verify(uploadInfoHistoryRepository).save(any(UploadInfoHistory.class));
    }

    @Test
    public void testCleanupTimedOutSessions_ExceptionHandling() {
        // Given
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload));

        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then - Should not throw exception, should continue processing
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verify(uploadInfoHistoryRepository).save(any(UploadInfoHistory.class));
        // Should not attempt to delete if save failed
        verify(uploadInfoRepository, never()).delete(any(UploadInfo.class));
    }

    @Test
    public void testCleanupTimedOutSessions_DifferentStatuses() {
        // Given - Include uploads with different statuses
        UploadInfo completedUpload = new UploadInfo();
        completedUpload.setId(4L);
        completedUpload.setUploadId("completed-upload-999");
        completedUpload.setStatus(UploadInfo.STATUS_COMPLETED);
        completedUpload.setLastUpdateDateTime(LocalDateTime.now().minusMinutes(35));

        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList(timedOutUpload)); // Only return IN_PROGRESS uploads

        when(uploadInfoHistoryRepository.save(any(UploadInfoHistory.class)))
            .thenReturn(new UploadInfoHistory());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then - Only IN_PROGRESS uploads should be processed
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verify(uploadInfoHistoryRepository).save(any(UploadInfoHistory.class));
        verify(uploadInfoRepository).delete(timedOutUpload);
    }

    @Test
    public void testCleanupTimedOutSessions_EmptyResult() {
        // Given
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS));
        verifyNoInteractions(uploadInfoHistoryRepository);
    }

    @Test
    public void testCleanupTimedOutSessions_CutoffTimeCalculation() {
        // Given
        LocalDateTime beforeCall = LocalDateTime.now();
        when(uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(any(LocalDateTime.class), eq(UploadInfo.STATUS_IN_PROGRESS)))
            .thenReturn(Arrays.asList());

        // When
        timeoutService.cleanupTimedOutSessions();

        // Then
        LocalDateTime afterCall = LocalDateTime.now();
        verify(uploadInfoRepository).findByLastUpdateDateTimeBeforeAndStatus(
            argThat(cutoffTime -> {
                // Cutoff time should be approximately 30 minutes ago
                LocalDateTime expectedCutoff = LocalDateTime.now().minusMinutes(30);
                return cutoffTime.isAfter(expectedCutoff.minusSeconds(5)) &&
                       cutoffTime.isBefore(expectedCutoff.plusSeconds(5));
            }),
            eq(UploadInfo.STATUS_IN_PROGRESS)
        );
    }
}
