package com.dreamspace.common.persistence.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SftpObjectStorageTest {
  @Test
  void requiresAuthenticationConfiguration() {
    var properties = new DreamSpaceProperties.Sftp("sftp.example", 22, "worker", null,
        null, null, null, false, "/dream-space", Duration.ofSeconds(10), Duration.ofSeconds(60), 1);

    assertThatThrownBy(() -> new SftpObjectStorage(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password or private key");
  }

  @Test
  void requiresKnownHostsForStrictChecking() {
    var properties = new DreamSpaceProperties.Sftp("sftp.example", 22, "worker", "password",
        null, null, null, true, "/dream-space", Duration.ofSeconds(10), Duration.ofSeconds(60), 1);

    assertThatThrownBy(() -> new SftpObjectStorage(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("known_hosts");
  }

  @Test
  void rejectsUnsafeRemoteRootBeforeConnecting() {
    var properties = new DreamSpaceProperties.Sftp("sftp.example", 22, "worker", "password",
        null, null, null, false, "../dream-space", Duration.ofSeconds(10), Duration.ofSeconds(60), 1);
    var storage = new SftpObjectStorage(properties);

    assertThatThrownBy(() -> storage.get("results/task-1/result.webp"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("absolute safe path");
  }
}
