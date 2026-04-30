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
import java.util.Set;

import javax.crypto.SecretKey;
import Network.Security;

/**
 * Manages the local user profile stored in {@code ~/.wheelbarrow/profile}.
 *
 * Like a Linux user account, the profile defines who the user is on this
 * machine. There is no centralized server involved – it is purely a local
 * identity used as the display name throughout the application.
 *
 * File format:
 * <ul>
 *   <li>Without password: {@code username}</li>
 *   <li>With password:    {@code username:salt:hash}</li>
 * </ul>
 */
public class LocalProfile {
    //probably better to have this on a system register no?
    private static final Path PROFILE_FILE =
            Paths.get(System.getProperty("user.home"), ".wheelbarrow", "profile");

    /** Returns {@code true} if a local profile exists on disk. */
    public static boolean hasProfile() {
        return Files.exists(PROFILE_FILE);
    }

    /** Returns {@code true} if the stored profile is password-protected. */
    public static boolean isPasswordProtected() {
        if (!hasProfile()) return false;
        try (BufferedReader reader = Files.newBufferedReader(PROFILE_FILE)) {
            String line = reader.readLine();
            return line != null && line.contains(":");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Creates an unprotected profile (display-name only).
     * An existing profile is overwritten.
     *
     * @throws IllegalArgumentException if username is blank
     */
    public static void create(String username) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        Files.createDirectories(PROFILE_FILE.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(PROFILE_FILE)) {
            writer.write(username.strip());
        }
        applyOwnerOnlyPermissions(PROFILE_FILE);
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
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
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

        Files.createDirectories(PROFILE_FILE.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(PROFILE_FILE)) {
            writer.write(username.strip() + ":" + salt + ":" + hash);
        }
        applyOwnerOnlyPermissions(PROFILE_FILE);
    }

    /**
     * Returns the stored username, or {@code null} if no profile exists.
     */
    public static String getUsername() {
        if (!hasProfile()) return null;
        try (BufferedReader reader = Files.newBufferedReader(PROFILE_FILE)) {
            String line = reader.readLine();
            if (line == null) return null;
            return line.contains(":") ? line.split(":", 2)[0] : line;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Verifies the given password against the stored profile.
     * Returns {@code true} if the profile has no password set, or if the
     * password matches the stored hash. Uses constant-time comparison.
     */
    public static boolean checkPassword(String password) {
        if (!isPasswordProtected()) return true;
        try (BufferedReader reader = Files.newBufferedReader(PROFILE_FILE)) {
            String line = reader.readLine();
            if (line == null) return false;
            String[] parts = line.split(":", 3);
            if (parts.length != 3) return false;
            String salt = parts[1];
            String storedHash = parts[2];
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
    public static void delete() throws IOException {
        Files.deleteIfExists(PROFILE_FILE);
    }

    private static void applyOwnerOnlyPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g., Windows NTFS) – skip silently
        } catch (IOException e) {
            System.out.println("Warning: could not set profile file permissions: " + e.getMessage());
        }
    }
}