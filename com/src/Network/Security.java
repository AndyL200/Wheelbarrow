package Network;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public interface Security {
    int GCM_IV_LENGTH = 12;
    int GCM_TAG_LENGTH = 128;
    String GCM_ALGORITHM = "AES/GCM/NoPadding";

    public static SecretKey generateKey(int n) throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(n);
        SecretKey key = keyGenerator.generateKey();
        return key;
    }
    public static SecretKey getKeyFromPassword(String password, String salt)
    throws NoSuchAlgorithmException, InvalidKeySpecException {
    
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256);
    SecretKey secret = new SecretKeySpec(factory.generateSecret(spec)
        .getEncoded(), "AES");
    return secret;
    }
    public static String encrypt(String algorithm, String input, SecretKey key,
    GCMParameterSpec iv) throws NoSuchPaddingException, NoSuchAlgorithmException,
    InvalidAlgorithmParameterException, InvalidKeyException,
    BadPaddingException, IllegalBlockSizeException {
    
    Cipher cipher = Cipher.getInstance(algorithm);
    cipher.init(Cipher.ENCRYPT_MODE, key, iv);
    byte[] cipherText = cipher.doFinal(input.getBytes());
    return Base64.getEncoder()
        .encodeToString(cipherText);
    }
    public static String decrypt(String algorithm, String cipherText, SecretKey key,
    GCMParameterSpec iv) throws NoSuchPaddingException, NoSuchAlgorithmException,
    InvalidAlgorithmParameterException, InvalidKeyException,
    BadPaddingException, IllegalBlockSizeException {
    
    Cipher cipher = Cipher.getInstance(algorithm);
    cipher.init(Cipher.DECRYPT_MODE, key, iv);
    byte[] plainText = cipher.doFinal(Base64.getDecoder()
        .decode(cipherText));
    return new String(plainText);
    }

    /**
     * Encrypts raw bytes using AES/GCM/NoPadding. A fresh random IV is generated
     * per call and prepended to the returned byte array as the first
     * {@value #GCM_IV_LENGTH} bytes.
     */
    public static byte[] encryptBytes(byte[] plaintext, SecretKey key)
    throws NoSuchPaddingException, NoSuchAlgorithmException,
    InvalidAlgorithmParameterException, InvalidKeyException,
    BadPaddingException, IllegalBlockSizeException {

    byte[] iv = new byte[GCM_IV_LENGTH];
    new SecureRandom().nextBytes(iv);
    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, key, spec);
    byte[] ciphertext = cipher.doFinal(plaintext);
    byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
    System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
    System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
    return result;
    }

    /**
     * Decrypts a byte array produced by {@link #encryptBytes}. The first
     * {@value #GCM_IV_LENGTH} bytes are treated as the IV.
     */
    public static byte[] decryptBytes(byte[] encrypted, SecretKey key)
    throws NoSuchPaddingException, NoSuchAlgorithmException,
    InvalidAlgorithmParameterException, InvalidKeyException,
    BadPaddingException, IllegalBlockSizeException {

    if (encrypted.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Encrypted payload too short to contain IV");
    }
    byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
    byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);
    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
    Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, key, spec);
    return cipher.doFinal(ciphertext);
    }
}