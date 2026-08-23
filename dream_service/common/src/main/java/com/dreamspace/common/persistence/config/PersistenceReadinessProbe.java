package com.dreamspace.common.persistence.config;

import com.dreamspace.common.ReadinessProbe;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Checks the database, queue and selected object storage used by the running process. */
@Component
public final class PersistenceReadinessProbe implements ReadinessProbe {
  private final DataSource dataSource;
  private final RedisConnectionFactory redisConnectionFactory;
  private final ObjectStorage storage;

  public PersistenceReadinessProbe(DataSource dataSource,
      RedisConnectionFactory redisConnectionFactory, ObjectStorage storage) {
    this.dataSource = dataSource;
    this.redisConnectionFactory = redisConnectionFactory;
    this.storage = storage;
  }

  @Override
  public boolean ready() {
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
    try { return storage.ready(); } catch (Exception ignored) { return false; }
  }
}
