package com.dreamspace.persistence.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dream-space")
public record DreamSpaceProperties(
    String externalServicesMode,
    Database database,
    Redis redis,
    Storage storage,
    Queue queue,
    Auth auth,
    Quota quota) {

  public DreamSpaceProperties {
    externalServicesMode = valueOr(externalServicesMode, "mock");
    database = database == null ? new Database(null, null) : database;
    redis = redis == null ? new Redis(null, "generation", "generation-workers", Duration.ofSeconds(30)) : redis;
    storage = storage == null ? new Storage("local", null, null, null, null, null, null, 300) : storage;
    queue = queue == null ? new Queue(3, Duration.ofMillis(500)) : queue;
    auth = auth == null ? new Auth(300, 30) : auth;
    quota = quota == null ? new Quota(100) : quota;
  }

  public boolean isProduction() {
    return "production".equalsIgnoreCase(externalServicesMode)
        || "prod".equalsIgnoreCase(externalServicesMode);
  }

  public boolean externalServicesEnabled() {
    return "live".equalsIgnoreCase(externalServicesMode)
        || "real".equalsIgnoreCase(externalServicesMode)
        || isProduction();
  }

  public record Database(String url, String username) {
    public URI uri() { return url == null || url.isBlank() ? null : URI.create(url); }
  }

  public record Redis(String url, String stream, String consumerGroup, Duration reclaimIdle) {
    public URI uri() { return url == null || url.isBlank() ? null : URI.create(url); }
  }

  public record Storage(String mode, String localDirectory, String endpoint, String bucket,
      String region, String accessKey, String secretKey, long signedUrlTtlSeconds) {
    public boolean isS3() { return "s3".equalsIgnoreCase(mode); }
  }

  public record Queue(int maxAttempts, Duration retryBackoff) {
    public Queue { if (maxAttempts < 1) maxAttempts = 3; }
  }

  public record Auth(long codeTtlSeconds, long sessionDays) {}

  public record Quota(int initialTotal) {}

  private static String valueOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
