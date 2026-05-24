import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 验证重新生成的 icons.png（v14 改名）里 x=240..255 的 MC-pixel-perfect 16×64 垂直 fill sprite
 * 是否完全符合设计映射表（v7：从 8 宽扩为 16 宽，完整 7 阶镜像）。
 */
public class VerifyVFill {
	// 期望映射：col (x-240) 从上到下 y=0..63 的颜色规律
	// col 0:  全灰 #373737
	// col 1..7:  y=0 灰; y 奇 阶 1..7 LIGHT; y 偶 阶 1..7 DARK; y=63 白
	// col 8..14: y=0 灰; y 奇 阶 7..1 LIGHT (镜像); y 偶 阶 7..1 DARK (镜像); y=63 白
	// col 15: 全白 #FFFFFF

	private static final int[][] EXPECTED_LIGHT_DARK = {
		{0x373737, 0x373737},  // col 0  (左灰)
		{0x420000, 0x2B0000},  // col 1  阶 1
		{0x5C0000, 0x3C0000},  // col 2  阶 2
		{0x7A0000, 0x500000},  // col 3  阶 3
		{0x990000, 0x640000},  // col 4  阶 4
		{0xB80000, 0x780000},  // col 5  阶 5
		{0xD60000, 0x8C0000},  // col 6  阶 6
		{0xF50000, 0xA00000},  // col 7  阶 7 (最亮)
		{0xF50000, 0xA00000},  // col 8  阶 7 镜像
		{0xD60000, 0x8C0000},  // col 9  阶 6 镜像
		{0xB80000, 0x780000},  // col 10 阶 5 镜像
		{0x990000, 0x640000},  // col 11 阶 4 镜像
		{0x7A0000, 0x500000},  // col 12 阶 3 镜像
		{0x5C0000, 0x3C0000},  // col 13 阶 2 镜像
		{0x420000, 0x2B0000},  // col 14 阶 1 镜像
		{0xFFFFFF, 0xFFFFFF},  // col 15 (右白)
	};

	private static final int BASE_X = 240;
	private static final int SPRITE_W = 16;
	private static final int SPRITE_H = 64;

	public static void main(String[] args) throws Exception {
		String path = args.length > 0 ? args[0] : "src/main/resources/assets/virtual_craft/textures/gui/icons.png";
		BufferedImage img = ImageIO.read(new File(path));
		int errors = 0;

		System.out.println("=== Verify " + path + " sprite region [x=" + BASE_X + ".." + (BASE_X + SPRITE_W - 1) + ", y=0..63] ===");

		for (int col = 0; col < SPRITE_W; col++) {
			int x = BASE_X + col;
			int lightExp = EXPECTED_LIGHT_DARK[col][0];
			int darkExp  = EXPECTED_LIGHT_DARK[col][1];

			for (int y = 0; y < SPRITE_H; y++) {
				int actual = img.getRGB(x, y) & 0xFFFFFF;
				int expected;
				String reason;

				if (col == 0) {
					expected = 0x373737;
					reason = "col 0 all-gray";
				} else if (col == SPRITE_W - 1) {
					expected = 0xFFFFFF;
					reason = "col " + (SPRITE_W - 1) + " all-white";
				} else if (y == 0) {
					expected = 0x373737;
					reason = "top row gray";
				} else if (y == SPRITE_H - 1) {
					expected = 0xFFFFFF;
					reason = "bottom row white";
				} else if (y % 2 == 1) {
					expected = lightExp;
					reason = "odd y (light)";
				} else {
					expected = darkExp;
					reason = "even y (dark)";
				}

				if (actual != expected) {
					errors++;
					if (errors <= 20) {
						System.out.printf("  MISMATCH @ (x=%d, y=%d) [col=%d, %s]  expected #%06X  actual #%06X%n",
								x, y, col, reason, expected, actual);
					}
				}
			}
		}

		// 可视化输出（每行展示 16 列）
		System.out.println("\n=== Actual pixel grid (x=" + BASE_X + ".." + (BASE_X + SPRITE_W - 1) + ", y=0..63) ===");
		for (int y = 0; y < SPRITE_H; y++) {
			StringBuilder sb = new StringBuilder(String.format("  y=%2d: ", y));
			for (int col = 0; col < SPRITE_W; col++) {
				int rgb = img.getRGB(BASE_X + col, y) & 0xFFFFFF;
				sb.append(String.format("#%06X ", rgb));
			}
			System.out.println(sb);
		}

		// 颜色直方图（仅 sprite 区域）
		System.out.println("\n=== Color histogram in sprite region ===");
		java.util.Map<Integer, Integer> hist = new java.util.HashMap<>();
		int totalPx = SPRITE_W * SPRITE_H;
		for (int y = 0; y < SPRITE_H; y++) {
			for (int col = 0; col < SPRITE_W; col++) {
				int rgb = img.getRGB(BASE_X + col, y) & 0xFFFFFF;
				hist.merge(rgb, 1, Integer::sum);
			}
		}
		hist.entrySet().stream()
			.sorted(java.util.Map.Entry.<Integer, Integer>comparingByValue().reversed())
			.forEach(e -> {
				double pct = 100.0 * e.getValue() / totalPx;
				System.out.printf("  #%06X  %4d px  %5.2f%%%n", e.getKey(), e.getValue(), pct);
			});

		System.out.println();
		if (errors == 0) {
			System.out.println("[OK] All " + totalPx + " pixels match design spec!");
		} else {
			System.out.println("[FAIL] " + errors + " pixel(s) do not match design.");
			System.exit(1);
		}
	}
}
