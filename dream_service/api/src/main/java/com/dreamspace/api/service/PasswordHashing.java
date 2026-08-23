package com.dreamspace.api.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHashing {
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String PREFIX = "pbkdf2-sha256";
  private static final int ITERATIONS = 210_000;
  private static final int SALT_BYTES = 16;
  private static final int KEY_BITS = 256;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String DUMMY_HASH = encode("dream-space-invalid-password");

  private PasswordHashing() {}

  public static String encode(String password) {
    if (password == null) throw new IllegalArgumentException("password is required");
    byte[] salt = new byte[SALT_BYTES];
    RANDOM.nextBytes(salt);
    byte[] derived = derive(password, salt, ITERATIONS);
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return PREFIX + "$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$"
        + encoder.encodeToString(derived);
  }

  public static boolean matches(String password, String encoded) {
    String candidate = password == null ? "" : password;
    String stored = encoded == null || encoded.isBlank() ? DUMMY_HASH : encoded;
    try {
      String[] parts = stored.split("\\$", -1);
      if (parts.length != 4 || !PREFIX.equals(parts[0])) {
        derive(candidate, new byte[SALT_BYTES], ITERATIONS);
        return false;
      }
      int iterations = Integer.parseInt(parts[1]);
      byte[] salt = Base64.getUrlDecoder().decode(parts[2]);
      byte[] expected = Base64.getUrlDecoder().decode(parts[3]);
      byte[] actual = derive(candidate, salt, iterations);
      return MessageDigest.isEqual(expected, actual);
    } catch (RuntimeException error) {
      return false;
    }
  }

  private static byte[] derive(String password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
      try {
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
      } finally {
        spec.clearPassword();
      }
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("password hashing is unavailable", error);
    }
  }
}
