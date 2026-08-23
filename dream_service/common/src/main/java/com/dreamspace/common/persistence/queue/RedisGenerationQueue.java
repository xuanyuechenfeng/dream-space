package com.dreamspace.common.persistence.queue;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public final class RedisGenerationQueue implements GenerationQueue {
  private static final Logger log = LoggerFactory.getLogger(RedisGenerationQueue.class);
  private final StringRedisTemplate redis;
  private final DreamSpaceProperties properties;
  private final StreamOperations<String, String, String> streams;

  public RedisGenerationQueue(StringRedisTemplate redis, DreamSpaceProperties properties) {
    this.redis = redis; this.properties = properties; this.streams = redis.opsForStream();
  }

  public void ensureGroup() {
    try {
      streams.createGroup(properties.redis().stream(), ReadOffset.from("0-0"), properties.redis().consumerGroup());
      log.atInfo().addKeyValue("stream", properties.redis().stream())
          .addKeyValue("consumerGroup", properties.redis().consumerGroup()).log("generation Redis consumer group created");
    } catch (Exception error) {
      if (isBusyGroup(error)) return;
      log.error("generation Redis consumer group initialization failed", error);
      throw error;
    }
  }

  @Override public String publish(GenerationJob job) {
    ensureGroup();
    MapRecord<String, String, String> record = MapRecord.create(properties.redis().stream(), java.util.Map.of(
        "taskId", job.taskId(), "attemptKey", job.attemptKey(), "attemptNumber", Integer.toString(job.attemptNumber()),
        "maxAttempts", Integer.toString(job.maxAttempts()), "schemaVersion", Integer.toString(job.schemaVersion())));
    RecordId id = streams.add(record);
    return id.getValue();
  }

  @Override public List<Delivery> read(String consumer, int count, Duration block) {
    ensureGroup();
    var records = streams.read(Consumer.from(properties.redis().consumerGroup(), consumer),
        org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(count).block(block),
        StreamOffset.create(properties.redis().stream(), ReadOffset.lastConsumed()));
    return toDeliveries(records, Map.of());
  }

  @Override public void acknowledge(String messageId) {
    long acknowledged = streams.acknowledge(properties.redis().stream(), properties.redis().consumerGroup(), RecordId.of(messageId));
    if (acknowledged != 1) log.atWarn().addKeyValue("messageId", messageId).addKeyValue("acknowledged", acknowledged)
        .log("generation Redis acknowledgement did not acknowledge one message");
  }

  @Override public long pendingCount() {
    ensureGroup();
    return streams.pending(properties.redis().stream(), properties.redis().consumerGroup(),
        Range.unbounded(), 10_000).size();
  }

  @Override public List<Delivery> reclaim(String consumer, Duration minIdle, int count) {
    ensureGroup();
    var pending = streams.pending(properties.redis().stream(), properties.redis().consumerGroup(),
        Range.unbounded(), Math.max(count, count * 4), minIdle);
    var eligible = pending.stream().filter(message -> {
      int exponent = Math.min(6, Math.max(0, (int) message.getTotalDeliveryCount() - 1));
      Duration backoff = minIdle.multipliedBy(1L << exponent);
      long jitterBound = Math.max(1, minIdle.toMillis() / 4);
      long jitter = Math.floorMod(message.getIdAsString().hashCode(), jitterBound);
      return message.getElapsedTimeSinceLastDelivery().compareTo(backoff.plusMillis(jitter)) >= 0;
    }).limit(count).toList();
    var ids = eligible.stream().map(message -> message.getId()).toArray(RecordId[]::new);
    if (ids.length == 0) return List.of();
    var counts = eligible.stream().collect(Collectors.toMap(message -> message.getIdAsString(), message -> (int) message.getTotalDeliveryCount() + 1));
    var claimed = streams.claim(properties.redis().stream(), properties.redis().consumerGroup(), consumer, minIdle, ids);
    return toDeliveries(claimed, counts);
  }

  private List<Delivery> toDeliveries(List<MapRecord<String, String, String>> records, Map<String, Integer> deliveryCounts) {
    List<Delivery> result = new ArrayList<>();
    for (var record : records) {
      String taskId = record.getValue().get("taskId");
      String id = record.getId().getValue();
      if (taskId != null) result.add(new Delivery(id, new GenerationJob(taskId,
          record.getValue().getOrDefault("attemptKey", taskId + ":1"),
          parseInt(record.getValue().get("attemptNumber"), -1),
          parseInt(record.getValue().get("maxAttempts"), -1),
          parseInt(record.getValue().get("schemaVersion"), -1)), deliveryCounts.getOrDefault(id, 1)));
    }
    return result;
  }

  private static int parseInt(String value, int fallback) {
    try { return value == null ? fallback : Integer.parseInt(value); }
    catch (NumberFormatException ignored) { return fallback; }
  }

  private static boolean isBusyGroup(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) return true;
    }
    return false;
  }
}
