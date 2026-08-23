package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.worker.persistence.upload.ReferenceImageMapper;
import com.dreamspace.worker.persistence.upload.ReferenceImageRecord;
import org.springframework.stereotype.Service;

@Service
public class JdbcReferenceImageLoader implements ReferenceImageLoader {
  private final ReferenceImageMapper mapper;
  private final ObjectStorage storage;

  public JdbcReferenceImageLoader(ReferenceImageMapper mapper, ObjectStorage storage) {
    this.mapper = mapper;
    this.storage = storage;
  }

  @Override
  public ReferenceImage load(String userId, String imageId) {
    if (imageId == null || imageId.isBlank()) {
      throw unavailable("reference image id is required");
    }
    ReferenceImageRecord record = mapper.findOwned(userId, imageId);
    if (record == null) throw unavailable("reference image is not owned by the task user");
    ObjectStorage.ObjectData data = storage.get(record.objectKey()).orElseThrow(
        () -> unavailable("reference image object is unavailable"));
    if (data.bytes() == null || data.bytes().length == 0) throw unavailable("reference image is empty");
    return new ReferenceImage(record.id(), data.bytes(), record.mimeType());
  }

  private static GenerationProviderException unavailable(String message) {
    return new GenerationProviderException("REFERENCE_IMAGE_UNAVAILABLE", message, false);
  }
}
