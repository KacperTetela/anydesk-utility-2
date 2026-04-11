package raven.addressbook;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import raven.config.ConfigManager;

/**
 * Thread-safe and multi-process-safe address book persistence.
 *
 * <h3>Concurrent access (shared network drive)</h3>
 * All read and write operations use {@link FileLock} to coordinate between
 * separate JVM processes (e.g. multiple workstations sharing a single JSON
 * file on a network drive). Within the same JVM a {@code synchronized} block
 * prevents {@link OverlappingFileLockException}.
 *
 * <p>Write operations use a <em>read-modify-write cycle under an exclusive
 * lock</em>: the file is re-read after the lock is acquired so that changes
 * made by other clients between our last read and now are not overwritten.
 *
 * <h3>JSON schema</h3>
 * <pre>
 * [
 *   {
 *     "roomNumber":        "101",
 *     "fullName":          "Jan Kowalski",
 *     "hostname":          "PC-ROOM101",
 *     "anydeskId":         "123456789",
 *     "encryptedPassword": "BASE64==",
 *     "lastConnected":     1718000000000
 *   }
 * ]
 * </pre>
 * Old files that use the key {@code "alias"} instead of {@code "fullName"} are
 * still read correctly (backward compatibility).
 */
public final class AddressBookStore {

    // ── In-process mutex ─────────────────────────────────────────────────────
    // Java FileLock does NOT guard same-JVM threads against each other.
    private static final Object PROCESS_LOCK = new Object();

    private static final int LOCK_RETRY_MS  = 150;
    private static final int LOCK_MAX_TRIES = 20;  // ~3 s total wait

    // ── Public API ────────────────────────────────────────────────────────────

    public List<DeviceEntry> load() {
        Path path = resolvedPath();
        if (path == null) {
            return List.of();
        }
        try {
            return readUnderLock(path);
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            return List.of();
        }
    }

    public void upsert(DeviceEntry entry) throws IOException {
        modifyUnderLock(entries -> {
            entries.removeIf(e -> e.anyDeskId().equals(entry.anyDeskId()));
            entries.add(entry);
            return entries;
        });
    }

    public void remove(String anyDeskId) throws IOException {
        modifyUnderLock(entries -> {
            entries.removeIf(e -> e.anyDeskId().equals(anyDeskId));
            return entries;
        });
    }

