package com.dreamspace.common.persistence.config;

import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.image.PngImageWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreamspace.common.persistence.storage.LocalObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.SftpObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import java.nio.file.Path;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import com.dreamspace.common.persistence.quota.QuotaMapper;
import com.dreamspace.common.persistence.quota.QuotaLedgerMapper;
import com.dreamspace.common.persistence.quota.QuotaTransactionService;
import com.dreamspace.common.persistence.queue.GenerationQueue;
import com.dreamspace.common.persistence.queue.RedisGenerationQueue;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@MapperScan({
    "com.dreamspace.common.persistence.generation",
    "com.dreamspace.common.persistence.quota",
    "com.dreamspace.common.persistence.moderation"
})
@EnableConfigurationProperties(DreamSpaceProperties.class)
public class SharedPersistenceConfiguration {
  @Bean
  @ConditionalOnMissingBean(WebpImageWriter.class)
  WebpImageWriter webpImageWriter() {
    return new WebpImageWriter();
  }

  @Bean
  @ConditionalOnMissingBean(PngImageWriter.class)
  PngImageWriter pngImageWriter() {
    return new PngImageWriter();
  }

  @Bean
  @ConditionalOnMissingBean(QuotaTransactionService.class)
  QuotaTransactionService quotaTransactionService(QuotaMapper accounts, QuotaLedgerMapper ledger) {
    return new QuotaTransactionService(accounts, ledger);
  }

  @Bean
  @ConditionalOnMissingBean(GenerationQueue.class)
  GenerationQueue generationQueue(StringRedisTemplate redis, DreamSpaceProperties properties) {
    return new RedisGenerationQueue(redis, properties);
  }

  @Bean
  @ConditionalOnMissingBean(ObjectMapper.class)
  ObjectMapper persistenceObjectMapper() { return new ObjectMapper(); }

  @Bean
  ConfigurationCustomizer myBatisConfigurationCustomizer(ObjectMapper objectMapper) {
    return configuration -> {
      configuration.setMapUnderscoreToCamelCase(false);
      configuration.setArgNameBasedConstructorAutoMapping(true);
      configuration.getTypeHandlerRegistry().register(
          com.fasterxml.jackson.databind.JsonNode.class,
          new com.dreamspace.common.persistence.typehandler.JsonNodeTypeHandler(objectMapper));
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.InspirationCategory.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.InspirationStatus.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.InspirationSourceType.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.GenerationTaskStatus.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.QuotaLedgerType.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.AdminRole.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.ModerationStatus.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.GenerationRatio.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.GenerationResolution.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.QuotaReconciliationRunStatus.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.QuotaReconciliationFindingKind.class);
      registerEnum(configuration, com.dreamspace.common.persistence.database.DatabaseEnums.QuotaReconciliationFindingStatus.class);
    };
  }

  private static <E extends Enum<E> & com.dreamspace.common.persistence.database.DatabaseValue> void registerEnum(
      org.apache.ibatis.session.Configuration configuration, Class<E> type) {
    configuration.getTypeHandlerRegistry().register(type,
        new com.dreamspace.common.persistence.typehandler.DatabaseEnumTypeHandler<>(type));
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(ObjectStorage.class)
  ObjectStorage objectStorage(DreamSpaceProperties properties) {
    if (properties.storage().isLocal()) {
      String directory = properties.storage().localDirectory();
      return new LocalObjectStorage(Path.of(directory == null || directory.isBlank() ? "./var/objects" : directory));
    }
    if (properties.storage().isSftp()) return new SftpObjectStorage(properties.storage().sftp());
    throw new IllegalStateException("dream-space.storage.mode must be local or sftp");
  }

  @Bean
  ObjectStorageFactory objectStorageFactory(ObjectStorage storage) {
    return new ObjectStorageFactory(storage);
  }

  @Bean
  @ConditionalOnMissingBean(PersistenceReadinessProbe.class)
  PersistenceReadinessProbe persistenceReadinessProbe(DataSource dataSource, RedisConnectionFactory redisConnectionFactory,
      ObjectStorage storage) {
    return new PersistenceReadinessProbe(dataSource, redisConnectionFactory, storage);
  }

  @Bean("persistenceReadiness")
  @ConditionalOnMissingBean(name = "persistenceReadiness")
  PersistenceReadinessHealthIndicator persistenceReadinessHealthIndicator(PersistenceReadinessProbe probe) {
    return new PersistenceReadinessHealthIndicator(probe);
  }

}
