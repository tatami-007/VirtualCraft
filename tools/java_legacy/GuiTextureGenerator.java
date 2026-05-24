import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * VirtualCraft GUI 贴图生成器 — <b>v5：真实 AE2 Crystal Science 一比一复刻</b>。
 *
 * <h3>调色板（直接采样自 ae2cs/textures/gui/*.png）</h3>
 * <table>
 *   <tr><th>用途</th><th>色号</th><th>实测占比</th></tr>
 *   <tr><td>主背景</td><td>{@code #CBCCD4}</td><td>55-58%</td></tr>
 *   <tr><td>凹陷面板主底</td><td>{@code #ADB0C4}</td><td>28-32%</td></tr>
 *   <tr><td>顶/左 1px 高光</td><td>{@code #F2F2F2}</td><td>5-6%</td></tr>
 *   <tr><td>窗体外缘描边</td><td>{@code #413F54}</td><td>2.2%</td></tr>
 *   <tr><td>凹陷面板顶 1px 过渡</td><td>{@code #9A9FB4}</td><td>2%</td></tr>
 *   <tr><td>阴影</td><td>{@code #878FA5}</td><td>1.5-2.3%</td></tr>
 *   <tr><td>内部细线</td><td>{@code #4D4D67}</td><td>0.5%</td></tr>
 * </table>
 *
 * <h3>核心规则（严格按真实 AE2-CS 像素模式）</h3>
 * <ol>
 *   <li><b>窗体</b>：仅顶+左 1px {@code #F2F2F2} 高光，<i>没有底+右阴影</i>，外缘 1px {@code #413F54} 闭合。</li>
 *   <li><b>凹陷面板</b>（玩家背包/功能槽位组）：3-stop 凹陷
 *       <ul>
 *         <li>整组顶 1px 横线 {@code #F2F2F2}（高光）</li>
 *         <li>整组左 1px 竖线 {@code #F2F2F2}（高光）</li>
 *         <li>每个槽位 16×16 顶 1px {@code #9A9FB4} 过渡线</li>
 *         <li>每个槽位 16×16 主底 {@code #ADB0C4}</li>
 *       </ul>
 *   </li>
 *   <li><b>槽位本身不画 vanilla 4-stop 凹陷</b> — vanilla 渲染器会自动渲染槽位 hover/highlight，
 *       AE2-CS 的"槽位视觉"完全靠凹陷面板实现。</li>
 *   <li><b>能量条/进度条 track</b>：与凹陷面板同款 3-stop（不是 IC2 黑底斜条纹）。</li>
 *   <li><b>SideConfig</b>：极简窗体 + 几个空 18×18 槽位坑位（让 widgets 运行时叠加按钮）。</li>
 * </ol>
 */
public class GuiTextureGenerator {

	// ============================================================
	// 调色板（一比一采样自 ae2cs_*_menu.png）
	// ============================================================
	private static final class Palette {
		// === 窗体 ===
		static final Color WINDOW_BORDER = c(0x413F54);   // 外缘 1px
		static final Color WINDOW_HIGH   = c(0xF2F2F2);   // 顶+左 1px 高光
		static final Color WINDOW_BG     = c(0xCBCCD4);   // 主背景

		// === 凹陷面板（槽位组 / 玩家背包 / 能量条 / 进度条 track）===
		static final Color PANEL_TOP     = c(0x9A9FB4);   // 顶 1px 过渡
		static final Color PANEL_FILL    = c(0xADB0C4);   // 主底
		static final Color PANEL_SHADOW  = c(0x878FA5);   // 阴影色（用于装饰）
		static final Color PANEL_LINE    = c(0x4D4D67);   // 内部细线

		// === Accent（紫色系，用于进度箭头/能量条 fill/icon）===
		static final Color ACCENT_PURPLE = c(0x915DCD);   // 紫 accent
		static final Color ACCENT_VIOLET = c(0x6054A6);   // 暗紫 accent
		static final Color ACCENT_BLUE   = c(0x373B72);   // 蓝紫 logo
		static final Color ACCENT_HEADER = c(0x696D88);   // header 块
		static final Color GLOW_PINK     = c(0xFF80D7);   // 粉紫高光
		static final Color GLOW_VIOLET   = c(0xB06FDD);   // 紫色玻璃高光

		// === Icon 用色（采样自 ae2cs_icons.png 直方图）===
		// v8：随 drawIconInput/Output 被 copyIconCell 替代 + drawIconPickaxe 删除，
		//   ICON_PURPLE/ICON_PURPLE_DARK（Output 专用）、ICON_BLUE_*（Input 专用）全部清理。
		//   保留：ICON_PURPLE_LIGHT（进度箭头顶高光用）、ICON_RED_*（drawIconDisable 用）、ICON_FG（drawIconGear/Back/Question 用）
		static final Color ICON_PURPLE_LIGHT = c(0xA79CFF);
		static final Color ICON_RED_LIGHT    = c(0xFFA59C);
		static final Color ICON_RED          = c(0xDB7573);
		static final Color ICON_RED_DARK     = c(0xAC4339);
		static final Color ICON_FG           = c(0x4D4D67);

		// === FE 红能量 fill（Thermal Expansion 经典 RF 风：3D 凹陷管道 + 红色立体充能）===
		// 视觉规则参考 Thermal Foundation 1.12 mod 的 energy_meter.png：
		//   - 外缘 1px 纯黑 #000000（绝对的硬边）
		//   - 内缘 1px 双色 3D 凹陷：上+左 #2A2A2A 暗 / 下+右 #6E6E6E 亮 → 形成"凹陷管道"立体感
		//   - 内部主底 #383838 渐变到 #4F4F4F（顶暗底亮，模拟管道内部光照）
		/** 能量条外缘：纯黑 1px（硬边轮廓）。 */
		static final Color FE_TE_EDGE        = c(0x000000);
		/** 内缘 3D 凹陷上+左暗 1px（凹陷的阴影侧）。 */
		static final Color FE_TE_INNER_DARK  = c(0x2A2A2A);
		/** 内缘 3D 凹陷下+右亮 1px（凹陷的反射光侧）。 */
		static final Color FE_TE_INNER_LIGHT = c(0x6E6E6E);
		/** 能量条空底渐变 — 顶部 #383838 深灰。 */
		static final Color FE_TE_EMPTY_TOP    = c(0x383838);
		/** 能量条空底渐变 — 底部 #4F4F4F 中灰（模拟管道内部光照）。 */
		static final Color FE_TE_EMPTY_BOTTOM = c(0x4F4F4F);
		/** @deprecated 旧 API 兼容 — 单色空底，不再使用。 */
		@Deprecated static final Color FE_TE_EMPTY       = FE_TE_EMPTY_BOTTOM;

		/** @deprecated v5 垂直 fill 渐变顶色，v6 起 MCPP 不再使用；横向 fill 用的是 HIGH/LEFT/MID/BOTTOM/RIGHT 不含 TOP。保留色值供历史参考。 */
		@Deprecated static final Color FE_TE_RED_TOP     = c(0xFF6464);
		/** RF 红渐变 — 中部标红（TE 标志色）。 */
		static final Color FE_TE_RED_MID     = c(0xD22A2A);
		/** RF 红渐变 — 底部深红（能量条底端，深沉）。 */
		static final Color FE_TE_RED_BOTTOM  = c(0x801010);
		/** RF 红顶部 1px 粉白高光（模拟表面反光）。 */
		static final Color FE_TE_RED_HIGH    = c(0xFFC8B8);
		/** RF 红左 1px 立体管道高光（亮粉红，模拟管道左侧反光）。 */
		static final Color FE_TE_RED_LEFT    = c(0xFF9090);
		/** RF 红右 1px 立体管道阴影（深暗红，模拟管道右侧阴影）。 */
		static final Color FE_TE_RED_RIGHT   = c(0x6A0808);
		/** @deprecated 保留为 drawHorizontalBar 传参 fillColor 时的兼容常量，实际渲染用 FE_TE_RED_MID。 */
		@Deprecated static final Color FE_RED           = FE_TE_RED_MID;
		@Deprecated static final Color FE_RED_HIGHLIGHT = FE_TE_RED_HIGH;

		// ============================================================
		// MC-Pixel-Perfect 垂直能量条调色板
		// ============================================================
		// 精确采样自 tools/mc_pixel_perfect.png (160×640，原像素 16×64 × 10倍放大)。
		// 该参考图展示了 MC 像素艺术风格的"红色能量管道 fill" ：
		//   - 14 阶红色：7 种亮色（奇数 y 行）+ 7 种暗色（偶数 y 行），亮/暗成对出现
		//   - 外缘：顶 1px + 左 1px 深灰 #373737（阴影侧），底 1px + 右 1px 纯白 #FFFFFF（高光侧）
		//   - 视觉效果：红色液体在像素完美管道里流动，明暗交替闪烁，左下凸右上凹
		// ============================================================

		/** MCPP 顶 1px + 左 1px 深灰边（阴影侧）。 */
		static final Color MCPP_EDGE_DARK  = c(0x373737);
		/** MCPP 底 1px + 右 1px 纯白边（高光侧）。 */
		static final Color MCPP_EDGE_LIGHT = c(0xFFFFFF);

		/**
		 * 7 阶红色"亮行"（奇数 y 行，y=1/3/5.../61）—— 对应参考图 col 1..7 的亮条。
		 * 索引 0 = 最暗阶 (#420000)，索引 6 = 最亮阶 (#F50000)。
		 */
		static final Color[] MCPP_RED_LIGHT = {
				c(0x420000), c(0x5C0000), c(0x7A0000), c(0x990000),
				c(0xB80000), c(0xD60000), c(0xF50000),
		};

		/**
		 * 7 阶红色"暗行"（偶数 y 行，y=2/4/6.../62）—— 对应参考图 col 1..7 的暗条。
		 * 索引 0 = 最暗阶 (#2B0000)，索引 6 = 最亮阶 (#A00000)。
		 */
		static final Color[] MCPP_RED_DARK = {
				c(0x2B0000), c(0x3C0000), c(0x500000), c(0x640000),
				c(0x780000), c(0x8C0000), c(0xA00000),
		};

		// === 燃烧条琥珀（v20.7 之前用，v20.9 被紫色替代）===
		static final Color BURN_AMBER        = c(0xC89466);
		static final Color BURN_AMBER_DARK   = c(0xAA702F);

