package raven.history;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class ConnectionHistoryStore {

    private static final int MAX_RECORDS = 50;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;

    public ConnectionHistoryStore() {
        this(defaultPath());
    }

    public ConnectionHistoryStore(Path filePath) {
        this.filePath = filePath;
    }

    public List<ConnectionRecord> load() {
        Properties p = readProps();
        List<ConnectionRecord> list = new ArrayList<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("rec.")) {
                continue;
            }
            String anyDeskId = key.substring("rec.".length());
            String raw = p.getProperty(key, "");
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 2) {
                continue;
            }
            try {
                LocalDateTime dt = LocalDateTime.parse(parts[0], ISO);
                long dur = Long.parseLong(parts[1]);
                list.add(new ConnectionRecord(anyDeskId, dt, dur));
            } catch (Exception ignore) {
                // skip malformed rows
            }
        }
        list.sort(Comparator.comparing(ConnectionRecord::connectionDate).reversed());
        return list;
    }

    public void append(ConnectionRecord record) {
        try {
            List<ConnectionRecord> all = new ArrayList<>(load());
            all.add(0, record);
            while (all.size() > MAX_RECORDS) {
                all.remove(all.size() - 1);
            }

            Properties p = new Properties();
            // store latest record per ID (simple + compact)
            // Key: rec.<id> Value: <datetime>|<durationSeconds>
            for (ConnectionRecord r : all) {
                // keep the newest only for each id
                String k = "rec." + r.anydeskId().trim();
                if (!p.containsKey(k)) {
                    p.setProperty(k, r.connectionDate().format(ISO) + "|" + r.durationSeconds());
                }
            }
            writeProps(p);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private Properties readProps() {
        Properties p = new Properties();
        if (!Files.isRegularFile(filePath)) {
            return p;
        }
        try (InputStream in = Files.newInputStream(filePath, StandardOpenOption.READ)) {
            p.load(in);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return p;
    }

    private void writeProps(Properties p) throws Exception {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            p.store(out, "AnyDesk Manager Connection History");
        }
    }

    private static Path defaultPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".anydesk-manager", "history.properties");
    }
}

