package com.dreamspace.api;

import com.dreamspace.persistence.storage.ObjectStorage;
import com.dreamspace.persistence.storage.ObjectStorageFactory;
import com.dreamspace.persistence.upload.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private static final long MAX_BYTES = 10L * 1024 * 1024, MAX_PIXELS = 40_000_000L;
  private final ReferenceUploadMapper mapper; private final ObjectStorageFactory storageFactory;
  public UploadService(ReferenceUploadMapper mapper, ObjectStorageFactory storageFactory) { this.mapper = mapper; this.storageFactory = storageFactory; }
  public record Response(String id, String url, String filename, String mimeType, int width, int height, int byteSize, String checksumSha256) {}
  public Response create(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) throw bad("UPLOAD_FILE_REQUIRED", "请选择参考图"); if (file.getSize() > MAX_BYTES) throw bad("UPLOAD_TOO_LARGE", "参考图不能超过 10MB");
    String mime = file.getContentType(); if (!("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime))) throw bad("UPLOAD_MIME_INVALID", "仅支持 JPG、PNG、WebP");
    byte[] data; try { data = file.getBytes(); } catch (IOException e) { throw bad("UPLOAD_INVALID", "参考图读取失败"); }
    if (!magic(data, mime)) throw bad("UPLOAD_MAGIC_INVALID", "文件内容与类型不一致");
    BufferedImage image; try { image = ImageIO.read(new ByteArrayInputStream(data)); } catch (IOException e) { throw bad("UPLOAD_INVALID", "参考图已损坏"); }
    int width = image == null ? webpWidth(data) : image.getWidth(), height = image == null ? webpHeight(data) : image.getHeight();
    if (width <= 0 || height <= 0) throw bad("UPLOAD_INVALID", "参考图已损坏"); long pixels = (long) width * height; if (pixels > MAX_PIXELS) throw bad("UPLOAD_DIMENSIONS_INVALID", "图片像素超过限制");
    byte[] normalized = normalizeToWebp(image, data);
    String id = UUID.randomUUID().toString(), key = "references/" + userId + "/" + id + ".webp"; ObjectStorage storage = storageFactory.selected(); storage.put(key, normalized, "image/webp");
    try { String filename = java.nio.file.Paths.get(file.getOriginalFilename() == null ? "reference" : file.getOriginalFilename()).getFileName().toString(); String sum = HexFormat.of().formatHex(sha(normalized)); ReferenceUploadRecord rec = new ReferenceUploadRecord(id, userId, key, filename, "image/webp", normalized.length, width, height, sum, null, null); mapper.insert(rec); return new Response(id, "/uploads/references/" + id + "/content", filename, "image/webp", width, height, normalized.length, sum); } catch (RuntimeException e) { storage.delete(key); throw e; }
  }
  public ObjectStorage.ObjectData read(String userId, String id) { ReferenceUploadRecord r = mapper.findOwned(userId, id); if (r == null) throw bad("NOT_FOUND", "参考图不存在"); return storageFactory.selected().get(r.objectKey()).orElseThrow(() -> bad("NOT_FOUND", "参考图不存在")); }
  private static boolean magic(byte[] b, String mime) { if ("image/png".equals(mime)) return b.length > 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71; if ("image/jpeg".equals(mime)) return b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255; return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P'; }
  private static byte[] sha(byte[] b) { try { return MessageDigest.getInstance("SHA-256").digest(b); } catch (Exception e) { throw new IllegalStateException(e); } }
  private static byte[] normalizeToWebp(BufferedImage image, byte[] original) {
    if (image == null) return original;
    try {
      var writers = ImageIO.getImageWritersByFormatName("webp");
      if (!writers.hasNext()) return original;
      var output = new ByteArrayOutputStream();
      try (var imageOutput = ImageIO.createImageOutputStream(output)) {
        var writer = writers.next();
        try { writer.setOutput(imageOutput); writer.write(image); } finally { writer.dispose(); }
      }
      return output.toByteArray();
    } catch (IOException | RuntimeException ignored) {
      return original;
    }
  }
  private static int webpWidth(byte[] data) {
    if (data.length < 31 || data[12] != 'V' || data[13] != 'P' || data[14] != '8' || data[15] != 'X') return -1;
    return 1 + ((data[25] & 255) | ((data[26] & 255) << 8) | ((data[27] & 255) << 16));
  }
  private static int webpHeight(byte[] data) {
    if (data.length < 31 || data[12] != 'V' || data[13] != 'P' || data[14] != '8' || data[15] != 'X') return -1;
    return 1 + ((data[28] & 255) | ((data[29] & 255) << 8) | ((data[30] & 255) << 16));
  }
  private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}
