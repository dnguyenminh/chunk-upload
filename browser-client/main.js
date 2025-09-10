document.addEventListener('DOMContentLoaded', () => {
    const fileInput = document.getElementById('fileInput');
    const uploadButton = document.getElementById('uploadButton');
    const retryButton = document.getElementById('retryButton');
    const statusDiv = document.getElementById('status');
    const userSelect = document.getElementById('userSelect');
    const chunkGrid = document.getElementById('chunk-grid');
    const progressBar = document.getElementById('progress-bar');

    let uploadClient; // To hold the client instance for retries
    let successfulChunks = 0;

    /**
     * Renders the initial grid of chunk cells.
     */
    function renderChunkGrid(totalChunks) {
        chunkGrid.innerHTML = '';
        progressBar.max = totalChunks;
        progressBar.value = 0;
        successfulChunks = 0;
        for (let i = 0; i < totalChunks; i++) {
            const cell = document.createElement('div');
            cell.className = 'chunk-cell pending';
            cell.id = `chunk-${i}`;
            chunkGrid.appendChild(cell);
        }
    }

    /**
     * Fetches the list of users from the server and populates the dropdown.
     */
    async function loadUsers() {
        statusDiv.textContent = 'Loading users...';
        try {
            // ** THE FIX IS HERE: Corrected the API endpoint URL **
            const response = await fetch('http://localhost:8080/api/users', {
                headers: {
                    'Authorization': 'Basic ' + btoa('user1:password')
                }
            });

            if (!response.ok) {
                throw new Error('Could not fetch user list: ' + await response.text());
            }

            const users = await response.json();

            userSelect.innerHTML = ''; // Clear existing options
            users.forEach(user => {
                const option = document.createElement('option');
                option.value = user.username;
                option.textContent = user.username;
                userSelect.appendChild(option);
            });
            statusDiv.textContent = 'Please select a file to upload.';
        } catch (error) {
            statusDiv.textContent = 'Failed to load users: ' + error.message;
            statusDiv.style.color = 'red';
            console.error('Failed to load users:', error);
        }
    }

    uploadButton.addEventListener('click', async () => {
        const file = fileInput.files[0];
        if (!file) {
            statusDiv.textContent = 'Please select a file first.';
            statusDiv.style.color = 'red';
            return;
        }

        const selectedUsername = userSelect.value;
        if (!selectedUsername) {
            statusDiv.textContent = 'No user selected or user list failed to load.';
            statusDiv.style.color = 'red';
            return;
        }

        retryButton.style.display = 'none';
        chunkGrid.innerHTML = '';
        progressBar.style.display = 'block';

        const config = {
            file: file,
            uploadUrl: 'http://localhost:8080/api/upload',
            username: selectedUsername,
            password: 'password',
            onProgress: (message) => {
                statusDiv.textContent = message;
                statusDiv.style.color = 'black';
            },
            onTotalChunks: (totalChunks) => {
                renderChunkGrid(totalChunks);
            },
            onChunkStatusChange: (chunkNumber, status) => {
                const cell = document.getElementById(`chunk-${chunkNumber}`);
                if (cell) {
                    cell.className = `chunk-cell ${status}`;
                }
                if (status === 'success') {
                    successfulChunks++;
                    progressBar.value = successfulChunks;
                }
            },
            onRetryableFailure: () => {
                statusDiv.textContent = 'Some chunks failed to upload. Please retry.';
                statusDiv.style.color = 'red';
                retryButton.style.display = 'block';
            }
        };

        uploadClient = new ChunkedUploadClient(config);

        try {
            uploadButton.disabled = true;
            fileInput.disabled = true;
            userSelect.disabled = true;

            const uploadId = await uploadClient.start();
            statusDiv.textContent = `Upload successful! Upload ID: ${uploadId}`;
            statusDiv.style.color = 'green';
        } catch (error) {
            statusDiv.textContent = 'Upload failed: ' + error.message;
            statusDiv.style.color = 'red';
            console.error('Upload failed:', error);
        } finally {
            uploadButton.disabled = false;
            fileInput.disabled = false;
            userSelect.disabled = false;
        }
    });

    retryButton.addEventListener('click', async () => {
        if (!uploadClient) return;

        retryButton.style.display = 'none';
        try {
            uploadButton.disabled = true;
            fileInput.disabled = true;
            userSelect.disabled = true;

            const uploadId = await uploadClient.retry();
            statusDiv.textContent = `Upload successful! Upload ID: ${uploadId}`;
            statusDiv.style.color = 'green';
        } catch (error) {
            statusDiv.textContent = 'Upload failed again: ' + error.message;
            statusDiv.style.color = 'red';
            console.error('Retry failed:', error);
        } finally {
            uploadButton.disabled = false;
            fileInput.disabled = false;
            userSelect.disabled = false;
        }
    });

    // Load users on page load
    loadUsers();
});