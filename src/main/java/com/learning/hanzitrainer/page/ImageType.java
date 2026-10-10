package com.learning.hanzitrainer.page;

import java.util.Optional;

/** Accepted upload formats, recognised by their magic bytes rather than the declared content type. */
enum ImageType {
    JPEG("image/jpeg", "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    private final String mimeType;
    private final String extension;
    private final byte[] magic;

    ImageType(String mimeType, String extension, byte[] magic) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.magic = magic;
    }

    static Optional<ImageType> detect(byte[] content) {
        for (ImageType type : values()) {
            if (startsWith(content, type.magic)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    String mimeType() {
        return mimeType;
    }

    String extension() {
        return extension;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content == null || content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
