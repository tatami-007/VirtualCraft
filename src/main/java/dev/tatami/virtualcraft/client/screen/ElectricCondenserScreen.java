package dev.tatami.virtualcraft.client.screen;

import java.util.List;

import dev.tatami.virtualcraft.client.screen.widget.IconButton;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * <b>v20.2 电凝聚器 GUI — 左大能量条 + 中间横向单行槽位 + 右升级槽组</b>
 *
 * <h3>布局（imageWidth=176, imageHeight=178）— 以中轴 X=88 严格对称</h3>
 * <ul>
 *   <li>标题 @ (8, 6) + 齿轮侧面配置按钮 @ (152, 4) 18×18 IconButton ICON_GEAR_U</li>
 *   <li>能量条 @ (8, 25) 16×52 — 左侧最外 (离中轴 72)</li>
 *   <li>INPUT 槽组：INGOT (47, 31) + BALL (47, 53) 两格间 gap=4 分开</li>
 *   <li>进度箭头 @ (77, 43) 22×16 — 中点 (88, 51) 正中（空状态纯浅紫灰平面）</li>
 *   <li>OUTPUT 槽 @ (111, 42) 18×18 — 中点 (120, 51)</li>
 *   <li>UPGRADE 3 槽 @ (152, 24) (152, 42) (152, 60) — 组中点 (160, 51)，右侧最外</li>
 *   <li>玩家背包标题 y=84，背包 y=96..150，Hotbar y=154..170</li>
 * </ul>
 */
public class ElectricCondenserScreen extends AbstractContainerScreen<ElectricCondenserMenu> {
	private static final ResourceLocation BG = MachineAssets.bgTexture("electric_condenser");
	private static final int SIDE_CONFIG_BUTTON_ID = 120;

	// v20.13 widget 整体上移 2px 让 ENERGY 上下边距接近对称（用户反馈："电凝聚器对齐了吗"）
	private static final int ARROW_X = 77, ARROW_Y = 41;           // v20.13 43→41，中点 (88, 49)
	private static final int ARROW_W = 22, ARROW_H = 16;
	private static final int ENERGY_X = 8,  ENERGY_Y = 23;         // v20.13 25→23，中点 (16, 49)，占 23..75 上 8/下 7 ✓
	private static final int ENERGY_W = 16, ENERGY_H = 52;
	// UPGRADE 3 槽 @ (152, 24/42/60)，组中点 (160, 51) — 离中轴 72
	// 与 ElectricCondenserMenu.UPGRADE_*_X/Y + GuiTextureGenerator drawSlotPanel(152, 24, 1, 3) 严格对齐。
	private static final int UPGRADE_GROUP_X = 152, UPGRADE_GROUP_Y = 28;
	private static final int UPGRADE_GROUP_W = 16, UPGRADE_GROUP_H = 54; // 3 槽 × 18 = 54

	public ElectricCondenserScreen(ElectricCondenserMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 178;
		titleLabelX = 8;
		titleLabelY = 6;
		inventoryLabelX = 8;
		inventoryLabelY = 84;
	}

	@Override
	protected void init() {
		super.init();
		MouseTransitionGuard.restoreIfRecent();
		// v20.2 齿轮侧面配置按钮 @ (152, 4) 18×18 IconButton
		// 位置选择：UPGRADE 列从 137 → 152（往外偏），齿轮同步右移跟随 UPGRADE 列。
		// 与 UPGRADE 在 X 完全重叠、Y 不重（4-22 vs 24-78），视觉上齿轮是 UPGRADE 列的“帽”。
		addRenderableWidget(new IconButton(leftPos + 152, topPos + 4, MachineAssets.ICON_GEAR_U,
				Component.translatable("screen.virtual_craft.side_config.title"),
				button -> {
					if (minecraft != null && minecraft.gameMode != null) {
						MouseTransitionGuard.capture();
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SIDE_CONFIG_BUTTON_ID);
					}
				}));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		MachineDraw.drawBackground(guiGraphics, left, top, imageWidth, imageHeight, BG);
		// 进度箭头动态填充（v16：sprite 在本机器主背景 (180, 0) 22×16 区域）
		MachineDraw.drawProgressArrow(guiGraphics, left + ARROW_X, top + ARROW_Y,
				menu.getScaledProgress() / (double) ARROW_W, BG, 180, 0);
		// 能量条动态填充（红色 FE 风格 — 6×18 AE2-CS 真实尺寸）
		double energyFraction = menu.getMaxEnergyStored() == 0 ? 0.0D : (double) menu.getEnergyStored() / (double) menu.getMaxEnergyStored();
		MachineDraw.drawVerticalBar(guiGraphics, left + ENERGY_X, top + ENERGY_Y, ENERGY_W, ENERGY_H, energyFraction, MachinePalette.RED, MachinePalette.RED);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// AE2 极简：只画机器名 + 玩家背包标题
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachinePalette.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		// v17 关键修复：vanilla 1.21.1 AbstractContainerScreen.render() 不会自动调 renderTooltip，
		// hovered slot 物品 tooltip 必须由 subclass 显式调用。
		renderTooltip(guiGraphics, mouseX, mouseY);
		// 追加自定义区域 tooltip（能量条/进度条/升级组），仅当 hoveredSlot==null 时画。
		renderHoverTooltips(guiGraphics, mouseX, mouseY);
	}

	/**
	 * AE2 风格 hover tooltip：把全部状态信息塞到鼠标悬停弹窗，让 GUI 主体保持极简。
	 * <p><b>不与槽位/按钮 tooltip 冲突</b>：hoveredSlot 不为 null 时让 vanilla 优先渲染槽位 tooltip。
	 * v16 齿轮 IconButton 由 vanilla 自动渲染 tooltip（setTooltip() 在 IconButton 构造时已调），
	 * 本方法不再手动画其 tooltip。</p>
	 */
	private void renderHoverTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// 悬停于任何 slot 时让 vanilla 优先渲染该槽位的 ItemStack tooltip
		if (hoveredSlot != null) {
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, ENERGY_W, ENERGY_H)) {
			guiGraphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.energy", menu.getEnergyStored(), menu.getMaxEnergyStored())),
					mouseX, mouseY);
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + ARROW_X, topPos + ARROW_Y, ARROW_W, ARROW_H)) {
			guiGraphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.progress")),
					mouseX, mouseY);
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + UPGRADE_GROUP_X, topPos + UPGRADE_GROUP_Y, UPGRADE_GROUP_W, UPGRADE_GROUP_H)) {
			guiGraphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.cards_short"),
					Component.translatable("screen.virtual_craft.machine.cards", menu.getInstalledSpeedCards())),
					mouseX, mouseY);
		}
	}

}
