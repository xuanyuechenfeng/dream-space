package com.dreamspace.worker.generation;

import java.time.Duration;

/** Time limits for model calls whose purpose is safety or readiness detection. */
public final class ModelTimeouts {
  public static final Duration DETECTION = Duration.ofSeconds(90);

  private ModelTimeouts() {}
}
