package com.dreamspace.worker.generation;

import com.dreamspace.persistence.config.DreamSpaceProperties;
import com.dreamspace.persistence.queue.GenerationQueue;
import com.dreamspace.persistence.queue.RedisGenerationQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GenerationWorkerConfiguration {
  @Bean
  GenerationQueue redisGenerationQueue(StringRedisTemplate redis, DreamSpaceProperties properties) {
    return new RedisGenerationQueue(redis, properties);
  }

  @Bean
  GenerationProvider generationProvider(DreamSpaceProperties properties,
      ObjectProvider<ChatModel> chatModels, ObjectMapper json,
      @Value("${dream-space.worker.mock-delay-ms:0}") long mockDelayMillis,
      @Value("${dream-space.worker.provider-timeout:PT30S}") Duration providerTimeout) {
    if (!properties.externalServicesEnabled()) return new DeterministicMockProvider(mockDelayMillis);
    ChatModel chatModel = chatModels.getIfAvailable();
    if (chatModel == null) throw new IllegalStateException("ChatModel is required when external services are enabled");
    return new OpenAiCompatibleGenerationProvider(chatModel, json, providerTimeout);
  }
}
