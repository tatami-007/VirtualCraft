import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * VirtualCraft 物品与机器方块贴图程序化生成器。
 *
 * <p>风格与 {@code GuiTextureGenerator} 一致（AE2 Crystal Science 紫灰调），但负责
 * <ul>
 *   <li>{@code textures/item/*.png}：8 个非币种物品（electric_ingot, hash_voucher,
 *       electric_absorber, electric_absorber_on, mining_surveyor, speed_upgrade_card,
 *       wireless_transfer_upgrade_card, link_card）。</li>
 *   <li>{@code textures/block/*.png}：4 个机器方块的 cube_all 16×16 单图
 *       （electric_ball_generator / wireless_power_tower /
 *       holographic_shop / pow_miner）。</li>
 * </ul>
 *
 * <h3>共用设计语言</h3>
 * <ul>
 *   <li>外缘 1px 深紫黑 {@link Palette#OUTLINE} 描边。</li>
 *   <li>顶 1px + 左 1px 浅高光 {@link Palette#HIGHLIGHT}；底 1px + 右 1px 紫灰阴影
 *       {@link Palette#SHADOW}。</li>
 *   <li>主体 14×14 内填充各模块自定义。</li>
 *   <li>卡片类（speed/wireless/link）统一用 12×14 圆角矩形 + 边框 + 中央符号。</li>
 *   <li>球形/罗盘类用 Bresenham 圆 + 中央 accent。</li>
 *   <li>方块统一用机箱框架（外壳 + 4 螺丝 + 中央 8×8 特征图标）。</li>
 * </ul>
 *
 * <h3>编译运行</h3>
 * <pre>
 *   javac tools/AssetTextureGenerator.java
 *   java  -cp tools AssetTextureGenerator
 * </pre>
 * 在仓库根目录运行；运行后会覆盖
 * {@code src/main/resources/assets/virtual_craft/textures/item/*.png} 和
 * {@code src/main/resources/assets/virtual_craft/textures/block/*.png} 中的指定贴图。
 */
public class AssetTextureGenerator {

	// ====================================================================
	// 调色板（与 GuiTextureGenerator 的 Palette 同源，保证视觉一致）
	// ====================================================================
	private static final class Palette {
		// 通用窗体
		static final Color OUTLINE = c(0x413F54);
		static final Color HIGHLIGHT = c(0xF2F2F2);
		static final Color SHADOW = c(0x696D88);
		static final Color SCREW = c(0x4D4D67);

		// 机箱主体
		static final Color BODY_FILL = c(0xADB0C4);
		static final Color BODY_TOP = c(0x9A9FB4);
		static final Color BODY_DEEP = c(0x878FA5);

		// Accent 紫（AE2 标志色，多处使用）
		static final Color PURPLE = c(0x915DCD);
		static final Color PURPLE_DARK = c(0x6054A6);
		static final Color PURPLE_LIGHT = c(0xA79CFF);
		static final Color PURPLE_GLOW = c(0xC8B6FF);

		// FE 红（电球/能源核心）
		static final Color FE_RED = c(0xD22A2A);
		static final Color FE_RED_DARK = c(0x801010);
		static final Color FE_RED_LIGHT = c(0xFF9090);

		// 黄/电核心
		static final Color ELECTRIC = c(0xFFC107);
		static final Color ELECTRIC_HOT = c(0xFFF080);
		static final Color ELECTRIC_DIM = c(0xC68F08);

		// 金属/锭
		static final Color METAL_HIGHLIGHT = c(0xE6E8F2);
		static final Color METAL_BASE = c(0xB5B8CC);
		static final Color METAL_SHADE = c(0x6E7385);

		// 纸/凭证
		static final Color PAPER = c(0xE8DDB8);
		static final Color PAPER_DARK = c(0xC8B580);
		static final Color INK_BROWN = c(0x8C5A1F);
		static final Color INK_DARK = c(0x4D2E0E);

		// 罗盘/勘探（黄铜）
		static final Color BRASS = c(0xB5872C);
		static final Color BRASS_DARK = c(0x5D4520);
		static final Color NORTH_RED = c(0xD22A2A);
		static final Color SOUTH_BLUE = c(0x373B72);

		// 链接卡
		static final Color GOLD_LIGHT = c(0xFFD75A);

		// 无线（青）
		static final Color CYAN_LIGHT = c(0x80D7FF);
		static final Color CYAN_DARK = c(0x1F4D6E);

		// 商店（青绿）
		static final Color TEAL = c(0x2C8C8C);
		static final Color TEAL_DARK = c(0x18504F);
		static final Color TEAL_LIGHT = c(0x6FBDB9);

		// 矿机（铂银）
		static final Color SILVER = c(0xC4C8D6);
		static final Color SILVER_DARK = c(0x747890);

		// 货币 generator（金）
		static final Color GOLD = c(0xD4A434);
		static final Color GOLD_DARK = c(0x8C6A1B);
		static final Color GOLD_HIGH = c(0xFFE680);

		// 电吸收器（玻璃绿）
		static final Color ABSORBER_GLASS = c(0x8C7AC0);
		static final Color ABSORBER_GLASS_DARK = c(0x4A3D80);
		static final Color ABSORBER_GLASS_ON = c(0xC8B6FF);

		// ============================================================
		// AE2 Crystal Science 风格扩展色（水晶方块 + 立体卡片 + 处理器风）
		// 4 层凹陷框：OUTLINE(0) → DEEP → MID → INNER；INNER 之上还有 1px HI 高光
		// ============================================================
		// —— 共用银白机箱风（参考 silicon_block / resonating_print_press）——
		static final Color SILVER_HI = c(0xE6E8F2);    // 顶左 1px 镜面白
		static final Color SILVER_MID = c(0xC0C4D6);   // 主体 14×14
		static final Color SILVER_LOW = c(0x8088A0);   // 底右 1px
		static final Color SILVER_BLACK = c(0x282834); // 凹陷最深处

		// —— 卡片底色（AE2CS 卡片是浅银底 + 彩色 accent 条）——
		static final Color CARD_BASE_HI = c(0xE2E4F0);
		static final Color CARD_BASE = c(0xB2B6C8);
		static final Color CARD_BASE_LOW = c(0x6F7488);

		// —— 红色水晶（参考 redstone_crystal_block / purified_redstone_crystal）——
		static final Color RED_GEM_HI = c(0xFF8888);
		static final Color RED_GEM = c(0xD03030);
		static final Color RED_GEM_DEEP = c(0x6A0808);
		static final Color RED_GEM_GLINT = c(0xFFC0C0);

		// —— 金色水晶（参考 ember_crystal / rose_quartz）——
		static final Color GOLD_GEM_HI = c(0xFFE680);
		static final Color GOLD_GEM = c(0xD4A434);
		static final Color GOLD_GEM_DEEP = c(0x6A4810);
		static final Color GOLD_GEM_GLINT = c(0xFFFFC8);

		// —— 紫色水晶（参考 quantum / fluix）——
		static final Color VIOLET_GEM_HI = c(0xC080FF);
		static final Color VIOLET_GEM = c(0x6038A8);
		static final Color VIOLET_GEM_DEEP = c(0x2A1058);
		static final Color VIOLET_GEM_GLINT = c(0xE6B0FF);

		// —— 青色水晶（参考 certus_quartz）——
		static final Color CYAN_GEM_HI = c(0xC0F0FF);
		static final Color CYAN_GEM = c(0x4090C8);
		static final Color CYAN_GEM_DEEP = c(0x103060);
		static final Color CYAN_GEM_GLINT = c(0xE0F8FF);

		// —— 青绿水晶（玻璃 / 全息）——
		static final Color TEAL_GEM_HI = c(0x80E8E0);
		static final Color TEAL_GEM = c(0x2E8C84);
		static final Color TEAL_GEM_DEEP = c(0x103030);

		// —— LED / 处理器红点 —— 
		static final Color LED_RED = c(0xFF3838);
		static final Color LED_RED_DARK = c(0x801010);

		private Palette() {}

		private static Color c(int hex) {
			return new Color(hex);
		}
	}

	// ====================================================================
	// 输出路径
	// ====================================================================
	private static final String ITEM_DIR = "src/main/resources/assets/virtual_craft/textures/item";
	private static final String BLOCK_DIR = "src/main/resources/assets/virtual_craft/textures/block";

	private static final int SIZE = 16;

	public static void main(String[] args) throws IOException {
		ensure(ITEM_DIR);
		ensure(BLOCK_DIR);

		// 注意：electric_ingot / electric_absorber / electric_absorber_on / electric_ball
		// 这 4 张是手绘原图，本生成器保留不动。drawElectricIngot/drawElectricAbsorber 仍然
		// 留在文件里作为参考实现，但不会写入资源目录。

		// ========== 5 个新生成物品（仅这些被写入） ==========
		save(ITEM_DIR, "hash_voucher.png", drawHashVoucher());
		save(ITEM_DIR, "mining_surveyor.png", drawMiningSurveyor());
		save(ITEM_DIR, "speed_upgrade_card.png", drawSpeedUpgradeCard());
		save(ITEM_DIR, "wireless_transfer_upgrade_card.png", drawWirelessTransferUpgradeCard());
		save(ITEM_DIR, "link_card.png", drawLinkCard());

		// ========== 4 个机器方块（cube_all） ==========
		save(BLOCK_DIR, "electric_ball_generator.png", drawElectricBallGeneratorBlock());
		save(BLOCK_DIR, "wireless_power_tower.png", drawWirelessPowerTowerBlock());
		save(BLOCK_DIR, "holographic_shop.png", drawHolographicShopBlock());
		save(BLOCK_DIR, "pow_miner.png", drawPowMinerBlock());

		System.out.println("Done. 10 textures regenerated (4 hand-drawn skipped).");
	}

	// ====================================================================
	// === 物品 ===
	// ====================================================================

	/**
	 * electric_ingot: 经典金属锭外形（梯形条），银紫色金属底 + 中央 闪电 accent。
	 */
	private static BufferedImage drawElectricIngot() {
		BufferedImage img = canvas();
		// 锭形：宽渐变（顶 8 → 底 12），高 6 像素，垂直居中
		// y=5..10 共 6 行
		int[] left =  { 4,  3,  2,  2,  3,  4 };
		int[] right = {11, 12, 13, 13, 12, 11 };
		// 主体填充
		for (int row = 0; row < 6; row++) {
			int y = 5 + row;
			fillRect(img, left[row], y, right[row] - left[row] + 1, 1, Palette.METAL_BASE);
		}
		// 顶 1px 高光线
		for (int row = 0; row < 1; row++) {
			int y = 5 + row;
			fillRect(img, left[row], y, right[row] - left[row] + 1, 1, Palette.METAL_HIGHLIGHT);
		}
		// 底 1px 暗
		for (int row = 5; row < 6; row++) {
			int y = 5 + row;
			fillRect(img, left[row], y, right[row] - left[row] + 1, 1, Palette.METAL_SHADE);
		}
		// 描边（每行左右 1px 暗）
		for (int row = 0; row < 6; row++) {
			int y = 5 + row;
			pixel(img, left[row] - 1, y, Palette.OUTLINE);
			pixel(img, right[row] + 1, y, Palette.OUTLINE);
		}
		// 上下底边描边
		fillRect(img, 4, 4, 8, 1, Palette.OUTLINE);
		fillRect(img, 2, 11, 12, 1, Palette.OUTLINE);

		// 中央闪电符号（accent 紫）
		// Z 形 闪电：左上 -> 中 -> 右下
		pixel(img, 7, 6, Palette.PURPLE_LIGHT);
		pixel(img, 8, 7, Palette.PURPLE_LIGHT);
		pixel(img, 7, 8, Palette.PURPLE_LIGHT);
		pixel(img, 8, 9, Palette.PURPLE_LIGHT);
		pixel(img, 9, 7, Palette.PURPLE);
		pixel(img, 8, 8, Palette.PURPLE);
		return img;
	}

	/**
	 * hash_voucher: AE2CS 风 3D 平铺纸（仿 resonating_pattern）。
	 *
	 * <p>立体银纸底（顶+左 1px 高光、底+右 1px 阴影）+ 中央紫色 # 印章
	 * + 4 角金钉 + 顶部撕边锯齿。</p>
	 */
	private static BufferedImage drawHashVoucher() {
		BufferedImage img = canvas();
		// 主体：14×12 银纸 (1, 2) -> (14, 13)
		fillRect(img, 1, 2, 14, 12, Palette.CARD_BASE);
		// 顶 + 左 1px 高光
		fillRect(img, 1, 2, 14, 1, Palette.CARD_BASE_HI);
		fillRect(img, 1, 2, 1, 12, Palette.CARD_BASE_HI);
		// 底 + 右 1px 阴影
		fillRect(img, 1, 13, 14, 1, Palette.CARD_BASE_LOW);
		fillRect(img, 14, 2, 1, 12, Palette.CARD_BASE_LOW);
		// 外缘黑描边
		drawOutline(img, 1, 2, 14, 12, Palette.SILVER_BLACK);
		// 顶部撕边锯齿（凹凸）
		for (int x = 2; x < 14; x += 2) {
			pixel(img, x, 1, Palette.CARD_BASE_HI);
			pixel(img, x, 2, Palette.SILVER_BLACK);
		}

		// 中央紫色 # 印章 (5×5 in 5..10, 5..10)
		fillRect(img, 5, 6, 6, 1, Palette.VIOLET_GEM);
		fillRect(img, 5, 9, 6, 1, Palette.VIOLET_GEM);
		fillRect(img, 6, 5, 1, 6, Palette.VIOLET_GEM);
		fillRect(img, 9, 5, 1, 6, Palette.VIOLET_GEM);
		// # 内部高光（让"印章"有立体感）
		pixel(img, 6, 6, Palette.VIOLET_GEM_HI);
		pixel(img, 9, 6, Palette.VIOLET_GEM_HI);
		pixel(img, 6, 9, Palette.VIOLET_GEM_DEEP);
		pixel(img, 9, 9, Palette.VIOLET_GEM_DEEP);

		// 4 角金钉（装订点）
		pixel(img, 2, 3, Palette.GOLD_GEM_GLINT);
		pixel(img, 13, 3, Palette.GOLD_GEM_GLINT);
		pixel(img, 2, 12, Palette.GOLD_GEM_GLINT);
		pixel(img, 13, 12, Palette.GOLD_GEM_GLINT);
		return img;
	}

	/**
	 * electric_absorber: 紫色玻璃球，球内闪电核心；on 状态核心更亮 + 外晕。
	 */
	private static BufferedImage drawElectricAbsorber(boolean on) {
		BufferedImage img = canvas();
		// 圆心 (8, 8)，半径 6（直径 12）；用 midpoint 圆
		Color glass = on ? Palette.ABSORBER_GLASS_ON : Palette.ABSORBER_GLASS;
		Color glassDark = Palette.ABSORBER_GLASS_DARK;
		fillCircle(img, 8, 8, 6, glass);
		drawCircle(img, 8, 8, 6, glassDark);
		drawCircle(img, 8, 8, 7, Palette.OUTLINE);
		// 左上高光
		pixel(img, 5, 5, Palette.HIGHLIGHT);
		pixel(img, 6, 4, Palette.HIGHLIGHT);
		pixel(img, 4, 6, Palette.HIGHLIGHT);

		// 内核闪电（4×4，居中）
		Color core = on ? Palette.ELECTRIC_HOT : Palette.ELECTRIC;
		Color coreDim = on ? Palette.ELECTRIC : Palette.ELECTRIC_DIM;
		// Z 形闪电
		pixel(img, 7, 6, core);
		pixel(img, 8, 7, core);
		pixel(img, 9, 8, core);
		pixel(img, 8, 8, core);
		pixel(img, 7, 9, core);
		pixel(img, 8, 10, core);
		// 暗影增强
		pixel(img, 9, 7, coreDim);
		pixel(img, 7, 10, coreDim);

		if (on) {
			// 外晕（4 个对角点）
			pixel(img, 1, 1, Palette.PURPLE_GLOW);
			pixel(img, 14, 1, Palette.PURPLE_GLOW);
			pixel(img, 1, 14, Palette.PURPLE_GLOW);
			pixel(img, 14, 14, Palette.PURPLE_GLOW);
		}
		return img;
	}

	/**
	 * mining_surveyor: AE2CS 风银壳仪器（仿 resonating_print_press）。
	 *
	 * <p>4 层凹陷银框 + 中央青色屏幕 + 红蓝双色指针 + 4 角红 LED。</p>
	 */
	private static BufferedImage drawMiningSurveyor() {
		BufferedImage img = drawCrystalGemBlock(
				Palette.SILVER_HI, Palette.SILVER_MID, Palette.SILVER_LOW, Palette.CYAN_GEM_DEEP);
		// 中央 6×6 暗腔 (5..10) 是青色屏幕底
		// 上下 1px 屏幕扫描线
		fillRect(img, 5, 5, 6, 1, Palette.CYAN_GEM);
		fillRect(img, 5, 10, 6, 1, Palette.CYAN_GEM);
		// 罗盘指针：上半红（北）+ 下半蓝（南）
		fillRect(img, 7, 6, 1, 2, Palette.NORTH_RED);
		fillRect(img, 7, 8, 1, 2, Palette.SOUTH_BLUE);
		// 指针端点高光
		pixel(img, 7, 6, Palette.RED_GEM_GLINT);
		pixel(img, 7, 9, Palette.CYAN_GEM_HI);
		// 指针中心枢轴
		pixel(img, 7, 7, Palette.HIGHLIGHT);
		// 4 角红 LED（仪器风）
		pixel(img, 3, 3, Palette.LED_RED);
		pixel(img, 12, 3, Palette.LED_RED);
		pixel(img, 3, 12, Palette.LED_RED);
		pixel(img, 12, 12, Palette.LED_RED);
		return img;
	}

	/**
	 * speed_upgrade_card: AE2CS 3D 银卡 + 紫色 tab + 中央粗体 ">>" 双箭头。
	 *
	 * <p>箭头主体用亮紫水晶色（VIOLET_GEM = 6038A8），尖端 1px 高光（GLINT），
	 * 阴影 1px 深紫（DEEP），三色立体提升识别度。</p>
	 */
	private static BufferedImage drawSpeedUpgradeCard() {
		BufferedImage img = drawCardSilver3D(
				Palette.VIOLET_GEM_HI, Palette.VIOLET_GEM, Palette.VIOLET_GEM_DEEP);
		// 双箭头：主体用 VIOLET_GEM 亮紫，每个 ">" 5×5 像素 2px 粗
		// 第一箭头 (3, 7)
		drawFatChevron(img, 3, 7, Palette.VIOLET_GEM, Palette.VIOLET_GEM_GLINT);
		// 第二箭头 (7, 7)
		drawFatChevron(img, 7, 7, Palette.VIOLET_GEM, Palette.VIOLET_GEM_GLINT);
		// 给两箭头顶/左加 1px 深紫高光强化立体感
		pixel(img, 3, 5, Palette.VIOLET_GEM_HI);
		pixel(img, 7, 5, Palette.VIOLET_GEM_HI);
		// 给两箭头底/右加 1px 深紫阴影
		pixel(img, 3, 9, Palette.VIOLET_GEM_DEEP);
		pixel(img, 7, 9, Palette.VIOLET_GEM_DEEP);
		return img;
	}

	/**
	 * wireless_transfer_upgrade_card: AE2CS 3D 银卡 + 青色 tab + 中央 WiFi 扇形。
	 *
	 * <p>WiFi 扇形："底部信号源 + 3 道弧线辐射"，比同心圆更易辨认是无线信号。</p>
	 */
	private static BufferedImage drawWirelessTransferUpgradeCard() {
		BufferedImage img = drawCardSilver3D(
				Palette.CYAN_GEM_HI, Palette.CYAN_GEM, Palette.CYAN_GEM_DEEP);
		// 信号源点（底部中央 (7, 11)）
		pixel(img, 7, 11, Palette.CYAN_GEM_GLINT);
		pixel(img, 7, 10, Palette.CYAN_GEM);
		// 第 1 道弧（小，r≈2，y=9..10）
		pixel(img, 5, 9, Palette.CYAN_GEM);
		pixel(img, 6, 8, Palette.CYAN_GEM);
		pixel(img, 7, 8, Palette.CYAN_GEM_GLINT);
		pixel(img, 8, 8, Palette.CYAN_GEM);
		pixel(img, 9, 9, Palette.CYAN_GEM);
		// 第 2 道弧（中，r≈3）
		pixel(img, 4, 7, Palette.CYAN_GEM_HI);
		pixel(img, 5, 6, Palette.CYAN_GEM_HI);
		pixel(img, 7, 5, Palette.CYAN_GEM_HI);
		pixel(img, 9, 6, Palette.CYAN_GEM_HI);
		pixel(img, 10, 7, Palette.CYAN_GEM_HI);
		pixel(img, 6, 6, Palette.CYAN_GEM_HI);
		pixel(img, 8, 6, Palette.CYAN_GEM_HI);
		// 中心高亮点
		pixel(img, 7, 7, Palette.HIGHLIGHT);
		return img;
	}

	/**
	 * link_card: AE2CS 3D 银卡 + 金色 tab + 中央两节点连线（仿 ender_linker）。
	 *
	 * <p>视觉语义："binding" 用两个 3×3 紫水晶节点 + 中间 2px 高粉紫能量线表达。</p>
	 */
	private static BufferedImage drawLinkCard() {
		BufferedImage img = drawCardSilver3D(
				Palette.GOLD_GEM_HI, Palette.GOLD_GEM, Palette.GOLD_GEM_DEEP);
		// 左节点 3×3 (3..5, 6..8)
		fillRect(img, 3, 6, 3, 3, Palette.VIOLET_GEM);
		pixel(img, 3, 6, Palette.VIOLET_GEM_GLINT);   // 左上高光
		pixel(img, 4, 7, Palette.VIOLET_GEM_HI);      // 中央亮
		pixel(img, 5, 8, Palette.VIOLET_GEM_DEEP);    // 右下阴影
		drawOutline(img, 3, 6, 3, 3, Palette.SILVER_BLACK);
		// 右节点 3×3 (8..10, 6..8)
		fillRect(img, 8, 6, 3, 3, Palette.VIOLET_GEM);
		pixel(img, 8, 6, Palette.VIOLET_GEM_GLINT);
		pixel(img, 9, 7, Palette.VIOLET_GEM_HI);
		pixel(img, 10, 8, Palette.VIOLET_GEM_DEEP);
		drawOutline(img, 8, 6, 3, 3, Palette.SILVER_BLACK);
		// 中间能量连线 (6..7, 7..8) — 2px 高粉紫脉冲
		fillRect(img, 6, 7, 2, 2, Palette.VIOLET_GEM_GLINT);
		pixel(img, 6, 7, Palette.HIGHLIGHT);
		pixel(img, 7, 8, Palette.VIOLET_GEM_HI);
		return img;
	}

	// ====================================================================
	// === 5 个机器方块（cube_all 单图，16×16） ===
	// ====================================================================

	/**
	 * electric_ball_generator: 红色水晶方块（仿 redstone_crystal_block）+ 中央闪电球。
	 *
	 * <p>4 层凹陷红框 + 中央 4×4 红黄高对比能量球（球体 + 内核 + 闪电）+ 4 角红闪光。</p>
	 */
	private static BufferedImage drawElectricBallGeneratorBlock() {
		BufferedImage img = drawCrystalGemBlock(
				Palette.RED_GEM_HI, Palette.RED_GEM, Palette.RED_GEM_DEEP, Palette.RED_GEM_DEEP);
		// 中央闪电球：4×4 区域 (6..9, 6..9)
		fillRect(img, 6, 6, 4, 4, Palette.RED_GEM);
		// 球高光（左上 1px 白）
		pixel(img, 6, 6, Palette.RED_GEM_GLINT);
		pixel(img, 7, 6, Palette.RED_GEM_HI);
		// 球阴影（右下 1px 暗）
		pixel(img, 9, 9, Palette.RED_GEM_DEEP);
		// 内核黄闪电（Z 形 4 像素）
		pixel(img, 7, 7, Palette.ELECTRIC_HOT);
		pixel(img, 8, 7, Palette.ELECTRIC);
		pixel(img, 7, 8, Palette.ELECTRIC);
		pixel(img, 8, 8, Palette.ELECTRIC_HOT);
		// 4 角微闪光
		pixel(img, 3, 3, Palette.RED_GEM_GLINT);
		pixel(img, 12, 3, Palette.RED_GEM_GLINT);
		pixel(img, 3, 12, Palette.RED_GEM_GLINT);
		pixel(img, 12, 12, Palette.RED_GEM_GLINT);
		return img;
	}

	/**
	 * wireless_power_tower: 紫色水晶方块（仿 quantum_crystal_block）+ 内嵌天线塔。
	 *
	 * <p>4 层凹陷紫框 + 中央 6×6 暗腔内：竖向水晶柱（紫水晶）+ 顶端高光球
	 * + 底部基座 + 两侧 1px 信号光斑。</p>
	 */
	private static BufferedImage drawWirelessPowerTowerBlock() {
		BufferedImage img = drawCrystalGemBlock(
				Palette.VIOLET_GEM_HI, Palette.VIOLET_GEM, Palette.VIOLET_GEM_DEEP, Palette.VIOLET_GEM_DEEP);
		// 天线竖向水晶柱（cx=7, y=5..9）
		fillRect(img, 7, 5, 1, 5, Palette.VIOLET_GEM);
		fillRect(img, 7, 5, 1, 1, Palette.VIOLET_GEM_GLINT); // 顶端高光球
		fillRect(img, 7, 9, 1, 1, Palette.VIOLET_GEM_HI);    // 底端高光
		// 柱子右侧阴影 1px
		pixel(img, 8, 6, Palette.VIOLET_GEM_DEEP);
		pixel(img, 8, 7, Palette.VIOLET_GEM_DEEP);
		pixel(img, 8, 8, Palette.VIOLET_GEM_DEEP);
		// 顶端发光球（信号源）
		pixel(img, 7, 4, Palette.VIOLET_GEM_GLINT);
		pixel(img, 6, 5, Palette.VIOLET_GEM_HI);
		pixel(img, 8, 5, Palette.VIOLET_GEM_HI);
		// 底部基座 (6..9, 10)
		fillRect(img, 6, 10, 4, 1, Palette.VIOLET_GEM);
		// 两侧信号波（左右各 1px 高光）
		pixel(img, 5, 6, Palette.VIOLET_GEM_GLINT);
		pixel(img, 9, 6, Palette.VIOLET_GEM_GLINT);
		// 4 角微闪光
		pixel(img, 3, 3, Palette.VIOLET_GEM_GLINT);
		pixel(img, 12, 3, Palette.VIOLET_GEM_GLINT);
		pixel(img, 3, 12, Palette.VIOLET_GEM_GLINT);
		pixel(img, 12, 12, Palette.VIOLET_GEM_GLINT);
		return img;
	}

	/**
	 * holographic_shop: 青绿水晶方块（仿 fluix）+ 中央显示屏。
	 *
	 * <p>4 层凹陷青绿框 + 中央 6×6 暗腔内：屏幕底色 + 全息光球 + CRT 扫描线。</p>
	 */
	private static BufferedImage drawHolographicShopBlock() {
		BufferedImage img = drawCrystalGemBlock(
				Palette.TEAL_GEM_HI, Palette.TEAL_GEM, Palette.TEAL_GEM_DEEP, Palette.TEAL_GEM_DEEP);
		// 中央 6×6 暗腔本身就是屏幕底色（5..10）
		// CRT 扫描线 (5..10, 6) 顶
		fillRect(img, 5, 6, 6, 1, Palette.TEAL_GEM_HI);
		// 中央 4×4 全息光球
		fillRect(img, 6, 7, 4, 3, Palette.ELECTRIC);
		fillRect(img, 7, 8, 2, 1, Palette.ELECTRIC_HOT);
		pixel(img, 6, 7, Palette.HIGHLIGHT);
		// 底部 CRT 扫描线
		fillRect(img, 5, 10, 6, 1, Palette.TEAL_GEM_HI);
		// 4 角微闪光
		pixel(img, 3, 3, Palette.TEAL_GEM_HI);
		pixel(img, 12, 3, Palette.TEAL_GEM_HI);
		pixel(img, 3, 12, Palette.TEAL_GEM_HI);
		pixel(img, 12, 12, Palette.TEAL_GEM_HI);
		return img;
	}

	/**
	 * pow_miner: 银色机箱风（仿 silicon_block / resonating_processor）。
	 *
	 * <p>4 层凹陷银框 + 中央暗紫水晶腔 + # 哈希紫水晶刻印 + 角落红 LED 灯。</p>
	 */
	private static BufferedImage drawPowMinerBlock() {
		BufferedImage img = drawCrystalGemBlock(
				Palette.SILVER_HI, Palette.SILVER_MID, Palette.SILVER_LOW, Palette.VIOLET_GEM_DEEP);
		// 中央 # 紫色刻印（5×5 在 6×6 凹腔内）
		fillRect(img, 5, 6, 6, 1, Palette.VIOLET_GEM_HI);   // 横上
		fillRect(img, 5, 9, 6, 1, Palette.VIOLET_GEM_HI);   // 横下
		fillRect(img, 6, 5, 1, 6, Palette.VIOLET_GEM_HI);   // 竖左
		fillRect(img, 9, 5, 1, 6, Palette.VIOLET_GEM_HI);   // 竖右
		// 中央高光（# 中心微亮）
		pixel(img, 7, 7, Palette.VIOLET_GEM_GLINT);
		pixel(img, 8, 8, Palette.VIOLET_GEM_GLINT);
		// 4 角红 LED（处理器风）
		pixel(img, 3, 3, Palette.LED_RED);
		pixel(img, 12, 3, Palette.LED_RED);
		pixel(img, 3, 12, Palette.LED_RED);
		pixel(img, 12, 12, Palette.LED_RED);
		// LED 暗影
		pixel(img, 4, 3, Palette.LED_RED_DARK);
		pixel(img, 11, 3, Palette.LED_RED_DARK);
		return img;
	}

	// ====================================================================
	// === 共用 helper ===
	// ====================================================================

	private static BufferedImage canvas() {
		return new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
	}

	private static void pixel(BufferedImage img, int x, int y, Color c) {
		if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
			return;
		}
		img.setRGB(x, y, c.getRGB());
	}

	private static void fillRect(BufferedImage img, int x, int y, int w, int h, Color c) {
		int rgb = c.getRGB();
		for (int yy = y; yy < y + h; yy++) {
			if (yy < 0 || yy >= img.getHeight()) continue;
			for (int xx = x; xx < x + w; xx++) {
				if (xx < 0 || xx >= img.getWidth()) continue;
				img.setRGB(xx, yy, rgb);
			}
		}
	}

	/** 画矩形 outline（不填内部）。 */
	private static void drawRect(BufferedImage img, int x, int y, int w, int h, Color c) {
		fillRect(img, x, y, w, 1, c);
		fillRect(img, x, y + h - 1, w, 1, c);
		fillRect(img, x, y, 1, h, c);
		fillRect(img, x + w - 1, y, 1, h, c);
	}

	/** 画 1px 描边外轮廓（不动内部填充）。 */
	private static void drawOutline(BufferedImage img, int x, int y, int w, int h, Color c) {
		drawRect(img, x, y, w, h, c);
	}

	/** 实心圆（midpoint 算法 + 行扫描填充）。 */
	private static void fillCircle(BufferedImage img, int cx, int cy, int r, Color c) {
		for (int dy = -r; dy <= r; dy++) {
			int dxMax = (int) Math.floor(Math.sqrt(r * r - dy * dy));
			fillRect(img, cx - dxMax, cy + dy, dxMax * 2 + 1, 1, c);
		}
	}

	/** 圆形 outline（midpoint 算法）。 */
	private static void drawCircle(BufferedImage img, int cx, int cy, int r, Color c) {
		int x = r, y = 0, err = 0;
		while (x >= y) {
			pixel(img, cx + x, cy + y, c);
			pixel(img, cx + y, cy + x, c);
			pixel(img, cx - y, cy + x, c);
			pixel(img, cx - x, cy + y, c);
			pixel(img, cx - x, cy - y, c);
			pixel(img, cx - y, cy - x, c);
			pixel(img, cx + y, cy - x, c);
			pixel(img, cx + x, cy - y, c);
			y++;
			err += 1 + 2 * y;
			if (2 * (err - x) + 1 > 0) {
				x--;
				err += 1 - 2 * x;
			}
		}
	}

	/** 卡片底框：12×14 居中，主色填充 + 1px 边框 + 内部 1px 高光带。 */
	private static void drawCard(BufferedImage img, Color fill, Color border) {
		fillRect(img, 2, 1, 12, 14, fill);
		drawOutline(img, 2, 1, 12, 14, border);
		// 顶部 1px 浅色高光（卡片上沿反光）
		fillRect(img, 3, 2, 10, 1, Palette.HIGHLIGHT);
		// 左 1px 高光
		fillRect(img, 3, 2, 1, 12, Palette.HIGHLIGHT);
		// 底+右 1px 阴影
		fillRect(img, 3, 13, 11, 1, border);
		fillRect(img, 13, 2, 1, 12, border);
		// 外缘加深
		drawOutline(img, 2, 1, 12, 14, Palette.OUTLINE);
	}

	/** 一个简单的 ">" V 形（4×5），用于升级卡的双箭头。 */
	@SuppressWarnings("unused")
	private static void drawChevron(BufferedImage img, int x, int y, Color c) {
		pixel(img, x, y - 2, c);
		pixel(img, x + 1, y - 1, c);
		pixel(img, x + 2, y, c);
		pixel(img, x + 1, y + 1, c);
		pixel(img, x, y + 2, c);
	}

	/**
	 * 粗体 "&gt;" 双像素厚，从 (x, y) 的"开口侧"画起。
	 *
	 * <pre>
	 *   ##....
	 *   .##...
	 *   ..##.+   ← + 处是 tip 色高光（与 body 不同）
	 *   .##...
	 *   ##....
	 * </pre>
	 *
	 * @param x   箭头开口左侧 x（5 列宽，覆盖 x..x+3）
	 * @param y   箭头中心 y（5 行高，覆盖 y-2..y+2）
	 * @param body 主体色（描边/粗线）
	 * @param tip  尖端高光色
	 */
	private static void drawFatChevron(BufferedImage img, int x, int y, Color body, Color tip) {
		// 主体 2px 厚阶梯
		fillRect(img, x,     y - 2, 2, 1, body);
		fillRect(img, x + 1, y - 1, 2, 1, body);
		fillRect(img, x + 2, y,     2, 1, body);
		fillRect(img, x + 1, y + 1, 2, 1, body);
		fillRect(img, x,     y + 2, 2, 1, body);
		// 尖端高光
		pixel(img, x + 3, y, tip);
	}

	/**
	 * 通用机器方块外壳（cube_all 单图）。
	 * 1px 外描边 + 14×14 紫灰内底 + 顶左高光 + 底右阴影 + 4 角螺丝 + 中央 8×8 标识区。
	 */
	@SuppressWarnings("unused")
	private static BufferedImage drawMachineFrame() {
		return drawMachineFrameTinted(Palette.BODY_FILL, Palette.BODY_DEEP, Palette.HIGHLIGHT);
	}

	/**
	 * AE2CS 风格水晶方块外壳（参考 redstone_crystal_block / quantum_crystal_block）。
	 *
	 * <pre>
	 *   外缘 1px black                   边 0
	 *   ↳ 1px hi（顶+左）/ low（底+右）   边 1
	 *     ↳ 1px mid                     边 2
	 *       ↳ 内 10×10 中央"能量水晶"区，由调用方进一步填中心菱形
	 * </pre>
	 *
	 * @param hi  最亮（顶+左 1px 镜面）
	 * @param mid 主体（边 2）
	 * @param low 阴影（底+右 1px）
	 * @param dim 中央底色（最暗，水晶背景）
	 */
	private static BufferedImage drawCrystalGemBlock(Color hi, Color mid, Color low, Color dim) {
		BufferedImage img = canvas();
		// 边 0：纯黑外描边
		fillRect(img, 0, 0, 16, 16, Palette.SILVER_BLACK);
		// 边 1：hi (顶+左) / low (底+右) 立体亮
		fillRect(img, 1, 1, 14, 1, hi);
		fillRect(img, 1, 1, 1, 14, hi);
		fillRect(img, 1, 14, 14, 1, low);
		fillRect(img, 14, 1, 1, 14, low);
		// 边 2：主体 mid
		fillRect(img, 2, 2, 12, 12, mid);
		// 中央水晶腔：6×6 dim（边 3）
		fillRect(img, 5, 5, 6, 6, dim);
		// 中央 1px 内描边强化深度
		drawRect(img, 4, 4, 8, 8, Palette.SILVER_BLACK);
		// 4 角微高光（晶体反射）
		pixel(img, 1, 1, Palette.HIGHLIGHT);
		pixel(img, 14, 14, low);
		return img;
	}

	/**
	 * AE2CS 风格 3D 银卡片（参考 crystal_growth_card / integrated_interface_upgrade）。
	 *
	 * <pre>
	 *   左上斜视卡片 + 顶/左 1px 高光 + 底/右 1px 阴影
	 *   主体 11×13 区域，留出 (12..15) 用于右侧"卡 tab"
	 * </pre>
	 *
	 * <p>调用方在返回的图片上继续画中央图案（图标 / 水晶 / 矩阵）。</p>
	 *
	 * @param accentTop  右侧 tab 顶部高光（决定卡片"颜色"）
	 * @param accentMid  右侧 tab 主体
	 * @param accentLow  右侧 tab 阴影
	 */
	private static BufferedImage drawCardSilver3D(Color accentTop, Color accentMid, Color accentLow) {
		BufferedImage img = canvas();
		// 主体卡片 11×13（x=2..12, y=1..13）
		fillRect(img, 2, 1, 11, 13, Palette.CARD_BASE);
		// 顶 1px 高光
		fillRect(img, 2, 1, 11, 1, Palette.CARD_BASE_HI);
		// 左 1px 高光
		fillRect(img, 2, 1, 1, 13, Palette.CARD_BASE_HI);
		// 底 1px 阴影
		fillRect(img, 2, 13, 11, 1, Palette.CARD_BASE_LOW);
		// 右 1px 中性阴影
		fillRect(img, 12, 1, 1, 13, Palette.CARD_BASE_LOW);
		// 外缘描边
		drawOutline(img, 2, 1, 11, 13, Palette.SILVER_BLACK);

		// 右侧彩色 tab（accent 标识，是 AE2CS 卡片最显眼的特征）
		// 位置 (13, 3) 大小 2×9
		fillRect(img, 13, 3, 2, 9, accentMid);
		fillRect(img, 13, 3, 2, 1, accentTop);   // tab 顶高光
		fillRect(img, 13, 11, 2, 1, accentLow);  // tab 底阴影
		drawOutline(img, 13, 3, 2, 9, Palette.SILVER_BLACK);

		// 整体外缘 4 角微圆（角落 1px 黑像素强化"卡片"轮廓）
		pixel(img, 2, 1, Palette.SILVER_BLACK);
		pixel(img, 12, 1, Palette.SILVER_BLACK);
		pixel(img, 2, 13, Palette.SILVER_BLACK);
		pixel(img, 12, 13, Palette.SILVER_BLACK);
		return img;
	}

	/**
	 * 在 (cx, cy) 画一颗 AE2CS 风的菱形水晶（4×4 像素核心 + 4 个边角延伸）。
	 *
	 * <pre>
	 *     .#.       y=cy-3
	 *    #@@#      y=cy-2..cy-1（@ 为亮高光 hi）
	 *    #&&#      y=cy..cy+1（& 为主体 main）
	 *    #@@#      y=cy+2 ...
	 *     .#.       y=cy+3
	 * </pre>
	 *
	 * 实际更稳的画法：6×6 菱形（cx-2..cx+3, cy-3..cy+2）。
	 */
	private static void drawCrystalGem(BufferedImage img, int cx, int cy, Color hi, Color main, Color deep, Color glint) {
		// 顶尖 (cx, cy-3)
		pixel(img, cx, cy - 3, hi);
		// 上排 (cx-1..cx+1, cy-2)
		fillRect(img, cx - 1, cy - 2, 3, 1, hi);
		// 中上 (cx-2..cx+2, cy-1)
		fillRect(img, cx - 2, cy - 1, 5, 1, main);
		pixel(img, cx, cy - 1, hi);
		// 中下 (cx-2..cx+2, cy)
		fillRect(img, cx - 2, cy, 5, 1, main);
		pixel(img, cx + 1, cy, glint);
		// 下排 (cx-1..cx+1, cy+1)
		fillRect(img, cx - 1, cy + 1, 3, 1, deep);
		// 底尖 (cx, cy+2)
		pixel(img, cx, cy + 2, deep);
	}

	/**
	 * 给定主色 / 阴影色 / 高光色画机箱外壳（让 5 个机器有不同色调）。
	 */
	private static BufferedImage drawMachineFrameTinted(Color body, Color deep, Color hi) {
		BufferedImage img = canvas();
		// 主底
		fillRect(img, 0, 0, 16, 16, body);
		// 顶左 1px 高光
		fillRect(img, 0, 0, 16, 1, hi);
		fillRect(img, 0, 0, 1, 16, hi);
		// 底右 1px 阴影
		fillRect(img, 0, 15, 16, 1, deep);
		fillRect(img, 15, 0, 1, 16, deep);
		// 外缘 1px 描边
		drawOutline(img, 0, 0, 16, 16, Palette.OUTLINE);
		// 4 角螺丝
		fillRect(img, 2, 2, 2, 2, Palette.SCREW);
		fillRect(img, 12, 2, 2, 2, Palette.SCREW);
		fillRect(img, 2, 12, 2, 2, Palette.SCREW);
		fillRect(img, 12, 12, 2, 2, Palette.SCREW);
		pixel(img, 2, 2, Palette.HIGHLIGHT);
		pixel(img, 12, 2, Palette.HIGHLIGHT);
		pixel(img, 2, 12, Palette.HIGHLIGHT);
		pixel(img, 12, 12, Palette.HIGHLIGHT);
		// 中央 8×8 凹陷面板（暗一阶）
		fillRect(img, 4, 4, 8, 8, deep);
		drawOutline(img, 4, 4, 8, 8, Palette.OUTLINE);
		// 凹陷顶 1px 过渡
		fillRect(img, 5, 5, 6, 1, body);
		return img;
	}

	private static Color c(int hex) {
		return new Color(hex);
	}

	private static void ensure(String dir) throws IOException {
		File d = new File(dir);
		if (!d.exists() && !d.mkdirs()) {
			throw new IOException("Failed to create dir: " + d.getAbsolutePath());
		}
	}

	private static void save(String dir, String name, BufferedImage img) throws IOException {
		File out = new File(dir, name);
		ImageIO.write(img, "png", out);
		System.out.println("  Wrote " + name + " (" + img.getWidth() + "x" + img.getHeight() + ")");
	}
}
