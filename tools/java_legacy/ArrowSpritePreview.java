import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 electric_condenser.png (180, 0, 23, 17) 的进度箭头 sprite 裁出来，
 * 在 AE2 GUI 主背景 #CBCCD4 上叠加 + 12x 放大，观察 v14 精致版立体感。
 *
 * <pre>
 *   v14 sprite 设计（drawProgressArrowSprite）:
 *     主体 16×9 (y=4..12) 顶 1px 浅紫高光 + 7 行主紫 + 底 1px 深紫阴影
 *     三角尖 7 高 (y=4..12) 宽 3,4,5,6,7,6,5,4,3 同色
 *     主体最左 1px 列 (y=5..11) 浅紫 — 圆润感
 *     三角尖最右像素 (顶点 y=8) 深紫 — 点状阴影
 * </pre>
 */
public class ArrowSpritePreview {
	public static void main(String[] args) throws Exception {
		File out = new File("preview_arrow_sprite.png");
		BufferedImage src = ImageIO.read(new File(
			"src/main/resources/assets/virtual_craft/textures/gui/electric_condenser.png"));
		int srcX = 180, srcY = 0, w = 23, h = 17, scale = 12;
		BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xCB, 0xCC, 0xD4));  // AE2 GUI 主背景
		g.fillRect(0, 0, w * scale, h * scale);
		g.dispose();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = src.getRGB(srcX + x, srcY + y);
				int alpha = (rgb >>> 24) & 0xFF;
				if (alpha == 0) continue;
				int r = (rgb >> 16) & 0xFF;
				int gr = (rgb >> 8) & 0xFF;
				int b = rgb & 0xFF;
				float a = alpha / 255f;
				int mr = Math.round(r * a + 0xCB * (1 - a));
				int mg = Math.round(gr * a + 0xCC * (1 - a));
				int mb = Math.round(b * a + 0xD4 * (1 - a));
				int blended = (mr << 16) | (mg << 8) | mb;
				for (int sy = 0; sy < scale; sy++) {
					for (int sx = 0; sx < scale; sx++) {
						img.setRGB(x * scale + sx, y * scale + sy, blended);
					}
				}
			}
		}
		ImageIO.write(img, "png", out);
		System.out.printf("Wrote %s (%dx%d, sprite (%d,%d) %dx%d scaled %dx)%n",
			out.getAbsolutePath(), w * scale, h * scale, srcX, srcY, w, h, scale);
	}
}
