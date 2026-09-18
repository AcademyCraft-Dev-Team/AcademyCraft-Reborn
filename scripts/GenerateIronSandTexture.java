import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/** Authored angular iron filings, with a dense center and disconnected peripheral grains. */
class GenerateIronSandTexture {
    public static void main(String[] args) throws Exception {
        var image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        var random = new Random(6842);
        for (int i = 0; i < 125; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(random.nextDouble()) * 56;
            int x = 64 + (int) (Math.cos(a) * r), y = 64 + (int) (Math.sin(a) * r);
            int size = 2 + random.nextInt(r < 28 ? 8 : 4);
            int shade = 23 + random.nextInt(29);
            g.setColor(new Color(shade, shade + 2, shade + 5, 235 + random.nextInt(21)));
            g.fillPolygon(new int[]{x - size, x, x + size, x + size / 2, x - size / 2},
                    new int[]{y, y - size / 2, y, y + size, y + size / 2}, 5);
            g.setColor(new Color(83, 89, 98, 150));
            g.drawLine(x - size, y, x, y - size / 2);
        }
        g.dispose();
        var output = Path.of("src/main/resources/assets/academy/textures/ability/electromaster/skill/iron_sand_arsenal/effect/iron_sand.png");
        if (!Files.isDirectory(output.getParent())) throw new IllegalStateException("Missing texture directory");
        ImageIO.write(image, "png", output.toFile());
    }
}
