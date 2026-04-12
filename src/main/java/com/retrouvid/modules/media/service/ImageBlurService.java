package com.retrouvid.modules.media.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ImageBlurService {

    private static final int MAX_PREVIEW_WIDTH = 1024;
    private static final int PIXELATION_BLOCK = 18;

    public byte[] buildPreview(byte[] original, String contentType) throws IOException {
        BufferedImage src;
        try (ByteArrayInputStream in = new ByteArrayInputStream(original)) {
            src = ImageIO.read(in);
        }
        if (src == null) {
            throw new IOException("Image illisible");
        }
        BufferedImage resized = resize(src, MAX_PREVIEW_WIDTH);
        BufferedImage pixelated = pixelateLowerHalf(resized, PIXELATION_BLOCK);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String format = (contentType != null && contentType.contains("png")) ? "png" : "jpg";
            ImageIO.write(pixelated, format, out);
            return out.toByteArray();
        }
    }

    private BufferedImage resize(BufferedImage src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        double ratio = (double) maxWidth / src.getWidth();
        int w = maxWidth;
        int h = (int) Math.round(src.getHeight() * ratio);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private BufferedImage pixelateLowerHalf(BufferedImage src, int block) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        int startY = h / 2;
        for (int y = startY; y < h; y += block) {
            for (int x = 0; x < w; x += block) {
                int bw = Math.min(block, w - x);
                int bh = Math.min(block, h - y);
                long r = 0, gr = 0, b = 0;
                int count = 0;
                for (int yy = y; yy < y + bh; yy++) {
                    for (int xx = x; xx < x + bw; xx++) {
                        int rgb = src.getRGB(xx, yy);
                        r += (rgb >> 16) & 0xFF;
                        gr += (rgb >> 8) & 0xFF;
                        b += rgb & 0xFF;
                        count++;
                    }
                }
                int avg = (((int) (r / count)) << 16) | (((int) (gr / count)) << 8) | ((int) (b / count));
                for (int yy = y; yy < y + bh; yy++) {
                    for (int xx = x; xx < x + bw; xx++) {
                        dst.setRGB(xx, yy, avg);
                    }
                }
            }
        }
        return dst;
    }
}
