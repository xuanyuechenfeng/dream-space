package com.dreamspace.persistence.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dreamspace.persistence.storage.LocalObjectStorage;
import com.dreamspace.persistence.storage.ObjectStorage;
import com.dreamspace.persistence.storage.S3ObjectStorage;
import com.dreamspace.persistence.storage.ObjectStorageFactory;
import java.net.URI;
import java.nio.file.Path;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import com.dreamspace.persistence.quota.QuotaMapper;
import com.dreamspace.persistence.quota.QuotaLedgerMapper;
import com.dreamspace.persistence.quota.QuotaTransactionService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@MapperScan({
    "com.dreamspace.persistence.auth",
    "com.dreamspace.persistence.inspiration",
    "com.dreamspace.persistence.generation",
    "com.dreamspace.persistence.quota",
    "com.dreamspace.persistence.reconciliation",
    "com.dreamspace.persistence.admin",
    "com.dreamspace.persistence.upload"
})
@EnableConfigurationProperties(DreamSpaceProperties.class)
public class PersistenceConfiguration {
  @Bean
  @ConditionalOnMissingBean(QuotaTransactionService.class)
  QuotaTransactionService quotaTransactionService(QuotaMapper accounts, QuotaLedgerMapper ledger) {
    return new QuotaTransactionService(accounts, ledger);
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
          new com.dreamspace.persistence.typehandler.JsonNodeTypeHandler(objectMapper));
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.InspirationCategory.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.InspirationStatus.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.InspirationSourceType.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.GenerationTaskStatus.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.QuotaLedgerType.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.AdminRole.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.ModerationStatus.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.GenerationRatio.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.GenerationResolution.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationRunStatus.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationFindingKind.class);
      registerEnum(configuration, com.dreamspace.persistence.database.DatabaseEnums.QuotaReconciliationFindingStatus.class);
    };
  }

  private static <E extends Enum<E> & com.dreamspace.persistence.database.DatabaseValue> void registerEnum(
      org.apache.ibatis.session.Configuration configuration, Class<E> type) {
    configuration.getTypeHandlerRegistry().register(type,
        new com.dreamspace.persistence.typehandler.DatabaseEnumTypeHandler<>(type));
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(ObjectStorage.class)
  @ConditionalOnProperty(prefix = "dream-space.storage", name = "mode", havingValue = "local", matchIfMissing = true)
  ObjectStorage localObjectStorage(DreamSpaceProperties properties) {
    String directory = properties.storage().localDirectory();
    return new LocalObjectStorage(Path.of(directory == null || directory.isBlank() ? "./var/objects" : directory));
  }

  @Bean
  ObjectStorageFactory objectStorageFactory(ObjectStorage storage) {
    return new ObjectStorageFactory(storage);
  }

  @Bean
  @ConditionalOnProperty(prefix = "dream-space.storage", name = "mode", havingValue = "s3")
  S3Client s3Client(DreamSpaceProperties properties) {
    var storage = properties.storage();
    var builder = S3Client.builder()
        .region(Region.of(storage.region() == null || storage.region().isBlank() ? "us-east-1" : storage.region()))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    if (storage.endpoint() != null && !storage.endpoint().isBlank()) builder.endpointOverride(URI.create(storage.endpoint()));
    if (storage.accessKey() != null && !storage.accessKey().isBlank() && storage.secretKey() != null && !storage.secretKey().isBlank()) {
      builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "dream-space.storage", name = "mode", havingValue = "s3")
  S3Presigner s3Presigner(DreamSpaceProperties properties) {
    var storage = properties.storage();
    var builder = S3Presigner.builder()
        .region(Region.of(storage.region() == null || storage.region().isBlank() ? "us-east-1" : storage.region()));
    if (storage.endpoint() != null && !storage.endpoint().isBlank()) builder.endpointOverride(URI.create(storage.endpoint()));
    if (storage.accessKey() != null && !storage.accessKey().isBlank() && storage.secretKey() != null && !storage.secretKey().isBlank()) {
      builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "dream-space.storage", name = "mode", havingValue = "s3")
  ObjectStorage s3ObjectStorage(S3Client client, S3Presigner presigner, DreamSpaceProperties properties) {
    return new S3ObjectStorage(client, presigner, properties.storage().bucket());
  }
}
