package co.ke.bremac.posapp;

import java.io.ByteArrayOutputStream;

final class EscPosRaster {
    interface Pixels {
        int color(int x, int y);
    }

    private EscPosRaster() {
    }

    static int width(int paperMm) {
        if (paperMm == 58) return 384;
        if (paperMm == 80) return 576;
        throw new IllegalArgumentException("Select 58 mm or 80 mm paper.");
    }

    static byte[] encode(int width, int height, Pixels pixels, boolean cut) {
        if ((width != 384 && width != 576) || height <= 0 || height > 20000) {
            throw new IllegalArgumentException("Receipt image dimensions are not supported.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(27);
        output.write(64);
        // Left aligned, normal size; raster pixels, not printer fonts, carry the website layout.
        output.write(27);
        output.write(97);
        output.write(0);
        int rowBytes = (width + 7) / 8;
        for (int start = 0; start < height; start += 128) {
            int rows = Math.min(128, height - start);
            output.write(29);
            output.write(118);
            output.write(48);
            output.write(0);
            output.write(rowBytes & 255);
            output.write(rowBytes >> 8);
            output.write(rows & 255);
            output.write(rows >> 8);
            for (int y = start; y < start + rows; y++) {
                for (int xByte = 0; xByte < rowBytes; xByte++) {
                    int packed = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = xByte * 8 + bit;
                        if (x < width && dark(pixels.color(x, y))) {
                            packed |= 128 >> bit;
                        }
                    }
                    output.write(packed);
                }
            }
        }
        output.write(10);
        output.write(10);
        output.write(10);
        if (cut) {
            output.write(29);
            output.write(86);
            output.write(0);
        }
        return output.toByteArray();
    }

    private static boolean dark(int argb) {
        int alpha = argb >>> 24;
        int red = (((argb >> 16) & 255) * alpha + 255 * (255 - alpha)) / 255;
        int green = (((argb >> 8) & 255) * alpha + 255 * (255 - alpha)) / 255;
        int blue = ((argb & 255) * alpha + 255 * (255 - alpha)) / 255;
        return (red * 299 + green * 587 + blue * 114) / 1000 < 170;
    }
}
