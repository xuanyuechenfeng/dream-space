package com.dreamspace.common.persistence.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public final class S3ObjectStorage implements ObjectStorage {
  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
    this.client = client; this.presigner = presigner; this.bucket = bucket;
  }

  @Override public void put(String key, byte[] data, String contentType) {
    ObjectKeyPolicy.validate(key);
    client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromBytes(data));
  }
  @Override public Optional<ObjectData> get(String key) {
    ObjectKeyPolicy.validate(key);
    try {
      var response = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
      return Optional.of(new ObjectData(response.asByteArray(), response.response().contentType()));
    } catch (NoSuchKeyException e) { return Optional.empty(); }
    catch (S3Exception e) { if (e.statusCode() == 404) return Optional.empty(); throw e; }
  }
  @Override public void delete(String key) {
    ObjectKeyPolicy.validate(key);
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }
  @Override public URI createSignedGetUrl(String key, long ttlSeconds) {
    ObjectKeyPolicy.validate(key);
    if (ttlSeconds <= 0 || ttlSeconds > 3600) throw new IllegalArgumentException("signed URL TTL must be 1..3600 seconds");
    var request = GetObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(ttlSeconds))
        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build()).build();
    return URI.create(presigner.presignGetObject(request).url().toString());
  }
}
