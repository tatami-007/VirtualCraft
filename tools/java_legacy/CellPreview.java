import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 icons.png 里指定的几个 16×16 cell 单独裁出来放大并排显示，
 * 用于精确视觉确认图标。
 *
 * Usage: java -cp tools/_cs CellPreview <output> <gx,gy> [<gx,gy>...]
 */
public class CellPreview {
	public static void main(String[] args) throws Exception {
		BufferedImage icons = ImageIO.read(new File("src/main/resources/assets/virtual_craft/textures/gui/icons.png"));
		int scale = 16;
		int cellSize = 16 * scale;  // 256
		int gap = 16;
		int n = args.length - 1;
		int destW = (cellSize + gap) * n + gap;
		int destH = cellSize + 2 * gap;
		BufferedImage out = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(0xCB, 0xCC, 0xD4));
		g.fillRect(0, 0, destW, destH);
		g.dispose();

		for (int i = 0; i < n; i++) {
			String[] parts = args[i + 1].split(",");
			int gx = Integer.parseInt(parts[0]);
			int gy = Integer.parseInt(parts[1]);
			int srcX = gx * 16, srcY = gy * 16;
			int dstX0 = gap + i * (cellSize + gap);
			int dstY0 = gap;
			System.out.printf("cell (%d,%d) px=(%d,%d)%n", gx, gy, srcX, srcY);
			for (int dy = 0; dy < 16; dy++) {
				for (int dx = 0; dx < 16; dx++) {
					int rgb = icons.getRGB(srcX + dx, srcY + dy);
					int alpha = (rgb >>> 24) & 0xFF;
					int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
					float a = alpha / 255f;
					int mr = Math.round(r * a + 0xCB * (1 - a));
					int mg = Math.round(gr * a + 0xCC * (1 - a));
					int mb = Math.round(b * a + 0xD4 * (1 - a));
					int blended = (mr << 16) | (mg << 8) | mb;
					for (int sy = 0; sy < scale; sy++) {
						for (int sx = 0; sx < scale; sx++) {
							out.setRGB(dstX0 + dx * scale + sx, dstY0 + dy * scale + sy, blended);
						}
					}
				}
			}
		}
		ImageIO.write(out, "png", new File(args[0]));
		System.out.println("Wrote " + args[0]);
	}
}
