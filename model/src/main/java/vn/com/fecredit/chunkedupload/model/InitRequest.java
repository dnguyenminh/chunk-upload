package vn.com.fecredit.chunkedupload.model;

/**
 * Request object for initializing or resuming a chunked upload.
 *
 * <p>
 * Contains all necessary information to:
 * <ul>
 * <li>Start a new upload session</li>
 * <li>Resume a broken upload</li>
 * <li>Validate file integrity</li>
 * </ul>
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class InitRequest {
    /**
     * ID of a previous broken upload to resume, if any.
     * Optional - only used when resuming an interrupted upload.
     */
    private String brokenUploadId;

    /**
     * SHA-256 checksum of the complete file.
     * Used for integrity validation during resume and completion.
     */
    @NotNull
    private String checksum;

    /**
     * Total size of the file in bytes.
     * Must be greater than 0.
     */
    @Positive
    private long fileSize;

    /**
     * Original name of the file being uploaded.
     * Required and must not be blank.
     */
    @NotBlank
    private String filename;


    /**
     * Gets the total file size in bytes.
     *
     * @return The total file size in bytes.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Sets the total file size in bytes.
     *
     * @param fileSize The total file size in bytes.
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets the filename being uploaded.
     *
     * @return The filename.
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the filename being uploaded.
     *
     * @param filename The filename.
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }

    /**
     * Gets the ID of a previous broken upload to resume.
     *
     * @return The broken upload ID, or null if this is a new upload
     */
    public String getBrokenUploadId() {
        return brokenUploadId;
    }

    /**
     * Sets the ID of a previous broken upload to resume.
     *
     * @param brokenUploadId The broken upload ID to resume from
     */
    public void setBrokenUploadId(String brokenUploadId) {
        this.brokenUploadId = brokenUploadId;
    }

    /**
     * Gets the SHA-256 checksum of the complete file.
     *
     * @return The file's SHA-256 checksum
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Sets the SHA-256 checksum of the complete file.
     *
     * @param checksum The file's SHA-256 checksum
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
