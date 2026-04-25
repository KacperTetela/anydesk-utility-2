package raven.addressbook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple AES-GCM encryption / decryption utility.
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
 *
 * <p>AES-GCM is used instead of ECB: a random 12-byte IV is generated for
 * every encryption call and prepended to the ciphertext before Base64
 * encoding, so identical plaintexts produce different ciphertexts.
 */
public final class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int AES_KEY_LENGTH = 32;  // bytes (256 bits)

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
    }

    /**
     * Encrypts {@code plainText} using AES-GCM and returns a Base64-encoded string
     * of the form {@code <iv (12 bytes)><ciphertext+tag>}.
     *
     * @param plainText the text to encrypt
     * @param masterKey the master key (any length; hashed internally to 256 bits)
     * @return Base64-encoded encrypted string (IV prepended)
     * @throws RuntimeException if encryption fails
     */
    public static String encrypt(String plainText, String masterKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKeySpec key = buildKey(masterKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a Base64-encoded AES-GCM cipher string (IV prepended) back to plain text.
     *
     * @param encryptedText Base64-encoded encrypted string produced by {@link #encrypt}
     * @param masterKey     the master key used during encryption
     * @return the original plain text
     * @throws RuntimeException if decryption fails
     */
    public static String decrypt(String encryptedText, String masterKey) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            SecretKeySpec key = buildKey(masterKey);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

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
        keyBytes = Arrays.copyOf(keyBytes, AES_KEY_LENGTH);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
