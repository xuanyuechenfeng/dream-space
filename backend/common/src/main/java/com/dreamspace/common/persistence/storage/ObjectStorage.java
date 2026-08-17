package com.dreamspace.common.persistence.storage;

import java.net.URI;
import java.util.Optional;

public interface ObjectStorage {
  void put(String key, byte[] data, String contentType);
  Optional<ObjectData> get(String key);
  void delete(String key);
  default URI createSignedGetUrl(String key, long ttlSeconds) { throw new UnsupportedOperationException("signed URLs unavailable"); }
  record ObjectData(byte[] bytes, String contentType) {}
}
