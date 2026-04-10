package raven.addressbook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple AES encryption / decryption utility.
 *
 * <p>Usage example:
 * <pre>
 *   String masterKey = "my-secret-key";   // replace with user-provided key later
 *   String cipher    = CryptoUtils.encrypt("password123", masterKey);
 *   String plain     = CryptoUtils.decrypt(cipher, masterKey);
 * </pre>
 *
 * <p>The master key is hashed with SHA-256 to produce a 256-bit AES key, so
 * any key length is acceptable.
 */
public final class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private CryptoUtils() {
    }

    /**
     * Encrypts {@code plainText} using AES and returns a Base64-encoded cipher string.
     *
     * @param plainText the text to encrypt
     * @param masterKey the master key (any length; hashed internally to 256 bits)
     * @return Base64-encoded encrypted string
     * @throws RuntimeException if encryption fails
     */
    public static String encrypt(String plainText, String masterKey) {
        try {
            SecretKeySpec key = buildKey(masterKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES cipher string back to plain text.
     *
     * @param encryptedText Base64-encoded encrypted string
     * @param masterKey     the master key used during encryption
     * @return the original plain text
     * @throws RuntimeException if decryption fails
     */
    public static String decrypt(String encryptedText, String masterKey) {
        try {
            SecretKeySpec key = buildKey(masterKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /** Derives a 256-bit AES key from the master key string via SHA-256. */
    private static SecretKeySpec buildKey(String masterKey) throws Exception {
        byte[] keyBytes = masterKey.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        keyBytes = sha.digest(keyBytes);
        keyBytes = Arrays.copyOf(keyBytes, 32);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
