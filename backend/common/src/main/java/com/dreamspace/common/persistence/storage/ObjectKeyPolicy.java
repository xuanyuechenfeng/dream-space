package com.dreamspace.common.persistence.storage;

import java.util.regex.Pattern;

public final class ObjectKeyPolicy {
  private static final Pattern KEY = Pattern.compile(
      "^(references|results|thumbnails)/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.webp$");

  private ObjectKeyPolicy() {}

  public static String validate(String key) {
    if (key == null || key.isBlank() || key.indexOf('\\') >= 0 || key.startsWith("/") || key.contains("..") || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("invalid object key");
    }
    return key;
  }
}
