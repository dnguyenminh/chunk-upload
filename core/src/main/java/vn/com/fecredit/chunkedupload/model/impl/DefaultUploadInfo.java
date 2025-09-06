package vn.com.fecredit.chunkedupload.model;

import java.time.LocalDateTime;

public class UploadInfo implements IUploadInfo {

    //    private Long id;
    private String uploadId;
    private String checksum;
    private String filename;
    private LocalDateTime uploadDateTime;
//    private TenantAccount tenant;

    @Override
    public String getUploadId() {
        return uploadId;
    }

    @Override
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    @Override
    public String getChecksum() {
        return checksum;
    }

    @Override
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Override
    public LocalDateTime getUploadDateTime() {
        return uploadDateTime;
    }

    @Override
    public void setUploadDateTime(LocalDateTime uploadDateTime) {
        this.uploadDateTime = uploadDateTime;
    }
}
