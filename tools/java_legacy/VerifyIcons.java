import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 验证输出 icons.png（v14 从 widgets.png 改名）里 copy-from-AE2 图标像素与源图严格一致 + 原 BURN/PICKAXE 位置已清空。
 *
 * 校验项（v14）：
 *   1. out[48..63,40..55] (INPUT)   == ref[0..15, 112..127]   （(gx,gy)=(0,7) 蓝色四向阀）
 *   2. out[64..79,40..55] (OUTPUT)  == ref[16..31, 112..127]  （(gx,gy)=(1,7) 橙色四向阀）
 *   3. out[112..127,40..55] (LINK)  == ref[16..31, 96..111]   （(gx,gy)=(1,6) wifi 信号）
 *   4. out[96..111,40..55] (原 BURN)    全部 alpha=0 透明
 *   5. out[128..143,40..55] (原 PICKAXE) 全部 alpha=0 透明
 *
 * out = src/main/resources/.../icons.png（生成器输出的运行时 sprite sheet）
 * ref = tools/refs/ae2cs_icons.png（AE2-CS 原版参考，只读）
 */
public class VerifyIcons {
	public static void main(String[] args) throws Exception {
		BufferedImage out = ImageIO.read(new File("src/main/resources/assets/virtual_craft/textures/gui/icons.png"));
		BufferedImage ref = ImageIO.read(new File("tools/refs/ae2cs_icons.png"));
		int errors = 0;

		// 校验 3 个 copy-from-icons 图标（v10：input/output 改用 (0,7)/(1,7) 蓝/橙阀）
		errors += checkCopy(out, "INPUT",   48, 40, ref,  0, 112);
		errors += checkCopy(out, "OUTPUT",  64, 40, ref, 16, 112);
		errors += checkCopy(out, "LINK",   112, 40, ref, 16, 96);

		// 校验原 BURN/PICKAXE 位置已清空（全透明）
		errors += checkEmpty(out, "BURN (u=96)",     96, 40);
		errors += checkEmpty(out, "PICKAXE (u=128)", 128, 40);

		System.out.println();
		if (errors == 0) {
			System.out.println("[OK] All icon checks passed!");
		} else {
			System.out.println("[FAIL] " + errors + " check(s) failed.");
			System.exit(1);
		}
	}

	private static int checkCopy(BufferedImage out, String label, int wx, int wy,
	                              BufferedImage ref, int ix, int iy) {
		int mismatched = 0;
		for (int dy = 0; dy < 16; dy++) {
			for (int dx = 0; dx < 16; dx++) {
				int oRgb = out.getRGB(wx + dx, wy + dy);
				int rRgb = ref.getRGB(ix + dx, iy + dy);
				if (oRgb != rRgb) {
					mismatched++;
					if (mismatched <= 3) {
						System.out.printf("  [FAIL] %s @ +(%d,%d): out=#%08X vs ref=#%08X%n",
								label, dx, dy, oRgb, rRgb);
					}
				}
			}
		}
		if (mismatched == 0) {
			System.out.printf("  [OK] %s: 256/256 pixels @ out(%d,%d) == ref(%d,%d)%n",
					label, wx, wy, ix, iy);
			return 0;
		}
		System.out.printf("  [FAIL] %s: %d/256 pixel(s) mismatch%n", label, mismatched);
		return 1;
	}

	private static int checkEmpty(BufferedImage out, String label, int wx, int wy) {
		int nonEmpty = 0;
		for (int dy = 0; dy < 16; dy++) {
			for (int dx = 0; dx < 16; dx++) {
				int rgb = out.getRGB(wx + dx, wy + dy);
				int alpha = (rgb >>> 24) & 0xFF;
				if (alpha > 0) nonEmpty++;
			}
		}
		if (nonEmpty == 0) {
			System.out.printf("  [OK] %s: 256/256 pixels fully transparent @ out(%d,%d)%n", label, wx, wy);
			return 0;
		}
		System.out.printf("  [FAIL] %s: %d/256 pixel(s) not transparent @ out(%d,%d)%n", label, nonEmpty, wx, wy);
		return 1;
	}
}
