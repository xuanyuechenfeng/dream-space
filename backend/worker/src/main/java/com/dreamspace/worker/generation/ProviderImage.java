package com.dreamspace.worker.generation;

public record ProviderImage(int index, byte[] data, String mimeType, String sourceName) {
  public ProviderImage {
    if (index < 0 || data == null || data.length == 0) throw new IllegalArgumentException("invalid provider image");
  }
}