		// === v20.10 火焰色（电球发电机燃烧条专用，区别于矿机紫色算力条）===
		/** 高亮火苗黄白：火焰点亮、火焰顶 1px 横线。 */
		static final Color FLAME_BRIGHT      = c(0xFFD955);
		/** 主火焰桃橙：从 y=1 起填充主体亦作为顶部跳动的中间调。 */
		static final Color FLAME_LIGHT       = c(0xFFB347);
		/** 深火焰深橙：火焰主体下部，与黑底烉膛形成火焰“点火”脚注。 */
		static final Color FLAME_DARK        = c(0xDD5500);

		private Palette() {}
		private static Color c(int hex) { return new Color(hex); }
	}

	private static final String OUT_DIR = "src/main/resources/assets/virtual_craft/textures/gui";

	/**
	 * AE2 Crystal Science 官方 icons.png 路径 — 256×256 大型 sprite sheet，作为生成器**只读输入源**。
	 *
	 * <h4>v14 路径调整</h4>
	 * v13 之前 ICONS_PATH 指向 {@code src/main/resources/.../icons.png}（项目运行时资源）+ 自己输出的 widgets.png 共存，
	 * 同一目录两个 sprite sheet 用途混淆。v14 起：
	 * <ul>
	 *   <li>AE2 原版 icons.png 移到 {@code tools/refs/ae2cs_icons.png}（构建时输入，不参与运行时）</li>
	 *   <li>原 widgets.png 改名输出为 icons.png（成为运行时唯一 sprite sheet）</li>
	 *   <li>{@code src/main/resources/.../icons.png}（旧 AE2 原版下载）+ {@code widgets.png}（旧输出）都被删除</li>
	 * </ul>
	 *
	 * <h4>关键 16×16 sprite cell 坐标（gx, gy）— 通过 IconsGrid 工具扫描确定</h4>
	 * <ul>
	 *   <li>(0,7)  蓝色阀输入  → icons.png ICON_INPUT_U=48</li>
	 *   <li>(1,7)  橙色阀输出  → icons.png ICON_OUTPUT_U=64</li>
	 *   <li>(1,6)  蓝色 wifi    → icons.png ICON_LINK_U=112</li>
	 *   <li>(1..9, 12..15) 调色板 144×64 整块复制 → icons.png (0, 64)</li>
	 * </ul>
	 *
	 * <p>其他 icons.png 图标（gear/back/question/disable）AE2 源没有对应，仍由 GuiTextureGenerator
	 * 自己 hand-draw（调色板已对齐 #4D4D67/#F2F2F2/#413F54 等 AE2-CS 风）。</p>
	 */
	private static final String ICONS_PATH = "tools/refs/ae2cs_icons.png";

	// ============================================================
	// main
	// ============================================================
	public static void main(String[] args) throws IOException {
		File outDir = new File(OUT_DIR);
		if (!outDir.exists() && !outDir.mkdirs()) {
			throw new IOException("Failed to create output directory: " + outDir.getAbsolutePath());
		}
		System.out.println("Output directory: " + outDir.getAbsolutePath());

		save(generatePowMiner(),                    "pow_miner.png");
		save(generateElectricBallGenerator(),       "electric_ball_generator.png");
		save(generateElectricCondenser(),           "electric_condenser.png");
		// v15 通用面配置白板贴图—所有机器共用（代替 electric_condenser_side_config.png）
		save(generateSideConfigMenu(),              "side_config_menu.png");
		// v14: widgets.png 改名 icons.png（成为运行时唯一 sprite sheet）
		save(generateIconsSheet(),                  "icons.png");
		// v20.7 删除 wireless_power.png——WirelessPowerTower 没有传统 GUI（无 Screen/Menu），
		// 只通过 LaserBindingTool + BoundMachineHighlighter 进行绑定可视化，主背景贴图从未被引用。

		System.out.println("Done. 5 textures generated.");
	}

