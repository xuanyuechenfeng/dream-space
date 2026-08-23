package com.dreamspace.common.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Encodes provider output and thumbnails as lossless PNG. */
public final class PngImageWriter {
  public static final String MIME_TYPE = "image/png";

  public PngImageWriter() {
    if (!ImageIO.getImageWritersByFormatName("png").hasNext()) {
      throw new ImageProcessingException("IMAGE_CODEC_UNAVAILABLE", "PNG ImageIO writer is unavailable");
    }
  }

  public EncodedImage cover(byte[] input, int width, int height, int thumbnailMaxWidth, long maxPixels) {
    if (width < 1 || height < 1 || thumbnailMaxWidth < 1) throw new IllegalArgumentException("output dimensions must be positive");
    BufferedImage source = decode(input, maxPixels);
    BufferedImage output = cover(source, width, height);
    int thumbnailWidth = Math.min(thumbnailMaxWidth, width);
    int thumbnailHeight = Math.max(1, Math.round((float) height * thumbnailWidth / width));
    byte[] encoded = encode(output);
    byte[] thumbnail = encode(resize(output, thumbnailWidth, thumbnailHeight));
    return new EncodedImage(encoded, thumbnail, thumbnailWidth, thumbnailHeight, sha256(encoded));
  }

  private static BufferedImage decode(byte[] input, long maxPixels) {
    if (input == null || input.length == 0) throw new ImageProcessingException("IMAGE_DECODE_FAILED", "image data is empty");
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
      if (image == null || (long) image.getWidth() * image.getHeight() > maxPixels) throw new IOException("unsupported or oversized image data");
      return image;
    } catch (IOException | RuntimeException error) {
      throw new ImageProcessingException("IMAGE_DECODE_FAILED", "image cannot be decoded", error);
    }
  }

  private static BufferedImage cover(BufferedImage source, int width, int height) {
    double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
    BufferedImage scaled = resize(source, Math.max(width, (int) Math.ceil(source.getWidth() * scale)), Math.max(height, (int) Math.ceil(source.getHeight() * scale)));
    BufferedImage target = newImage(source, width, height);
    Graphics2D graphics = target.createGraphics();
    try { graphics.drawImage(scaled, (width - scaled.getWidth()) / 2, (height - scaled.getHeight()) / 2, null); }
    finally { graphics.dispose(); }
    return target;
  }

  private static BufferedImage resize(BufferedImage source, int width, int height) {
    BufferedImage target = newImage(source, width, height);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally { graphics.dispose(); }
    return target;
  }

  private static BufferedImage newImage(BufferedImage source, int width, int height) {
    return new BufferedImage(width, height, source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
  }

  private static byte[] encode(BufferedImage image) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer is unavailable");
      return output.toByteArray();
    } catch (IOException | RuntimeException error) {
      throw new ImageProcessingException("IMAGE_ENCODE_FAILED", "PNG encoding failed", error);
    }
  }

  private static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
  }

  public record EncodedImage(byte[] data, byte[] thumbnail, int thumbnailWidth, int thumbnailHeight, String checksumSha256) {}
}
