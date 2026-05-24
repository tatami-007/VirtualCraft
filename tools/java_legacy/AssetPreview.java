import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 AssetTextureGenerator 输出的 13 张 16×16 贴图拼成一张大 sheet 预览，
 * 每张 8× 放大 + 名字标注，方便肉眼 review 不必到游戏里看。
 *
 * 输出：tools/asset_preview.png
 */
public class AssetPreview {
	private static final int SCALE = 8;
	private static final int CELL = 16 * SCALE; // 128
	private static final int LABEL_H = 18;
	private static final int CELL_TOTAL_H = CELL + LABEL_H;
	private static final int COLS = 5;
	private static final int PADDING = 8;

	private static final String ITEM_DIR = "src/main/resources/assets/virtual_craft/textures/item";
	private static final String BLOCK_DIR = "src/main/resources/assets/virtual_craft/textures/block";

	private static final String[][] ITEMS = {
			{"electric_ingot",                 ITEM_DIR},
			{"hash_voucher",                   ITEM_DIR},
			{"electric_absorber",              ITEM_DIR},
			{"electric_absorber_on",           ITEM_DIR},
			{"mining_surveyor",                ITEM_DIR},
			{"speed_upgrade_card",             ITEM_DIR},
			{"wireless_transfer_upgrade_card", ITEM_DIR},
			{"link_card",                      ITEM_DIR},
			{"electric_ball_generator",        BLOCK_DIR},
			{"wireless_power_tower",           BLOCK_DIR},
			{"holographic_shop",               BLOCK_DIR},
			{"pow_miner",                      BLOCK_DIR},
	};

	public static void main(String[] args) throws Exception {
		int rows = (ITEMS.length + COLS - 1) / COLS;
		int W = COLS * CELL + (COLS + 1) * PADDING;
		int H = rows * CELL_TOTAL_H + (rows + 1) * PADDING + 24;

		BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(0x202028));
		g.fillRect(0, 0, W, H);

		// 标题
		g.setColor(Color.WHITE);
		g.setFont(new Font("Consolas", Font.BOLD, 14));
		g.drawString("VirtualCraft Asset Preview (8x scale)", PADDING, 18);

		for (int i = 0; i < ITEMS.length; i++) {
			int col = i % COLS;
			int row = i / COLS;
			int x = PADDING + col * (CELL + PADDING);
			int y = 24 + PADDING + row * (CELL_TOTAL_H + PADDING);

			// cell 边框
			g.setColor(new Color(0x303040));
			g.fillRect(x - 2, y - 2, CELL + 4, CELL_TOTAL_H + 4);

			// 棋盘背景（区分透明像素）
			drawCheckerboard(out, x, y, CELL, CELL);

			// 加载并放大 blit
			File f = new File(ITEMS[i][1], ITEMS[i][0] + ".png");
			BufferedImage src = ImageIO.read(f);
			g.drawImage(src, x, y, CELL, CELL, null);

			// 标签
			g.setColor(new Color(0xC8C8D8));
			g.setFont(new Font("Consolas", Font.PLAIN, 10));
			String name = ITEMS[i][0];
			if (name.length() > 22) {
				name = name.substring(0, 21) + "…";
			}
			g.drawString(name, x + 2, y + CELL + 13);
		}

		g.dispose();
		File outFile = new File("tools/asset_preview.png");
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
