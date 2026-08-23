package com.dreamspace.common.persistence.storage;

import com.dreamspace.common.persistence.config.DreamSpaceProperties;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Object storage backed by an SFTP server. Logical keys never escape the configured root. */
public final class SftpObjectStorage implements ObjectStorage {
  private final DreamSpaceProperties.Sftp properties;

  public SftpObjectStorage(DreamSpaceProperties.Sftp properties) {
    this.properties = properties;
    validateConfiguration(properties);
  }

  @Override
  public void put(String key, byte[] data, String contentType) {
    String target = remotePath(key);
    String temporary = target + ".upload-" + UUID.randomUUID();
    withChannel(channel -> {
      ensureDirectories(channel, parent(target));
      try {
        channel.put(new ByteArrayInputStream(data), temporary, ChannelSftp.OVERWRITE);
        channel.rename(temporary, target);
      } finally {
        removeQuietly(channel, temporary);
      }
      return null;
    });
  }

  @Override
  public Optional<ObjectData> get(String key) {
    String target = remotePath(key);
    return withChannel(channel -> {
      try {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        channel.get(target, output);
        return Optional.of(new ObjectData(output.toByteArray(), contentType(key)));
      } catch (SftpException error) {
        if (error.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return Optional.empty();
        throw error;
      }
    });
  }

  @Override
  public void delete(String key) {
    String target = remotePath(key);
    withChannel(channel -> {
      removeIfExists(channel, target);
      return null;
    });
  }

  @Override
  public boolean ready() {
    return withChannel(channel -> {
      ensureDirectories(channel, root());
      return true;
    });
  }

  @Override
  public URI createSignedGetUrl(String key, long ttlSeconds) {
    throw new UnsupportedOperationException("SFTP storage does not provide signed URLs");
  }

  private String remotePath(String key) {
    ObjectKeyPolicy.validate(key);
    return root() + "/" + key;
  }

  private String root() {
    String value = properties.rootDirectory().replace('\\', '/').trim();
    while (value.endsWith("/") && value.length() > 1) value = value.substring(0, value.length() - 1);
    if (value.isBlank() || value.contains("..") || !value.startsWith("/")) {
      throw new IllegalArgumentException("SFTP root directory must be an absolute safe path");
    }
    return value;
  }

  private static String parent(String path) {
    int index = path.lastIndexOf('/');
    return index <= 0 ? "/" : path.substring(0, index);
  }

  private static String contentType(String key) {
    return key.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : key.toLowerCase(Locale.ROOT).endsWith(".webp") ? "image/webp" : "application/octet-stream";
  }

  private static void ensureDirectories(ChannelSftp channel, String path) throws SftpException {
    String normalized = path.replace('\\', '/');
    String[] parts = normalized.split("/");
    String current = normalized.startsWith("/") ? "" : ".";
    for (String part : parts) {
      if (part.isBlank()) continue;
      current += "/" + part;
      try {
        channel.stat(current);
      } catch (SftpException missing) {
        if (missing.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw missing;
        channel.mkdir(current);
      }
    }
  }

  private static void removeQuietly(ChannelSftp channel, String path) {
    try {
      channel.rm(path);
    } catch (SftpException ignored) {
      // Cleanup is best effort; the original operation owns the failure.
    }
  }

  private static void removeIfExists(ChannelSftp channel, String path) throws SftpException {
    try {
      channel.rm(path);
    } catch (SftpException error) {
      if (error.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw error;
    }
  }

  private static void validateConfiguration(DreamSpaceProperties.Sftp value) {
    if (value == null || blank(value.host()) || blank(value.username())) {
      throw new IllegalArgumentException("SFTP host and username are required");
    }
    if (blank(value.password()) && blank(value.privateKeyFile())) {
      throw new IllegalArgumentException("SFTP password or private key is required");
    }
    if (value.strictHostKeyChecking() && blank(value.knownHostsFile())) {
      throw new IllegalArgumentException("SFTP known_hosts file is required when strict host checking is enabled");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private <T> T withChannel(ChannelOperation<T> operation) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
      Session session = null;
      ChannelSftp channel = null;
      try {
        JSch jsch = new JSch();
        if (properties.knownHostsFile() != null && !properties.knownHostsFile().isBlank()) {
          jsch.setKnownHosts(properties.knownHostsFile());
        }
        if (properties.privateKeyFile() != null && !properties.privateKeyFile().isBlank()) {
          byte[] passphrase = properties.privateKeyPassphrase() == null
              ? null : properties.privateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
          if (passphrase == null || passphrase.length == 0) jsch.addIdentity(properties.privateKeyFile());
          else jsch.addIdentity(properties.privateKeyFile(), passphrase);
        }
        session = jsch.getSession(properties.username(), properties.host(), properties.port());
        if (properties.password() != null && !properties.password().isBlank()) session.setPassword(properties.password());
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", properties.strictHostKeyChecking() ? "yes" : "no");
        session.setConfig(config);
        session.connect((int) properties.connectTimeout().toMillis());
        channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect((int) properties.operationTimeout().toMillis());
        return operation.apply(channel);
      } catch (Exception error) {
        last = new IllegalStateException("SFTP storage operation failed", error);
        if (attempt == properties.maxAttempts()) throw last;
      } finally {
        if (channel != null) channel.disconnect();
        if (session != null) session.disconnect();
      }
    }
    throw last == null ? new IllegalStateException("SFTP storage operation failed") : last;
  }

  @FunctionalInterface
  private interface ChannelOperation<T> {
    T apply(ChannelSftp channel) throws Exception;
  }
}
