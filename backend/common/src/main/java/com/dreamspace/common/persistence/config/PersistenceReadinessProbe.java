package com.dreamspace.common.persistence.config;

import com.dreamspace.common.ReadinessProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** Checks required external dependencies only when a live profile is enabled. */
@Component
public final class PersistenceReadinessProbe implements ReadinessProbe {
  private final DreamSpaceProperties properties;
  private final DataSource dataSource;
  private final RedisConnectionFactory redisConnectionFactory;
  private final S3Client s3Client;

  public PersistenceReadinessProbe(DreamSpaceProperties properties, DataSource dataSource,
      RedisConnectionFactory redisConnectionFactory,
      org.springframework.beans.factory.ObjectProvider<S3Client> s3Client) {
    this.properties = properties;
    this.dataSource = dataSource;
    this.redisConnectionFactory = redisConnectionFactory;
    this.s3Client = s3Client.getIfAvailable();
  }

  @Override
  public boolean ready() {
    if (!properties.externalServicesEnabled()) return true;
    return databaseReady() && redisReady() && storageReady();
  }

  private boolean databaseReady() {
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT 1")) {
      return result.next();
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean redisReady() {
    try (RedisConnection connection = redisConnectionFactory.getConnection()) {
      return "PONG".equalsIgnoreCase(connection.ping());
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean storageReady() {
    var storage = properties.storage();
    if (storage.isS3()) {
      if (s3Client == null || storage.bucket() == null || storage.bucket().isBlank()) return false;
      try {
        s3Client.headBucket(HeadBucketRequest.builder().bucket(storage.bucket()).build());
        return true;
      } catch (Exception ignored) {
        return false;
      }
    }
    Path root = Path.of(storage.localDirectory() == null || storage.localDirectory().isBlank()
        ? "./var/objects" : storage.localDirectory());
    return Files.isDirectory(root) && Files.isReadable(root) && Files.isWritable(root);
  }
}
