package com.dreamspace.common.persistence.queue;

import java.time.Duration;
import java.util.List;

public interface GenerationQueue {
  String publish(GenerationJob job);
  List<Delivery> read(String consumer, int count, Duration block);
  void acknowledge(String messageId);
  List<Delivery> reclaim(String consumer, Duration minIdle, int count);
  record Delivery(String messageId, GenerationJob job, int deliveryCount) {}
}
