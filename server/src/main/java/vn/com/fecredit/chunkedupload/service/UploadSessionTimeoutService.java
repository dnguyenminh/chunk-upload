package vn.com.fecredit.chunkedupload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import vn.com.fecredit.chunkedupload.model.UploadInfo;
import vn.com.fecredit.chunkedupload.model.UploadInfoHistory;
import vn.com.fecredit.chunkedupload.model.UploadInfoHistoryRepository;
import vn.com.fecredit.chunkedupload.model.UploadInfoRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for managing upload session timeouts and cleanup.
 * Runs in the background every 5 minutes to check for timed-out uploads.
 */
@Service
public class UploadSessionTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionTimeoutService.class);

    private final UploadInfoRepository uploadInfoRepository;
    private final UploadInfoHistoryRepository uploadInfoHistoryRepository;
    private final int timeoutMinutes;
    private final Path inProgressDir;

    public UploadSessionTimeoutService(
            UploadInfoRepository uploadInfoRepository,
            UploadInfoHistoryRepository uploadInfoHistoryRepository,
            @Value("${chunkedupload.session-timeout-minutes:30}") int timeoutMinutes,
            @Value("${chunkedupload.inprogress-dir:uploads/in-progress}") String inProgressDirPath) {
        this.uploadInfoRepository = uploadInfoRepository;
        this.uploadInfoHistoryRepository = uploadInfoHistoryRepository;
        this.timeoutMinutes = timeoutMinutes;
        this.inProgressDir = Paths.get(inProgressDirPath);
        
        log.info("Upload session timeout service initialized with timeout: {} minutes", timeoutMinutes);
    }

    /**
     * Cleanup task that runs every 5 minutes to check for timed-out upload sessions.
     * Checks for part files and UploadInfo records that haven't been updated within the timeout period.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes = 300,000 milliseconds
    public void cleanupTimedOutSessions() {
        log.debug("Starting cleanup of timed-out upload sessions");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
            
            // Find all upload sessions that haven't been updated within the timeout period
            List<UploadInfo> timedOutUploads = uploadInfoRepository.findByLastUpdateDateTimeBeforeAndStatus(
                cutoffTime, UploadInfo.STATUS_IN_PROGRESS);
            
            log.info("Found {} timed-out upload sessions (older than {} minutes)", 
                    timedOutUploads.size(), timeoutMinutes);
            
            for (UploadInfo uploadInfo : timedOutUploads) {
                try {
                    cleanupTimedOutUpload(uploadInfo);
                } catch (Exception e) {
                    log.error("Failed to cleanup timed-out upload: uploadId={}, error={}", 
                            uploadInfo.getUploadId(), e.getMessage(), e);
                }
            }
            
            log.debug("Completed cleanup of timed-out upload sessions");
            
        } catch (Exception e) {
            log.error("Error during timed-out upload sessions cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleanup a single timed-out upload session.
     * 
     * @param uploadInfo The timed-out upload session to cleanup
     */
    private void cleanupTimedOutUpload(UploadInfo uploadInfo) {
        String uploadId = uploadInfo.getUploadId();
        
        try {
            // Move to history with TIMED_OUT status
            UploadInfoHistory history = UploadInfoHistory.fromUploadInfo(uploadInfo, UploadInfoHistory.STATUS_TIMED_OUT);
            uploadInfoHistoryRepository.save(history);
            
            // Delete the original upload info
            uploadInfoRepository.delete(uploadInfo);
            
            // Delete the part file if it exists
            deletePartFile(uploadInfo);
            
            log.info("Successfully cleaned up timed-out upload: uploadId={}", uploadId);
            
        } catch (Exception e) {
            log.error("Failed to cleanup timed-out upload: uploadId={}, error={}", uploadId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Delete the part file associated with a timed-out upload.
     * 
     * @param uploadInfo The upload info containing the details needed to locate the part file
     */
    private void deletePartFile(UploadInfo uploadInfo) {
        try {
            // Construct the part file path based on tenant ID and upload ID
            Long tenantId = uploadInfo.getTenant().getId();
            String uploadId = uploadInfo.getUploadId();
            Path partFilePath = inProgressDir.resolve(String.valueOf(tenantId)).resolve(uploadId + ".part");
            
            if (Files.exists(partFilePath)) {
                Files.delete(partFilePath);
                log.debug("Deleted part file: {}", partFilePath);
            } else {
                log.debug("Part file not found (already deleted): {}", partFilePath);
            }
            
            // Also try to delete any lock files
            Path lockFilePath = partFilePath.resolveSibling(partFilePath.getFileName() + ".lock");
            if (Files.exists(lockFilePath)) {
                Files.delete(lockFilePath);
                log.debug("Deleted lock file: {}", lockFilePath);
            }
            
        } catch (IOException e) {
            log.warn("Failed to delete part file for uploadId={}: {}", uploadInfo.getUploadId(), e.getMessage());
        }
    }
}