    public void markLastConnected(String anyDeskId, long epochMs) {
        try {
            modifyUnderLock(entries -> {
                for (int i = 0; i < entries.size(); i++) {
                    DeviceEntry e = entries.get(i);
                    if (e.anyDeskId().equals(anyDeskId)) {
                        entries.set(i, new DeviceEntry(
                                e.roomNumber(), e.fullName(), e.hostname(),
                                e.anyDeskId(), e.encryptedPassword(), epochMs));
                        break;
                    }
                }
                return entries;
            });
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

    // ── ConfigManager integration ─────────────────────────────────────────────

    private static Path resolvedPath() {
        String p = ConfigManager.getAddressBookPath();
        return (p != null && !p.isBlank()) ? Path.of(p) : null;
    }

    // ── Concurrent-safe read ──────────────────────────────────────────────────

    private static List<DeviceEntry> readUnderLock(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        synchronized (PROCESS_LOCK) {
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                FileLock lock = acquireLock(ch, true /* shared */);
                try {
                    return parseChannel(ch);
                } finally {
                    releaseLock(lock);
                }
            }
        }
    }

    // ── Concurrent-safe read-modify-write ─────────────────────────────────────

    private static void modifyUnderLock(UnaryOperator<List<DeviceEntry>> transform)
            throws IOException {
        Path path = resolvedPath();
        if (path == null) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        synchronized (PROCESS_LOCK) {
            try (FileChannel ch = FileChannel.open(path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE)) {
                FileLock lock = acquireLock(ch, false /* exclusive */);
                try {
                    // Re-read under the exclusive lock — captures any writes by peer clients
                    List<DeviceEntry> current = parseChannel(ch);
                    List<DeviceEntry> updated = transform.apply(new ArrayList<>(current));

                    byte[] bytes = toJson(updated).getBytes(StandardCharsets.UTF_8);
                    ch.truncate(0);
                    ch.position(0);
                    ch.write(ByteBuffer.wrap(bytes));
                    ch.force(true); // flush metadata + data to the underlying storage
                } finally {
                    releaseLock(lock);
                }
            }
        }
    }

    // ── FileLock helpers ──────────────────────────────────────────────────────

    /**
     * Tries to acquire the lock, retrying up to {@link #LOCK_MAX_TRIES} times.
     * Returns {@code null} if the lock cannot be obtained (e.g. the filesystem
     * does not support locking — common on some NFS mounts). In that case the
     * operation proceeds without a cross-process lock but remains safe within a
     * single JVM thanks to {@link #PROCESS_LOCK}.
     */
    private static FileLock acquireLock(FileChannel ch, boolean shared) throws IOException {
        for (int i = 0; i < LOCK_MAX_TRIES; i++) {
            try {
                FileLock lock = ch.tryLock(0, Long.MAX_VALUE, shared);
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException ignored) {
                // Should not happen because PROCESS_LOCK serialises same-JVM access.
            }
            try {
                Thread.sleep(LOCK_RETRY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null; // degraded — proceed without cross-process lock
    }

    private static void releaseLock(FileLock lock) {
        if (lock != null && lock.isValid()) {
            try {
                lock.release();
            } catch (IOException ignored) {
            }
        }
    }

    // ── Channel → List<DeviceEntry> ───────────────────────────────────────────

    private static List<DeviceEntry> parseChannel(FileChannel ch) throws IOException {
        long size = ch.size();
        if (size == 0) {
            return List.of();
        }
        ch.position(0);
        int cap = (int) Math.min(size, 8L * 1024 * 1024); // guard against huge files
        ByteBuffer buf = ByteBuffer.allocate(cap);
        ch.read(buf);
        buf.flip();
        String json = StandardCharsets.UTF_8.decode(buf).toString();
        if (json.isBlank()) {
            return List.of();
        }
        List<DeviceEntry> entries = parseJson(json);
        entries.sort(Comparator
                .comparingLong(DeviceEntry::lastConnectedEpochMs).reversed()
                .thenComparing(DeviceEntry::fullName));
        return entries;
    }

    // ── JSON serialisation ────────────────────────────────────────────────────

    private static String toJson(List<DeviceEntry> entries) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            DeviceEntry e = entries.get(i);
            sb.append("  {\n");
            sb.append("    \"roomNumber\":        ").append(jsonStr(e.roomNumber())).append(",\n");
            sb.append("    \"fullName\":          ").append(jsonStr(e.fullName())).append(",\n");
            sb.append("    \"hostname\":          ").append(jsonStr(e.hostname())).append(",\n");
            sb.append("    \"anydeskId\":         ").append(jsonStr(e.anyDeskId())).append(",\n");
            sb.append("    \"encryptedPassword\": ").append(jsonStr(e.encryptedPassword())).append(",\n");
            sb.append("    \"lastConnected\":     ").append(e.lastConnectedEpochMs()).append("\n");
            sb.append("  }");
            if (i < entries.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("]");
        return sb.toString();
    }

    private static String jsonStr(String s) {
        if (s == null) {
            return "\"\"";
        }
        return '"'
                + s.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                + '"';
    }

    // ── JSON deserialisation ──────────────────────────────────────────────────

    /**
     * Minimal recursive-descent JSON parser for the fixed schema above.
     * Handles string escape sequences. Returns an empty list on parse errors.
     * Backward-compatible: accepts {@code "alias"} as a synonym for {@code "fullName"}.
     */
    private static List<DeviceEntry> parseJson(String json) {
        List<DeviceEntry> result = new ArrayList<>();
        try {
            int[] pos = {0};
            skipWs(json, pos);
            if (pos[0] >= json.length() || json.charAt(pos[0]) != '[') {
                return result;
            }
            pos[0]++;
            while (pos[0] < json.length()) {
                skipWs(json, pos);
                if (pos[0] >= json.length()) break;
                char c = json.charAt(pos[0]);
                if (c == ']') break;
                if (c == ',') { pos[0]++; continue; }
                if (c == '{') {
                    DeviceEntry e = mapToEntry(parseObject(json, pos));
                    if (e != null) result.add(e);
                } else {
                    pos[0]++;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
        return result;
    }

    private static Map<String, String> parseObject(String json, int[] pos) {
        Map<String, String> map = new LinkedHashMap<>();
        pos[0]++; // consume '{'
        while (pos[0] < json.length()) {
            skipWs(json, pos);
            if (pos[0] >= json.length()) break;
            char c = json.charAt(pos[0]);
            if (c == '}') { pos[0]++; break; }
            if (c == ',') { pos[0]++; continue; }
            if (c != '"') { pos[0]++; continue; }
            String key = parseString(json, pos);
            skipWs(json, pos);
            if (pos[0] < json.length() && json.charAt(pos[0]) == ':') pos[0]++;
            skipWs(json, pos);
            if (pos[0] >= json.length()) break;
            String value;
            if (json.charAt(pos[0]) == '"') {
                value = parseString(json, pos);
            } else {
                int start = pos[0];
                while (pos[0] < json.length()) {
                    char ch = json.charAt(pos[0]);
                    if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) break;
                    pos[0]++;
                }
                value = json.substring(start, pos[0]).trim();
            }
            map.put(key, value);
        }
        return map;
    }

    private static String parseString(String json, int[] pos) {
        pos[0]++; // consume opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < json.length()) {
            char c = json.charAt(pos[0]);
            if (c == '\\') {
                pos[0]++;
                if (pos[0] < json.length()) {
                    switch (json.charAt(pos[0])) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u'  -> {
                            if (pos[0] + 4 < json.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(
                                            json.substring(pos[0] + 1, pos[0] + 5), 16));
                                    pos[0] += 4;
                                } catch (NumberFormatException e) {
                                    sb.append('u');
                                }
                            }
                        }
                        default -> sb.append(json.charAt(pos[0]));
                    }
                }
            } else if (c == '"') {
                pos[0]++;
                break;
            } else {
                sb.append(c);
            }
            pos[0]++;
        }
        return sb.toString();
    }

    private static void skipWs(String json, int[] pos) {
        while (pos[0] < json.length() && Character.isWhitespace(json.charAt(pos[0]))) pos[0]++;
    }

    private static DeviceEntry mapToEntry(Map<String, String> map) {
        // "alias" is the legacy key from the old schema — fall back to it if "fullName" absent
        String fullName  = map.getOrDefault("fullName",
                           map.getOrDefault("alias", "")).trim();
        String id        = map.getOrDefault("anydeskId", "").trim();
        if (fullName.isEmpty() && id.isEmpty()) {
            return null;
        }
        String roomNumber = map.getOrDefault("roomNumber", "").trim();
        String hostname   = map.getOrDefault("hostname",   "").trim();
        String pwd        = map.getOrDefault("encryptedPassword", "");
        long   last       = 0;
        try {
            last = Long.parseLong(map.getOrDefault("lastConnected", "0"));
        } catch (NumberFormatException ignore) {
        }
        return new DeviceEntry(roomNumber, fullName, hostname, id, pwd, last);
    }
}
