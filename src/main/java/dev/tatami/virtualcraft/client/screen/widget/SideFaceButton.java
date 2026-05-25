package dev.tatami.virtualcraft.client.screen.widget;

import dev.tatami.virtualcraft.client.screen.MachineAssets;
import dev.tatami.virtualcraft.machine.SideMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 18×20 面配置按钮 — <b>v20.7 atlas 搬到 icons.png</b>。
 *
 * <h3>历史</h3>
 * <ul>
 *   <li><b>v18</b>：{@code GuiGraphics.fill} 画 16×16 纯色块 + 1px 暗边 — "没按压感、偏色"</li>
 *   <li><b>v19</b>：改用 {@link GuiGraphics#blit} 从 {@code side_config_menu.png} 直接 blit AE2-CS
 *       官方 icons.png 的 BUTTON_*_DARK 18×20 sprite，纹理 1:1 拷贝零调色失真</li>
 *   <li><b>v20.7</b>：BUTTON atlas 从 side_config_menu.png 搬到公用 icons.png
 *       —— 按钮 sprite 本质是公用 widget，不该住在某个特定 GUI 的主背景里。
 *       atlas 在 icons.png 内的 (0, 130) 坐标与原 side_config_menu.png 完全一致，
 *       本类只改 {@code BG → ICONS_TEXTURE}，sprite 坐标常量纹丝不变</li>
 * </ul>
 *
 * <h3>sprite atlas 在 icons.png 的布局（见 {@code GuiTextureGenerator.generateIconsSheet}）</h3>
 * <p>atlas 起点 (0, 130)，DARK 行 v=130：</p>
 * <pre>
 *   DARK 行 (v=130) — 4 mode normal + 4 mode hover：
 *      u=0   BOTH    (PURPLE_DARK)
 *      u=18  OUTPUT  (BLUE_DARK)
 *      u=36  INPUT   (RED_DARK)
 *      u=54  NONE    (ORIGINAL_DARK)
 *      u=72  BOTH_HOVER
 *      u=90  OUTPUT_HOVER
 *      u=108 INPUT_HOVER
 *      u=126 NONE_HOVER
 * </pre>
 *
 * <h3>颜色 → mode 映射（与 AE2-CS {@code AECSSideConfigToggleButton.backgroundForPolicy} 一致）</h3>
 * <ul>
 *   <li><b>PURPLE</b> = BOTH      (双向 I/O)</li>
 *   <li><b>RED</b>    = INPUT     (INSERT / 流入)</li>
 *   <li><b>BLUE</b>   = OUTPUT    (EXTRACT / 流出)</li>
 *   <li><b>ORIGINAL</b> (gray) = NONE / Disabled</li>
 * </ul>
 *
 * <p>sprite 18×20 包含按下立体阴影边和高光梯度，hover 系列额外亮 1 阶 — 视觉效果由
 * AE2-CS 原作者操刀，不画 1px 黄色 hover 边框。</p>
 */
@SuppressWarnings("this-escape") // setTooltip() in the constructor is the standard vanilla widget pattern.
public class SideFaceButton extends Button {
	private static final int CONTENT_Y_OFFSET = -1;

	/** sprite 宽度 — 与 AE2-CS BUTTON sprite 一致 18 px。 */
	public static final int WIDTH = 18;
	/** sprite 高度 — 与 AE2-CS BUTTON sprite 一致 20 px（含按下立体阴影边）。 */
	public static final int HEIGHT = 20;

	/** icons.png 中 DARK 行 atlas 起点 y（含 normal + hover 共 8 sprite，144 px 宽）。 */
	private static final int ATLAS_DARK_V = 130;
	/** atlas 中每行 normal 块与 hover 块的 u 偏移 = 4 mode × 18 px = 72。 */
	private static final int ATLAS_HOVER_U_OFFSET = 72;
	/** icons.png 纹理总尺寸（GuiTextureGenerator 输出 256×256）。 */
	private static final int TEX_SIZE = 256;

	private SideMode mode = SideMode.NONE;
	private final Component faceName;
	private ItemStack adjacentStack = ItemStack.EMPTY;

	public SideFaceButton(int x, int y, Component tooltipText, OnPress onPress) {
		super(x, y, WIDTH, HEIGHT, Component.empty(), onPress, DEFAULT_NARRATION);
		this.faceName = tooltipText;
		updateTooltip();
	}

	public void setMode(SideMode mode) {
		if (this.mode == mode) {
			return;
		}
		this.mode = mode;
		updateTooltip();
	}

	public SideMode getMode() {
		return mode;
	}

	public void setAdjacentStack(ItemStack adjacentStack) {
		ItemStack next = adjacentStack == null ? ItemStack.EMPTY : adjacentStack;
		if (ItemStack.isSameItemSameComponents(this.adjacentStack, next)) {
			return;
		}
		this.adjacentStack = next;
		updateTooltip();
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();

		// 选 sprite：mode 决定 u（atlas DARK 行内 4 列），hover 决定 +72 偏移到 hover 半区
		int u = modeAtlasU(mode);
		if (isHoveredOrFocused()) {
			u += ATLAS_HOVER_U_OFFSET;
		}
		// partial blit AE2-CS 真实按压立体 sprite（v20.7：atlas 从 side_config_menu.png 搬到 icons.png）
		int hoverOffset = isHoveredOrFocused() ? 1 : 0;
		guiGraphics.blit(MachineAssets.ICONS_TEXTURE, x - 1, y + hoverOffset, u, ATLAS_DARK_V, WIDTH, HEIGHT, TEX_SIZE, TEX_SIZE);
		if (!adjacentStack.isEmpty()) {
			int iconX = x - 1 + (WIDTH - 16) / 2;
			int iconY = y + hoverOffset + (HEIGHT - 16) / 2 + CONTENT_Y_OFFSET;
			guiGraphics.renderFakeItem(adjacentStack, iconX, iconY);
		}
	}

	private void updateTooltip() {
		Component adjacent = adjacentStack.isEmpty()
				? Component.translatable("tooltip.virtual_craft.side_config.adjacent.none")
				: adjacentStack.getHoverName();
		setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.translatable("tooltip.virtual_craft.side_config.button", faceName, mode.displayName(), adjacent)));
	}

	/** mode → atlas 内的 u 偏移（DARK 行内 4 列 normal sprite）。 */
	private static int modeAtlasU(SideMode mode) {
		return switch (mode) {
			case BOTH   -> 0;   // PURPLE_DARK   双向
			case OUTPUT -> 18;  // BLUE_DARK     EXTRACT / 流出
			case INPUT  -> 36;  // RED_DARK      INSERT / 流入
			case NONE   -> 54;  // ORIGINAL_DARK 无 I/O
		};
	}
}
