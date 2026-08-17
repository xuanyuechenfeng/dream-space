package com.dreamspace.common.persistence.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectKeyPolicyTest {
  @Test void acceptsApprovedWebpKeys() {
    assertThat(ObjectKeyPolicy.validate("references/user-1/file.webp")).isEqualTo("references/user-1/file.webp");
    assertThat(ObjectKeyPolicy.validate("results/task-1/result-1.webp")).isEqualTo("results/task-1/result-1.webp");
  }

  @Test void rejectsTraversalAndUnknownPrefixes() {
    assertThatThrownBy(() -> ObjectKeyPolicy.validate("../secret.webp")).hasMessage("invalid object key");
    assertThatThrownBy(() -> ObjectKeyPolicy.validate("references/user-1/../secret.webp")).hasMessage("invalid object key");
    assertThatThrownBy(() -> ObjectKeyPolicy.validate("avatars/user-1/file.webp")).hasMessage("invalid object key");
  }

  @Test void rejectsCharactersOutsideServerGeneratedKeyAlphabet() {
    assertThatThrownBy(() -> ObjectKeyPolicy.validate("results/task-1/id with space.webp"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ObjectKeyPolicy.validate("results/task-1/id?download.webp"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void localWritesAreReadableAndDeletesAreIdempotent() throws Exception {
    Path root = Files.createTempDirectory("dream-space-storage-");
    var storage = new LocalObjectStorage(root);
    storage.put("references/user-1/file.webp", new byte[] {1, 2, 3}, "image/webp");
    assertThat(storage.get("references/user-1/file.webp")).get().extracting(ObjectStorage.ObjectData::bytes).isEqualTo(new byte[] {1, 2, 3});
    storage.delete("references/user-1/file.webp");
    storage.delete("references/user-1/file.webp");
    assertThat(storage.get("references/user-1/file.webp")).isEmpty();
  }
}
