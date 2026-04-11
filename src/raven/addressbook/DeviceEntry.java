package raven.addressbook;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable record representing one contact in the address book.
 *
 * <p>JSON keys: {@code roomNumber}, {@code fullName}, {@code hostname},
 * {@code anydeskId}, {@code encryptedPassword}, {@code lastConnected}.
 * Old key {@code alias} is accepted as a fallback for {@code fullName}
 * when reading files written by an earlier version of the app.
 */
public record DeviceEntry(
        String roomNumber,
        String fullName,
        String hostname,
        String anyDeskId,
        String encryptedPassword,
        long   lastConnectedEpochMs) {

    /** Convenience constructor for entries that have no saved password. */
    public DeviceEntry(String roomNumber, String fullName, String hostname,
                       String anyDeskId, long lastConnectedEpochMs) {
        this(roomNumber, fullName, hostname, anyDeskId, "", lastConnectedEpochMs);
    }

    /** Returns {@code true} when a non-blank encrypted password is stored. */
    public boolean hasPassword() {
        return encryptedPassword != null && !encryptedPassword.isBlank();
    }

    /**
     * Human-readable label used in suggestion dropdowns and log messages.
     * Combines the available identifying fields into a short, readable string.
     */
    public String displayLabel() {
        List<String> parts = new ArrayList<>();
        if (fullName  != null && !fullName.isBlank())   parts.add(fullName);
        if (roomNumber != null && !roomNumber.isBlank()) parts.add("pok.\u00a0" + roomNumber);
        if (hostname   != null && !hostname.isBlank())   parts.add(hostname);
        return parts.isEmpty() ? anyDeskId : String.join(" / ", parts);
    }
}
