package com.dreamspace.api.common;

import com.dreamspace.common.persistence.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ImageResponseSupport {
  private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

  private ImageResponseSupport() {}

  public static ResponseEntity<byte[]> inline(ObjectStorage.ObjectData data,
      HttpServletRequest request, String filename) {
    String contentType = data.contentType() == null || data.contentType().isBlank()
        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : data.contentType();
    String etag = etag(data.bytes());
    String disposition = filename == null || filename.isBlank()
        ? "inline" : "inline; filename=\"" + filename + "\"";
    if (matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(CACHE_CONTROL).eTag(etag)
          .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
          .header("X-Content-Type-Options", "nosniff").build();
    }
    return ResponseEntity.ok().cacheControl(CACHE_CONTROL).eTag(etag)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(contentType))
        .contentLength(data.bytes().length).body(data.bytes());
  }

  private static boolean matches(String header, String etag) {
    if (header == null || header.isBlank()) return false;
    for (String candidate : header.split(",")) {
      if (etag.equals(candidate.trim()) || "*".equals(candidate.trim())) return true;
    }
    return false;
  }

  private static String etag(byte[] bytes) {
    try {
      return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "\"";
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
