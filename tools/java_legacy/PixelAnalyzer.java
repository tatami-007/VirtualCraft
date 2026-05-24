import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 针对 mc_pixel_perfect.png 做行列结构分析：
 *   - 按列（x 方向）采 y=中心的颜色分布
 *   - 按行（y 方向）采 x=中心的颜色分布
 *   - 按目标 sprite 尺寸（8×64 / 16×64）做最近邻降采样并打印
 *
 * Usage:
 *   javac -d tools/_cs tools/PixelAnalyzer.java
 *   java -cp tools/_cs PixelAnalyzer tools/mc_pixel_perfect.png
 */
public class PixelAnalyzer {
	public static void main(String[] args) throws Exception {
		BufferedImage img = ImageIO.read(new File(args[0]));
		int W = img.getWidth(), H = img.getHeight();
		System.out.printf("Size: %d x %d%n", W, H);

		// === 按列扫描（横向像素规律） ===
		System.out.println("\n=== Column sweep (y = H/2 = " + (H / 2) + ") ===");
		for (int x = 0; x < W; x++) {
			int rgb = img.getRGB(x, H / 2) & 0xFFFFFF;
			if (x % 16 == 0) System.out.printf("%n  x=%3d: ", x);
			System.out.printf("#%06X ", rgb);
		}
		System.out.println();

		// === 按行扫描（纵向像素规律），取 x=W/2 中心列 ===
		System.out.println("\n=== Row sweep (x = W/2 = " + (W / 2) + ") ===");
		int prev = -1;
		int runStart = 0;
		for (int y = 0; y < H; y++) {
			int rgb = img.getRGB(W / 2, y) & 0xFFFFFF;
			if (rgb != prev) {
				if (prev != -1) {
					System.out.printf("  y=%3d..%3d (%2d px): #%06X%n", runStart, y - 1, y - runStart, prev);
				}
				prev = rgb;
				runStart = y;
			}
		}
		System.out.printf("  y=%3d..%3d (%2d px): #%06X%n", runStart, H - 1, H - runStart, prev);

		// === 多列扫描看白色高光和灰色背景的位置 ===
		System.out.println("\n=== Multi-col run-length on specific columns ===");
		int[] testCols = {0, 5, 10, 20, 40, 60, 80, 100, 120, 140, 155, 159};
		for (int x : testCols) {
			System.out.printf("%n-- Column x=%d --%n", x);
			int pv = -1, rs = 0;
			for (int y = 0; y < H; y++) {
				int rgb = img.getRGB(x, y) & 0xFFFFFF;
				if (rgb != pv) {
					if (pv != -1) {
						System.out.printf("  y=%3d..%3d (%2d): #%06X%n", rs, y - 1, y - rs, pv);
					}
					pv = rgb;
					rs = y;
				}
			}
			System.out.printf("  y=%3d..%3d (%2d): #%06X%n", rs, H - 1, H - rs, pv);
		}

		// === 最近邻降采样到 8×64 和 16×64 ===
		for (int targetW : new int[]{8, 16}) {
			int targetH = 64;
			System.out.printf("%n=== Nearest-neighbor downsample to %dx%d ===%n", targetW, targetH);
			double sx = W / (double) targetW;
			double sy = H / (double) targetH;
			for (int ty = 0; ty < targetH; ty++) {
				int srcY = (int) ((ty + 0.5) * sy);
				StringBuilder sb = new StringBuilder(String.format("  y=%2d: ", ty));
				for (int tx = 0; tx < targetW; tx++) {
					int srcX = (int) ((tx + 0.5) * sx);
					int rgb = img.getRGB(srcX, srcY) & 0xFFFFFF;
					sb.append(String.format("#%06X ", rgb));
				}
				System.out.println(sb);
			}
		}

		// === 按行做区域平均采样 (目标 8×64) ===
		System.out.println("\n=== Block-average downsample to 8x64 ===");
		int tw = 8, th = 64;
		for (int ty = 0; ty < th; ty++) {
			int y0 = ty * H / th;
			int y1 = (ty + 1) * H / th;
			StringBuilder sb = new StringBuilder(String.format("  y=%2d: ", ty));
			for (int tx = 0; tx < tw; tx++) {
				int x0 = tx * W / tw;
				int x1 = (tx + 1) * W / tw;
				long r = 0, g = 0, b = 0, n = 0;
				for (int yy = y0; yy < y1; yy++) {
					for (int xx = x0; xx < x1; xx++) {
						int rgb = img.getRGB(xx, yy);
						r += (rgb >> 16) & 0xFF;
						g += (rgb >> 8) & 0xFF;
						b += rgb & 0xFF;
						n++;
					}
				}
				int rgb = ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
				sb.append(String.format("#%06X ", rgb));
			}
			System.out.println(sb);
		}
	}
}
