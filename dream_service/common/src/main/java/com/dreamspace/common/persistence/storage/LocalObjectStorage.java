package com.dreamspace.common.persistence.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class LocalObjectStorage implements ObjectStorage {
  private final Path root;

  public LocalObjectStorage(Path root) { this.root = root.toAbsolutePath().normalize(); }

  @Override public void put(String key, byte[] data, String contentType) {
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      try {
        Files.write(temp, data);
        try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
      } finally { Files.deleteIfExists(temp); }
    } catch (IOException e) { throw new IllegalStateException("object write failed", e); }
  }

  @Override public Optional<ObjectData> get(String key) {
    Path target = resolve(key);
    try { return Files.exists(target) ? Optional.of(new ObjectData(Files.readAllBytes(target), contentType(key))) : Optional.empty(); }
    catch (IOException e) { throw new IllegalStateException("object read failed", e); }
  }

  @Override public void delete(String key) {
    try { Files.deleteIfExists(resolve(key)); } catch (IOException e) { throw new IllegalStateException("object delete failed", e); }
  }

  @Override public URI createSignedGetUrl(String key, long ttlSeconds) { throw new UnsupportedOperationException("local storage does not sign URLs"); }

  @Override public boolean ready() {
    try {
      Files.createDirectories(root);
      return Files.isDirectory(root) && Files.isReadable(root) && Files.isWritable(root);
    } catch (IOException e) { return false; }
  }

  private Path resolve(String key) {
    ObjectKeyPolicy.validate(key);
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw new IllegalArgumentException("invalid object key");
    return path;
  }

  private static String contentType(String key) { return key.toLowerCase().endsWith(".png") ? "image/png" : "image/webp"; }
}
