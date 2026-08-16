package com.dreamspace.worker.generation;

import com.dreamspace.persistence.storage.ObjectStorage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class GenerationOutputPipeline {
  private static final String WEBP_MIME = "image/webp";
  private final ObjectStorage storage;

  public GenerationOutputPipeline(ObjectStorage storage) {
    this.storage = storage;
    ImageIO.scanForPlugins();
  }

  public List<StoredGenerationResult> persist(WorkerTaskSnapshot task, List<ProviderImage> images) {
    validateProviderOutput(task, images);
    List<StoredGenerationResult> stored = new ArrayList<>();
    try {
      for (ProviderImage image : images) stored.add(persistOne(task, image));
      return List.copyOf(stored);
    } catch (RuntimeException error) {
      cleanup(stored);
      throw error;
    }
  }

  public void cleanup(List<StoredGenerationResult> results) {
    for (StoredGenerationResult result : results) {
      deleteQuietly(result.thumbnailObjectKey());
      deleteQuietly(result.objectKey());
    }
  }

  private StoredGenerationResult persistOne(WorkerTaskSnapshot task, ProviderImage image) {
    OutputDimensions dimensions = OutputDimensions.resolve(task.ratio(), task.resolution());
    BufferedImage decoded = decode(image.data());
    BufferedImage oriented = orient(decoded, ExifOrientation.read(image.data()));
    BufferedImage outputImage = cover(oriented, dimensions.width(), dimensions.height());
    byte[] output = encodeWebp(outputImage, 0.90f);
    int thumbnailWidth = Math.min(480, dimensions.width());
    int thumbnailHeight = Math.max(1, Math.round((float) dimensions.height() * thumbnailWidth / dimensions.width()));
    BufferedImage thumbnailImage = resize(outputImage, thumbnailWidth, thumbnailHeight);
    byte[] thumbnail = encodeWebp(thumbnailImage, 0.80f);

    String resultId = UUID.randomUUID().toString();
    String objectKey = "results/" + task.id() + "/" + resultId + ".webp";
    String thumbnailObjectKey = "thumbnails/" + task.id() + "/" + resultId + ".webp";
    storage.put(objectKey, output, WEBP_MIME);
    try {
      storage.put(thumbnailObjectKey, thumbnail, WEBP_MIME);
    } catch (RuntimeException error) {
      deleteQuietly(thumbnailObjectKey);
      deleteQuietly(objectKey);
      throw error;
    }
    return new StoredGenerationResult(resultId, image.index(),
        "/generation/results/" + resultId + "/content", objectKey, thumbnailObjectKey,
        sha256(output), dimensions.width(), dimensions.height(), WEBP_MIME, output.length,
        thumbnailWidth, thumbnailHeight, thumbnail.length);
  }

  private static void validateProviderOutput(WorkerTaskSnapshot task, List<ProviderImage> images) {
    if (images == null || images.isEmpty() || images.size() > task.imageCount()) {
      throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID",
          "provider returned an invalid image count", false);
    }
    Set<Integer> indexes = new HashSet<>();
    for (ProviderImage image : images) {
      if (image.index() >= task.imageCount() || !indexes.add(image.index())) {
        throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID",
            "provider returned an invalid image index", false);
      }
    }
  }

  private static BufferedImage decode(byte[] data) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
      if (image == null) throw new IOException("unsupported image data");
      return image;
    } catch (IOException error) {
      throw new GenerationProviderException("PROVIDER_OUTPUT_INVALID", "provider image cannot be decoded", false, error);
    }
  }

  private static BufferedImage cover(BufferedImage source, int targetWidth, int targetHeight) {
    double scale = Math.max((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
    int scaledWidth = Math.max(targetWidth, (int) Math.ceil(source.getWidth() * scale));
    int scaledHeight = Math.max(targetHeight, (int) Math.ceil(source.getHeight() * scale));
    BufferedImage scaled = resize(source, scaledWidth, scaledHeight);
    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.drawImage(scaled, (targetWidth - scaledWidth) / 2, (targetHeight - scaledHeight) / 2, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static BufferedImage resize(BufferedImage source, int width, int height) {
    BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static BufferedImage orient(BufferedImage source, int orientation) {
    if (orientation <= 1 || orientation > 8) return source;
    int width = source.getWidth();
    int height = source.getHeight();
    boolean swap = orientation >= 5 && orientation <= 8;
    BufferedImage target = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
    AffineTransform transform = switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
      default -> throw new IllegalStateException("unsupported EXIF orientation");
    };
    Graphics2D graphics = target.createGraphics();
    try { graphics.drawImage(source, transform, null); } finally { graphics.dispose(); }
    return target;
  }

  private static byte[] encodeWebp(BufferedImage image, float quality) {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(WEBP_MIME);
    if (!writers.hasNext()) throw new IllegalStateException("WebP ImageIO writer is unavailable");
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      ImageWriteParam parameters = writer.getDefaultWriteParam();
      if (parameters.canWriteCompressed()) {
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        if (parameters.getCompressionTypes() != null && parameters.getCompressionTypes().length > 0) {
          parameters.setCompressionType(parameters.getCompressionTypes()[0]);
        }
        parameters.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), parameters);
      output.flush();
      return bytes.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("WebP encoding failed", error);
    } finally {
      writer.dispose();
    }
  }

  private static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
  }

  private void deleteQuietly(String key) {
    try { storage.delete(key); } catch (RuntimeException ignored) { }
  }

  private static final class ExifOrientation {
    private ExifOrientation() {}

    static int read(byte[] bytes) {
      if (bytes.length < 12 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) return 1;
      int cursor = 2;
      while (cursor + 4 <= bytes.length) {
        if ((bytes[cursor] & 0xff) != 0xff) break;
        int marker = bytes[cursor + 1] & 0xff;
        int length = unsignedShort(bytes, cursor + 2, false);
        if (length < 2 || cursor + 2 + length > bytes.length) break;
        if (marker == 0xe1 && length >= 10 && asciiEquals(bytes, cursor + 4, "Exif\0\0")) {
          return readTiff(bytes, cursor + 10, cursor + 2 + length);
        }
        cursor += 2 + length;
      }
      return 1;
    }

    private static int readTiff(byte[] bytes, int offset, int limit) {
      if (offset + 8 > limit) return 1;
      boolean little = bytes[offset] == 'I' && bytes[offset + 1] == 'I';
      if (!little && !(bytes[offset] == 'M' && bytes[offset + 1] == 'M')) return 1;
      int ifd = offset + unsignedInt(bytes, offset + 4, little);
      if (ifd + 2 > limit) return 1;
      int count = unsignedShort(bytes, ifd, little);
      for (int i = 0; i < count; i++) {
        int entry = ifd + 2 + i * 12;
        if (entry + 12 > limit) return 1;
        if (unsignedShort(bytes, entry, little) == 0x0112) return unsignedShort(bytes, entry + 8, little);
      }
      return 1;
    }

    private static boolean asciiEquals(byte[] bytes, int offset, String expected) {
      if (offset + expected.length() > bytes.length) return false;
      for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
      return true;
    }

    private static int unsignedShort(byte[] bytes, int offset, boolean little) {
      return little ? (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
          : ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int unsignedInt(byte[] bytes, int offset, boolean little) {
      long value = little
          ? (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8) | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24)
          : ((bytes[offset] & 0xffL) << 24) | ((bytes[offset + 1] & 0xffL) << 16) | ((bytes[offset + 2] & 0xffL) << 8) | (bytes[offset + 3] & 0xffL);
      return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
  }
}
