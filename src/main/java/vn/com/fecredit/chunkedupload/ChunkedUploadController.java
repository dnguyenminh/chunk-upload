package vn.com.fecredit.chunkedupload;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@RestController
@RequestMapping("/api/upload")
public class ChunkedUploadController {

    private static final int HEADER_MAGIC = 0xCAFECAFE; // 4 bytes
    private static final int FIXED_HEADER_SIZE = 4 /*magic*/ + 4 /*totalChunks*/ + 4 /*chunkSize*/ + 8 /*fileSize*/;

    private final Path inProgressDir = Paths.get("uploads/in-progress");
    private final Path completeDir = Paths.get("uploads/complete");

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, ScheduledFuture<?>>> reservations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    // Store filename for each uploadId
    private final ConcurrentHashMap<String, String> uploadFilenames = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

    private static final Duration DEFAULT_RESERVATION_TTL = Duration.ofSeconds(60);

    public ChunkedUploadController() throws IOException {
        Files.createDirectories(inProgressDir);
        Files.createDirectories(completeDir);
    }

    @PostMapping("/init")
    public InitResponse initUpload(@RequestBody InitRequest req) throws IOException {
        Objects.requireNonNull(req, "InitRequest required");
        if (req.totalChunks <= 0 || req.chunkSize <= 0 || req.fileSize <= 0 || !StringUtils.hasText(req.filename)) {
            throw new IllegalArgumentException("totalChunks, chunkSize, fileSize, filename must be > 0 and filename must be provided");
        }
        String uploadId = StringUtils.hasText(req.uploadId) ? req.uploadId : newUploadId();
        Path partPath = inProgressDir.resolve(uploadId + ".part");

        // Store filename for this uploadId
        uploadFilenames.put(uploadId, req.filename);

        ReentrantLock lock = locks.computeIfAbsent(uploadId, k -> new ReentrantLock());
        lock.lock();
        try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw");
             FileChannel ch = raf.getChannel()) {

            int bitsetBytes = bitsetBytes(req.totalChunks);
            int headerSize = FIXED_HEADER_SIZE + bitsetBytes;

            if (ch.size() == 0) {
                ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN);
                header.putInt(HEADER_MAGIC);
                header.putInt(req.totalChunks);
                header.putInt(req.chunkSize);
                header.putLong(req.fileSize);
                header.put(new byte[bitsetBytes]);
                header.flip();
                ch.write(header, 0);
                raf.setLength(headerSize + (long) req.totalChunks * req.chunkSize);
            } else {
                Header h = readHeader(ch);
                if (h.totalChunks != req.totalChunks || h.chunkSize != req.chunkSize || h.fileSize != req.fileSize) {
                    throw new IllegalStateException("Existing upload file has different parameters");
                }
            }

