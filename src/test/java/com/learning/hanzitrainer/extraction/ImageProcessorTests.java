package com.learning.hanzitrainer.extraction;

import com.learning.hanzitrainer.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ImageProcessorTests {

    @Test
    void downscalesLongSideAndKeepsAspectRatio() throws IOException {
        BufferedImage result = decode(ImageProcessor.toJpeg(TestImages.png(4000, 3000), 2576));

        assertThat(result.getWidth()).isEqualTo(2576);
        assertThat(result.getHeight()).isEqualTo(1932);
    }

    @Test
    void portraitPagesAreLimitedByHeight() throws IOException {
        BufferedImage result = decode(ImageProcessor.toJpeg(TestImages.jpeg(1500, 3000), 2000));

        assertThat(result.getHeight()).isEqualTo(2000);
        assertThat(result.getWidth()).isEqualTo(1000);
    }

    @Test
    void neverUpscales() throws IOException {
        BufferedImage result = decode(ImageProcessor.toJpeg(TestImages.png(800, 600), 2576));

        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(600);
    }

    @Test
    void outputIsJpegEvenForTransparentPng() {
        byte[] jpeg = ImageProcessor.toJpeg(TestImages.png(100, 100), 2576);

        assertThat(jpeg).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    @Test
    void rejectsUnreadableData() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ImageProcessor.toJpeg("RIFF....WEBPVP8 ".getBytes(), 2576))
                .withMessageContaining("JPEG or PNG");
    }

    private static BufferedImage decode(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }
}
