import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Generates the seamless 16x16 background tile used by the ability advancement tab.
 *
 * <p>Run from the repository root:</p>
 *
 * <pre>{@code
 * java tools/ui/GenerateAbilityAdvancementBackground.java --preview
 * }</pre>
 */
public final class GenerateAbilityAdvancementBackground {
    private static final Path TEMPLATE = Path.of(
            "src/main/resources/assets/academy/textures/gui/developer/skill_panel_back.png"
    );
    private static final Path OUTPUT = Path.of(
            "src/main/resources/assets/academy/textures/gui/advancements/ability_background.png"
    );
    private static final Path PREVIEW = Path.of(
            "build/reports/ui/ability_advancement_background_preview.png"
    );

    private static final int TILE_SIZE = 16;
    private static final int BASE_COLOR = 0xFF080D14;
    private static final int TRACE_RGB = 0x00FFFFFF;
    private static final int ACCENT_RGB = 0x001177D6;

    private GenerateAbilityAdvancementBackground() {
    }

    public static void main(String[] args) throws IOException {
        var root = findRepositoryRoot();
        var templatePath = root.resolve(TEMPLATE);
        var outputPath = root.resolve(OUTPUT);
        var template = ImageIO.read(templatePath.toFile());
        if (template == null) {
            throw new IOException("Unsupported template image: " + templatePath);
        }

        var tile = generateTile(template);
        validateTile(tile);
        Files.createDirectories(outputPath.getParent());
        ImageIO.write(tile, "png", outputPath.toFile());
        System.out.println("Generated " + root.relativize(outputPath));

        if (Arrays.asList(args).contains("--preview")) {
            var previewPath = root.resolve(PREVIEW);
            Files.createDirectories(previewPath.getParent());
            ImageIO.write(buildPreview(tile), "png", previewPath.toFile());
            System.out.println("Generated " + root.relativize(previewPath));
        }
    }

