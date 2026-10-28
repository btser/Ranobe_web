package main.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Bam mat khau bang ham dan xuat khoa co san trong Java, khong can thu vien moi. */
public final class Passwords {
    private static final String PREFIX = "{pbkdf2-sha256}$";
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static String hash(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Mat khau khong duoc de trong.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] key = derive(raw, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(key);
    }

    public static boolean isEncoded(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        if (!isEncoded(stored)) {
            return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8),
                    stored.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] parts = stored.substring(PREFIX.length()).split("\\$", -1);
            if (parts.length != 3) return false;
            int rounds = Integer.parseInt(parts[0]);
            if (rounds < 1 || rounds > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(raw, salt, rounds));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String raw, byte[] salt, int rounds) {
        char[] chars = raw.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, rounds, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Khong khoi tao duoc ham bam mat khau.", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, (char) 0);
        }
    }
}
