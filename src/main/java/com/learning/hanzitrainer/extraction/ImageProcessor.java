package com.learning.hanzitrainer.extraction;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Prepares an uploaded page for the model: downscale to the long-side limit and re-encode as JPEG (spec 05). */
public final class ImageProcessor {

    private static final float JPEG_QUALITY = 0.9f;

    private ImageProcessor() {
    }

    public static byte[] toJpeg(byte[] image, int maxLongSide) {
        BufferedImage source = read(image);
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxLongSide / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        // Always redraw onto opaque RGB: JPEG has no alpha, and PNGs may be indexed or transparent.
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return writeJpeg(target);
    }

    private static BufferedImage read(byte[] image) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null) {
                throw new IllegalArgumentException("Image format not readable (expected JPEG or PNG)");
            }
            return decoded;
        } catch (IOException e) {
            throw new UncheckedIOException("Decoding image failed", e);
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException("Encoding JPEG failed", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
