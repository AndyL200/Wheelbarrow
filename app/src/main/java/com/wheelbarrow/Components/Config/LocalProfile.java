package com.wheelbarrow.Components.Config;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.crypto.SecretKey;

import org.json.JSONException;
import org.json.JSONObject;

import com.wheelbarrow.Network.Security;

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
    //Just put a json object in the user preferences registry
    static Preferences prefs = Preferences.userRoot().node("wheelbarrow");
    private User currentUser;


    public LocalProfile() {
        String source = prefs.get("last", null);
        if (source == null) {
            currentUser = null;
            return;
        }
        InnerLast last = new InnerLast(source);
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        if (users.has(last.getString("username"))) {
            currentUser = User.fromJSON(last.getString("username"), users.getJSONObject(last.getString("username")));
        }
        else {            
            currentUser = null;
        }
    }

    public int login(String username) {
        if (!hasProfile()) {
            System.out.println("No profile exists. Please create a profile first.");
            return -1;
        }
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        String hash;
        try {
            hash = users.getJSONObject(username).getString("hash");
        } catch (JSONException j) {
            hash = null;
        }
        if (!users.has(username)) {
            System.out.println("Username not found: " + username);
            return 1;
        }
        if (hash != null && !hash.isEmpty()) {
            System.out.println("Profile is password-protected. Please enter a password.");
            return 2;
        }
        currentUser = new User(username, null);
        return 0;
    }

    public int login(String username, String password) {
        if (!hasProfile()) {
            System.out.println("No profile exists. Please create a profile first.");
            return -1;
        }
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        String hash;
        try {
            hash = users.getJSONObject(username).getString("hash");
        } catch (JSONException j) {
            hash = null;
        }
        if (!users.has(username)) {
            System.out.println("Username not found: " + username);
            return 1;
        }
        if (hash != null && !hash.isEmpty()) {
            currentUser = new User(username, null);
            if (!checkPassword(username, password)) {
                System.out.println("Incorrect password for username: " + username);
                currentUser = null;
                return 2;
            }
        }
        currentUser = new User(username, null);
        InnerLast last = new InnerLast();
        last.setUsername(currentUser.getUsername());
        last.setImg(currentUser.getImgUrl());
        prefs.put("last", last.toString());
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.out.println("Warning: could not flush preferences: " + e.getMessage());
        }
        return 0;
    }

    public void logout() {
        currentUser = null;
    }




    /** Returns {@code true} if a local profile exists on disk. */
    public static boolean hasProfile() {
        return prefs.get("users", null) != null;
    }

    /** Returns {@code true} if the stored profile is password-protected. */
    public static boolean isPasswordProtected(String username) {
        if (!hasProfile()) return false;
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        String hash = users.getJSONObject(username).getString("hash");
        return hash != null && !hash.isEmpty();
    }
    public boolean isPasswordProtected() {
        if (!hasProfile() || currentUser == null) return false;
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        String hash = users.getJSONObject(currentUser.getUsername()).getString("hash");
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
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        if (users.has(username.strip())) {
            System.out.println("Warning: overwriting existing profile for username: " + username.strip());
            return;
        }
        String hashtag = "#";
        for(int i = 0; i < 4; i++) {
            hashtag += ThreadLocalRandom.current().nextInt(0, 10);
        }

        users.put(username.strip() + hashtag, new InnerUser());
        prefs.put("users", users.toString());
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
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        InnerUser innerUser = new InnerUser();

        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        SecretKey key = Security.getKeyFromPassword(password, salt);
        String hash = Base64.getEncoder().encodeToString(key.getEncoded());
        innerUser.put("salt", salt);
        innerUser.put("hash", hash);
        
        if (hash.length() >= 4) {
            int r = ThreadLocalRandom.current().nextInt(0, hash.length()-4);
            String partialHash = hash.substring(r, r+4);
            users.put(username.strip() + partialHash, innerUser);
        }
        else {
            String hashtag = "#";
            for(int i = 0; i < 4; i++) {
                hashtag += ThreadLocalRandom.current().nextInt(0, 10);
            }
            users.put(username.strip() + hashtag, innerUser);
        }
        prefs.put("users", users.toString());

        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            System.out.println("Warning: could not flush preferences: " + e.getMessage());
        }
    }

    public static List<User> listUsers() {
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        List<User> userList = new ArrayList<>();
        users.keys().forEachRemaining(key -> userList.add(User.fromJSON(key, users.getJSONObject(key))));
        return userList;
    }

    /**
     * Returns the stored user, or {@code null} if no profile exists.
     */
    public User getUser() {
        if (!hasProfile()) return null;
        return currentUser;
    }

    /**
     * Verifies the given password against the stored profile.
     * Returns {@code true} if the profile has no password set, or if the
     * password matches the stored hash. Uses constant-time comparison.
     */
    public static boolean checkPassword(String username, String password) {
        if (!isPasswordProtected(username)) {return true;}
            JSONObject users = new JSONObject(prefs.get("users", "{}"));
            String salt = users.getString("salt");
            String storedHash = users.getString("hash");
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
    public void delete(String username, String password) {
        JSONObject users = new JSONObject(prefs.get("users", "{}"));
        if (!users.has(username)) {
            System.out.println("Username not found: " + username);
            return;
        }
        if (isPasswordProtected(username) &&!checkPassword(username, password)) {
            System.out.println("Incorrect password for username: " + username);
            return;
        }
        users.remove(username);
        prefs.put("users", users.toString());
        if (currentUser != null && currentUser.getUsername().equals(username)) {
            currentUser = null;
        }
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

class InnerUser extends JSONObject {
    //key == username
    public InnerUser() {
        super();
        this.put("hash", "");
        this.put("salt", "");
        this.put("img", "");
    }
}
class InnerLast extends JSONObject {
    //key == "last"
    public InnerLast() {
        super();
        this.put("username", "");
    }
    public InnerLast(String source) {
        super(source);
        if (!this.has("username")) {
            this.put("username", "");
        }
    }

    public void setUsername(String username) {
        this.put("username", username);
    }
    public void setImg(String url) {
        if (url == null) {
            url = "";
        }
        this.put("img_url", url);
    }
}

