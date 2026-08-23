package com.dreamspace.common.persistence;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

final class DockerTestSupport {
  private DockerTestSupport() {}

  static void requireDocker() throws InterruptedException {
    boolean available = dockerAvailable();
    if (!available && "true".equalsIgnoreCase(System.getenv("CI"))) {
      fail("Docker is required for Testcontainers in CI");
    }
    Assumptions.assumeTrue(available, "Docker is required for integration tests");
  }

  private static boolean dockerAvailable() throws InterruptedException {
    try {
      Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
          .redirectErrorStream(true).start();
      return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
    } catch (IOException unavailable) {
      return false;
    }
  }
}
