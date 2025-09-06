package vn.com.fecredit.chunkedupload.model.interfaces;

import java.time.LocalDateTime;

public interface IUploadInfo {
    String getUploadId();

    void setUploadId(String uploadId);

    String getChecksum();

    void setChecksum(String checksum);

    String getFilename();

    void setFilename(String filename);

    LocalDateTime getUploadDateTime();

    void setUploadDateTime(LocalDateTime uploadDateTime);
}
