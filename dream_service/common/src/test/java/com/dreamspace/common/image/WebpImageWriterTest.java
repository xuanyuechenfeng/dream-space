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

  @Test
  void createsProportionalLandscapePreviewUsingLongestEdge() throws Exception {
    byte[] input = png(1600, 900);

    WebpImageWriter.EncodedPreview result = new WebpImageWriter()
        .preview(input, 640, 0.80f, 2_000_000);

    assertThat(result.width()).isEqualTo(640);
    assertThat(result.height()).isEqualTo(360);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.data())))
        .extracting(BufferedImage::getWidth, BufferedImage::getHeight)
        .containsExactly(640, 360);
  }

  @Test
  void createsProportionalPortraitPreviewAndDoesNotUpscaleSmallImages() throws Exception {
    WebpImageWriter writer = new WebpImageWriter();

    WebpImageWriter.EncodedPreview portrait = writer.preview(png(900, 1600), 640, 0.80f, 2_000_000);
    WebpImageWriter.EncodedPreview small = writer.preview(png(320, 200), 640, 0.80f, 2_000_000);

    assertThat(portrait.width()).isEqualTo(360);
    assertThat(portrait.height()).isEqualTo(640);
    assertThat(small.width()).isEqualTo(320);
    assertThat(small.height()).isEqualTo(200);
  }

  @Test
  void createsSquarePreviewAtTheConfiguredLongestEdge() throws Exception {
    WebpImageWriter.EncodedPreview result = new WebpImageWriter()
        .preview(png(1200, 1200), 640, 0.80f, 2_000_000);

    assertThat(result.width()).isEqualTo(640);
    assertThat(result.height()).isEqualTo(640);
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "png", output);
    return output.toByteArray();
  }
}
