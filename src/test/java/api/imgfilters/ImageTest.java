package api.imgfilters;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageTest {
    @Test
    void adjustBrightnessChangesRgbAndPreservesAlpha() {
        BufferedImage source = onePixel(128, 10, 20, 30);

        BufferedImage result = Image.adjustBrightness(source, 20);

        assertArrayEquals(new int[]{128, 30, 40, 50}, argb(result));
    }

    @Test
    void adjustBrightnessClampsRgbChannels() {
        BufferedImage source = onePixel(255, 250, 5, 100);

        BufferedImage result = Image.adjustBrightness(source, 20);

        assertArrayEquals(new int[]{255, 255, 25, 120}, argb(result));
    }

    @Test
    void zeroContrastMovesChannelsToMidpoint() {
        BufferedImage source = onePixel(255, 0, 200, 255);

        BufferedImage result = Image.adjustContrast(source, 0.0);

        assertArrayEquals(new int[]{255, 128, 128, 128}, argb(result));
    }

    @Test
    void filterOptionsClampUnsafeValues() {
        Image.FilterOptions options = new Image.FilterOptions(false, 999, 99.0, 999, 99.0);

        assertEquals(255, options.brightness());
        assertEquals(4.0, options.contrast());
        assertEquals(25, options.blurRadius());
        assertEquals(1.0, options.sharpenAmount());
    }

    @Test
    void normalizesCommonFormatsAndRejectsUnknownFormats() {
        assertEquals("jpeg", Image.normalizeFormat("jpg"));
        assertEquals("png", Image.normalizeFormat(" PNG "));

        assertThrows(IllegalArgumentException.class, () -> Image.normalizeFormat("webp"));
    }

    @Test
    void writesAndReadsPng() {
        BufferedImage source = onePixel(255, 12, 34, 56);

        byte[] bytes = Image.write(source, "png");
        BufferedImage result = Image.read(bytes);

        assertEquals(1, result.getWidth());
        assertEquals(1, result.getHeight());
        assertArrayEquals(new int[]{255, 12, 34, 56}, argb(result));
    }

    private static BufferedImage onePixel(int alpha, int red, int green, int blue) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, (alpha << 24) | (red << 16) | (green << 8) | blue);
        return image;
    }

    private static int[] argb(BufferedImage image) {
        int argb = image.getRGB(0, 0);
        return new int[]{
                (argb >>> 24) & 0xff,
                (argb >>> 16) & 0xff,
                (argb >>> 8) & 0xff,
                argb & 0xff
        };
    }
}
