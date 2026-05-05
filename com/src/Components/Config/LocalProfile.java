package Components.Config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.SecretKey;
import Network.Security;

/**
 * Manages the local user profile stored in the system preferences registry.
 *
 * Like a Linux user account, the profile defines who the user is on this
 * machine. There is no centralized server involved – it is purely a local
 * identity used as the display name throughout the application.
 *
 * Stored under the "wheelbarrow" preferences node:
 * - username: the display name
 * - salt: Base64-encoded random salt (only if password-protected)
 * - hash: Base64-encoded PBKDF2 hash (only if password-protected)
 */


public class LocalProfile {
    //probably better to have this on a system register no?
    static Preferences prefs = Preferences.userRoot().node("wheelbarrow");

    /** Returns {@code true} if a local profile exists on disk. */
    public static boolean hasProfile() {
        return prefs.get("username", null) != null;
    }

    /** Returns {@code true} if the stored profile is password-protected. */
    public static boolean isPasswordProtected() {
        if (!hasProfile()) return false;
        String hash = prefs.get("hash", null);
        return hash != null && !hash.isEmpty();
    }

    /**
     * Creates an unprotected profile (display-name only).
     * An existing profile is overwritten.
     *
     * @throws IllegalArgumentException if username is blank
     */
    public static void create(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        String hashtag = "#";
        for(int i = 0; i < 4; i++) {
            hashtag += ThreadLocalRandom.current().nextInt(0, 10);
        }
        prefs.put("username", username.strip() + hashtag);
        prefs.remove("salt");
        prefs.remove("hash");

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.out.println("Warning: could not flush preferences: " + e.getMessage());
}
    }

    /**
     * Creates a password-protected profile.
     * The password is hashed via PBKDF2 – it is never stored in plaintext.
     * Passing a blank password is equivalent to calling {@link #create(String)}.
     * An existing profile is overwritten.
     *
     * @throws IllegalArgumentException if username is blank
     */
    public static void create(String username, String password)
            throws IllegalArgumentException, NoSuchAlgorithmException, InvalidKeySpecException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (password == null || password.isEmpty()) {
            create(username);
            return;
        }
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        SecretKey key = Security.getKeyFromPassword(password, salt);
        String hash = Base64.getEncoder().encodeToString(key.getEncoded());
        
        if (hash.length() >= 4) {
            int r = ThreadLocalRandom.current().nextInt(0, hash.length()-4);
            String partialHash = hash.substring(r, r+4);
            prefs.put("username", username.strip() + partialHash);
        }
        else {
            String hashtag = "#";
            for(int i = 0; i < 4; i++) {
                hashtag += ThreadLocalRandom.current().nextInt(0, 10);
            }
            prefs.put("username", username.strip() + hashtag + "mabh");
        }
        prefs.put("salt", salt);
        prefs.put("hash", hash);

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.out.println("Warning: could not flush preferences: " + e.getMessage());
        }
    }

    /**
     * Returns the stored username, or {@code null} if no profile exists.
     */
    public static String getUsername() {
        if (!hasProfile()) return null;
        return prefs.get("username", null);
    }

    /**
     * Verifies the given password against the stored profile.
     * Returns {@code true} if the profile has no password set, or if the
     * password matches the stored hash. Uses constant-time comparison.
     */
    public static boolean checkPassword(String password) {
        if (!isPasswordProtected()) {return true;}
            String salt = prefs.get("salt", null);
            String storedHash = prefs.get("hash", null);
            try {
                SecretKey key = Security.getKeyFromPassword(password, salt);
                byte[] computed = key.getEncoded();
                byte[] stored = Base64.getDecoder().decode(storedHash);
                return MessageDigest.isEqual(computed, stored);
            } catch (Exception e) {
                System.out.println("Error checking password: " + e.getMessage());
                return false;
            }
    }

    /**
     * Deletes the local profile. Used when the user wants to switch accounts.
     */
    public static void delete() {
        prefs.remove("username");
        prefs.remove("salt");
        prefs.remove("hash");

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.out.println("Warning: could not flush preferences: " + e.getMessage());
        }
    }


    //Moving away from a file
    // private static void applyOwnerOnlyPermissions(Path path) {
    //     try {
    //         Set<PosixFilePermission> perms = EnumSet.of(
    //                 PosixFilePermission.OWNER_READ,
    //                 PosixFilePermission.OWNER_WRITE);
    //         Files.setPosixFilePermissions(path, perms);
    //     } catch (UnsupportedOperationException ignored) {
    //         // Non-POSIX filesystem (e.g., Windows NTFS) – skip silently
    //     } catch (IOException e) {
    //         System.out.println("Warning: could not set profile file permissions: " + e.getMessage());
    //     }
    // }
}