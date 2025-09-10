/**
 * Browser-Native Chunked Upload Client with Concurrent Workers and Retry Logic.
 * 
 * This class manages the state of a file upload, allowing for concurrent chunk
 * uploads and the ability to retry only the chunks that failed.
 */
class ChunkedUploadClient {
    constructor(config) {
        this.config = config;
        this.chunks = [];
        this.uploadId = null;
        this.workerPool = [];
        this.workerCount = navigator.hardwareConcurrency || 4;
    }

    /**
     * Calculates the SHA-256 checksum of the file.
     */
    async _calculateChecksum() {
        this.config.onProgress('Calculating checksum...');
        const buffer = await this.config.file.arrayBuffer();
        const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        const checksum = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
        this.config.onProgress('Checksum calculated.');
        return checksum;
    }

    /**
     * Initializes the upload with the server.
     */
    async _initUpload(checksum) {
        this.config.onProgress('Initializing upload...');
        const initRequest = {
            filename: this.config.file.name,
            fileSize: this.config.file.size,
            checksum: checksum
        };

        const response = await fetch(this.config.uploadUrl + '/init', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Basic ' + btoa(this.config.username + ':' + this.config.password)
            },
            body: JSON.stringify(initRequest)
        });

        if (!response.ok) {
            throw new Error('Failed to initialize upload: ' + await response.text());
        }

        return await response.json();
    }

    /**
     * Starts a new upload process.
     */
    async start() {
        const checksum = await this._calculateChecksum();
        const initData = await this._initUpload(checksum);

        this.uploadId = initData.uploadId;
        const { chunkSize, totalChunks } = initData;

        // Create the initial list of chunk objects
        this.chunks = [];
        for (let i = 0; i < totalChunks; i++) {
            const offset = i * chunkSize;
            const chunkData = this.config.file.slice(offset, offset + chunkSize);
            this.chunks.push({ number: i, data: chunkData, status: 'pending' });
        }

        this.config.onTotalChunks(totalChunks);
        return this._runUpload(this.chunks);
    }

    /**
     * Retries uploading only the chunks that previously failed.
     */
    retry() {
        const failedChunks = this.chunks.filter(c => c.status === 'error');
        if (failedChunks.length === 0) {
            return Promise.resolve(this.uploadId);
        }

        this.config.onProgress(`Retrying ${failedChunks.length} failed chunks...`);
        failedChunks.forEach(chunk => {
            this.chunks[chunk.number].status = 'pending';
            this.config.onChunkStatusChange(chunk.number, 'pending');
        });

        return this._runUpload(failedChunks);
    }

    /**
     * The core logic for running an upload process with a worker pool.
     * @param {Array<object>} tasks - The list of chunk tasks to process.
     * @private
     */
    _runUpload(tasks) {
        return new Promise((resolve, reject) => {
            const taskQueue = [...tasks];
            let activeWorkers = 0;
            let completedTasks = 0;
            let hasFailed = false;

            const dispatchTask = (worker) => {
                if (hasFailed || taskQueue.length === 0) {
                    if (--activeWorkers === 0 && !hasFailed) {
                        const anyFailed = this.chunks.some(c => c.status === 'error');
                        if (anyFailed) {
                            this.config.onRetryableFailure();
                            reject(new Error('Some chunks failed to upload.'));
                        } else {
                            resolve(this.uploadId);
                        }
                    }
                    return;
                }

                const task = taskQueue.shift();
                this.chunks[task.number].status = 'sending';
                this.config.onChunkStatusChange(task.number, 'sending');

                worker.postMessage({
                    uploadUrl: this.config.uploadUrl,
                    username: this.config.username,
                    password: this.config.password,
                    uploadId: this.uploadId,
                    chunkNumber: task.number,
                    chunk: task.data
                });
            };

            // Create and manage the worker pool
            this.workerPool = [];
            for (let i = 0; i < this.workerCount; i++) {
                const worker = new Worker('worker.js');
                this.workerPool.push(worker);
                activeWorkers++;

                worker.onmessage = (event) => {
                    const { status, chunkNumber } = event.data;
                    this.chunks[chunkNumber].status = status;
                    this.config.onChunkStatusChange(chunkNumber, status);

                    if (status === 'error') {
                        hasFailed = true;
                    }

                    completedTasks++;
                    dispatchTask(worker);
                };

                worker.onerror = (error) => {
                    hasFailed = true;
                    reject(new Error(`A worker encountered an error: ${error.message}`));
                };
            }

            // Start the process
            this.workerPool.forEach(worker => dispatchTask(worker));
        });
    }
}