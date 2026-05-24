import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把一张带透明背景的 PNG 叠加到指定背景色 + N 倍缩放，便于真实查看 sprite sheet 内容。
 *
 * Usage:
 *   java -cp tools/_cs PreviewOnWhite <input.png> <output.png> [scale=2] [bgHex=FFFFFF]
 *
 * Examples:
 *   PreviewOnWhite widgets.png widgets_white.png 2          ← 白底（默认）
 *   PreviewOnWhite widgets.png widgets_gui.png  2 CBCCD4    ← AE2 GUI 主背景
 *   PreviewOnWhite widgets.png widgets_dark.png 3 1A1A1A    ← 深色对比背景
 */
public class PreviewOnWhite {
	public static void main(String[] args) throws Exception {
		BufferedImage src = ImageIO.read(new File(args[0]));
		int scale = args.length > 2 ? Integer.parseInt(args[2]) : 2;
		int bgHex = args.length > 3 ? Integer.parseInt(args[3], 16) : 0xFFFFFF;
		int bgR = (bgHex >> 16) & 0xFF;
		int bgG = (bgHex >> 8) & 0xFF;
		int bgB = bgHex & 0xFF;
		int w = src.getWidth() * scale;
		int h = src.getHeight() * scale;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(bgR, bgG, bgB));
		g.fillRect(0, 0, w, h);
		g.dispose();
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int rgb = src.getRGB(x, y);
				int alpha = (rgb >>> 24) & 0xFF;
				if (alpha == 0) continue;
				// alpha blend against bg color
				int r = (rgb >> 16) & 0xFF;
				int gr = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				float a = alpha / 255f;
				int mr = Math.round(r * a + bgR * (1 - a));
				int mg = Math.round(gr * a + bgG * (1 - a));
				int mb = Math.round(b * a + bgB * (1 - a));
				int blended = (mr << 16) | (mg << 8) | mb;
				for (int sy = 0; sy < scale; sy++) {
					for (int sx = 0; sx < scale; sx++) {
						out.setRGB(x * scale + sx, y * scale + sy, blended);
					}
				}
			}
		}
		ImageIO.write(out, "png", new File(args[1]));
		System.out.printf("Wrote %s (%dx%d, scale=%d, bg=#%06X)%n", args[1], w, h, scale, bgHex);
	}
}