            Duration ttl = computeAdaptiveTtl(req.fileSize);
            scheduleOrRefreshSession(uploadId, ttl);
        } finally {
            lock.unlock();
        }

        return new InitResponse(uploadId, req.totalChunks, req.chunkSize, req.fileSize, req.filename);
    }

    @GetMapping("/{uploadId}/next")
    public ResponseEntity<?> reserveNext(@PathVariable String uploadId) throws IOException {
        Path partPath = inProgressDir.resolve(uploadId + ".part");
        if (!Files.exists(partPath)) return ResponseEntity.notFound().build();

        ReentrantLock lock = locks.computeIfAbsent(uploadId, k -> new ReentrantLock());
        lock.lock();
        try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw");
             FileChannel ch = raf.getChannel()) {
            Header h = readHeader(ch);

            Duration ttl = computeAdaptiveTtl(h.fileSize);
            scheduleOrRefreshSession(uploadId, ttl);

            Integer next = findNextUnreservedChunk(uploadId, h);
            if (next == null) return ResponseEntity.ok(new NextResponse(null));

            reserveChunk(uploadId, next, DEFAULT_RESERVATION_TTL);
            return ResponseEntity.ok(new NextResponse(next));
        } finally {
            lock.unlock();
        }
    }

    @PostMapping("/chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkNumber,
            @RequestParam int totalChunks,
            @RequestParam int chunkSize,
            @RequestParam long fileSize,
            @RequestParam String chunkChecksum,
            @RequestParam(required = false) String fileChecksum,
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        byte[] data = file.getBytes();
        String calc = sha256Base64(data);
        if (!calc.equalsIgnoreCase(chunkChecksum)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Checksum mismatch for chunk " + chunkNumber));
        }
        if (chunkNumber < 1 || totalChunks < chunkNumber) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid chunk number"));
        }

        Path partPath = inProgressDir.resolve(uploadId + ".part");
        if (!Files.exists(partPath)) return ResponseEntity.notFound().build();

        ReentrantLock lock = locks.computeIfAbsent(uploadId, k -> new ReentrantLock());
        lock.lock();
        try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "rw");
             FileChannel ch = raf.getChannel()) {

            Header h = readHeader(ch);
            if (h.totalChunks != totalChunks || h.chunkSize != chunkSize || h.fileSize != fileSize) {
                return ResponseEntity.badRequest().body(Map.of("error", "Upload parameters mismatch with existing header"));
            }

            Duration ttl = computeAdaptiveTtl(h.fileSize);
            scheduleOrRefreshSession(uploadId, ttl);

            int idx = chunkNumber - 1;
            boolean alreadyPresent = bitsetGet(h.bitset, idx);

            Integer nextUnreservedBefore = findNextUnreservedChunk(uploadId, h);

            if (alreadyPresent) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Chunk already uploaded",
                        "nextChunk", nextUnreservedBefore
                ));
            }

            long headerSize = FIXED_HEADER_SIZE + h.bitset.length;
            long offset = headerSize + (long) idx * h.chunkSize;
            ch.position(offset);
            ch.write(ByteBuffer.wrap(data));

            bitsetSet(h.bitset, idx);
            writeBitset(ch, h.bitset);

            cancelReservation(uploadId, chunkNumber);

            Integer next = findNextUnreservedChunk(uploadId, h);
            if (next == null) {
                // Use the original filename from init
                String originalFilename = uploadFilenames.get(uploadId);
                Path finalPath = completeDir.resolve(uploadId + (StringUtils.hasText(originalFilename) ? ("_" + originalFilename) : ""));
                try (FileChannel src = FileChannel.open(partPath, StandardOpenOption.READ);
                     FileChannel dst = FileChannel.open(finalPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    src.transferTo(headerSize, h.fileSize, dst);
                }
                if (StringUtils.hasText(fileChecksum)) {
                    String finalHash = sha256Base64(Files.readAllBytes(finalPath));
                    if (!finalHash.equalsIgnoreCase(fileChecksum)) {
                        Files.deleteIfExists(finalPath);
                        return ResponseEntity.badRequest().body(Map.of("error", "Final checksum mismatch"));
                    }
                }
                // Ensure all file handles are closed before deleting
                System.gc(); // Hint GC to release file locks on Windows
                boolean deleted = false;
                int attempts = 0;
                while (!deleted && attempts < 10) {
                    try {
                        Files.deleteIfExists(partPath);
                        deleted = true;
                    } catch (java.nio.file.FileSystemException e) {
                        attempts++;
                        Thread.sleep(50); // Wait 50ms before retry
                    }
                }
                clearReservations(uploadId);
                cancelSession(uploadId);
                locks.remove(uploadId);
                uploadFilenames.remove(uploadId); // Clean up filename map
                // Use HashMap to allow null values (Map.of does not allow nulls)
                Map<String, Object> result = new HashMap<>();
                result.put("status", "completed");
                result.put("nextChunk", null);
                result.put("finalPath", finalPath.toString());
                result.put("filename", originalFilename);
                return ResponseEntity.ok(result);
            }

            return ResponseEntity.ok(Map.of("status", "ok", "nextChunk", next));

        } finally {
            lock.unlock();
        }
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String uploadId) throws IOException {
        Path partPath = inProgressDir.resolve(uploadId + ".part");
        if (!Files.exists(partPath)) {
            return ResponseEntity.notFound().build();
        }
        try (RandomAccessFile raf = new RandomAccessFile(partPath.toFile(), "r");
             FileChannel ch = raf.getChannel()) {
            Header h = readHeader(ch);
            StatusResponse resp = new StatusResponse();
            resp.uploadId = uploadId;
            resp.totalChunks = h.totalChunks;
            resp.chunkSize = h.chunkSize;
            resp.fileSize = h.fileSize;
            resp.receivedCount = countBits(h.bitset, h.totalChunks);
            resp.completed = resp.receivedCount == h.totalChunks;
            resp.receivedRanges = toRanges(h.bitset, h.totalChunks);
            resp.missingRanges = invertRanges(resp.receivedRanges, h.totalChunks);
            resp.nextMissing = findFirstMissing(h.bitset, h.totalChunks);
            resp.reserved = getReservedList(uploadId);

            UploadSession s = sessions.get(uploadId);
            resp.sessionTtlSeconds = s == null ? null : s.getRemainingTtlSeconds();

            return ResponseEntity.ok(resp);
        }
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<?> abort(@PathVariable String uploadId) throws IOException {
        Path partPath = inProgressDir.resolve(uploadId + ".part");
        Files.deleteIfExists(partPath);
        clearReservations(uploadId);
        cancelSession(uploadId);
        locks.remove(uploadId);
        uploadFilenames.remove(uploadId); // Clean up filename map
        return ResponseEntity.noContent().build();
    }

    private Integer findNextUnreservedChunk(String uploadId, Header h) {
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.computeIfAbsent(uploadId, k -> new ConcurrentHashMap<>());
        for (int i = 0; i < h.totalChunks; i++) {
            if (!bitsetGet(h.bitset, i) && !map.containsKey(i + 1)) {
                return i + 1;
            }
        }
        return null;
    }

    private void reserveChunk(String uploadId, int chunkNumber, Duration ttl) {
        reservations.computeIfAbsent(uploadId, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.get(uploadId);
        map.compute(chunkNumber, (k, existing) -> {
            if (existing != null && !existing.isDone()) return existing;
            ScheduledFuture<?> sf = scheduler.schedule(() -> releaseReservation(uploadId, chunkNumber), ttl.toMillis(), TimeUnit.MILLISECONDS);
            return sf;
        });
    }

    private void releaseReservation(String uploadId, int chunkNumber) {
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.get(uploadId);
        if (map != null) map.remove(chunkNumber);
    }

    private void cancelReservation(String uploadId, int chunkNumber) {
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.get(uploadId);
        if (map != null) {
            ScheduledFuture<?> sf = map.remove(chunkNumber);
            if (sf != null) sf.cancel(false);
        }
    }

    private void clearReservations(String uploadId) {
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.remove(uploadId);
        if (map != null) {
            for (ScheduledFuture<?> sf : map.values()) if (sf != null) sf.cancel(false);
        }
    }

    private List<Integer> getReservedList(String uploadId) {
        ConcurrentHashMap<Integer, ScheduledFuture<?>> map = reservations.get(uploadId);
        if (map == null) return Collections.emptyList();
        return new ArrayList<>(map.keySet());
    }

    private void scheduleOrRefreshSession(String uploadId, Duration ttl) {
        sessions.compute(uploadId, (k, existing) -> {
            if (existing != null) {
                existing.refresh(ttl);
                return existing;
            } else {
                UploadSession s = new UploadSession(uploadId, ttl);
                s.scheduleCleanup();
                return s;
            }
        });
    }

    private void cancelSession(String uploadId) {
        UploadSession s = sessions.remove(uploadId);
        if (s != null) s.cancel();
    }

    private Duration computeAdaptiveTtl(long fileSizeBytes) {
        long MB = 1024L * 1024L;
        if (fileSizeBytes <= 100 * MB) return Duration.ofMinutes(15);
        if (fileSizeBytes <= 1024 * MB) return Duration.ofMinutes(60);
        if (fileSizeBytes <= 5L * 1024 * MB) return Duration.ofHours(3);
        return Duration.ofHours(6);
    }

    private class UploadSession {
        private final String uploadId;
        private volatile Instant expiry;
        private volatile ScheduledFuture<?> cleanupTask;
        private volatile Duration ttl;

        UploadSession(String uploadId, Duration ttl) {
            this.uploadId = uploadId;
            this.ttl = ttl;
            this.expiry = Instant.now().plus(ttl);
        }

        synchronized void scheduleCleanup() {
            if (cleanupTask != null && !cleanupTask.isDone()) cleanupTask.cancel(false);
            cleanupTask = scheduler.schedule(() -> onSessionExpired(), ttl.toMillis(), TimeUnit.MILLISECONDS);
            this.expiry = Instant.now().plus(ttl);
        }

        synchronized void refresh(Duration newTtl) {
            this.ttl = newTtl.compareTo(this.ttl) > 0 ? newTtl : this.ttl;
            scheduleCleanup();
        }

        synchronized void cancel() {
            if (cleanupTask != null) cleanupTask.cancel(false);
            cleanupTask = null;
        }

        long getRemainingTtlSeconds() {
            return Math.max(0, Duration.between(Instant.now(), expiry).getSeconds());
        }

        private void onSessionExpired() {
            try {
                Path partPath = inProgressDir.resolve(uploadId + ".part");
                try { Files.deleteIfExists(partPath); } catch (Exception ignored) {}
                clearReservations(uploadId);
                locks.remove(uploadId);
            } finally {
                sessions.remove(uploadId);
            }
        }
    }

    private static String newUploadId() {
        byte[] buf = new byte[12];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static int bitsetBytes(int totalChunks) {
        return (totalChunks + 7) / 8;
    }

    private static Header readHeader(FileChannel ch) throws IOException {
        ByteBuffer fixed = ByteBuffer.allocate(FIXED_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        ch.read(fixed, 0);
        fixed.flip();
        int magic = fixed.getInt();
        if (magic != HEADER_MAGIC) throw new IOException("Bad magic in upload file header");
        int totalChunks = fixed.getInt();
        int chunkSize = fixed.getInt();
        long fileSize = fixed.getLong();
        int bitsetBytes = bitsetBytes(totalChunks);
        ByteBuffer bits = ByteBuffer.allocate(bitsetBytes);
        ch.read(bits, FIXED_HEADER_SIZE);
        return new Header(totalChunks, chunkSize, fileSize, bits.array());
    }

    private static void writeBitset(FileChannel ch, byte[] bitset) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bitset);
        ch.write(buf, FIXED_HEADER_SIZE);
    }

    private static boolean bitsetGet(byte[] bits, int idx) {
        return (bits[idx / 8] & (1 << (idx % 8))) != 0;
    }

    private static void bitsetSet(byte[] bits, int idx) {
        bits[idx / 8] |= (1 << (idx % 8));
    }

    private static int countBits(byte[] bits, int total) {
        int c = 0;
        for (int i = 0; i < total; i++) if (bitsetGet(bits, i)) c++;
        return c;
    }

    private static List<Range> toRanges(byte[] bits, int total) {
        List<Range> ranges = new ArrayList<>();
        int i = 0;
        while (i < total) {
            while (i < total && !bitsetGet(bits, i)) i++;
            if (i >= total) break;
            int start = i + 1;
            while (i < total && bitsetGet(bits, i)) i++;
            int end = i;
            ranges.add(new Range(start, end));
        }
        return ranges;
    }

    private static List<Range> invertRanges(List<Range> received, int total) {
        List<Range> miss = new ArrayList<>();
        int cur = 1;
        for (Range r : received) {
            if (cur < r.start) miss.add(new Range(cur, r.start - 1));
            cur = r.end + 1;
        }
        if (cur <= total) miss.add(new Range(cur, total));
        return miss;
    }

    private static Integer findFirstMissing(byte[] bits, int total) {
        for (int i = 0; i < total; i++) if (!bitsetGet(bits, i)) return i + 1;
        return null;
    }

    private static String sha256Base64(byte[] data) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(d.digest(data));
    }

    public static class InitRequest {
        public String uploadId;
        public int totalChunks;
        public int chunkSize;
        public long fileSize;
        public String filename; // <-- add filename
    }
    public static class InitResponse {
        public String uploadId;
        public int totalChunks;
        public int chunkSize;
        public long fileSize;
        public String filename;
        public InitResponse(String uploadId, int totalChunks, int chunkSize, long fileSize, String filename) {
            this.uploadId = uploadId;
            this.totalChunks = totalChunks;
            this.chunkSize = chunkSize;
            this.fileSize = fileSize;
            this.filename = filename;
        }
    }
    public static class NextResponse { public Integer nextChunk; public NextResponse(Integer next) { this.nextChunk = next; } }
    public static class StatusResponse { public String uploadId; public int totalChunks; public int chunkSize; public long fileSize; public int receivedCount; public boolean completed; public Integer nextMissing; public List<Range> receivedRanges; public List<Range> missingRanges; public List<Integer> reserved; public Long sessionTtlSeconds; }
    public static class Range { public int start; public int end; public Range(int s, int e) { this.start = s; this.end = e; } }
    private static class Header { int totalChunks; int chunkSize; long fileSize; byte[] bitset; Header(int t, int c, long f, byte[] b) { this.totalChunks=t; this.chunkSize=c; this.fileSize=f; this.bitset=b; } }
}