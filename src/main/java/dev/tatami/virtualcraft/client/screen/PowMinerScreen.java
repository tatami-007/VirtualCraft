package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.menu.PowMinerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PowMinerScreen extends AbstractContainerScreen<PowMinerMenu> {
	private static final int BG = 0xFFF1F5FA;
	private static final int FRAME = 0xFFDCE6F1;
	private static final int PANEL = 0xFFF8FBFF;
	private static final int PANEL_ALT = 0xFFEAF1F8;
	private static final int BORDER = 0xFFB4C3D4;
	private static final int TEXT = 0xFF223347;
	private static final int TEXT_MUTED = 0xFF617284;
	private static final int TEXT_ACCENT = 0xFF1F996F;

	public PowMinerScreen(PowMinerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 196;
		imageHeight = 216;
		titleLabelX = 18;
		titleLabelY = 16;
		inventoryLabelX = 18;
		inventoryLabelY = 98;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
		guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, FRAME);
		drawPanel(guiGraphics, left + 16, top + 32, 164, 58, PANEL);
		drawPanel(guiGraphics, left + 16, top + 106, 164, 92, PANEL_ALT);
		guiGraphics.fill(left + 17, top + 33, left + 179, top + 35, 0xFF7AD0E2);
		guiGraphics.fill(left + 17, top + 107, left + 179, top + 109, 0xFFA4B8CA);

		drawSlot(guiGraphics, left + 133, top + 61);
		guiGraphics.fill(left + 42, top + 62, left + 88, top + 66, 0xFFD3DCE8);
		int progressPixels = menu.getScaledProgress();
		guiGraphics.fill(left + 42, top + 62, left + 42 + progressPixels, top + 66, 0xFF58B8FF);
		guiGraphics.fill(left + 90, top + 48, left + 98, top + 82, 0xFFD8E1EB);
		int energyPixels = menu.getMaxEnergyStored() == 0 ? 0 : (int) ((long) menu.getEnergyStored() * 32 / menu.getMaxEnergyStored());
		guiGraphics.fill(left + 91, top + 81 - energyPixels, left + 97, top + 81, 0xFF63D19E);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				drawSlot(guiGraphics, left + 18 + column * 18, top + 114 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			drawSlot(guiGraphics, left + 18 + column * 18, top + 172);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
		guiGraphics.drawString(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), 18, 86, TEXT_MUTED, false);
		guiGraphics.drawString(font, Component.translatable("screen.virtual_craft.pow_miner.status"), 18, 48, TEXT_ACCENT, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (mouseX >= leftPos + 90 && mouseX <= leftPos + 98 && mouseY >= topPos + 48 && mouseY <= topPos + 82) {
			guiGraphics.renderTooltip(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), mouseX, mouseY);
		}
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill) {
		guiGraphics.fill(x, y, x + width, y + height, BORDER);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
	}

	private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFB4C3D4);
		guiGraphics.fill(x, y, x + 16, y + 16, 0xFFF5F8FC);
	}
}
