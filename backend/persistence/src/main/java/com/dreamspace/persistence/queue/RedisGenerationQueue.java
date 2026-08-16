package com.dreamspace.persistence.queue;

import com.dreamspace.persistence.config.DreamSpaceProperties;
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

@Component
public final class RedisGenerationQueue implements GenerationQueue {
  private final StringRedisTemplate redis;
  private final DreamSpaceProperties properties;
  private final StreamOperations<String, String, String> streams;

  public RedisGenerationQueue(StringRedisTemplate redis, DreamSpaceProperties properties) {
    this.redis = redis; this.properties = properties; this.streams = redis.opsForStream();
  }

  public void ensureGroup() {
    try { streams.createGroup(properties.redis().stream(), ReadOffset.from("0-0"), properties.redis().consumerGroup()); }
    catch (Exception ignored) { /* BUSYGROUP means the desired group already exists. */ }
  }

  @Override public String publish(GenerationJob job) {
    ensureGroup();
    MapRecord<String, String, String> record = MapRecord.create(properties.redis().stream(), java.util.Map.of("taskId", job.taskId()));
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

  @Override public void acknowledge(String messageId) { streams.acknowledge(properties.redis().stream(), properties.redis().consumerGroup(), RecordId.of(messageId)); }

  @Override public List<Delivery> reclaim(String consumer, Duration minIdle, int count) {
    ensureGroup();
    var pending = streams.pending(properties.redis().stream(), properties.redis().consumerGroup(), Range.unbounded(), count, minIdle);
    var ids = pending.stream().map(message -> message.getId()).toArray(RecordId[]::new);
    if (ids.length == 0) return List.of();
    var counts = pending.stream().collect(Collectors.toMap(message -> message.getIdAsString(), message -> (int) message.getTotalDeliveryCount() + 1));
    var claimed = streams.claim(properties.redis().stream(), properties.redis().consumerGroup(), consumer, minIdle, ids);
    return toDeliveries(claimed, counts);
  }

  private List<Delivery> toDeliveries(List<MapRecord<String, String, String>> records, Map<String, Integer> deliveryCounts) {
    List<Delivery> result = new ArrayList<>();
    for (var record : records) {
      String taskId = record.getValue().get("taskId");
      String id = record.getId().getValue();
      if (taskId != null) result.add(new Delivery(id, new GenerationJob(taskId), deliveryCounts.getOrDefault(id, 1)));
    }
    return result;
  }
}