    private static BufferedImage generateTile(BufferedImage template) {
        var peakAlpha = findPeakAlpha(template);
        if (peakAlpha == 0) {
            throw new IllegalArgumentException("Template contains no visible circuit line work");
        }

        var tile = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (var y = 0; y < TILE_SIZE; y++) {
            for (var x = 0; x < TILE_SIZE; x++) {
                tile.setRGB(x, y, BASE_COLOR);
            }
        }

        addTemplateGrain(template, tile, peakAlpha);

        var traceAlpha = clamp(Math.round(peakAlpha * 0.75f), 32, 56);
        var secondaryAlpha = clamp(Math.round(traceAlpha * 0.55f), 18, 36);
        var graphics = tile.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));

            graphics.setColor(withAlpha(TRACE_RGB, traceAlpha));
            drawPath(graphics,
                    new int[]{0, 3, 5, 8, 10, 15},
                    new int[]{3, 3, 5, 5, 3, 3});
            drawPath(graphics,
                    new int[]{12, 12, 10, 10, 12, 12},
                    new int[]{0, 2, 4, 8, 10, 15});

            graphics.setColor(withAlpha(TRACE_RGB, secondaryAlpha));
            drawPath(graphics,
                    new int[]{0, 2, 4, 7, 9, 12, 15},
                    new int[]{12, 12, 10, 10, 12, 12, 12});
            drawPath(graphics,
                    new int[]{4, 4, 2, 2, 4, 4},
                    new int[]{0, 2, 4, 7, 9, 15});

            drawVia(graphics, 8, 5, withAlpha(TRACE_RGB, traceAlpha));
            drawVia(graphics, 7, 10, withAlpha(TRACE_RGB, secondaryAlpha));
            drawVia(graphics, 10, 8, withAlpha(ACCENT_RGB, clamp(peakAlpha * 2, 72, 112)));
        } finally {
            graphics.dispose();
        }
        synchronizeEdges(tile);
        return tile;
    }

    private static void synchronizeEdges(BufferedImage tile) {
        for (var y = 0; y < TILE_SIZE; y++) {
            var edgeColor = brighter(tile.getRGB(0, y), tile.getRGB(TILE_SIZE - 1, y));
            tile.setRGB(0, y, edgeColor);
            tile.setRGB(TILE_SIZE - 1, y, edgeColor);
        }
        for (var x = 0; x < TILE_SIZE; x++) {
            var edgeColor = brighter(tile.getRGB(x, 0), tile.getRGB(x, TILE_SIZE - 1));
            tile.setRGB(x, 0, edgeColor);
            tile.setRGB(x, TILE_SIZE - 1, edgeColor);
        }
    }

    private static int brighter(int first, int second) {
        var firstLuminance = (first >> 16 & 0xFF) + (first >> 8 & 0xFF) + (first & 0xFF);
        var secondLuminance = (second >> 16 & 0xFF) + (second >> 8 & 0xFF) + (second & 0xFF);
        return firstLuminance >= secondLuminance ? first : second;
    }

    private static void addTemplateGrain(BufferedImage template, BufferedImage tile, int peakAlpha) {
        var patchSize = Math.min(96, Math.min(template.getWidth(), template.getHeight()));
        var patch = findDensestPatch(template, patchSize);
        for (var y = 1; y < TILE_SIZE - 1; y++) {
            for (var x = 1; x < TILE_SIZE - 1; x++) {
                var x0 = patch.x() + (x - 1) * patchSize / (TILE_SIZE - 2);
                var x1 = patch.x() + x * patchSize / (TILE_SIZE - 2);
                var y0 = patch.y() + (y - 1) * patchSize / (TILE_SIZE - 2);
                var y1 = patch.y() + y * patchSize / (TILE_SIZE - 2);
                var alpha = averageAlpha(template, x0, y0, x1, y1);
                var grainAlpha = clamp(Math.round(alpha * 10f / peakAlpha), 0, 10);
                if (grainAlpha > 0) {
                    tile.setRGB(x, y, blendOver(tile.getRGB(x, y), TRACE_RGB, grainAlpha));
                }
            }
        }
    }

    private static Patch findDensestPatch(BufferedImage image, int size) {
        long bestEnergy = -1;
        var bestX = 0;
        var bestY = 0;
        var step = Math.max(1, size / 12);
        for (var y = 0; y <= image.getHeight() - size; y += step) {
            for (var x = 0; x <= image.getWidth() - size; x += step) {
                long energy = 0;
                for (var py = y; py < y + size; py += 2) {
                    for (var px = x; px < x + size; px += 2) {
                        energy += alpha(image.getRGB(px, py));
                    }
                }
                if (energy > bestEnergy) {
                    bestEnergy = energy;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new Patch(bestX, bestY);
    }

    private static int averageAlpha(BufferedImage image, int x0, int y0, int x1, int y1) {
        var sum = 0;
        var count = 0;
        for (var y = y0; y < Math.max(y0 + 1, y1); y++) {
            for (var x = x0; x < Math.max(x0 + 1, x1); x++) {
                sum += alpha(image.getRGB(x, y));
                count++;
            }
        }
        return count == 0 ? 0 : Math.round((float) sum / count);
    }

    private static int findPeakAlpha(BufferedImage image) {
        var peak = 0;
        for (var y = 0; y < image.getHeight(); y++) {
            for (var x = 0; x < image.getWidth(); x++) {
                peak = Math.max(peak, alpha(image.getRGB(x, y)));
            }
        }
        return peak;
    }

    private static void drawPath(Graphics2D graphics, int[] x, int[] y) {
        graphics.drawPolyline(x, y, Math.min(x.length, y.length));
    }

    private static void drawVia(Graphics2D graphics, int x, int y, Color color) {
        var oldColor = graphics.getColor();
        graphics.setColor(color);
        graphics.drawOval(x - 1, y - 1, 2, 2);
        graphics.setColor(new Color(BASE_COLOR, true));
        graphics.fillRect(x, y, 1, 1);
        graphics.setColor(oldColor);
    }

    private static void validateTile(BufferedImage tile) {
        if (tile.getWidth() != TILE_SIZE || tile.getHeight() != TILE_SIZE) {
            throw new IllegalStateException("Advancement background must be exactly 16x16 pixels");
        }
        for (var y = 0; y < TILE_SIZE; y++) {
            if (isTrace(tile.getRGB(0, y)) != isTrace(tile.getRGB(TILE_SIZE - 1, y))) {
                throw new IllegalStateException("Left/right circuit ports do not match at y=" + y);
            }
        }
        for (var x = 0; x < TILE_SIZE; x++) {
            if (isTrace(tile.getRGB(x, 0)) != isTrace(tile.getRGB(x, TILE_SIZE - 1))) {
                throw new IllegalStateException("Top/bottom circuit ports do not match at x=" + x);
            }
        }
        if (countTracePixelsOnVerticalEdge(tile) < 2 || countTracePixelsOnHorizontalEdge(tile) < 2) {
            throw new IllegalStateException("Tile must expose circuit traces in all four directions");
        }
    }

    private static int countTracePixelsOnVerticalEdge(BufferedImage tile) {
        var count = 0;
        for (var y = 0; y < TILE_SIZE; y++) {
            if (isTrace(tile.getRGB(0, y))) count++;
        }
        return count;
    }

    private static int countTracePixelsOnHorizontalEdge(BufferedImage tile) {
        var count = 0;
        for (var x = 0; x < TILE_SIZE; x++) {
            if (isTrace(tile.getRGB(x, 0))) count++;
        }
        return count;
    }

    private static boolean isTrace(int argb) {
        return (argb & 0x00FFFFFF) != (BASE_COLOR & 0x00FFFFFF);
    }

    private static BufferedImage buildPreview(BufferedImage tile) {
        var tiled = new BufferedImage(TILE_SIZE * 16, TILE_SIZE * 8, BufferedImage.TYPE_INT_ARGB);
        var graphics = tiled.createGraphics();
        try {
            for (var y = 0; y < tiled.getHeight(); y += TILE_SIZE) {
                for (var x = 0; x < tiled.getWidth(); x += TILE_SIZE) {
                    graphics.drawImage(tile, x, y, null);
                }
            }
        } finally {
            graphics.dispose();
        }

        var preview = new BufferedImage(tiled.getWidth() * 2, tiled.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        var previewGraphics = preview.createGraphics();
        try {
            previewGraphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            previewGraphics.drawImage(tiled, 0, 0, preview.getWidth(), preview.getHeight(), null);
        } finally {
            previewGraphics.dispose();
        }
        return preview;
    }

    private static Path findRepositoryRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve(TEMPLATE))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Run the generator from inside the AcademyCraft repository");
        }
        return current;
    }

    private static Color withAlpha(int rgb, int alpha) {
        return new Color((rgb & 0x00FFFFFF) | (clamp(alpha, 0, 255) << 24), true);
    }

    private static int blendOver(int baseArgb, int overlayRgb, int overlayAlpha) {
        var inverse = 255 - overlayAlpha;
        var red = ((baseArgb >> 16 & 0xFF) * inverse + (overlayRgb >> 16 & 0xFF) * overlayAlpha) / 255;
        var green = ((baseArgb >> 8 & 0xFF) * inverse + (overlayRgb >> 8 & 0xFF) * overlayAlpha) / 255;
        var blue = ((baseArgb & 0xFF) * inverse + (overlayRgb & 0xFF) * overlayAlpha) / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Patch(int x, int y) {
    }
}
