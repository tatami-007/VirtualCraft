import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 AE2 Crystal Science 的代表性物品和方块贴图拼成大 sheet（8× 放大），
 * 用来观察其调色板、构图、立体感模式，作为 AssetTextureGenerator 的设计参考。
 *
 * 输出：tools/ae2cs_reference.png
 */
public class Ae2csReference {
	private static final int SCALE = 8;
	private static final int CELL = 16 * SCALE;
	private static final int LABEL_H = 18;
	private static final int CELL_TOTAL_H = CELL + LABEL_H;
	private static final int COLS = 6;
	private static final int PADDING = 8;

	private static final String ITEM_DIR = "tools/ae2cs/extracted/assets/ae2cs/textures/item";
	private static final String BLOCK_DIR = "tools/ae2cs/extracted/assets/ae2cs/textures/block";

	private static final String[][] SAMPLES = {
			// 卡片 / 升级 / 工具
			{"crystal_growth_card",          ITEM_DIR},
			{"integrated_interface_upgrade", ITEM_DIR},
			{"ender_linker",                 ITEM_DIR},
			{"resonating_pattern_converter", ITEM_DIR},
			{"meteor_pattern_provider_upgrade", ITEM_DIR},
			{"crystal_growth_card",          ITEM_DIR},
			// 处理器
			{"simple_processor",             ITEM_DIR},
			{"resonating_processor",         ITEM_DIR},
			{"resonating_pattern",           ITEM_DIR},
			{"resonating_circuit_print",     ITEM_DIR},
			{"resonating_print_press",       ITEM_DIR},
			{"blank_print_press",            ITEM_DIR},
			// 水晶
			{"purified_fluix_crystal",       ITEM_DIR},
			{"purified_resonating_crystal",  ITEM_DIR},
			{"purified_certus_quartz_crystal", ITEM_DIR},
			{"purified_quantum_crystal",     ITEM_DIR},
			{"purified_redstone_crystal",    ITEM_DIR},
			{"purified_ember_crystal",       ITEM_DIR},
			// 方块
			{"silicon_block",                BLOCK_DIR},
			{"resonating_crystal_block",     BLOCK_DIR},
			{"redstone_crystal_block",       BLOCK_DIR},
			{"meteor_crystal_block",         BLOCK_DIR},
			{"quantum_crystal_block",        BLOCK_DIR},
			{"rose_quartz_block",            BLOCK_DIR},
	};

	public static void main(String[] args) throws Exception {
		int rows = (SAMPLES.length + COLS - 1) / COLS;
		int W = COLS * CELL + (COLS + 1) * PADDING;
		int H = rows * CELL_TOTAL_H + (rows + 1) * PADDING + 24;

		BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(0x1A1A22));
		g.fillRect(0, 0, W, H);

		g.setColor(Color.WHITE);
		g.setFont(new Font("Consolas", Font.BOLD, 14));
		g.drawString("AE2 Crystal Science Reference (8x scale)", PADDING, 18);

		for (int i = 0; i < SAMPLES.length; i++) {
			int col = i % COLS;
			int row = i / COLS;
			int x = PADDING + col * (CELL + PADDING);
			int y = 24 + PADDING + row * (CELL_TOTAL_H + PADDING);

			// 棋盘背景
			drawCheckerboard(out, x, y, CELL, CELL);

			File f = new File(SAMPLES[i][1], SAMPLES[i][0] + ".png");
			if (!f.exists()) {
				g.setColor(Color.RED);
				g.drawString("MISSING", x + 4, y + 16);
				continue;
			}
			BufferedImage src = ImageIO.read(f);
			g.drawImage(src, x, y, CELL, CELL, null);

			g.setColor(new Color(0xC8C8D8));
			g.setFont(new Font("Consolas", Font.PLAIN, 9));
			String name = SAMPLES[i][0];
			if (name.length() > 22) name = name.substring(0, 21) + "…";
			g.drawString(name, x + 2, y + CELL + 13);
		}

		g.dispose();
		File outFile = new File("tools/ae2cs_reference.png");
		ImageIO.write(out, "png", outFile);
		System.out.println("Wrote " + outFile.getAbsolutePath() + " (" + W + "×" + H + ")");
	}

	private static void drawCheckerboard(BufferedImage img, int x, int y, int w, int h) {
		int sq = 8;
		for (int yy = 0; yy < h; yy += sq) {
			for (int xx = 0; xx < w; xx += sq) {
				boolean dark = ((xx / sq) + (yy / sq)) % 2 == 0;
				int rgb = dark ? 0xFF606068 : 0xFF808088;
				int xMax = Math.min(xx + sq, w);
				int yMax = Math.min(yy + sq, h);
				for (int py = yy; py < yMax; py++) {
					for (int px = xx; px < xMax; px++) {
						img.setRGB(x + px, y + py, rgb);
					}
				}
			}
		}
	}
}
