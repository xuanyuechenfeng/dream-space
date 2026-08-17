package com.dreamspace.common.persistence.storage;

import org.springframework.stereotype.Component;

@Component
public final class ObjectStorageFactory {
  private final ObjectStorage selected;

  public ObjectStorageFactory(ObjectStorage selected) { this.selected = selected; }

  public ObjectStorage selected() {
    return selected;
  }
}
