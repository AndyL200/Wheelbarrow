package Network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.EnumSet;
import java.util.Set;
import java.util.Base64;

import javax.crypto.SecretKey;

/**
 * Manages a single local user account stored in
 * {@code ~/.wheelbarrow/credentials}.
 *
 * The file format is a single line: {@code username:salt:hash} where
 * {@code hash} is the Base64-encoded AES key derived via PBKDF2.
 *
 * When no credentials file is present the server runs in <em>open mode</em>
 * (all connections are accepted without a login check).
 */
public class LocalCredentials {

    private static final Path CREDENTIALS_FILE =
            Paths.get(System.getProperty("user.home"), ".wheelbarrow", "credentials");

    /** Returns {@code true} if a credentials file exists on disk. */
    public static boolean hasCredentials() {
        return Files.exists(CREDENTIALS_FILE);
    }

    /**
     * Registers (or overwrites) local credentials for the given username.
     * The password is never stored in plaintext; only its PBKDF2-derived key
     * hash is persisted. The credentials file is created with owner-only
     * read/write permissions (600) where the filesystem supports POSIX
     * permissions.
     */
    public static void register(String username, String password)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        SecretKey key = Security.getKeyFromPassword(password, salt);
        String hash = Base64.getEncoder().encodeToString(key.getEncoded());

        Files.createDirectories(CREDENTIALS_FILE.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(CREDENTIALS_FILE)) {
            writer.write(username + ":" + salt + ":" + hash);
        }
        applyOwnerOnlyPermissions(CREDENTIALS_FILE);
    }

    /** Sets owner-read/write-only permissions on the given path (silently skips on non-POSIX filesystems). */
    private static void applyOwnerOnlyPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g., Windows NTFS) – skip silently
        } catch (IOException e) {
            System.out.println("Warning: could not set credentials file permissions: " + e.getMessage());
        }
    }

    /**
     * Verifies that the supplied credentials match the stored ones.
     * Returns {@code true} (open mode) only when no credentials file exists.
     * An empty or malformed credentials file is treated as a configuration
     * error and returns {@code false}.
     */
    public static boolean verify(String username, String password) {
        if (!Files.exists(CREDENTIALS_FILE)) {
            return true;
        }
        try (BufferedReader reader = Files.newBufferedReader(CREDENTIALS_FILE)) {
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                return false;
            }
            String[] parts = line.split(":", 3);
            if (parts.length != 3) {
                return false;
            }
            String storedUser = parts[0];
            String salt = parts[1];
            String storedHash = parts[2];
            if (!storedUser.equals(username)) {
                return false;
            }
            SecretKey key = Security.getKeyFromPassword(password, salt);
            String hash = Base64.getEncoder().encodeToString(key.getEncoded());
            return hash.equals(storedHash);
        } catch (Exception e) {
            System.out.println("Error verifying credentials: " + e.getMessage());
            return false;
        }
    }

    /** Returns the stored username, or {@code null} if no credentials file exists. */
    public static String loadUsername() {
        if (!Files.exists(CREDENTIALS_FILE)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(CREDENTIALS_FILE)) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            String[] parts = line.split(":", 3);
            return parts.length > 0 ? parts[0] : null;
        } catch (IOException e) {
            return null;
        }
    }
}
