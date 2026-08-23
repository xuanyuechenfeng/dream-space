package com.dreamspace.api.service;

import com.dreamspace.api.common.ApiException;
import com.dreamspace.common.image.ImageProcessingException;
import com.dreamspace.common.image.WebpImageWriter;
import com.dreamspace.common.persistence.storage.ObjectStorage;
import com.dreamspace.common.persistence.storage.ObjectStorageFactory;
import com.dreamspace.api.persistence.upload.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private static final long MAX_BYTES = 10L * 1024 * 1024, MAX_PIXELS = 40_000_000L;
  private final ReferenceUploadMapper mapper;
  private final ObjectStorageFactory storageFactory;
  private final WebpImageWriter webp;
  public UploadService(ReferenceUploadMapper mapper, ObjectStorageFactory storageFactory,
      WebpImageWriter webp) {
    this.mapper = mapper;
    this.storageFactory = storageFactory;
    this.webp = webp;
  }
  public record Response(String id, String url, String filename, String mimeType, int width, int height, int byteSize, String checksumSha256) {}
  public Response create(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) throw bad("UPLOAD_FILE_REQUIRED", "请选择参考图"); if (file.getSize() > MAX_BYTES) throw bad("UPLOAD_TOO_LARGE", "参考图不能超过 10MB");
    String mime = file.getContentType(); if (!("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime))) throw bad("UPLOAD_MIME_INVALID", "仅支持 JPG、PNG、WebP");
    byte[] data; try { data = file.getBytes(); } catch (IOException e) { throw bad("UPLOAD_INVALID", "参考图读取失败"); }
    if (!magic(data, mime)) throw bad("UPLOAD_MAGIC_INVALID", "文件内容与类型不一致");
    WebpImageWriter.EncodedImage normalized;
    try {
      normalized = webp.normalize(data, MAX_PIXELS);
    } catch (ImageProcessingException error) {
      HttpStatus status = "IMAGE_CODEC_UNAVAILABLE".equals(error.code())
          ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
      throw bad(status, error.code(), "参考图处理失败");
    }
    int width = normalized.width(), height = normalized.height();
    byte[] normalizedBytes = normalized.data();
    String id = UUID.randomUUID().toString(), key = "references/" + userId + "/" + id + ".webp"; ObjectStorage storage = storageFactory.selected(); storage.put(key, normalizedBytes, "image/webp");
    try { String filename = java.nio.file.Paths.get(file.getOriginalFilename() == null ? "reference" : file.getOriginalFilename()).getFileName().toString(); String sum = normalized.checksumSha256(); ReferenceUploadRecord rec = new ReferenceUploadRecord(id, userId, key, filename, "image/webp", normalizedBytes.length, width, height, sum, null, null); mapper.insert(rec); return new Response(id, "/dream_web/uploads/references/" + id + "/content", filename, "image/webp", width, height, normalizedBytes.length, sum); } catch (RuntimeException e) { storage.delete(key); throw e; }
  }
  public ObjectStorage.ObjectData read(String userId, String id) { ReferenceUploadRecord r = mapper.findOwned(userId, id); if (r == null) throw bad("NOT_FOUND", "参考图不存在"); return storageFactory.selected().get(r.objectKey()).orElseThrow(() -> bad("NOT_FOUND", "参考图不存在")); }
  private static boolean magic(byte[] b, String mime) { if ("image/png".equals(mime)) return b.length > 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71; if ("image/jpeg".equals(mime)) return b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255; return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P'; }
  private static ApiException bad(String code, String message) { return bad(HttpStatus.BAD_REQUEST, code, message); }
  private static ApiException bad(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
