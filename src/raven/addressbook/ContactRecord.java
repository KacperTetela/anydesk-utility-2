package raven.addressbook;

import java.util.UUID;

/**
 * Represents a single contact entry in the Address Book.
 */
public class ContactRecord {

    private String id;
    private String alias;
    private String anydeskId;
    private String encryptedPassword;

    /** Required for Gson deserialization. */
    public ContactRecord() {
    }

    public ContactRecord(String alias, String anydeskId, String encryptedPassword) {
        this(UUID.randomUUID().toString(), alias, anydeskId, encryptedPassword);
    }

    public ContactRecord(String id, String alias, String anydeskId, String encryptedPassword) {
        this.id = id;
        this.alias = alias;
        this.anydeskId = anydeskId;
        this.encryptedPassword = encryptedPassword;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getAnydeskId() {
        return anydeskId;
    }

    public void setAnydeskId(String anydeskId) {
        this.anydeskId = anydeskId;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    @Override
    public String toString() {
        return "ContactRecord{id='" + id + "', alias='" + alias + "', anydeskId='" + anydeskId + "'}";
    }
}
