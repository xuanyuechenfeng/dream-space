package com.dreamspace.common.persistence.config;

import java.time.Duration;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "dream-space")
public record DreamSpaceProperties(
    Redis redis,
    Storage storage,
    Queue queue,
    Auth auth,
    Email email,
    Quota quota,
    Ai ai,
    Security security) {

  public DreamSpaceProperties(Redis redis, Storage storage, Queue queue,
      Auth auth, Quota quota) {
    this(redis, storage, queue, auth, null, quota, null, null);
  }

  public DreamSpaceProperties(Redis redis, Storage storage, Queue queue,
      Auth auth, Quota quota, Ai ai) {
    this(redis, storage, queue, auth, null, quota, ai, null);
  }

  public DreamSpaceProperties(Redis redis, Storage storage, Queue queue,
      Auth auth, Quota quota, Ai ai, Security security) {
    this(redis, storage, queue, auth, null, quota, ai, security);
  }

  @ConstructorBinding
  public DreamSpaceProperties {
    redis = redis == null ? new Redis(null, "generation", "generation-workers", Duration.ofSeconds(30)) : redis;
    storage = storage == null ? new Storage("local", null, null) : storage;
    queue = queue == null ? new Queue(3, Duration.ofMillis(500)) : queue;
    auth = auth == null ? new Auth(300, 30) : auth;
    email = email == null ? new Email(null, 600, 5, 5) : email;
    quota = quota == null ? new Quota(100) : quota;
    ai = ai == null ? new Ai(new Planning(false, 2),
        new Image(false, null, null, null, null, "/v1/images/generations", Duration.ofSeconds(60), 3),
        new Harness(3, 0.8, false, 7)) : ai;
    security = security == null ? new Security(false, null) : security;
  }

  public record Redis(String url, String stream, String consumerGroup, Duration reclaimIdle) {
    public URI uri() { return url == null || url.isBlank() ? null : URI.create(url); }
  }

  public record Storage(String mode, String localDirectory, Sftp sftp) {
    public Storage {
      mode = mode == null || mode.isBlank() ? "local" : mode.trim().toLowerCase();
      sftp = sftp == null ? new Sftp(null, 22, null, null, null, null, null, true,
          "/dream-space", Duration.ofSeconds(10), Duration.ofSeconds(60), 3) : sftp;
    }
    public boolean isSftp() { return "sftp".equalsIgnoreCase(mode); }
    public boolean isLocal() { return "local".equalsIgnoreCase(mode); }
  }

  public record Sftp(String host, int port, String username, String password,
      String privateKeyFile, String privateKeyPassphrase, String knownHostsFile,
      boolean strictHostKeyChecking, String rootDirectory, Duration connectTimeout,
      Duration operationTimeout, int maxAttempts) {
    public Sftp {
      port = port < 1 || port > 65535 ? 22 : port;
      rootDirectory = rootDirectory == null || rootDirectory.isBlank() ? "/dream-space" : rootDirectory.trim();
      connectTimeout = connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
          ? Duration.ofSeconds(10) : connectTimeout;
      operationTimeout = operationTimeout == null || operationTimeout.isNegative() || operationTimeout.isZero()
          ? Duration.ofSeconds(60) : operationTimeout;
      maxAttempts = Math.max(1, maxAttempts);
    }
  }

  public record Queue(int maxAttempts, Duration retryBackoff) {
    public Queue { if (maxAttempts < 1) maxAttempts = 3; }
  }

  public record Auth(long codeTtlSeconds, long sessionDays, long captchaTtlSeconds,
      int captchaMaxAttempts, int captchaIssueLimitPerMinute) {
    public Auth(long codeTtlSeconds, long sessionDays) {
      this(codeTtlSeconds, sessionDays, 300, 5, 10);
    }

    public Auth {
      if (codeTtlSeconds < 30) codeTtlSeconds = 300;
      if (sessionDays < 1) sessionDays = 30;
      if (captchaTtlSeconds < 30) captchaTtlSeconds = 300;
      if (captchaMaxAttempts < 1) captchaMaxAttempts = 5;
      if (captchaIssueLimitPerMinute < 1) captchaIssueLimitPerMinute = 10;
    }
  }

  public record Email(String from, long codeTtlSeconds, int codeMaxAttempts,
      int codeIssueLimitPerMinute) {
    public Email {
      if (codeTtlSeconds < 60) codeTtlSeconds = 600;
      if (codeMaxAttempts < 1) codeMaxAttempts = 5;
      if (codeIssueLimitPerMinute < 1) codeIssueLimitPerMinute = 5;
    }
  }

  public record Security(boolean secureCookies, String preflightTokenSecret) {}

  public record Quota(int initialTotal) {}

  public record Ai(Planning planning, Image image, Harness harness) {}

  public record Planning(boolean enabled, int maxAttempts) {}

  public record Image(boolean enabled, String provider, String baseUrl, String apiKey, String model,
      String endpoint, Duration timeout, int maxAttempts) {}

  public record Harness(int maxLoopIterations, double acceptScore, boolean failOnClarification,
      int artifactRetentionDays) {}

}