	// ============================================================
	// 像素 helper
	// ============================================================
	private static BufferedImage canvas(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	private static void fillRect(BufferedImage img, int x, int y, int w, int h, Color c) {
		int rgb = c.getRGB();
		int W = img.getWidth(), H = img.getHeight();
		for (int yy = y; yy < y + h; yy++) {
			if (yy < 0 || yy >= H) continue;
			for (int xx = x; xx < x + w; xx++) {
				if (xx < 0 || xx >= W) continue;
				img.setRGB(xx, yy, rgb);
			}
		}
	}

	private static void clearRect(BufferedImage img, int x, int y, int w, int h) {
		int W = img.getWidth(), H = img.getHeight();
		for (int yy = y; yy < y + h; yy++) {
			if (yy < 0 || yy >= H) continue;
			for (int xx = x; xx < x + w; xx++) {
				if (xx < 0 || xx >= W) continue;
				img.setRGB(xx, yy, 0);
			}
		}
	}

	private static void save(BufferedImage img, String name) throws IOException {
		File out = new File(OUT_DIR, name);
		ImageIO.write(img, "png", out);
		System.out.println("  Wrote " + name + " (" + img.getWidth() + "×" + img.getHeight() + ")");
	}

	// ============================================================
	// icons.png 复制工具（v8：从 AE2-CS 官方 sprite 借用精致 16×16 图标）
	// ============================================================

	/**
	 * 加载 AE2-CS 官方 icons.png 为 BufferedImage，供后续 copyIconCell 调用。
	 * 文件不存在时抛 IOException，让生成器明确报错而不是悄悄退化为手画图标。
	 */
	private static BufferedImage loadIconsSheet() throws IOException {
		File f = new File(ICONS_PATH);
		if (!f.exists()) {
			throw new IOException("icons.png not found at " + f.getAbsolutePath()
					+ " — please ensure AE2-CS icons.png is present in textures/gui/");
		}
		BufferedImage img = ImageIO.read(f);
		if (img == null) {
			throw new IOException("Failed to read icons.png as PNG: " + f.getAbsolutePath());
		}
		return img;
	}

	/**
	 * 从 icons.png 的 16×16 grid cell ({@code srcGx}, {@code srcGy}) 复制 16×16 像素块到目标 sprite sheet 的
	 * ({@code dstX}, {@code dstY}) 位置。透明像素（alpha &lt; 128）也照样复制（保持透明）。
	 *
	 * @param src  来源 BufferedImage（icons.png）
	 * @param srcGx 源 cell 的 grid x（0 = x=0..15，1 = x=16..31，依此类推）
	 * @param srcGy 源 cell 的 grid y
	 * @param dst  目标 BufferedImage（widgets.png）
	 * @param dstX 目标左上角绝对 x
	 * @param dstY 目标左上角绝对 y
	 */
	private static void copyIconCell(BufferedImage src, int srcGx, int srcGy, BufferedImage dst, int dstX, int dstY) {
		int sx = srcGx * 16;
		int sy = srcGy * 16;
		for (int dy = 0; dy < 16; dy++) {
			for (int dx = 0; dx < 16; dx++) {
				int rgb = src.getRGB(sx + dx, sy + dy);
				dst.setRGB(dstX + dx, dstY + dy, rgb);
			}
		}
	}

	/**
	 * 从 src 图的 ({@code srcX}, {@code srcY}) 起复制 {@code w}×{@code h} 像素到 dst 图的 ({@code dstX}, {@code dstY})。
	 * 用于整块矩形复制（如 icons.png 调色板整体切过来），保留原图的色块布局/立体边/间距，
	 * 而不需要逐 cell 复制 + 手动加间距（那样会破坏 AE2 原本的视觉关系）。
	 *
	 * @param src  来源 BufferedImage
	 * @param srcX 源左上角绝对 x
	 * @param srcY 源左上角绝对 y
	 * @param w    复制宽度
	 * @param h    复制高度
	 * @param dst  目标 BufferedImage
	 * @param dstX 目标左上角绝对 x
	 * @param dstY 目标左上角绝对 y
	 */
	private static void copyRect(BufferedImage src, int srcX, int srcY, int w, int h, BufferedImage dst, int dstX, int dstY) {
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				int rgb = src.getRGB(srcX + dx, srcY + dy);
				dst.setRGB(dstX + dx, dstY + dy, rgb);
			}
		}
	}

	// ============================================================
	// 共享视觉元素 — 严格按 AE2-CS 真实像素模式
	// ============================================================

	/**
	 * <b>AE2-CS 风窗体</b>：仅顶+左 1px 高光，外缘 1px 描边闭合，<i>无底右阴影</i>。
	 * <pre>
	 *   y=0          : XXXXXXXXXX  (#413F54 整行外缘)
	 *   y=1          : X HHHHHHHH  (#413F54 + 1px #F2F2F2 顶高光)
	 *   y=2..h-2     : X H ......  (#413F54 + 1px #F2F2F2 + #CBCCD4 主体)
	 *   y=h-1        : XXXXXXXXXX  (#413F54 整行外缘)
	 *   x=0          : 整列 #413F54
	 *   x=w-1        : 整列 #413F54
	 * </pre>
	 */
	private static void drawWindow(BufferedImage img, int x, int y, int w, int h) {
		// 1) 主背景 #CBCCD4
		fillRect(img, x, y, w, h, Palette.WINDOW_BG);
		// 2) 整 1px 外缘（顶/底/左/右）
		fillRect(img, x,         y,         w, 1, Palette.WINDOW_BORDER);
		fillRect(img, x,         y + h - 1, w, 1, Palette.WINDOW_BORDER);
		fillRect(img, x,         y,         1, h, Palette.WINDOW_BORDER);
		fillRect(img, x + w - 1, y,         1, h, Palette.WINDOW_BORDER);
		// 3) 顶 1px 高光（在外缘内侧）
		fillRect(img, x + 1, y + 1, w - 2, 1, Palette.WINDOW_HIGH);
		// 4) 左 1px 高光（在外缘内侧）
		fillRect(img, x + 1, y + 1, 1, h - 2, Palette.WINDOW_HIGH);
	}

	/**
	 * <b>AE2-CS 风凹陷面板（槽位组 / 玩家背包）</b>。
	 *
	 * @param firstSlotX 第一个 vanilla 槽位的 x 坐标（即 16×16 内填充区左上角）
	 * @param firstSlotY 第一个 vanilla 槽位的 y 坐标
	 * @param cols       槽位列数
	 * @param rows       槽位行数
	 *
	 * <p>每行槽位画法（参考真实 ae2cs_crystal_pulverizer_menu.png 第 83-99 行采样）：</p>
	 * <pre>
	 *   y=topY       : HHHHHHHHHHHHHHHHHHHHHHHH...  整行 F2F2F2 (整组顶高光)
	 *   y=topY+1     : H ddddddddddddddddd .. dddd  H + 每槽 16px 9A9FB4 顶过渡，槽间 2px CBCCD4
	 *   y=topY+2..16 : H ################# .. ####  H + 每槽 16px ADB0C4 主底，槽间 2px CBCCD4
	 *   x=topX       : 整列 F2F2F2 (整组左高光)
	 * </pre>
	 *
	 * <p>注意：槽位之间的 2px 横向间隔和 2px 纵向间隔不画 — 它们就是窗体主背景 #CBCCD4。</p>
	 */
	private static void drawSlotPanel(BufferedImage img, int firstSlotX, int firstSlotY, int cols, int rows) {
		int gx = firstSlotX;
		int gy = firstSlotY;
		// 实际主体宽/高（最后一槽后不带 2px 间距）
		int innerW = cols * 18 - 2;
		int innerH = rows * 18 - 2;
		// === v20.4 完整 4 边 F2F2F2 高光描边（严格模仿水晶科技 ae2cs_*_menu.png 槽位面板） ===
		// v20 之前只画顶 + 左，槽位面板视觉上"右下角开口"；水晶科技实测每个槽位面板都有完整 4 边描边，
		// 用户反馈"高光是一整圈的"——此处补全底 + 右。
		// 1) 顶 1px 高光（含左右两侧 1px 延伸）
		fillRect(img, gx - 1, gy - 1, innerW + 2, 1, Palette.WINDOW_HIGH);
		// 2) 左 1px 高光（含顶底两侧 1px 延伸）
		fillRect(img, gx - 1, gy - 1, 1, innerH + 2, Palette.WINDOW_HIGH);
		// 3) 底 1px 高光（v20.4 新增）
		fillRect(img, gx - 1, gy + innerH, innerW + 2, 1, Palette.WINDOW_HIGH);
		// 4) 右 1px 高光（v20.4 新增）
		fillRect(img, gx + innerW, gy - 1, 1, innerH + 2, Palette.WINDOW_HIGH);
		// 5) 每个槽位 16×16 主底 + 顶 1px 过渡
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				int sx = gx + col * 18;
				int sy = gy + row * 18;
				// 16×16 ADB0C4 主底
				fillRect(img, sx, sy, 16, 16, Palette.PANEL_FILL);
				// 顶 1px 9A9FB4 过渡（覆盖槽位顶部 1px）
				fillRect(img, sx, sy, 16, 1, Palette.PANEL_TOP);
			}
		}
	}

	/**
	 * <b>v20.8 AE2-CS 风凹陷条带（4 边 F2F2F2 描边版）</b>。
	 *
	 * <p>用于"非能量"进度指示：PowMiner 紫色算力进度条、ElectricBallGenerator 琥珀燃烧条等。
	 * v20.7 之前只画顶+左 2 边 高光，与 drawSlotPanel / drawEnergyTrackMCPP 的“4 边 F2F2F2 描边” GUI 标准
	 * 视觉割裂（右下角看起来开口）。v20.8 补全 4 边 F2F2F2 描边，与全项目所有 widget 统一。</p>
	 *
	 * <h4>新视觉规则</h4>
	 * <pre>
	 *   y      : 顶 1px F2F2F2 高光（整行）
	 *   y+1    : 左 1px F2F2F2 + 内顶过渡 (w-2) PANEL_TOP 9A9FB4 + 右 1px F2F2F2
	 *   y+2..h-2 : 左 1px F2F2F2 + 内部 (w-2) PANEL_FILL ADB0C4 + 右 1px F2F2F2
	 *   y+h-1  : 底 1px F2F2F2 高光（整行）
	 * </pre>
	 *
	 * <p><b>填充 sprite 兼容性</b>：{@code MachineUi.drawHorizontalBar} 已在 blit 时
	 * +1 跳左 1px + +1 跳顶 1px、sourceHeight = min(h-2, 8)，息好叠加到内部 (w-2) × min(h-2, 8) 区域，
	 * 不触碰新的底/右 1px 描边。零运行时改动。</p>
	 *
	 * @param x 凹陷条带左上角
	 * @param y 凹陷条带左上角
	 * @param w 总宽（含 4 边 1px 描边，最小 3）
	 * @param h 总高（含 4 边 1px 描边，最小 3）
	 */
	private static void drawRecessedTrack(BufferedImage img, int x, int y, int w, int h) {
		// v20.9 简化形状：去 PANEL_TOP 过渡 — 36×8 进度条太矮，1px PANEL_TOP 过渡看起来像“杂线”老的调子。
		// 纯净凹陷面板：4 边 F2F2F2 + 内部纯 PANEL_FILL，与物品槽凹陷同款。
		fillRect(img, x, y, w, h, Palette.PANEL_FILL);              // 内部纯 PANEL_FILL
		fillRect(img, x, y, w, 1, Palette.WINDOW_HIGH);             // 顶 1px F2F2F2
		fillRect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH);    // 底 1px F2F2F2
		fillRect(img, x, y, 1, h, Palette.WINDOW_HIGH);            // 左 1px F2F2F2
		fillRect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH);    // 右 1px F2F2F2
	}

	/**
	 * <b>Thermal Expansion 风 RF 能量条空 track（v3 立体管道版）</b>。
	 *
	 * <h4>视觉规则（参考 Thermal Foundation 经典 energy_meter.png）</h4>
	 * <pre>
	 *   外缘 1px 纯黑 #000000        ← 硬边轮廓
	 *   内缘上+左 1px #2A2A2A 暗     ┐ 形成 3D 凹陷管道立体感
	 *   内缘下+右 1px #6E6E6E 亮     ┘
	 *   内部主底 顶 #383838 → 底 #4F4F4F 垂直渐变 ← 模拟管道内部光照
	 * </pre>
	 *
	 * <p>这种"双层凹陷 + 内部渐变"的画法是 Thermal Expansion 系列的核心视觉签名，
	 * 完全区别于 IC2/BuildCraft 的"黑底斜条纹"，也不是简单的"灰底纯色"——
	 * 它有清晰的"凹陷进面板里"的立体感，让能量条看起来像嵌入金属面板的玻璃管道。</p>
	 *
	 * <p>当能量充满时，红色 fill sprite (widgets.png x=248) 从底部向上叠加，
	 * 也会带左右立体高光阴影，整体形成"红色液体在凹陷管道里向上充满"的强烈视觉。</p>
	 *
	 * @param x 能量条左上角
	 * @param y 能量条左上角
	 * @param w 总宽（最小 4px：外缘 1+1 + 内缘 1+1）
	 * @param h 总高（最小 4px）
	 */
	private static void drawEnergyTrackTE(BufferedImage img, int x, int y, int w, int h) {
		// === Layer 1: 外缘 1px 纯黑（硬边轮廓） ===
		fillRect(img, x,         y,         w, 1, Palette.FE_TE_EDGE);
		fillRect(img, x,         y + h - 1, w, 1, Palette.FE_TE_EDGE);
		fillRect(img, x,         y,         1, h, Palette.FE_TE_EDGE);
		fillRect(img, x + w - 1, y,         1, h, Palette.FE_TE_EDGE);

		// === Layer 2: 内部主底（垂直渐变 顶暗底亮，模拟管道内部光照） ===
		int innerY = y + 1;
		int innerH = h - 2;
		for (int dy = 0; dy < innerH; dy++) {
			float t = innerH > 1 ? dy / (float) (innerH - 1) : 0f;
			Color rowColor = lerpColor(Palette.FE_TE_EMPTY_TOP, Palette.FE_TE_EMPTY_BOTTOM, t);
			fillRect(img, x + 1, innerY + dy, w - 2, 1, rowColor);
		}

		// === Layer 3: 内缘 3D 凹陷立体（上+左暗 / 下+右亮） ===
		// 顶 1px 内缘暗（覆盖在主底渐变最顶层之上）
		fillRect(img, x + 1, y + 1, w - 2, 1, Palette.FE_TE_INNER_DARK);
		// 左 1px 内缘暗
		fillRect(img, x + 1, y + 1, 1, h - 2, Palette.FE_TE_INNER_DARK);
		// 底 1px 内缘亮（覆盖在主底渐变最底层之上）
		fillRect(img, x + 1, y + h - 2, w - 2, 1, Palette.FE_TE_INNER_LIGHT);
		// 右 1px 内缘亮
		fillRect(img, x + w - 2, y + 1, 1, h - 2, Palette.FE_TE_INNER_LIGHT);
	}

	/**
	 * <b>v20.6 能量条空 track —— MC-pixel-perfect 深灰内部 + 4 边 F2F2F2 高光描边</b>。
	 *
	 * <p>v20.5 曾把内部改成浅紫灰 ADB0C4（与槽位完全一致的水晶科技扁平风），但用户要求保留 MCPP 深灰
	 * 内部（红色充能在深灰底上的"管道液体"视觉对比强），只在外围补 4 边 F2F2F2 高光以满足 GUI 标准。
	 * v20.6 折中：内部仍 #373737 深灰，外围 4 边 F2F2F2，删除原 MCPP "顶左灰 + 底右白" 的反凹陷立体。</p>
	 *
	 * <h4>新视觉规则</h4>
	 * <pre>
	 *   y     : 顶 1px F2F2F2 高光（整行）
	 *   y+1..h-2 : 左 1px F2F2F2 + 内部 (w-2) MCPP 深灰 #373737 + 右 1px F2F2F2
	 *   y+h-1 : 底 1px F2F2F2 高光（整行）
	 * </pre>
	 *
	 * <p><b>充能 fill 兼容性</b>：{@code MachineUi.drawVerticalBar} 已经在 blit 时
	 * +1 跳过左 1px、sourceWidth = min(w-2, 14)、targetY = y + h - 1 - filled，
	 * 恰好叠加到内部 14×filled 深灰区域，不触碰 4 边 F2F2F2 高光。</p>
	 *
	 * @param x track 左上角
	 * @param y track 左上角
	 * @param w 总宽（建议 16，最小 3）
	 * @param h 总高（建议 52/54，最小 3）
	 */
	private static void drawEnergyTrackMCPP(BufferedImage img, int x, int y, int w, int h) {
		// 1) 内部主底 MCPP 深灰（先整块填，后续 4 边描边覆盖外缘）
		fillRect(img, x, y, w, h, Palette.MCPP_EDGE_DARK);
		// 2) 顶 1px F2F2F2 高光（含左右两侧 1px）
		fillRect(img, x, y, w, 1, Palette.WINDOW_HIGH);
		// 3) 底 1px F2F2F2 高光
		fillRect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH);
		// 4) 左 1px F2F2F2 高光
		fillRect(img, x, y, 1, h, Palette.WINDOW_HIGH);
		// 5) 右 1px F2F2F2 高光
		fillRect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH);
	}

	/** RGB 线性插值：t=0 返回 a，t=1 返回 b。 */
	private static Color lerpColor(Color a, Color b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(a.getRed()   + t * (b.getRed()   - a.getRed()));
		int g = Math.round(a.getGreen() + t * (b.getGreen() - a.getGreen()));
		int bl = Math.round(a.getBlue()  + t * (b.getBlue()  - a.getBlue()));
		return new Color(r, g, bl);
	}

	// ============================================================
	// 6 个机器贴图
	// ============================================================

	/**
	 * <b>PowMiner 176×166</b>：左能量条 (TE 纯色纯背) + 算力进度条 + 输出槽 + 玩家背包。
	 * <pre>
	 *   能量条 (TE 深灰纯背 8×52) @ (10, 18) — 对齐 {@code PowMinerScreen.ENERGY_*}
	 *   算力进度条 (紫色凹陷 36×8) @ (50, 36) — 对齐 {@code PowMinerScreen.ARROW_*}
	 *   输出槽 @ (96, 32) — 对齐 {@code PowMinerMenu.OUTPUT_SLOT}
	 *   玩家背包 @ (8, 84..135) 9×3 + Hotbar @ (8, 142..157) 9×1
	 * </pre>
	 */
	private static BufferedImage generatePowMiner() {
		// v20.13 PowMiner widget 区域整体下移 7px 让 ENERGY 在可用区间居中（用户反馈：能量槽上下边缘距离不一样）：
		//   widget 可用区域 Y 15..82 (高 67)：上边贴标题底+1、下边贴背包标题顶-2
		//   ENERGY 52 高居中: 上 padding = (67-52)/2 = 7 → ENERGY Y = 15+7 = 22、占 Y 22..74
		//   原 v20.12 ENERGY Y=15 贴标题底、下方留 17px 大空白 — 玩家视觉上“偏上”。
		//
		//   v20.13 所有 widget 整体 +7、保持 ENERGY 上下边缘相对 OUTPUT/ARROW 上下边缘各 10px 对称：
		//   - ENERGY (8, 22)   16×52  占 Y 22..74
		//   - OUTPUT (79, 32)  18×18  占 Y 32..50、中点 (88, 41) — ENERGY 上边比 OUTPUT 高 10px
		//   - ARROW  (62, 56)  52×8   占 Y 56..64、中点 (88, 60) — ENERGY 下边比 ARROW 低 10px ✅
		//   - gap (OUTPUT 底 50 → ARROW 顶 56) = 6px
		BufferedImage img = canvas(256, 256);
		drawWindow(img, 0, 0, 176, 178);
		// 左侧能量条 16×52 @ (8, 22) — widget 区域居中
		drawEnergyTrackMCPP(img, 8, 22, 16, 52);
		// 输出槽 @ (79, 32) — 唯一真实 Slot
		drawSlotPanel(img, 79, 32, 1, 1);
		// 算力进度条 52×8 @ (62, 56) — 紫灰 PANEL_FILL 凹陷 track
		drawRecessedTrack(img, 62, 56, 52, 8);
		// v16 玩家背包 下移 12px：96 / 154
		drawSlotPanel(img, 8, 96, 9, 3);
		drawSlotPanel(img, 8, 154, 9, 1);
		// 矿机紫色算力条 sprite @ (180, 0) 52×8 — 长度同步加长
		drawHorizontalArrowSprite(img, 180, 0, 52);
		return img;
	}

	/**
	 * <b>ElectricBallGenerator 176×166</b>：左能量条 (MCPP) + 燃料槽 + 横向琥珀燃烧条 + 装饰槽。
	 * <pre>
	 *   能量条 (MCPP 深灰纯底 16×52) @ (10, 18) — 7 阶镜像红条纹自底向上充能
	 *   燃料槽 @ (32, 32) — 对齐 {@code ElectricBallGeneratorMenu.FUEL_SLOT}
	 *   琥珀燃烧条 (AE2-CS 凹陷 36×8) @ (54, 36) — 运行时 drawHorizontalBar 叠加琥珀 fill
	 *   装饰槽 @ (96, 32) — 无真实 Slot，仅作为能量缓存 hover tooltip 视觉提示
	 * </pre>
	 */
	private static BufferedImage generateElectricBallGenerator() {
		// v20.15 右侧改成 16×52 能量条形状（用户反馈："右侧输出槽是能量槽，不是物品槽，很难理解吗？"）
		//   原 v20.14 右侧 DECOR 槽 (111, 39) 18×18 视觉上像物品槽（黑边+灰底），玩家会误以为能放物品。
		//   v20.15 直接画成 16×52 垂直能量条 track（与原左侧能量条同款 MCPP 风），语义明确："这是能量缓存"。
		//
		//   v20.15 布局（FUEL → ARROW→ → ENERGY）：
		//   - FUEL   (47, 39)   18×18 中点 (56, 48) ← 左输入：烧电球
		//   - ARROW  (77, 40)   22×16 中点 (88, 48) ← 中间橙色火焰箭头（→ 指向右）
		//   - ENERGY (112, 22)  16×52 中点 (120, 48) → 右能量缓存（垂直能量条，明显的能量条形状）
		//   FUEL 与 ENERGY 中心 X 各离 ARROW 中心 32px ✓ 严格对称
		//   同步需改 ElectricBallGeneratorScreen drawVerticalBar(112, 22, 16, 52) 和 hover (112, 22, 16, 52)
		BufferedImage img = canvas(256, 256);
		drawWindow(img, 0, 0, 176, 178);
		// FUEL 槽 @ (47, 39) — 左输入（烧电球）
		drawSlotPanel(img, 47, 39, 1, 1);
		// 中间进度箭头空状态 22×16 @ (77, 40) — 浅紫灰平面
		drawProgressArrowEmpty(img, 77, 40);
		// 右侧能量缓存条 16×52 @ (112, 22) — MCPP 深灰底，运行时填充红色能量
		drawEnergyTrackMCPP(img, 112, 22, 16, 52);
		// v16 玩家背包 下移 12px：96 / 154
		drawSlotPanel(img, 8, 96, 9, 3);
		drawSlotPanel(img, 8, 154, 9, 1);
		// 发电机橙色火焰箭头 sprite @ (180, 0) 22×16 — 复用 drawProgressArrowFilled 棱形几何 + FLAME 三色
		//   fill = FLAME_LIGHT 主桃橙、highlight = FLAME_BRIGHT 顶部高亮黄白、shadow = FLAME_DARK 底部深橙
		drawProgressArrowFilled(img, 180, 0, Palette.FLAME_LIGHT, Palette.FLAME_BRIGHT, Palette.FLAME_DARK);
		return img;
	}

	/**
	 * <b>ElectricCondenser 176×178</b>：左能量条 (MCPP) + 双输入槽 + 进度箭头 + 输出槽 + 升级槽组。
	 * <pre>
	 *   v20 重新对齐：所有元素中线 y=51，视觉上上半区一条水平线贯穿全部 widget。
	 *   能量条 (MCPP 深灰纯底 16×52) @ (10, 25) — 对齐 {@code ElectricCondenserScreen.ENERGY_*}
	 *   输入槽 1 INGOT @ (32, 33) — 对齐 {@code ElectricCondenserMenu.INGOT_SLOT_Y}
	 *   输入槽 2 BALL  @ (32, 51) — 对齐 {@code ElectricCondenserMenu.BALL_SLOT_Y}（连续 18px）
	 *   进度箭头 (AE2-CS 凹陷 22×16) @ (60, 43) — 运行时 drawProgressArrow 叠加紫色箭头
	 *   输出槽 @ (98, 42) — 对齐 {@code ElectricCondenserMenu.OUTPUT_SLOT_Y}
	 *   升级槽组 3 行 @ (124, 24..60) — 对齐 {@code ElectricCondenserMenu.UPGRADE_ONE/TWO/THREE_Y}
	 * </pre>
	 */
	private static BufferedImage generateElectricCondenser() {
		// v20.13 电凝聚器 widget 整体上移 2px 让 ENERGY 上下边距接近对称（用户反馈："电凝聚器对齐了吗"）
		//   widget 可用区 Y 15..82 (高 67)。UPGRADE 3 槽组 54 高占满 widget 区域，结构性约束：
		//     UPGRADE 顶必须 ≥ 16 (标题底 14+2)、底必须 ≤ 81 (背包标签顶 84-3) — 共 65 可用高
		//     54 居中 → 上 padding = (67-54)/2 = 6.5 → 取 7 → UPGRADE Y = 15+7 = 22（v20.2 原 24，上 9）
		//   全部 widget 整体上移 2px（24→22、25→23、31→29、43→41 等等）：
		//   - ENERGY (8,23)   16×52 占 23..75，上 8/下 7 ✓ 几乎对称（v20.2 原 25..77，上 10/下 5 差 5）
		//   - INGOT  (47,29)  18×18 中点 (56, 38)
		//   - BALL   (47,51)  18×18 中点 (56, 60) — 双格中线 (56, 49)
		//   - ARROW  (77,41)  22×16 中点 (88, 49)
		//   - OUTPUT (111,40) 18×18 中点 (120, 49)
		//   - UPGRADE(152,22/40/58) 1×3 组中点 (160, 49) — 顶 22 距标题 7、底 76 距背包标签 6 ✓
		//   中线 y=49（与 PowMiner/ELB 的 y=48 仅差 1px，结构性约束下最贴近统一标准）
		//   同步需改 ElectricCondenserScreen ARROW_Y 43→41, ENERGY_Y 25→23
		//   和 ElectricCondenserMenu INGOT_SLOT_Y 31→29, BALL_SLOT_Y 53→51, OUTPUT_SLOT_Y 42→40, UPGRADE_*_Y 24/42/60→22/40/58
		BufferedImage img = canvas(256, 256);
		drawWindow(img, 0, 0, 176, 178);
		// 左侧大能量条 16×52 @ (8, 23) — MCPP 风格深灰底
		drawEnergyTrackMCPP(img, 8, 23, 16, 52);
		// INPUT 双格 @ (47, 29)+(47, 51) — gap=4 让两槽视觉分离
		drawSlotPanel(img, 47, 29, 1, 1);
		drawSlotPanel(img, 47, 51, 1, 1);
		// 进度箭头空状态 22×16 @ (77, 41) — 纯浅紫灰平面
		drawProgressArrowEmpty(img, 77, 41);
		// OUTPUT 槽 @ (111, 40) — 中点 (120, 49)
		drawSlotPanel(img, 111, 40, 1, 1);
		// UPGRADE 3 槽垂直堆叠 @ (152, 22)+(152, 40)+(152, 58) — 组中点 (160, 49) v20.13 上移 2
		// 与齿轮按钮 (152, 4) 18×18：X 完全重叠、Y 不重（齿轮 4-22 vs slot 22-76 — 紧贴但不重）
		drawSlotPanel(img, 152, 22, 1, 3);
		// 玩家背包 (8, 96) + Hotbar (8, 154)
		drawSlotPanel(img, 8, 96, 9, 3);
		drawSlotPanel(img, 8, 154, 9, 1);
		// 进度箭头紫色 sprite @ (180, 0) 22×16 — 运行时 partial-blit 覆盖空箭头
		drawProgressArrowSprite(img, 180, 0);
		return img;
	}

	/**
	 * <b>v20.7 通用面配置白板贴图 — 所有机器共用 side_config_menu.png</b>。
	 *
	 * <p>贴图本身只画 120×107 凹陷窗体；按钮位置的"槽位底凹陷"画面由运行时 SideConfigScreen
	 * 动态画（与按钮位置 hardcode 在一起），任意机器接入时只换 BE 即可，贴图不重生成。</p>
	 *
	 * <h4>历史演进</h4>
	 * <ul>
	 *   <li>v14 之前：electric_condenser_side_config.png — 画了 11 个按钮槽位底，与 ElectricCondenser
	 *       布局耦合，无法复用</li>
	 *   <li>v15：改名 side_config_menu.png，白板设计</li>
	 *   <li>v19：在贴图 (0, 110) 嵌入 AE2-CS BUTTON DARK atlas，SideFaceButton 从这里 blit</li>
	 *   <li><b>v20.7</b>：BUTTON atlas 搬到公用 icons.png (0, 130)（按钮 sprite 本质是公用 widget，
	 *       不该住在某个特定 GUI 的主背景里）。本方法不再 copyRect、不再 throws IOException</li>
	 * </ul>
	 *
	 * <h4>玩家可见布局</h4>
	 * <pre>
	 *   title         @ (8, 6)
	 *   返回按钮       @ (102, 4)   18×18  ICON_BACK_U
	 *   6 面按钮（cube unfold）：
	 *      TOP        (39, 20)
	 *      LEFT(18,42) FRONT(39,42) RIGHT(60,42)
	 *      BACK(18,64) BOTTOM(39,64)
	 *   3 工具按钮：
	 *      AUTO_IMPORT (96, 20)  AUTO_EXPORT (96, 42)  CLEAR_ALL (96, 64)
	 * </pre>
	 */
	private static BufferedImage generateSideConfigMenu() {
		BufferedImage img = canvas(256, 256);
		drawWindow(img, 0, 0, 120, 107);
		return img;
	}

	// ============================================================
	// widgets.png sprite sheet (256×64)
	// ============================================================

	/**
	 * icons.png 布局（运行时 partial blit 叠加在主贴图凹陷条带上方）：
	 * <pre>
	 * y=24..31  x=  0..91  burn_h_full          琥珀燃烧条 fill 92×8
	 * y=32..39  x=  0..91  energy_h_full        FE 红横向 fill 92×8（v9 已删）
	 * y= 0..63  x=240..255 energy_v_full        MC-pixel-perfect 垂直红 fill 16×64 （右侧避开图标行）
	 *
	 * y=40..55 (16×16 each, x 间距 16):
	 *   x=  0..15  icon_gear      x= 16..31  icon_back     x= 32..47  icon_question
	 *   x= 48..63  icon_input     x= 64..79  icon_output   x= 80..95  icon_disable
	 *   x= 96..111 icon_burn      x=112..127 icon_link     x=128..143 icon_pickaxe
	 * </pre>
	 *
	 * <p><b>重要</b>：energy_v_full 16 列 sprite 位于 x=240..255（y=0..63 全高），避开图标行 x=0..143。
	 * {@code MachineUi.drawVerticalBar} 的 source x 必须同步为 240 且 +1 跳过左灰列。</p>
	 *
	 * <h4>energy_v_full 视觉风格演进（v7：从 8×64 扩为 16×64 完整 7 阶镜像）</h4>
	 * <p>v6 在 8×64 空间里只能塞 3 阶镜像（阶 1/4/7），视觉显得单薄；v7 扩容到 16×64 与参考图
	 * {@code tools/mc_pixel_perfect.png}（160×640 = 16×64 ×10 倍放大）一比一对齐，完整保留
	 * 7 阶亮/暗镜像红条纹 + 左灰/右白立体边 + 顶灰/底白端点。同步改造：</p>
	 * <ul>
	 *   <li>所有机器 Screen 的 {@code ENERGY_W} 从 8 改为 16</li>
	 *   <li>所有机器贴图里垂直能量条 track 从 {@code drawEnergyTrackTE(..., 8, h)} 改为 {@code drawEnergyTrackMCPP(..., 16, h)}</li>
	 *   <li>{@code MachineUi.drawVerticalBar} 的 {@code sourceWidth} 上限从 8 提升到 14，source x 从 248 改为 240</li>
	 * </ul>
	 *
	 * <h4>16×64 sprite 精确像素映射表（严格匹配 mc_pixel_perfect.png）</h4>
	 * <pre>
	 *   col 0  (x=240): 整列 #373737      ← 左 1px 阴影边（drawVerticalBar blit 时跳过）
	 *   col 1  (x=241): 红阶 1 亮/暗 #420000/#2B0000
	 *   col 2  (x=242): 红阶 2 亮/暗 #5C0000/#3C0000
	 *   col 3  (x=243): 红阶 3 亮/暗 #7A0000/#500000
	 *   col 4  (x=244): 红阶 4 亮/暗 #990000/#640000
	 *   col 5  (x=245): 红阶 5 亮/暗 #B80000/#780000
	 *   col 6  (x=246): 红阶 6 亮/暗 #D60000/#8C0000
	 *   col 7  (x=247): 红阶 7 亮/暗 #F50000/#A00000  ← 最亮（管道中心左）
	 *   col 8  (x=248): 红阶 7 亮/暗 #F50000/#A00000  ← 最亮镜像（管道中心右）
	 *   col 9  (x=249): 红阶 6 镜像
	 *   col 10 (x=250): 红阶 5 镜像
	 *   col 11 (x=251): 红阶 4 镜像
	 *   col 12 (x=252): 红阶 3 镜像
	 *   col 13 (x=253): 红阶 2 镜像
	 *   col 14 (x=254): 红阶 1 镜像
	 *   col 15 (x=255): 整列 #FFFFFF      ← 右 1px 高光边（drawVerticalBar blit 时跳过）
	 *
	 *   行规律（y 方向 64 行）：
	 *     y=0       : 全深灰顶边（覆盖 col 0..14，col 15 保持白）
	 *     y=1/3/.../61: 亮行 — 按 paletteIdxPerCol 选 MCPP_RED_LIGHT[idx]
	 *     y=2/4/.../62: 暗行 — 按 paletteIdxPerCol 选 MCPP_RED_DARK[idx]
	 *     y=63      : 底行 col 0 灰 + col 1..15 纯白
	 * </pre>
	 *
	 * <p>{@code drawVerticalBar} 实际 blit 的是 col 1..14（{@code sourceWidth=w-2=14}，从 source x=241 起），
	 * 游戏里能量条看到 <b>14 列 7 阶镜像红条纹</b>：暗→亮→暗 的完整渐变 + 中心 2 列最亮，
	 * 配合 y 方向"亮暗行交替" = MC-pixel-perfect 像素艺术的"红色能量液体"质感。
	 * col 0 灰 / col 15 白仅在 sprite sheet 预览时可见，作为立体边框美化。</p>
	 */
	private static BufferedImage generateIconsSheet() throws IOException {
		// v14 改名 widgets.png → icons.png：成为运行时唯一公用 sprite sheet（按钮图标 + 燃烧/能量 fill + 调色板）。
		// v14 删除：进度箭头 sprite —— 已移到各机器主背景贴图右上外延区（如 electric_condenser.png (180, 0)）。
		// 原因：进度箭头是 ElectricCondenser 独有 sprite，不应该污染公用 icons sheet；视觉风格也可独立设计。
		BufferedImage img = canvas(256, 256);
		// 一次加载 AE2-CS 官方 icons.png（v14 路径改 tools/refs/ae2cs_icons.png），供下面调色板色块 copy + 图标 copy 共享使用
		BufferedImage icons = loadIconsSheet();

		// === [v15 删除] 横向琥珀燃烧条 fill 92×8 @ (0, 24) ===
		// v14 之前 icons.png 统一提供横向琥珀 sprite，但这使得 sprite 宽度被 limited (92 px)
		// 且需要推断各机器的 fillColor。v15 跟进度箭头一样：每个机器贴图自带 sprite source，
		// 位置/宽度独立调控，drawHorizontalBar 加 bg/sourceX/sourceY 参数。

		// === [v9 删除] 原 横向 FE 红 fill 92×8 @ y=32..39 ===
		// (同上，v15 本身已不依赖该区域)
		// 用户反馈"红色横向能量条不要"。该 sprite 历史上被 CurrencyGen "燃烧进度条" + WirelessPower
		// "链接状态条"使用，但实际上：
		//   - CurrencyGen 燃烧进度本质等价于 ElectricBallGen 的琥珀燃烧条（同语义不同色，统一用 AMBER）
		//   - WirelessPower 状态条 GREEN/RED 切换原本就有 bug（pickHorizontalSpriteRow 不识别 GREEN，全显示红）
		// v9 起：CurrencyGen 改 AMBER，WirelessPower 改 AMBER，pickHorizontalSpriteRow 简化为只返回 24（琥珀行）。

		// === [v13 整块切] icons.png 调色板矩形整体复制 @ widgets.png (0, 64) ===
		// v11/v12 cell-by-cell 复制 + 手动加间距的方案破坏了 AE2 原本的视觉关系：
		//   v11 紧挨 → 看起来像一整块马赛克
		//   v12 加 8px gap → 色块间是 GUI 主背景，丢失 AE2 原本的立体边
		// v13 直接整块 copyRect：把 icons.png 调色板矩形 (16, 192) 144×64 整体切到 widgets.png (0, 64)，
		//   保留 AE2 原本的色块布局/立体边/分隔，每个色块在原图里就是带凸起阴影的"按钮"风格。
		//
		// <pre>
		// icons.png src rect: (16, 192)..(159, 255)  共 144×64 px
		//   gy=12 (y=192..207): 9 列 light shades   紫/蓝/粉/天蓝 + 镜像
		//   gy=13 (y=208..223): 9 列 dark shades    深紫/深蓝/深红/深黑紫 + 镜像
		//   gy=14 (y=224..239): 9 列 dark shades 重复
		//   gy=15 (y=240..255): 9 列 mid shades     紫/蓝/红/灰 + 镜像
		// widgets.png dst:    (0, 64)..(143, 127)
		// </pre>
		copyRect(icons, 16, 192, 144, 64, img, 0, 64);

		// === v20.7 AE2-CS BUTTON DARK 行 atlas 144×20 @ (0, 130) ===
		// 之前 v19 把这块 sprite 复制到了 side_config_menu.png (0, 110) 给 SideFaceButton 用——
		// 但 side_config_menu.png 是"机器侧面 I/O 配置界面"的主背景贴图，里面塞按钮 atlas
		// 是冗余：按钮 sprite 是全 GUI 通用 widget，应该跟其他图标一样住在公用 icons.png。
		// v20.7 把 atlas 搬到 icons.png 后 generateSideConfigMenu 不再 copyRect，
		// SideFaceButton 改成从 ICONS_TEXTURE blit。
		//
		// 源：AE2-CS icons.png (16, 216) 144×20 — DARK 行（按下/激活状态，本项目唯一使用）
		//   col 0..3 (u=16..70):   PURPLE_DARK / BLUE_DARK / RED_DARK / ORIGINAL_DARK   normal
		//   col 4..7 (u=88..142):  PURPLE_DARK / BLUE_DARK / RED_DARK / ORIGINAL_DARK   hover (+72px)
		// 目标：本项目 icons.png (0, 130) 144×20 — atlas 在新坐标下：
		//   normal u=0..54   (步长 18)
		//   hover  u=72..126 (步长 18)
		//   atlas 起点 v=130
		// SideFaceButton 的 ATLAS_DARK_V=130 / ATLAS_HOVER_U_OFFSET=72 / modeAtlasU 数值不变，
		// 只需把 BG → ICONS_TEXTURE，零额外坐标重写。
		copyRect(icons, 16, 216, 144, 20, img, 0, 130);

		// === 垂直 FE 红 fill 16×64 (MC-pixel-perfect 完整 7 阶镜像版) @ x=240..255 ===
		// 视觉规则：严格 1:1 复刻 tools/mc_pixel_perfect.png（160×640 = 16×64 ×10 倍放大）像素分布。
		//
		// 列规律（7 阶完整镜像，参考图 Row/Column sweep 实测数据）：
		//   col 0  (x=240): 整列深灰 #373737   ← 左 1px 阴影边（drawVerticalBar blit 时跳过）
		//   col 1..7  (x=241..247): 阶 1→2→3→4→5→6→7 暗到亮（暗红 #2B0000 → 最亮红 #F50000）
		//   col 8..14 (x=248..254): 阶 7→6→5→4→3→2→1 镜像（最亮 → 最暗）
		//   col 15 (x=255): 整列纯白 #FFFFFF   ← 右 1px 高光边（drawVerticalBar blit 时跳过）
		//
		// 行规律（y 方向 64 行）：
		//   y=0   : 全深灰顶边（左阴影侧的延伸，col 0..14 灰，col 15 最后一步被改白）
		//   y=1/3/5/.../61: 亮行 — 每列取 MCPP_RED_LIGHT[paletteIdxPerCol[col-1]]
		//   y=2/4/6/.../62: 暗行 — 每列取 MCPP_RED_DARK[paletteIdxPerCol[col-1]]
		//   y=63  : 底行 col 0 灰 + col 1..15 纯白（高光延伸到底）
		//
		// drawVerticalBar 实际 blit 宽度 = min(w-2, 14) = 14（能量条 track 16 宽），
		// 即 col 1..14 被渲染到能量条 track 内部：完整的"暗→亮→暗 7 阶镜像红条纹"，
		// 配合"亮暗行交替"= 标准 MC-pixel-perfect 像素艺术的"红色液体流动"质感。
		//
		// 绘制顺序采用"先整体默认 → 后局部覆盖"的分层画法，避免 overlap 逻辑混乱：
		//   Step 1 整块 16×64 灰底 → Step 2 右列整白 → Step 3 中央 14 列 7 阶镜像条纹 → Step 4 底 1px col 1..15 改白
		final int baseX = 240;

		// v20.7 重写 4 边描边部分：
		//   原设计：col 0 整列 #373737 + col 15 整列 #FFFFFF + 顶 1px 灰 + 底 1px col 1..15 白——
		//     这是 MCPP 参考图原始“左凹右凸” 3D 凹陷立体感签名，在 sprite sheet 上看参考价值高。
		//   但 v20.6 能量条 track 已经 4 边 F2F2F2 描边，sprite blit 时又跳过 col 0/15 + 顶/底 1px，
		//     运行时这些外边根本看不到。在 icons.png 上预览时 sprite 外边是“灰+白反面陈”与 GUI 标准
		//     “4 边 F2F2F2 统一”不一致（用户反馈“icon 里的能量槽描边问题”）。
		//   v20.7 统一 4 边 F2F2F2（取 WINDOW_HIGH）：sheet 预览视觉统一、运行时由于 blit 跳边也不受影响。

		// Step 1: 整个 16×64 填“参考黑底”（col 1..14 会被 Step 3 亮/暗红条纹覆盖，4 边 1px 会被 Step 2 覆盖）
		fillRect(img, baseX, 0, 16, 64, Palette.MCPP_EDGE_DARK);

		// Step 2: 4 边 F2F2F2 描边（与能量条 track / 槽位面板 / 凹陷 track 统一 GUI 标准）
		fillRect(img, baseX, 0, 16, 1, Palette.WINDOW_HIGH);             // 顶 1px
		fillRect(img, baseX, 63, 16, 1, Palette.WINDOW_HIGH);            // 底 1px
		fillRect(img, baseX, 0, 1, 64, Palette.WINDOW_HIGH);             // 左 1px
		fillRect(img, baseX + 15, 0, 1, 64, Palette.WINDOW_HIGH);        // 右 1px

		// Step 3: col 1..14 的 y=1..62 区域画 7 阶完整镜像红色条纹（亮/暗行交替）
		//   blit 时 drawVerticalBar 从 (baseX+1, 64-filled) 起照 14×filled 拼取——恶競 col 1..14 区域。
		final int[] paletteIdxPerCol = {0, 1, 2, 3, 4, 5, 6, 6, 5, 4, 3, 2, 1, 0};
		for (int col = 1; col <= 14; col++) {
			int px = baseX + col;
			int idx = paletteIdxPerCol[col - 1];
			Color light = Palette.MCPP_RED_LIGHT[idx];
			Color dark  = Palette.MCPP_RED_DARK[idx];
			for (int yy = 1; yy <= 62; yy++) {
				fillRect(img, px, yy, 1, 1, (yy % 2 == 1) ? light : dark);
			}
		}

		// === 16×16 图标 sprite (y=40..55) ===
		// v8 策略：能从 AE2-CS 官方 icons.png 找到视觉对应的 → 直接复制像素（精致的紫白调色板）；
		//         icons.png 没有对应的 → 保留 hand-drawn（调色板已对齐 AE2 风）；
		//         完全没被引用的 → 删除（原 ICON_BURN_U=96 火焰图标 + ICON_PICKAXE_U=128 镐图标）
		// (icons sheet 在函数顶部已加载，这里直接复用)
		// -- hand-drawn（icons.png 没有齿轮/返回箭头/问号/禁用 X 的对应）--
		// v20.8 经典 8 齿轮（4 主齿 4×2 + 4 斜齿 2×2 + 12×12 圆角主体 + 4×4 hole）单色 ICON_FG
		drawIconGear     (img,   0, 40);                // ICON_GEAR_U=0
		// v20.8 经典返回箭头（9 行渐进三角尖 + 5 行高箭杆延伸到 col 13）单色 ICON_FG
		drawIconBack     (img,  16, 40);                // ICON_BACK_U=16
		// v20.7 删 drawIconQuestion (ICON_QUESTION_U=32 全项零引用)
		// -- copy from icons.png（精致 AE2-CS 原版 sprite）--
		// v10 更正：input/output 从 (0,0)/(1,0) 无方向感的灰白盒 改为 (0,7)/(1,7) 蓝色阀/橙色阀 — 严格对应进/出语义
		copyIconCell(icons, 0, 7, img,  48, 40);        // ICON_INPUT_U=48  ← icons (0,7) 蓝色四向阀（进入）
		copyIconCell(icons, 1, 7, img,  64, 40);        // ICON_OUTPUT_U=64 ← icons (1,7) 橙黄色四向阀（出去）
		// -- hand-drawn（icons.png 的红 X 只有 4~5px 小号，视觉上不如 hand-drawn 大 X 清晰）--
		drawIconDisable  (img,  80, 40);                // ICON_DISABLE_U=80
		// -- ICON_BURN_U=96（原琥珀火焰）: 删除，无任何引用（v8 清理）--
		// v20.7 删 ICON_LINK_U=112 wifi（原 WirelessPower GUI 专用，GUI 废弃后零引用）
		// -- ICON_PICKAXE_U=128（原镌图标）: 删除，无任何引用（v8 清理）--

		return img;
	}

	/**
	 * <b>v15 进度箭头 sprite — 与 23×17 凹陷 track 形状对齐版</b>。
	 *
	 * <p>v14 之前主体只占 9 行 y=4..12，与 23×17 的凹陷 track 脱节（上下各 4 行透明，看起来“小一圈”）。
	 * v15 主体改为占满整个 17 行高度，三角尖仍在中间 9 行伋出，与 track 边界贴合。</p>
	 *
	 * <pre>
	 * 主体 16×17（占满 track 高度）：
	 *   y=0:        顶 1px 浅紫高光  ICON_PURPLE_LIGHT #A79CFF
	 *   y=1..15:    中间 15 行主紫   ACCENT_PURPLE      #915DCD
	 *   y=16:       底 1px 深紫阴影  ACCENT_VIOLET      #6054A6
	 *   x=0, y=1..15 最左 1px 列改浅紫 — 与顶光连成 L 形，作为“光从左上打入”的凸起立体感
	 *
	 * 三角尖 7×9 (x=16..22, y=4..12)：
	 *   y=4:    3 px 浅紫高光行
	 *   y=5..11: 4..7..4 主紫锥形
	 *   y=12:   3 px 深紫阴影行
	 *   x=22, y=8 顶点深紫点状阴影
	 *
	 * v14 主体只占 9 行（y=4..12）看起来“与 track 脱节”，v15 改为占满整个高度。
	 * </pre>
	 *
	 * @param img 目标 BufferedImage
	 * @param x   箭头左上角
	 * @param y   箭头左上角
	 */
	private static void drawProgressArrowSprite(BufferedImage img, int x, int y) {
		// 紫色版（右上 sprite source，运行时 drawProgressArrow partial-blit 到空箭头上覆盖）
		drawProgressArrowFilled(img, x, y, Palette.ACCENT_PURPLE, Palette.ICON_PURPLE_LIGHT, Palette.ACCENT_VIOLET);
	}

	/**
	 * <b>v20.2 进度箭头空状态</b>：与紫色 sprite 同几何，但用纯浅紫灰（无高光/无阴影）。
	 * v20 曾用 WINDOW_HIGH 高光 + PANEL_TOP 阴影，但在箭杆顶端会形成一条非常显眼的 22px F2F2F2 白线
	 * （用户反馈"箭头上奇怪白线"）。v20.2 三色统一为 PANEL_FILL，让空箭头是纯色平面，
	 * 立体感完全由紫色 sprite partial-blit 自带的高光/阴影提供。
	 */
	private static void drawProgressArrowEmpty(BufferedImage img, int x, int y) {
		drawProgressArrowFilled(img, x, y, Palette.PANEL_FILL, Palette.PANEL_FILL, Palette.PANEL_FILL);
	}

	/**
	 * <b>经典 vanilla furnace 进度箭头 22×16 棱形体（v20.3 全圈描边）</b>：
	 * 参数化颜色，紫色 sprite 与浅色空状态共用同一几何。
	 *
	 * <pre>
	 * 整体形状：
	 *   主身箭杆 12×6 (x..x+11, y+5..y+10)
	 *   棱形右尖 14 行 (y+1..y+14) 从 base 1px 到最宽 11px 再收尖
	 *
	 * 高光逻辑（v20.3 严格"vanilla 凸起立体 + 全圈描边"）：
	 *   顶/左/右上斜边 = highlight（受光面）
	 *   底/右下斜边     = shadow   （背光面）
	 *   左下角           = shadow   （底面延续）
	 *   棱形最右尖端     = highlight(顶半 y+7) / shadow(底半 y+8)
	 * </pre>
	 *
	 * <p>v20 之前只在主身顶 + 棱形最宽部上沿画 highlight，棱形右上斜边全部留 fill 主色，
	 * 导致放大看高光"只有顶部一长条 22px 横线"，与水晶科技"高光绕一整圈"的细节差距明显
	 * （用户反馈"紫色箭头右边高光没做好"）。
	 * v20.3 改为绕整圈逐像素描边：右上 7 行斜边每行最右 1px = highlight，
	 * 右下 7 行斜边每行最右 1px = shadow，让箭头外轮廓在"放大看像素"时仍清晰立体。</p>
	 *
	 * @param fill      主填充色
	 * @param highlight 受光面 1px（顶/左/右上）
	 * @param shadow    背光面 1px（底/右下）
	 */
	private static void drawProgressArrowFilled(BufferedImage img, int x, int y, Color fill, Color highlight, Color shadow) {
		// 行宽数组（棱形从 y+1 到 y+14 共 14 行）：1, 2, 4, 6, 8, 10, 11, 11, 10, 8, 6, 4, 2, 1
		// i=0..6 是上半部（含最宽行 i=6），i=7..13 是下半部（含最宽行 i=7）。
		final int[] rowWidths = {1, 2, 4, 6, 8, 10, 11, 11, 10, 8, 6, 4, 2, 1};
		final int n = rowWidths.length;

		// === 1) 主身箭杆 12×6 fill ===
		fillRect(img, x, y + 5, 12, 6, fill);

		// === 2) 棱形右尖 14 行 fill ===
		for (int i = 0; i < n; i++) {
			fillRect(img, x + 11, y + 1 + i, rowWidths[i], 1, fill);
		}

		// === 3) 受光面（顶/左/右上斜边）highlight ===
		// 主身顶 12px
		fillRect(img, x, y + 5, 12, 1, highlight);
		// 主身左 6px（绕圈左边）
		fillRect(img, x, y + 5, 1, 6, highlight);
		// 棱形上半部 i=0..6 每行最右 1px = highlight（绕圈右上斜边）
		for (int i = 0; i <= 6; i++) {
			int rightX = x + 11 + rowWidths[i] - 1;
			fillRect(img, rightX, y + 1 + i, 1, 1, highlight);
		}

		// === 4) 背光面（底/右下斜边）shadow ===
		// 主身底 12px
		fillRect(img, x, y + 10, 12, 1, shadow);
		// 主身左下角：底面延续，覆盖第 3 步画的左 highlight
		fillRect(img, x, y + 10, 1, 1, shadow);
		// 棱形下半部 i=7..13 每行最右 1px = shadow（绕圈右下斜边）
		for (int i = 7; i < n; i++) {
			int rightX = x + 11 + rowWidths[i] - 1;
			fillRect(img, rightX, y + 1 + i, 1, 1, shadow);
		}
	}

	/**
	 * <b>v20.10 矿机紫色算力进度条 sprite</b>（PowMiner 专用）。
	 *
	 * <p>调色与 ElectricCondenser 进度箭头 sprite 严格统一：主色 ACCENT_PURPLE #915DCD + 顶 1px 高光 ICON_PURPLE_LIGHT #A79CFF。
	 * 表达"哈希算力推进"语义 — 理性、冷静、数据流动感。</p>
	 *
	 * <p>原 drawHorizontalBurnSprite v20.10 拆分：矿机用 ArrowSprite（紫），发电机用 FlameSprite（火焰橙），
	 * 体现两者机器语义差异。</p>
	 */
	private static void drawHorizontalArrowSprite(BufferedImage img, int x, int y, int w) {
		fillRect(img, x, y,     w, 8, Palette.ACCENT_PURPLE);      // 下 7px 主紫 ACCENT_PURPLE #915DCD
		fillRect(img, x, y,     w, 1, Palette.ICON_PURPLE_LIGHT);  // 顶 1px 淡紫高光 ICON_PURPLE_LIGHT #A79CFF
	}

	/**
	 * <b>v20.10 发电机火焰燃烧条 sprite</b>（ElectricBallGenerator 专用）。
	 *
	 * <p>表达"点火燃电球"语义：火焰主体 + 顶部锯齿跳动火苗，玩家一眼能区别于矿机的冷静紫色算力条。</p>
	 *
	 * <h4>像素布局（36×8）</h4>
	 * <pre>
	 *   y=0  : 顶部锯齿火苗 — 每 3 列交替 FLAME_BRIGHT/FLAME_DARK，模拟火苗跳动上下不齐
	 *   y=1  : 整行 FLAME_BRIGHT #FFD955 — 火焰高亮中间层
	 *   y=2  : 整行 FLAME_LIGHT #FFB347 — 火焰桃橙中间层
	 *   y=3..5: 整行 FLAME_DARK #DD5500 — 火焰主体深橙点火脚
	 *   y=6,7: 不可见 — drawHorizontalBar sourceHeight=min(h-2, 8)=6、只 blit y=0..5
	 * </pre>
	 *
	 * <p><b>锯齿火苗规则</b>：y=0 顶行按列交替"BRIGHT BRIGHT DARK"循环（每 3 px 一个周期），
	 * 36 列 = 12 个火苗周期；BRIGHT 2 宽 + DARK 1 宽让亮点占主导 — 点上去是"满火"而非"火苗拍打"。</p>
	 */
	private static void drawHorizontalFlameSprite(BufferedImage img, int x, int y, int w) {
		// y=3..5: 火焰主体 FLAME_DARK【点火脚】
		fillRect(img, x, y + 3, w, 3, Palette.FLAME_DARK);
		// y=2: 桃橙中间层 FLAME_LIGHT
		fillRect(img, x, y + 2, w, 1, Palette.FLAME_LIGHT);
		// y=1: 高亮中间层 FLAME_BRIGHT
		fillRect(img, x, y + 1, w, 1, Palette.FLAME_BRIGHT);
		// y=0: 锯齿火苗 — 每 3 列 BRIGHT 2 + DARK 1，多次重复扫过 w 列
		for (int col = 0; col < w; col++) {
			Color c = (col % 3 == 2) ? Palette.FLAME_DARK : Palette.FLAME_BRIGHT;
			fillRect(img, x + col, y, 1, 1, c);
		}
	}

	/**
	 * <b>v20.10 发电机 MCPP 黑底"炉膛" track</b>（ElectricBallGenerator 专用）。
	 *
	 * <p>与 drawRecessedTrack 紫灰 PANEL_FILL track（矿机专用）区分开 — 这是点火烧电球的"黑色炉膛"，
	 * 深灰 #373737 底色创造"火焰在黑底上跳动"的高反差火焰质感。与能量条 drawEnergyTrackMCPP 同款：
	 * 4 边 F2F2F2 描边 + 内部纯 MCPP_EDGE_DARK 黑底。</p>
	 *
	 * <p><b>填充 sprite 兼容性</b>：{@code MachineUi.drawHorizontalBar} +1 跳左 1px + +1 跳顶 1px、
	 * sourceHeight = min(h-2, 8)，恰好叠加到内部 (w-2)×(h-2) 黑底区域。</p>
	 */
	private static void drawHorizontalBurnTrack(BufferedImage img, int x, int y, int w, int h) {
		fillRect(img, x, y, w, h, Palette.MCPP_EDGE_DARK);          // 内部纯黑底 #373737
		fillRect(img, x, y, w, 1, Palette.WINDOW_HIGH);             // 顶 1px F2F2F2
		fillRect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH);    // 底 1px F2F2F2
		fillRect(img, x, y, 1, h, Palette.WINDOW_HIGH);            // 左 1px F2F2F2
		fillRect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH);    // 右 1px F2F2F2
	}

	/**
	 * <b>v20.8 经典齿轮（重新设计形状）</b>。
	 *
	 * <p>v20.7 把齿轮改成紫色三调被用户否决（"不要紫色"），同时用户指出"原来的形状丑"——
	 * 真正的问题是几何而非颜色。v20.8 颜色回归单色 ICON_FG #4D4D67（vanilla 风），
	 * 但形状彻底重画为经典圆形 8 齿轮：4 个 4×4 主齿（上下左右）+ 4 个 2×2 斜角齿（45°），
	 * 中央 12×12 圆角主体 + 4×4 空心 hole。</p>
	 *
	 * <h4>像素布局（16×16）</h4>
	 * <pre>
	 *   . . . . . . X X X X . . . . . .   y=0   顶齿
	 *   . . . . . . X X X X . . . . . .   y=1
	 *   . . X X . . X X X X . . X X . .   y=2   顶斜齿 + 主体顶
	 *   . . X X X X X X X X X X X X . .   y=3
	 *   . . . . X X X X X X X X . . . .   y=4   主体上沿
	 *   . . . . X X X X X X X X . . . .   y=5
	 *   X X X X X X . . . . X X X X X X   y=6   左齿 + 中空 + 右齿
	 *   X X X X X X . . . . X X X X X X   y=7
	 *   X X X X X X . . . . X X X X X X   y=8
	 *   X X X X X X . . . . X X X X X X   y=9
	 *   . . . . X X X X X X X X . . . .   y=10  主体下沿
	 *   . . . . X X X X X X X X . . . .   y=11
	 *   . . X X X X X X X X X X X X . .   y=12
	 *   . . X X . . X X X X . . X X . .   y=13  底斜齿 + 底齿
	 *   . . . . . . X X X X . . . . . .   y=14  底齿
	 *   . . . . . . X X X X . . . . . .   y=15
	 * </pre>
	 *
	 * <p>关键改进：4 主齿宽度从原 4×3 → 4×4（更粗壮），斜齿从 2×2 → 2×2 但位置往内 1px
	 * 让整体形状是经典"圆形齿轮"轮廓而非"十字 + 4 角点"。中央 hole 保持 4×4 露出按钮槽底，
	 * 让玩家直观识别"这是齿轮 = 配置/设置"语义。</p>
	 */
	private static void drawIconGear(BufferedImage img, int x, int y) {
		// v20.14 缩小齿轮（用户反馈："齿轮有点大"）：
		//   原 v20.8 齿轮整体 16×16 占满 18×18 按钮 89%，齿粗外突 2px、斜齿 2×2。
		//   v20.14 整体内缩到 14×14（占按钮 78%），主齿外突 1px、斜齿 1×1、主体仍 10×10：
		//   - 主齿 4×2→4×1（外突减半）
		//   - 斜齿 2×2→1×1
		//   - 主体 12×12→10×10（内缩 1px 各侧）
		//   - hole 4×4 保留不动（中央识别度需求）
		Color fg = Palette.ICON_FG;

		// === 1) 4 主齿（4×1 / 1×4，外突 1px）===
		fillRect(img, x + 6, y + 1, 4, 1, fg);            // 顶齿（4 宽 1 高 在 y=1）
		fillRect(img, x + 6, y + 14, 4, 1, fg);           // 底齿（y=14）
		fillRect(img, x + 1, y + 6, 1, 4, fg);            // 左齿（1 宽 4 高 在 x=1）
		fillRect(img, x + 14, y + 6, 1, 4, fg);           // 右齿（x=14）

		// === 2) 4 斜齿（1×1，45°方向）===
		fillRect(img, x + 3, y + 3, 1, 1, fg);            // 左上
		fillRect(img, x + 12, y + 3, 1, 1, fg);           // 右上
		fillRect(img, x + 3, y + 12, 1, 1, fg);           // 左下
		fillRect(img, x + 12, y + 12, 1, 1, fg);          // 右下

		// === 3) 圆形主体（10×10 圆角，4 步阶梯）===
		// y=2..3 / y=12..13：主体顶/底过渡（6 宽中央）
		fillRect(img, x + 5, y + 2, 6, 2, fg);            // 顶过渡（col 5..10）
		fillRect(img, x + 5, y + 12, 6, 2, fg);           // 底过渡
		// y=4..5 / y=10..11：主体上下沿（8 宽）
		fillRect(img, x + 4, y + 4, 8, 2, fg);            // 上沿（col 4..11）
		fillRect(img, x + 4, y + 10, 8, 2, fg);           // 下沿
		// y=6..9：主体中段（10 宽 连接左右齿）
		fillRect(img, x + 3, y + 6, 10, 4, fg);           // 中段（col 3..12）

		// === 4) 中央 4×4 空心 hole ===
		clearRect(img, x + 6, y + 6, 4, 4);
	}

	/**
	 * <b>v20.8 经典返回箭头（重新设计形状）</b>。
	 *
	 * <p>v20.7 紫色三调被否决；原 v18-v20.6 的"3 行高箭杆 + 3 行高三角尖"看起来又扁又短，
	 * 用户反馈"原来的形状丑"。v20.8 颜色回归 ICON_FG #4D4D67，但形状改为标准的"长箭杆 +
	 * 7 行尖头三角"——三角尖逐行从 1px 到 7px 渐进（典型 vanilla 像素艺术箭头），
	 * 箭杆延伸到右边 col 13，整体居中且对称。</p>
	 *
	 * <h4>像素布局（16×16）</h4>
	 * <pre>
	 *   . . . . . . X . . . . . . . . .   y=4   尖最尖端 (col 6, 1 宽)
	 *   . . . . . X X . . . . . . . . .   y=5
	 *   . . . . X X X X X X X X X X . .   y=6   箭杆顶 (col 4..13)
	 *   . . . X X X X X X X X X X X . .   y=7
	 *   . . X X X X X X X X X X X X . .   y=8   最宽行（三角尖底 = 箭杆中）
	 *   . . . X X X X X X X X X X X . .   y=9
	 *   . . . . X X X X X X X X X X . .   y=10  箭杆底
	 *   . . . . . X X . . . . . . . . .   y=11
	 *   . . . . . . X . . . . . . . . .   y=12  尖最底端
	 * </pre>
	 *
	 * <p>三角尖 9 行（y=4..12）从 1 → 2 → 4 → 5 → 6 → 5 → 4 → 2 → 1 宽，形成"等腰锐三角"，
	 * 比原 5 行尖（高度 5）大幅扩大、视觉重心明确。箭杆 3 行高（y=6..10 居中），
	 * 横向延伸到 col 13，让"返回到主菜单"的方向感强烈清晰。</p>
	 */
	private static void drawIconBack(BufferedImage img, int x, int y) {
		Color fg = Palette.ICON_FG;

		// === 1) 三角尖（9 行渐进，y=4..12，最宽行 y=8 7 宽）===
		fillRect(img, x + 6, y + 4, 1, 1, fg);            // y=4: 1 宽
		fillRect(img, x + 5, y + 5, 2, 1, fg);            // y=5: 2 宽
		fillRect(img, x + 4, y + 6, 3, 1, fg);            // y=6: 3 宽（与箭杆顶连接）
		fillRect(img, x + 3, y + 7, 4, 1, fg);            // y=7: 4 宽
		fillRect(img, x + 2, y + 8, 5, 1, fg);            // y=8: 5 宽（最宽行 = 箭杆中线）
		fillRect(img, x + 3, y + 9, 4, 1, fg);            // y=9: 4 宽
		fillRect(img, x + 4, y + 10, 3, 1, fg);           // y=10: 3 宽（与箭杆底连接）
		fillRect(img, x + 5, y + 11, 2, 1, fg);           // y=11: 2 宽
		fillRect(img, x + 6, y + 12, 1, 1, fg);           // y=12: 1 宽

		// === 2) 箭杆（5 行高，y=6..10，从 col 7 延伸到 col 13）===
		fillRect(img, x + 7, y + 6, 7, 1, fg);            // y=6:  箭杆顶 7 宽
		fillRect(img, x + 7, y + 7, 7, 1, fg);            // y=7
		fillRect(img, x + 7, y + 8, 7, 1, fg);            // y=8:  箭杆中
		fillRect(img, x + 7, y + 9, 7, 1, fg);            // y=9
		fillRect(img, x + 7, y + 10, 7, 1, fg);           // y=10: 箭杆底 7 宽
	}


	/** 红色 X 禁用图标 (AE2-CS 真实红色调)。 */
	private static void drawIconDisable(BufferedImage img, int x, int y) {
		Color fg = Palette.ICON_RED;
		Color sh = Palette.ICON_RED_DARK;
		Color hi = Palette.ICON_RED_LIGHT;
		// 双对角线 (10 像素)
		for (int i = 0; i < 10; i++) {
			fillRect(img, x + 3 + i, y + 3 + i, 2, 2, fg);
			fillRect(img, x + 12 - i, y + 3 + i, 2, 2, fg);
		}
		// 中央阴影
		fillRect(img, x + 7, y + 7, 2, 2, sh);
		// 端点高光
		fillRect(img, x + 3, y + 3, 1, 1, hi);
		fillRect(img, x + 12, y + 3, 1, 1, hi);
	}

}
