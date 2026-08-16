package com.dreamspace.worker.generation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class DeterministicMockProvider implements GenerationProvider {
  private final long delayMillis;

  public DeterministicMockProvider(long delayMillis) { this.delayMillis = Math.max(0, delayMillis); }

  @Override public List<ProviderImage> generate(WorkerTaskSnapshot task, GenerationAttempt attempt) {
    if (task.prompt().contains("[mock-retry-once]") && attempt.number() == 1) {
      throw new GenerationProviderException("PROVIDER_TEMPORARILY_UNAVAILABLE", "mock transient failure", true);
    }
    if (task.prompt().contains("[mock-always-retryable-error]")) {
      throw new GenerationProviderException("PROVIDER_TEMPORARILY_UNAVAILABLE", "mock persistent failure", true);
    }
    if (delayMillis > 0) {
      try { Thread.sleep(delayMillis); }
      catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new GenerationProviderException("PROVIDER_INTERRUPTED", "mock generation interrupted", true, error); }
    }
    List<ProviderImage> images = new ArrayList<>();
    for (int index = 0; index < task.imageCount(); index++) {
      images.add(new ProviderImage(index, render(task.prompt(), task.model(), index), "image/png", "mock-" + index + ".png"));
    }
    return List.copyOf(images);
  }

  private static byte[] render(String prompt, String model, int index) {
    byte[] digest = digest(prompt + ":" + model + ":" + index);
    BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      Color first = new Color(digest[0] & 0xff, digest[1] & 0xff, digest[2] & 0xff);
      Color second = new Color(digest[3] & 0xff, digest[4] & 0xff, digest[5] & 0xff);
      graphics.setPaint(new java.awt.GradientPaint(0, 0, first, 512, 512, second));
      graphics.fillRect(0, 0, 512, 512);
      graphics.setColor(new Color(255, 255, 255, 160));
      for (int i = 0; i < 8; i++) {
        int size = 24 + (digest[6 + i] & 0x7f);
        int x = (digest[14 + i] & 0xff) * (512 - size) / 255;
        int y = (digest[22 + i] & 0xff) * (512 - size) / 255;
        graphics.fillOval(x, y, size, size);
      }
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
      return output.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("mock image encoding failed", error);
    }
  }

  private static byte[] digest(String input) {
    try { return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); }
    catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
  }
}
