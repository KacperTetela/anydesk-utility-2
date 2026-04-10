package raven.addressbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public final class AddressBookStore {

    private final Path filePath;

    public AddressBookStore() {
        this(defaultPath());
    }

    public AddressBookStore(Path filePath) {
        this.filePath = filePath;
    }

    public List<DeviceEntry> load() {
        Properties p = readProps();
        List<DeviceEntry> list = new ArrayList<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("device.")) {
                continue;
            }
            String anyDeskId = key.substring("device.".length());
            String raw = p.getProperty(key, "");
            String[] parts = raw.split("\\|", -1);
            String name = parts.length > 0 ? parts[0] : "";
            long last = 0;
            if (parts.length > 1) {
                try {
                    last = Long.parseLong(parts[1]);
                } catch (NumberFormatException ignore) {
                    last = 0;
                }
            }
            if (!name.isBlank() && !anyDeskId.isBlank()) {
                list.add(new DeviceEntry(name, anyDeskId, last));
            }
        }
        list.sort(Comparator.comparingLong(DeviceEntry::lastConnectedEpochMs).reversed().thenComparing(DeviceEntry::name));
        return list;
    }

    public void upsert(DeviceEntry entry) throws IOException {
        Properties p = readProps();
        p.setProperty(key(entry.anyDeskId()), entry.name() + "|" + entry.lastConnectedEpochMs());
        writeProps(p);
    }

    public void remove(String anyDeskId) throws IOException {
        Properties p = readProps();
        p.remove(key(anyDeskId));
        writeProps(p);
    }

    public void markLastConnected(String anyDeskId, long epochMs) {
        try {
            Properties p = readProps();
            String k = key(anyDeskId);
            String raw = p.getProperty(k);
            if (raw == null) {
                return;
            }
            String[] parts = raw.split("\\|", -1);
            String name = parts.length > 0 ? parts[0] : anyDeskId;
            p.setProperty(k, name + "|" + epochMs);
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

    private void writeProps(Properties p) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            p.store(out, "AnyDesk Manager Address Book");
        }
    }

    private static String key(String anyDeskId) {
        return "device." + anyDeskId.trim();
    }

    private static Path defaultPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".anydesk-manager", "addressbook.properties");
    }
}

