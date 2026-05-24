package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.client.screen.widget.IconButton;
import dev.tatami.virtualcraft.client.screen.widget.SideFaceButton;
import dev.tatami.virtualcraft.machine.SideMode;
import dev.tatami.virtualcraft.menu.SideConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>v16 通用面配置 Screen — 严格 1:1 复刻 AE2-CS</b>
 *
 * <h3>布局（参考 ae2cs.jar/assets/ae2/screens/side_config_menu.json 真实坐标）</h3>
 * <p>AE2-CS 真实 sub-window srcRect 是 96×101，6 面按钮 cube unfold 布局。
 * 我们扩展到 <b>120×101</b> — 右侧 24 px 容纳 3 个工具按钮 + 返回按钮。</p>
 *
 * <pre>
 *   imageWidth=120, imageHeight=101
 *   title @ (8, 6)
 *   返回按钮 @ (102, 4)  18×18  ICON_BACK_U
 *
 *   6 面按钮 cube unfold（AE2-CS 真坐标，含 NORTH/SOUTH 等 cube 折叠风）：
 *     TOP        (39, 20)
 *     LEFT(18,42) FRONT(39,42) RIGHT(60,42)
 *     BACK(18,64) BOTTOM(39,64)
 *
 *   3 工具按钮（保留 Clear，删除 ? 帮助）：
 *     AUTO_IMPORT (96, 20)  ICON_INPUT_U
 *     AUTO_EXPORT (96, 42)  ICON_OUTPUT_U
 *     CLEAR_ALL   (96, 64)  ICON_DISABLE_U
 * </pre>
 *
 * <h3>颜色语义（AE2-CS guide side_config.md）</h3>
 * <ul>
 *   <li>Red    = Input / Insert only</li>
 *   <li>Blue   = Output / Extract only</li>
 *   <li>Purple = Input + Output</li>
 *   <li>Gray   = Disabled</li>
 * </ul>
 *
 * <p><b>无玩家背包</b>：inventoryLabelY 设为 -9999 让 vanilla 不画背包标题。</p>
 */
public class SideConfigScreen extends AbstractContainerScreen<SideConfigMenu> {

	private static final ResourceLocation BG = MachineAssets.bgTexture("side_config_menu");

	private final Map<Integer, SideFaceButton> sideButtons = new LinkedHashMap<>();
	private IconButton autoImportButton;
	private IconButton autoExportButton;

	public SideConfigScreen(SideConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 120;
		imageHeight = 107;  // v18 从 101 增到 107，容纳 INPUT/OUTPUT/CLEAR 不与 BACK 重叠
		titleLabelX = 8;
		titleLabelY = 6;
		// 无玩家背包，设为负值让 vanilla 不画背包标题
		inventoryLabelX = -9999;
		inventoryLabelY = -9999;
	}

	@Override
	protected void init() {
		super.init();
		sideButtons.clear();

		// === 6 面按钮 cube unfold（严格按 AE2-CS json 真坐标） ===
		addSideButton(SideConfigMenu.TOP_BUTTON,    leftPos + 39, topPos + 20, "screen.virtual_craft.side_config.face.top");
		addSideButton(SideConfigMenu.LEFT_BUTTON,   leftPos + 18, topPos + 42, "screen.virtual_craft.side_config.face.left");
		addSideButton(SideConfigMenu.FRONT_BUTTON,  leftPos + 39, topPos + 42, "screen.virtual_craft.side_config.face.front");
		addSideButton(SideConfigMenu.RIGHT_BUTTON,  leftPos + 60, topPos + 42, "screen.virtual_craft.side_config.face.right");
		addSideButton(SideConfigMenu.BACK_BUTTON,   leftPos + 18, topPos + 64, "screen.virtual_craft.side_config.face.back");
		addSideButton(SideConfigMenu.BOTTOM_BUTTON, leftPos + 39, topPos + 64, "screen.virtual_craft.side_config.face.bottom");

		// === 右侧 3 个工具按钮 + 返回按钮：统一用 AE2-CS 18×20 button atlas ===
		autoImportButton = addRenderableWidget(new IconButton(leftPos + 96, topPos + 26, MachineAssets.ICON_INPUT_U,
				Component.translatable("screen.virtual_craft.side_config.auto_import"),
				button -> sendButton(SideConfigMenu.AUTO_IMPORT_BUTTON))
				.setHighlightedFrame(MachineAssets.ICON_BUTTON_NORMAL_U, MachineAssets.ICON_BUTTON_LIGHT_V));
		autoExportButton = addRenderableWidget(new IconButton(leftPos + 96, topPos + 48, MachineAssets.ICON_OUTPUT_U,
				Component.translatable("screen.virtual_craft.side_config.auto_export"),
				button -> sendButton(SideConfigMenu.AUTO_EXPORT_BUTTON))
				.setHighlightedFrame(MachineAssets.ICON_BUTTON_NORMAL_U, MachineAssets.ICON_BUTTON_LIGHT_V));
		addRenderableWidget(new IconButton(leftPos + 96, topPos + 70, MachineAssets.ICON_DISABLE_U,
				Component.translatable("screen.virtual_craft.side_config.clear_all"),
				button -> sendButton(SideConfigMenu.CLEAR_ALL_BUTTON)));

		// === 右上返回按钮 IconButton 18×18 ===
		addRenderableWidget(new IconButton(leftPos + 96, topPos + 4, MachineAssets.ICON_BACK_U,
				Component.translatable("screen.virtual_craft.machine.back"),
				button -> sendButton(SideConfigMenu.BACK_TO_MAIN_BUTTON)));
	}

	private void addSideButton(int id, int x, int y, String tooltipKey) {
		SideFaceButton button = new SideFaceButton(x, y, Component.translatable(tooltipKey),
				b -> sendButton(id));
		addRenderableWidget(button);
		sideButtons.put(id, button);
	}

	private void sendButton(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		// 1) 一键 blit 主背景 — v16 通用白板 side_config_menu.png（120×107 凹陷窗体，机器无关）
		MachineDraw.drawBackground(guiGraphics, leftPos, topPos, imageWidth, imageHeight, BG);

		// v19 6 面按钮 drawSlot 凹陷底已删除 — AE2-CS BUTTON sprite 18×20 自带凹陷立体边，
		// 不再需要单独画 slot 凹陷否则会与 sprite 边框重叠导致视觉污染。
		// 右侧工具按钮现在也直接画 AE2-CS button atlas，不再额外垫 vanilla 槽位。

		// 3) 实时同步方向按钮的 mode（每帧从 menu data 拉取）
		for (Map.Entry<Integer, SideFaceButton> entry : sideButtons.entrySet()) {
			SideMode mode = menu.getMode(entry.getKey());
			entry.getValue().setMode(mode);
		}

		// 4) 工具按钮高亮：AUTO_IMPORT/AUTO_EXPORT 当前 toggle 状态
		if (autoImportButton != null) {
			autoImportButton.setHighlighted(menu.isAutoImportEnabled());
		}
		if (autoExportButton != null) {
			autoExportButton.setHighlighted(menu.isAutoExportEnabled());
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// 仅画顶部机器名（title 来自 BE.createMainMenuProvider 或 SideConfigMenu.openFor）
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		// 不调 renderTooltip：super.render() 末尾已自动调过，再调会重复画按钮 tooltip（重叠 bug）。
	}
}
