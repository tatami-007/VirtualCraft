package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.client.screen.widget.IconButton;
import dev.tatami.virtualcraft.menu.ElectricBallGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * AE2 Crystal Science 公模 176×166 极简版电球发电机：
 * <ul>
 *   <li>主背景 blit electric_ball_generator.png（含窗体+槽位+能量条+燃烧条 track）</li>
 *   <li>v20.15 右侧改成垂直能量条 16×52 (用户反馈：不要看起来像物品槽) — 能量语义明确</li>
 *   <li>中间橙色火焰进度箭头 (77, 40, 22×16)</li>
 *   <li>FUEL 槽 (47, 39) + ENERGY 能量缓存条 (112, 22, 16×52) — 流水线布局，中点 X 各离火焰中点 32px</li>
 *   <li>文字仅机器名 + 玩家背包标题；其余 hover tooltip</li>
 * </ul>
 */
public class ElectricBallGeneratorScreen extends AbstractContainerScreen<ElectricBallGeneratorMenu> {

	private static final ResourceLocation BG = MachineAssets.bgTexture("electric_ball_generator");
	private static final int SIDE_CONFIG_BUTTON_ID = 120;

	public ElectricBallGeneratorScreen(ElectricBallGeneratorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 178;
		titleLabelX = 8;
		titleLabelY = 6;
		inventoryLabelX = 8;
		inventoryLabelY = 84;  // v16 vanilla 标准 = imageHeight - 94 = 178-94=84
	}

	@Override
	protected void init() {
		super.init();
		MouseTransitionGuard.restoreIfRecent();
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
		// 1) 一键 blit 主背景
		MachineDraw.drawBackground(guiGraphics, left, top, imageWidth, imageHeight, BG);
		// 2) 中间橙色火焰进度箭头 22×16 @ (77, 40) — 复用 drawProgressArrow API（与 ElectricCondenser 同款）
		double burnFraction = menu.getBurnTimeTotal() == 0 ? 0.0D : (double) menu.getBurnTimeRemaining() / (double) menu.getBurnTimeTotal();
		MachineDraw.drawProgressArrow(guiGraphics, left + 77, top + 40, burnFraction, BG, 180, 0);
		// 3) 右侧垂直能量缓存条 16×52 @ (112, 22) — 中点 (120, 48)、与 FUEL 中点 (56, 48) 关于 ARROW 中轴 X=88 对称
		//   v20.15 用户反馈："右侧输出槽是能量槽，不是物品槽" — 从 18×18 槽内嵌 16×16 能量条
		//   改为 16×52 独立能量条（与原左侧同款 MCPP 风），视觉上明显是“能量缓存”而不是“物品槽”。
		double energyFraction = menu.getMaxEnergyStored() == 0 ? 0.0D : (double) menu.getEnergyStored() / (double) menu.getMaxEnergyStored();
		MachineDraw.drawVerticalBar(guiGraphics, left + 112, top + 22, 16, 52, energyFraction, MachinePalette.RED, MachinePalette.RED);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachinePalette.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		// v17 vanilla 1.21.1 不自动调 renderTooltip，必须显式调用才能看到槽位物品 tooltip。
		renderTooltip(guiGraphics, mouseX, mouseY);
		// 追加能量条/燃烧条的自定义 tooltip，仅 hoveredSlot==null 时画。
		renderCustomTooltip(guiGraphics, mouseX, mouseY);
	}

	/**
	 * 仅在鼠标不悬停于任何槽位时画自定义 tooltip，避免与 vanilla 槽位 tooltip 同帧重叠。
	 * 原本为“燃料槽 / 装饰槽”手动画的 tooltip 已删除 — 它们都是 vanilla Slot，
	 * vanilla 会自动渲染上面物品的 tooltip，手动画会重叠。
	 */
	private void renderCustomTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// 悬停于槽位时让 vanilla 优先渲染该槽位的 ItemStack tooltip
		if (hoveredSlot != null) {
			return;
		}
		// v20.15 能量 hover 仅保留在右侧 (112, 22, 16×52)
		// 中间火焰进度箭头 hover (77, 40, 22×16 v20.13)
		if (MachineDraw.contains(mouseX, mouseY, leftPos + 77, topPos + 40, 22, 16)) {
			boolean burning = menu.getBurnTimeRemaining() > 0;
			guiGraphics.renderComponentTooltip(font, List.of(
					Component.translatable(burning ? "screen.virtual_craft.machine.running" : "screen.virtual_craft.machine.idle"),
					Component.translatable("screen.virtual_craft.machine.progress"),
					Component.literal(menu.getBurnTimeRemaining() + " / " + menu.getBurnTimeTotal() + " tick")
			), mouseX, mouseY);
			return;
		}
		// 右侧垂直能量缓存条 hover (112, 22, 16×52 v20.15)
		if (MachineDraw.contains(mouseX, mouseY, leftPos + 112, topPos + 22, 16, 52)) {
			guiGraphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()),
					Component.literal(menu.getEnergyStored() + " / " + menu.getMaxEnergyStored() + " FE")
			), mouseX, mouseY);
		}
	}

}
