package com.learning.hanzitrainer.page;

import com.learning.hanzitrainer.TestImages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTests {

    @Test
    void detectsByMagicBytes() {
        assertThat(ImageType.detect(TestImages.jpeg(10, 10))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(TestImages.png(10, 10))).contains(ImageType.PNG);
    }

    @Test
    void rejectsOtherFormats() {
        assertThat(ImageType.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes())).isEmpty();
        assertThat(ImageType.detect("GIF89a".getBytes())).isEmpty();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}
