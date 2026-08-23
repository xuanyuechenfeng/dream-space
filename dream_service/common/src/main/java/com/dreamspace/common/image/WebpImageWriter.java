package com.dreamspace.common.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public final class WebpImageWriter {
  public static final String MIME_TYPE = "image/webp";

  public WebpImageWriter() {
    ImageIO.scanForPlugins();
    requireWriter();
  }

  public EncodedImage normalize(byte[] input, long maxPixels) {
    BufferedImage oriented = decodeAndOrient(input, maxPixels);
    byte[] encoded = encode(oriented, 0.90f);
    verify(encoded);
    return new EncodedImage(encoded, oriented.getWidth(), oriented.getHeight(), sha256(encoded),
        null, 0, 0);
  }

  public EncodedImage cover(byte[] input, int width, int height, int thumbnailMaxWidth,
      long maxPixels) {
    if (width < 1 || height < 1 || thumbnailMaxWidth < 1) {
      throw new IllegalArgumentException("output dimensions must be positive");
    }
    BufferedImage oriented = decodeAndOrient(input, maxPixels);
    BufferedImage output = cover(oriented, width, height);
    byte[] encoded = encode(output, 0.90f);
    int thumbnailWidth = Math.min(thumbnailMaxWidth, width);
    int thumbnailHeight = Math.max(1, Math.round((float) height * thumbnailWidth / width));
    byte[] thumbnail = encode(resize(output, thumbnailWidth, thumbnailHeight), 0.80f);
    verify(encoded);
    verify(thumbnail);
    return new EncodedImage(encoded, width, height, sha256(encoded), thumbnail,
        thumbnailWidth, thumbnailHeight);
  }

  public boolean available() {
    return ImageIO.getImageWritersByMIMEType(MIME_TYPE).hasNext();
  }

  private static BufferedImage decodeAndOrient(byte[] input, long maxPixels) {
    if (input == null || input.length == 0) {
      throw new ImageProcessingException("IMAGE_DECODE_FAILED", "image data is empty");
    }
    try {
      BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(input));
      if (decoded == null) throw new IOException("unsupported image data");
      long pixels = (long) decoded.getWidth() * decoded.getHeight();
      if (pixels <= 0 || pixels > maxPixels) {
        throw new ImageProcessingException("IMAGE_DIMENSIONS_INVALID", "image pixels exceed the configured limit");
      }
      return orient(decoded, ExifOrientation.read(input));
    } catch (ImageProcessingException error) {
      throw error;
    } catch (IOException | RuntimeException error) {
      throw new ImageProcessingException("IMAGE_DECODE_FAILED", "image cannot be decoded", error);
    }
  }

  private static BufferedImage cover(BufferedImage source, int targetWidth, int targetHeight) {
    double scale = Math.max((double) targetWidth / source.getWidth(),
        (double) targetHeight / source.getHeight());
    int scaledWidth = Math.max(targetWidth, (int) Math.ceil(source.getWidth() * scale));
    int scaledHeight = Math.max(targetHeight, (int) Math.ceil(source.getHeight() * scale));
    BufferedImage scaled = resize(source, scaledWidth, scaledHeight);
    BufferedImage target = newImage(source, targetWidth, targetHeight);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.drawImage(scaled, (targetWidth - scaledWidth) / 2,
          (targetHeight - scaledHeight) / 2, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static BufferedImage resize(BufferedImage source, int width, int height) {
    BufferedImage target = newImage(source, width, height);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
          RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static BufferedImage newImage(BufferedImage source, int width, int height) {
    int type = source.getColorModel().hasAlpha()
        ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    return new BufferedImage(width, height, type);
  }

  private static BufferedImage orient(BufferedImage source, int orientation) {
    if (orientation <= 1 || orientation > 8) return source;
    int width = source.getWidth();
    int height = source.getHeight();
    boolean swap = orientation >= 5 && orientation <= 8;
    BufferedImage target = newImage(source, swap ? height : width, swap ? width : height);
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
    try {
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static byte[] encode(BufferedImage image, float quality) {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(MIME_TYPE);
    if (!writers.hasNext()) {
      throw new ImageProcessingException("IMAGE_CODEC_UNAVAILABLE", "WebP ImageIO writer is unavailable");
    }
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
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
    } catch (IOException | RuntimeException error) {
      throw new ImageProcessingException("IMAGE_ENCODE_FAILED", "WebP encoding failed", error);
    } finally {
      writer.dispose();
    }
  }

  private static void verify(byte[] bytes) {
    try {
      if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
        throw new IOException("encoded WebP cannot be decoded");
      }
    } catch (IOException error) {
      throw new ImageProcessingException("IMAGE_ENCODE_FAILED", "encoded WebP validation failed", error);
    }
  }

  private static void requireWriter() {
    if (!ImageIO.getImageWritersByMIMEType(MIME_TYPE).hasNext()) {
      throw new ImageProcessingException("IMAGE_CODEC_UNAVAILABLE", "WebP ImageIO writer is unavailable");
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  public record EncodedImage(byte[] data, int width, int height, String checksumSha256,
      byte[] thumbnail, int thumbnailWidth, int thumbnailHeight) {}

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
        if (unsignedShort(bytes, entry, little) == 0x0112) {
          return unsignedShort(bytes, entry + 8, little);
        }
      }
      return 1;
    }

    private static boolean asciiEquals(byte[] bytes, int offset, String expected) {
      if (offset + expected.length() > bytes.length) return false;
      for (int i = 0; i < expected.length(); i++) {
        if (bytes[offset + i] != (byte) expected.charAt(i)) return false;
      }
      return true;
    }

    private static int unsignedShort(byte[] bytes, int offset, boolean little) {
      return little ? (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
          : ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int unsignedInt(byte[] bytes, int offset, boolean little) {
      long value = little
          ? (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
              | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24)
          : ((bytes[offset] & 0xffL) << 24) | ((bytes[offset + 1] & 0xffL) << 16)
              | ((bytes[offset + 2] & 0xffL) << 8) | (bytes[offset + 3] & 0xffL);
      return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
  }
}
