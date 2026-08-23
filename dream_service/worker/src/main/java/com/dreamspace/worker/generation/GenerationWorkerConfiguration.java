package com.dreamspace.worker.generation;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.dreamspace.worker.observability.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class GenerationWorkerConfiguration {
  private static final Logger log = LoggerFactory.getLogger(GenerationWorkerConfiguration.class);
  @Bean
  ImageGenerationModel imageGenerationModel(DreamSpaceProperties properties, ObjectMapper json,
      ReferenceImageLoader references, WorkerMetrics metrics) {
    requireStorageConfiguration(properties);
    DreamSpaceProperties.Image image = properties.ai().image();
    if (!image.enabled()) throw new IllegalStateException("AI_IMAGE_ENABLED must be true");
    requireValue(image.apiKey(), "AI_IMAGE_API_KEY"); requireValue(image.baseUrl(), "AI_IMAGE_BASE_URL");
    requireValue(image.model(), "AI_IMAGE_MODEL"); requireValue(image.endpoint(), "AI_IMAGE_ENDPOINT");
    logModelConfiguration("image", image.baseUrl(), image.endpoint(), image.model(), image.apiKey(), image.provider());
    return new OpenAiCompatibleImageGenerationModel(image.baseUrl(), image.apiKey(), image.model(), image.endpoint(),
        json, image.timeout(), image.maxAttempts(), references, metrics);
  }

  @Bean("detectionChatModel")
  ChatModel detectionChatModel(OpenAiConnectionProperties connection, OpenAiChatProperties chat) {
    OpenAiChatOptions options = chat.getOptions().copy();
    options.setBaseUrl(connection.getBaseUrl());
    options.setApiKey(connection.getApiKey());
    options.setCredential(connection.getCredential());
    options.setTimeout(ModelTimeouts.DETECTION);
    return OpenAiChatModel.builder().options(options).build();
  }

  @Bean
  PlanningModel planningModel(@Qualifier("openAiChatModel") ChatModel chatModel, ObjectMapper json, ReferenceImageLoader references,
      DreamSpaceProperties properties, OpenAiConnectionProperties connection,
      OpenAiChatProperties chat, WorkerMetrics metrics) {
    requireStorageConfiguration(properties);
    DreamSpaceProperties.Planning planning = properties.ai().planning();
    if (!planning.enabled()) throw new IllegalStateException("AI_PLANNING_ENABLED must be true");
    requireValue(connection.getApiKey(), "AI_PLANNING_API_KEY");
    requireValue(connection.getBaseUrl(), "AI_PLANNING_BASE_URL");
    requireValue(chat.getOptions().getModel(), "AI_PLANNING_MODEL");
    logModelConfiguration("planning", connection.getBaseUrl(), "/chat/completions", chat.getOptions().getModel(),
        connection.getApiKey(), "openai-compatible");
    return new ChatPlanningModel(chatModel, json, references, metrics, chat.getOptions().getModel());
  }

  @Bean
  ContentModerator contentModerator(@Qualifier("detectionChatModel") ChatModel chatModel, ObjectMapper json, ReferenceImageLoader references,
      DreamSpaceProperties properties) {
    requireStorageConfiguration(properties);
    return new ChatContentModerator(chatModel, json, references);
  }

  @Bean
  GenerationHarness generationHarness(GenerationWorkerStore store, PlanningModel planning,
      DreamSpaceProperties properties) {
    return new GenerationHarness(store, planning, properties.ai().harness().failOnClarification(),
        properties.ai().planning().maxAttempts());
  }

  @Bean
  LoopEngine loopEngine(GenerationWorkerStore store, ImageGenerationModel imageGenerationModel,
      DreamSpaceProperties properties) {
    return new LoopEngine(store, imageGenerationModel, properties.ai().harness().maxLoopIterations());
  }

  private static void requireStorageConfiguration(DreamSpaceProperties properties) {
    DreamSpaceProperties.Storage storage = properties.storage();
    if (storage.isLocal()) return;
    if (!storage.isSftp()) throw new IllegalStateException("OBJECT_STORAGE_MODE must be local or sftp");
    var sftp = storage.sftp();
    requireValue(sftp.host(), "SFTP_HOST");
    requireValue(sftp.username(), "SFTP_USERNAME");
    requireValue(sftp.rootDirectory(), "SFTP_ROOT_DIRECTORY");
    if ((sftp.password() == null || sftp.password().isBlank())
        && (sftp.privateKeyFile() == null || sftp.privateKeyFile().isBlank())) {
      throw new IllegalStateException("SFTP_PASSWORD or SFTP_PRIVATE_KEY_FILE must be configured");
    }
    if (sftp.strictHostKeyChecking()) requireValue(sftp.knownHostsFile(), "SFTP_KNOWN_HOSTS_FILE");
  }

  private static void requireValue(String value, String name) {
    if (value == null || value.isBlank() || value.startsWith("local-mock")) {
      throw new IllegalStateException(name + " must be configured for the real model");
    }
  }
  private static void logModelConfiguration(String type, String baseUrl, String endpoint,
      String model, String apiKey, String provider) {
    log.atInfo().addKeyValue("modelType", type).addKeyValue("provider", provider)
        .addKeyValue("requestUrl", joinUrl(baseUrl, endpoint)).addKeyValue("model", model)
        .addKeyValue("apiKey", apiKey == null || apiKey.isBlank() ? "<empty>" : "***").log("AI model configuration loaded");
  }

  private static String joinUrl(String baseUrl, String endpoint) {
    return baseUrl.replaceAll("/$", "") + "/" + endpoint.replaceAll("^/", "");
  }

}
