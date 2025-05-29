package com.somnath.common;


import java.security.*;
import javax.crypto.*;
import java.util.Base64;

public class RSAUtil {

    private static final String ALGORITHM = "RSA";

    private static final int KEY_SIZE = 1024; // 1024 bits

    /**
     * Generates a new RSA key pair.
     * Server will use this.
     * @return The generated KeyPair.
     * @throws NoSuchAlgorithmException If RSA algorithm is not available.
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyGen.initialize(KEY_SIZE);
        return keyGen.generateKeyPair();
    }

    /**
     * Encrypts data using a public key.
     * Client will use this with server's public key.
     * @param data The data to encrypt.
     * @param publicKey The public key for encryption.
     * @return The encrypted data bytes.
     * @throws Exception If encryption fails.
     */
    public static byte[] encrypt(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /**
     * Decrypts data using a private key.
     * Server will use this with its private key.
     * @param data The data to decrypt.
     * @param privateKey The private key for decryption.
     * @return The decrypted data bytes.
     * @throws Exception If decryption fails.
     */
    public static byte[] decrypt(byte[] data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    /**
     * Helper to convert byte array to Base64 String for transmission/display.
     */
    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Helper to convert Base64 String back to byte array.
     */
    public static byte[] base64ToBytes(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

    /**
     * Helper to convert String to byte array using UTF-8.
     */
    public static byte[] stringToBytes(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Helper to convert byte array to String using UTF-8.
     */
    public static String bytesToString(byte[] bytes) {
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}