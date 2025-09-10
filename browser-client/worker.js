/**
 * A Web Worker dedicated to uploading a single file chunk.
 * This script runs in a separate background thread.
 */
self.onmessage = async (event) => {
    const {
        uploadUrl,
        username,
        password,
        uploadId,
        chunkNumber,
        chunk
    } = event.data;

    const formData = new FormData();
    formData.append('uploadId', uploadId);
    formData.append('chunkNumber', chunkNumber);
    formData.append('file', chunk, 'chunk.bin');

    try {
        const response = await fetch(uploadUrl + '/chunk', {
            method: 'POST',
            headers: {
                'Authorization': 'Basic ' + btoa(username + ':' + password)
            },
            body: formData
        });

        if (!response.ok) {
            throw new Error(`Worker failed to upload chunk ${chunkNumber}: ` + await response.text());
        }

        // Report success back to the main thread
        self.postMessage({ status: 'success', chunkNumber });
    } catch (error) {
        // Report failure back to the main thread
        self.postMessage({ status: 'error', chunkNumber, error: error.message });
    }
};