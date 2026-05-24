import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 widgets.png (256x128) 放大 6 倍（1536x768）并叠加：
 *   - #CBCCD4 AE2 GUI 主背景
 *   - 16×16 细网格 + 区段边界粗红线 + 区段标签
 * 让每个 sprite 的位置和边界都清晰可见，验证没有 "重叠" 问题。
 */
public class WidgetsAnnotated {
	public static void main(String[] args) throws Exception {
		BufferedImage src = ImageIO.read(new File("src/main/resources/assets/virtual_craft/textures/gui/widgets.png"));
		int W = src.getWidth(), H = src.getHeight();
		int scale = 6;
		int destW = W * scale;
		int destH = H * scale;
		BufferedImage out = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		// 1) GUI 背景
		g.setColor(new Color(0xCB, 0xCC, 0xD4));
		g.fillRect(0, 0, destW, destH);
		g.dispose();

		// 2) alpha blend widgets.png 到背景
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int rgb = src.getRGB(x, y);
				int alpha = (rgb >>> 24) & 0xFF;
				if (alpha == 0) continue;
				int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				float a = alpha / 255f;
				int mr = Math.round(r * a + 0xCB * (1 - a));
				int mg = Math.round(gr * a + 0xCC * (1 - a));
				int mb = Math.round(b * a + 0xD4 * (1 - a));
				int blended = (mr << 16) | (mg << 8) | mb;
				for (int sy = 0; sy < scale; sy++)
					for (int sx = 0; sx < scale; sx++)
						out.setRGB(x * scale + sx, y * scale + sy, blended);
			}
		}

		// 3) 叠加区段边界（粗红线）标出每个 sprite 的精确边界
		Graphics2D go = out.createGraphics();
		go.setStroke(new java.awt.BasicStroke(2f));
		go.setColor(new Color(0x00, 0xAA, 0x00, 200));
		// 16x16 细网格
		for (int gx = 0; gx <= W / 16; gx++) {
			int px = gx * 16 * scale;
			go.drawLine(px, 0, px, destH);
		}
		for (int gy = 0; gy <= H / 16; gy++) {
			int py = gy * 16 * scale;
			go.drawLine(0, py, destW, py);
		}

		// 4) 区段边界（粗红线 + 标签）
		go.setStroke(new java.awt.BasicStroke(4f));
		go.setColor(new Color(0xD0, 0x10, 0x10));
		go.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 20));
		// 进度箭头
		rect(go, 0, 0, 23, 17, scale, "arrow");
		// burn_h
		rect(go, 0, 24, 92, 8, scale, "burn_h_amber");
		// 7 图标
		rect(go, 0, 40, 128, 16, scale, "icons(gear/back/?/IN/OUT/dis/link)");
		// 垂直 fill
		rect(go, 240, 0, 16, 64, scale, "energy_v_full");
		// 调色板
		rect(go, 0, 64, 64, 64, scale, "ae2_palette_4x4");
		go.dispose();

		ImageIO.write(out, "png", new File("tools/widgets_v10_annotated.png"));
		System.out.println("Wrote tools/widgets_v10_annotated.png (" + destW + "x" + destH + ")");
	}

	private static void rect(Graphics2D g, int x, int y, int w, int h, int scale, String label) {
		g.drawRect(x * scale, y * scale, w * scale, h * scale);
		g.drawString(label, x * scale + 2, y * scale - 4);
	}
}
