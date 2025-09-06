package vn.com.fecredit.chunkedupload.port.intefaces;

import vn.com.fecredit.chunkedupload.model.impl.DeafultTenantAccount;
import vn.com.fecredit.chunkedupload.model.impl.DefaultUploadInfo;
import vn.com.fecredit.chunkedupload.model.interfaces.ITenantAccount;
import vn.com.fecredit.chunkedupload.model.interfaces.IUploadInfo;

import java.util.Optional;

/**
 * Port interface for interacting with the persistence layer for UploadInfo entities.
 * This defines the contract that the core application needs for data access,
 * regardless of the underlying database technology.
 */
public interface IUploadInfoPort<T extends IUploadInfo> {

    /**
     * Saves a new or updated UploadInfo record.
     *
     * @param uploadInfo The UploadInfo object to save.
     * @return The saved UploadInfo object.
     */
    T save(T uploadInfo);

    /**
     * Finds an UploadInfo record by its unique upload ID.
     *
     * @param uploadId The unique identifier for the upload.
     * @return An Optional containing the UploadInfo if found, otherwise empty.
     */
    Optional<T> findByUploadId(String uploadId);

    /**
     * Finds an UploadInfo record for a specific tenant and upload ID.
     *
     * @param tenant   The tenant account that owns the upload.
     * @param uploadId The unique identifier for the upload.
     * @return An Optional containing the UploadInfo if found, otherwise empty.
     */
    Optional<T> findByTenantAndUploadId(ITenantAccount tenant, String uploadId);

    /**
     * Deletes an UploadInfo record.
     *
     * @param uploadInfo The UploadInfo object to delete.
     */
    <Y extends IUploadInfo> void delete(Y uploadInfo);
}
