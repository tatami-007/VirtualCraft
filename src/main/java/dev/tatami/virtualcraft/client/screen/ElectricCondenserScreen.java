package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.client.screen.widget.FlatButton;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ElectricCondenserScreen extends AbstractContainerScreen<ElectricCondenserMenu> {
	private static final int BG = 0xFFF1F5FA;
	private static final int FRAME = 0xFFDCE6F1;
	private static final int PANEL = 0xFFF8FBFF;
	private static final int PANEL_ALT = 0xFFEAF1F8;
	private static final int BORDER = 0xFFB4C3D4;
	private static final int TEXT = 0xFF233348;
	private static final int TEXT_MUTED = 0xFF617284;
	private static final int TEXT_ACCENT = 0xFF1F996F;
	private static final int SIDE_CONFIG_BUTTON_ID = 120;
	private Button sideConfigButton;

	public ElectricCondenserScreen(ElectricCondenserMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 220;
		imageHeight = 242;
		titleLabelX = 20;
		titleLabelY = 16;
		inventoryLabelX = 20;
		inventoryLabelY = 118;
	}

	@Override
	protected void init() {
		super.init();
		sideConfigButton = addRenderableWidget(new FlatButton(leftPos + 166, topPos + 14, 34, 18, Component.literal("CFG"), button -> {
			if (minecraft != null && minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SIDE_CONFIG_BUTTON_ID);
			}
		}));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
		guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, FRAME);
		drawPanel(guiGraphics, left + 18, top + 34, 122, 76, PANEL);
		drawPanel(guiGraphics, left + 148, top + 34, 54, 76, PANEL);
		drawPanel(guiGraphics, left + 18, top + 128, 184, 94, PANEL_ALT);
		guiGraphics.fill(left + 19, top + 35, left + 139, top + 37, 0xFF6FD9EB);
		guiGraphics.fill(left + 149, top + 35, left + 201, top + 37, 0xFF74D4A7);

		drawSlotFrame(guiGraphics, left + 38, top + 44);
		drawSlotFrame(guiGraphics, left + 38, top + 68);
		drawSlotFrame(guiGraphics, left + 116, top + 56);
		drawSlotFrame(guiGraphics, left + 166, top + 40);
		drawSlotFrame(guiGraphics, left + 166, top + 62);
		drawSlotFrame(guiGraphics, left + 166, top + 84);

		guiGraphics.fill(left + 70, top + 61, left + 104, top + 65, 0xFFD3DCE8);
		int progress = menu.getScaledProgress();
		guiGraphics.fill(left + 70, top + 61, left + 70 + progress, top + 65, 0xFF58B8FF);
		guiGraphics.fill(left + 102, top + 56, left + 108, top + 70, 0xFF58B8FF);

		int energyBarHeight = menu.getMaxEnergyStored() == 0 ? 0 : (int) ((long) menu.getEnergyStored() * 54 / menu.getMaxEnergyStored());
		guiGraphics.fill(left + 150, top + 42, left + 158, top + 96, 0xFFD8E1EB);
		guiGraphics.fill(left + 151, top + 95 - energyBarHeight, left + 157, top + 95, 0xFF63D19E);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				drawInventorySlot(guiGraphics, left + 20 + column * 18, top + 136 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			drawInventorySlot(guiGraphics, left + 20 + column * 18, top + 194);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
		guiGraphics.drawString(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), 20, 106, TEXT_MUTED, false);
		guiGraphics.drawString(font, Component.translatable("screen.virtual_craft.electric_condenser.upgrades", menu.getInstalledSpeedCards()), 20, 118, TEXT_ACCENT, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (sideConfigButton != null && sideConfigButton.isHoveredOrFocused()) {
			guiGraphics.renderTooltip(font, Component.translatable("tooltip.virtual_craft.open_side_config"), mouseX, mouseY);
		}
		if (mouseX >= leftPos + 150 && mouseX <= leftPos + 158 && mouseY >= topPos + 42 && mouseY <= topPos + 96) {
			guiGraphics.renderTooltip(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), mouseX, mouseY);
		}
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill) {
		guiGraphics.fill(x, y, x + width, y + height, BORDER);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
	}

	private void drawSlotFrame(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF9FB2C4);
		guiGraphics.fill(x, y, x + 16, y + 16, 0xFFF4F8FC);
	}

	private void drawInventorySlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFB4C3D4);
		guiGraphics.fill(x, y, x + 16, y + 16, 0xFFF5F8FC);
	}
}
