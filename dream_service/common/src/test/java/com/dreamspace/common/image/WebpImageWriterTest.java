package com.dreamspace.common.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WebpImageWriterTest {
  @Test
  void normalizesRealPngBytesToDecodableWebp() throws Exception {
    BufferedImage source = new BufferedImage(32, 20, BufferedImage.TYPE_INT_RGB);
    source.createGraphics().setColor(Color.GREEN);
    source.createGraphics().fillRect(0, 0, 32, 20);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);

    WebpImageWriter.EncodedImage result = new WebpImageWriter().normalize(png.toByteArray(), 1_000_000);

    assertThat(result.data()).isNotEmpty();
    assertThat(result.width()).isEqualTo(32);
    assertThat(result.height()).isEqualTo(20);
    assertThat(result.checksumSha256()).hasSize(64);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.data()))).isNotNull();
  }

  @Test
  void createsRealMainAndThumbnailWebpBytes() throws Exception {
    BufferedImage source = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(source, "png", png);

    WebpImageWriter.EncodedImage result = new WebpImageWriter().cover(png.toByteArray(), 200, 100, 80, 1_000_000);

    assertThat(result.width()).isEqualTo(200);
    assertThat(result.height()).isEqualTo(100);
    assertThat(result.thumbnailWidth()).isEqualTo(80);
    assertThat(result.thumbnailHeight()).isEqualTo(40);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.data()))).isNotNull();
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.thumbnail()))).isNotNull();
  }
}
