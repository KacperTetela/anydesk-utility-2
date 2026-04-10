package raven.addressbook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages persistent storage of {@link ContactRecord} objects in a local JSON file.
 *
 * <p>The default storage location is OS-specific:
 * <ul>
 *   <li>Windows – {@code %LOCALAPPDATA%\AnyDeskManager\contacts.json}</li>
 *   <li>Other   – {@code $HOME/.config/AnyDeskManager/contacts.json}</li>
 * </ul>
 *
 * <p>A custom path can be supplied via the constructor (useful for testing).
 */
public final class AddressBookManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ContactRecord>>() {}.getType();

    private final Path filePath;

    /** Creates a manager that stores contacts in the default OS-specific location. */
    public AddressBookManager() {
        this(defaultPath());
    }

    /**
     * Creates a manager that stores contacts at the given path.
     *
     * @param filePath absolute path to the {@code contacts.json} file
     */
    public AddressBookManager(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Loads all contacts from the JSON file.
     *
     * @return mutable list of contacts; empty if the file does not exist or is empty
     */
    public List<ContactRecord> loadContacts() {
        if (!Files.isRegularFile(filePath)) {
            return new ArrayList<>();
        }
        try {
            String json = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return new ArrayList<>();
            }
            List<ContactRecord> list = GSON.fromJson(json, LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return new ArrayList<>();
        }
    }

    /**
     * Saves (adds or updates) a contact.
     * If a contact with the same {@code id} already exists it is replaced;
     * otherwise the contact is appended to the list.
     *
     * @param contact the contact to save
     * @throws IOException if the file cannot be written
     */
    public void saveContact(ContactRecord contact) throws IOException {
        List<ContactRecord> contacts = loadContacts();
        boolean updated = false;
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getId().equals(contact.getId())) {
                contacts.set(i, contact);
                updated = true;
                break;
            }
        }
        if (!updated) {
            contacts.add(contact);
        }
        writeContacts(contacts);
    }

    /**
     * Deletes the contact with the given {@code id}.
     * No-op if no contact with that id exists.
     *
     * @param id the UUID string of the contact to remove
     * @throws IOException if the file cannot be written
     */
    public void deleteContact(String id) throws IOException {
        List<ContactRecord> contacts = loadContacts();
        boolean removed = contacts.removeIf(c -> c.getId().equals(id));
        if (removed) {
            writeContacts(contacts);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void writeContacts(List<ContactRecord> contacts) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = GSON.toJson(contacts);
        Files.write(filePath, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Returns the default OS-specific path for the contacts file.
     * <ul>
     *   <li>Windows: {@code %LOCALAPPDATA%\AnyDeskManager\contacts.json}</li>
     *   <li>Other:   {@code $HOME/.config/AnyDeskManager/contacts.json}</li>
     * </ul>
     */
    private static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isBlank()) {
                localAppData = System.getProperty("user.home") + "\\AppData\\Local";
            }
            return Path.of(localAppData, "AnyDeskManager", "contacts.json");
        }
        // macOS / Linux
        String home = System.getProperty("user.home");
        return Path.of(home, ".config", "AnyDeskManager", "contacts.json");
    }
}
