package api.imgfilters;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class Image {
    private static final int MIN_COLOR_VALUE = 0;
    private static final int MAX_COLOR_VALUE = 255;
    private static final int MIN_BRIGHTNESS = -255;
    private static final int MAX_BRIGHTNESS = 255;
    private static final double MIN_CONTRAST = 0.0;
    private static final double MAX_CONTRAST = 4.0;
    private static final int MIN_BLUR_RADIUS = 0;
    private static final int MAX_BLUR_RADIUS = 25;
    private static final double MIN_SHARPEN_AMOUNT = 0.0;
    private static final double MAX_SHARPEN_AMOUNT = 1.0;

    private Image() {
    }

    public static BufferedImage read(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes must not be null");

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or invalid image file");
            }
            return toArgb(image);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read image file", exception);
        }
    }

    public static byte[] write(BufferedImage image, String format) {
        Objects.requireNonNull(image, "image must not be null");

        String normalizedFormat = normalizeFormat(format);
        BufferedImage output = image;
        if ("jpg".equals(normalizedFormat) || "jpeg".equals(normalizedFormat)) {
            output = removeAlpha(image, Color.WHITE);
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(output, normalizedFormat, out)) {
                throw new IllegalArgumentException("Unsupported output image format: " + format);
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write image file", exception);
        }
    }

    public static BufferedImage applyFilters(BufferedImage source, FilterOptions options) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(options, "options must not be null");

        BufferedImage result = toArgb(source);
        if (options.grayscale()) {
            result = grayscale(result);
        }
        if (options.brightness() != 0) {
            result = adjustBrightness(result, options.brightness());
        }
        if (Double.compare(options.contrast(), 1.0) != 0) {
            result = adjustContrast(result, options.contrast());
        }
        if (options.blurRadius() > 0) {
            result = blur(result, options.blurRadius());
        }
        if (options.sharpenAmount() > 0.0) {
            result = sharpen(result, options.sharpenAmount());
        }

        return result;
    }

    public static BufferedImage grayscale(BufferedImage source) {
        Objects.requireNonNull(source, "source must not be null");

        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp operation = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        operation.filter(source, gray);
        return toArgb(gray);
    }

    public static BufferedImage adjustBrightness(BufferedImage source, int amount) {
        Objects.requireNonNull(source, "source must not be null");
        int boundedAmount = clamp(amount, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = clamp(((argb >>> 16) & 0xff) + boundedAmount, MIN_COLOR_VALUE, MAX_COLOR_VALUE);
                int green = clamp(((argb >>> 8) & 0xff) + boundedAmount, MIN_COLOR_VALUE, MAX_COLOR_VALUE);
                int blue = clamp((argb & 0xff) + boundedAmount, MIN_COLOR_VALUE, MAX_COLOR_VALUE);
                result.setRGB(x, y, toArgb(alpha, red, green, blue));
            }
        }

        return result;
    }

    public static BufferedImage adjustContrast(BufferedImage source, double contrast) {
        Objects.requireNonNull(source, "source must not be null");
        double boundedContrast = clamp(contrast, MIN_CONTRAST, MAX_CONTRAST);
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = contrast(((argb >>> 16) & 0xff), boundedContrast);
                int green = contrast(((argb >>> 8) & 0xff), boundedContrast);
                int blue = contrast((argb & 0xff), boundedContrast);
                result.setRGB(x, y, toArgb(alpha, red, green, blue));
            }
        }

        return result;
    }

    public static BufferedImage blur(BufferedImage source, int radius) {
        Objects.requireNonNull(source, "source must not be null");
        int boundedRadius = clamp(radius, MIN_BLUR_RADIUS, MAX_BLUR_RADIUS);
        if (boundedRadius == 0) {
            return toArgb(source);
        }

        int size = boundedRadius * 2 + 1;
        float weight = 1.0f / (size * size);
        float[] kernelData = new float[size * size];
        Arrays.fill(kernelData, weight);

        ConvolveOp operation = new ConvolveOp(new Kernel(size, size, kernelData), ConvolveOp.EDGE_NO_OP, null);
        return operation.filter(toArgb(source), null);
    }

    public static BufferedImage sharpen(BufferedImage source, double amount) {
        Objects.requireNonNull(source, "source must not be null");
        double boundedAmount = clamp(amount, MIN_SHARPEN_AMOUNT, MAX_SHARPEN_AMOUNT);
        if (boundedAmount == 0.0) {
            return toArgb(source);
        }

        float[] sharpenKernel = {
                0.0f, -1.0f, 0.0f,
                -1.0f, 5.0f, -1.0f,
                0.0f, -1.0f, 0.0f
        };
        BufferedImage original = toArgb(source);
        BufferedImage sharpened = new ConvolveOp(
                new Kernel(3, 3, sharpenKernel),
                ConvolveOp.EDGE_NO_OP,
                null
        ).filter(original, null);

        return blend(original, sharpened, boundedAmount);
    }

    public static BufferedImage overlayText(BufferedImage source, TextOverlay overlay) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(overlay, "overlay must not be null");

        BufferedImage result = toArgb(source);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(new Font(overlay.fontName(), Font.PLAIN, overlay.fontSize()));
            FontMetrics metrics = graphics.getFontMetrics();
            int baseline = overlay.y();

            if (overlay.backgroundColor() != null) {
                graphics.setColor(overlay.backgroundColor());
                graphics.fillRect(
                        overlay.x(),
                        baseline - metrics.getAscent(),
                        metrics.stringWidth(overlay.text()),
                        metrics.getHeight()
                );
            }

            graphics.setColor(overlay.color());
            graphics.drawString(overlay.text(), overlay.x(), baseline);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    public static BufferedImage overlayImage(
            BufferedImage source,
            BufferedImage overlay,
            int x,
            int y,
            int width,
            int height,
            float opacity
    ) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(overlay, "overlay must not be null");

        BufferedImage result = toArgb(source);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp(opacity, 0.0f, 1.0f)));
            graphics.drawImage(overlay, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    public static String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "png";
        }

        String normalized = format.toLowerCase(Locale.ROOT).strip();
        if ("jpg".equals(normalized)) {
            return "jpeg";
        }
        if (!"png".equals(normalized) && !"jpeg".equals(normalized) && !"bmp".equals(normalized) && !"gif".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported output image format: " + format);
        }
        return normalized;
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = copy.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return copy;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage removeAlpha(BufferedImage source, Color background) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static BufferedImage blend(BufferedImage first, BufferedImage second, double secondOpacity) {
        BufferedImage result = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(first, 0, 0, null);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) secondOpacity));
            graphics.drawImage(second, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static int contrast(int value, double contrast) {
        return clamp((int) Math.round((value - 128) * contrast + 128), MIN_COLOR_VALUE, MAX_COLOR_VALUE);
    }

    private static int toArgb(int alpha, int red, int green, int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record FilterOptions(
            boolean grayscale,
            int brightness,
            double contrast,
            int blurRadius,
            double sharpenAmount
    ) {
        public FilterOptions {
            brightness = clamp(brightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
            contrast = clamp(contrast, MIN_CONTRAST, MAX_CONTRAST);
            blurRadius = clamp(blurRadius, MIN_BLUR_RADIUS, MAX_BLUR_RADIUS);
            sharpenAmount = clamp(sharpenAmount, MIN_SHARPEN_AMOUNT, MAX_SHARPEN_AMOUNT);
        }
    }

    public record TextOverlay(
            String text,
            int x,
            int y,
            int fontSize,
            String fontName,
            Color color,
            Color backgroundColor
    ) {
        public TextOverlay {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
            fontSize = Math.max(1, fontSize);
            fontName = (fontName == null || fontName.isBlank()) ? Font.SANS_SERIF : fontName;
            color = color == null ? Color.BLACK : color;
        }
    }
}
