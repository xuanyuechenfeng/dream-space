package com.dreamspace.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.dreamspace.common.persistence.queue.GenerationJob;
import com.dreamspace.common.persistence.queue.RedisGenerationQueue;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisGenerationQueueIntegrationTest {
  @Test
  void publishReadAndAcknowledgePreservesTheQueueContract() throws Exception {
    DockerTestSupport.requireDocker();
    try (var redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)) {
      redis.start();
      var connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
      connection.afterPropertiesSet();
      connection.start();
      try {
        var template = new StringRedisTemplate(connection);
        template.afterPropertiesSet();
        String stream = "quality-generation-" + UUID.randomUUID();
        var properties = new DreamSpaceProperties("mock", null,
            new DreamSpaceProperties.Redis(null, stream, "quality-workers", Duration.ofMillis(50)),
            null, null, null, null);
        var queue = new RedisGenerationQueue(template, properties);

        String messageId = queue.publish(new GenerationJob("task-1", "task-1:1", 1, 3, 1));
        var deliveries = queue.read("consumer-1", 10, Duration.ofSeconds(1));

        assertThat(deliveries).singleElement().satisfies(delivery -> {
          assertThat(delivery.messageId()).isEqualTo(messageId);
          assertThat(delivery.job().taskId()).isEqualTo("task-1");
          assertThat(delivery.job().attemptKey()).isEqualTo("task-1:1");
          assertThat(delivery.deliveryCount()).isEqualTo(1);
        });
        queue.acknowledge(messageId);
        assertThat(template.opsForStream().pending(stream, "quality-workers").getTotalPendingMessages()).isZero();
      } finally {
        connection.destroy();
      }
    }
  }
}
