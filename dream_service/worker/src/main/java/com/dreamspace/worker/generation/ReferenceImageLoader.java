package com.dreamspace.worker.generation;

public interface ReferenceImageLoader {
  ReferenceImage load(String userId, String imageId);
}
