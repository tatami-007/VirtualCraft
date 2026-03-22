package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.menu.PowMinerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PowMinerScreen extends AbstractContainerScreen<PowMinerMenu> {
	public PowMinerScreen(PowMinerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 196;
		imageHeight = 216;
		titleLabelX = 18;
		titleLabelY = 12;
		inventoryLabelX = 18;
		inventoryLabelY = 98;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		MachineUi.drawWindow(guiGraphics, left, top, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 40, 112, 54, false);
		MachineUi.drawPanel(guiGraphics, left + 134, top + 40, 46, 54, true);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 106, 164, 92, true);

		MachineUi.drawSlot(guiGraphics, left + 133, top + 61);
		MachineUi.drawHorizontalBar(guiGraphics, left + 42, top + 61, 46, 6, menu.getScaledProgress() / 44.0D, MachineUi.CYAN, 0xFF9BEFFF);
		double energyFraction = menu.getMaxEnergyStored() == 0 ? 0.0D : (double) menu.getEnergyStored() / (double) menu.getMaxEnergyStored();
		MachineUi.drawVerticalBar(guiGraphics, left + 92, top + 48, 8, 36, energyFraction, MachineUi.GREEN, MachineUi.CYAN);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				MachineUi.drawSlot(guiGraphics, left + 18 + column * 18, top + 114 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			MachineUi.drawSlot(guiGraphics, left + 18 + column * 18, top + 172);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachineUi.TEXT_PRIMARY, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachineUi.TEXT_PRIMARY, false);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()).getString(), 18, 94, 164, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.pow_miner.status").getString(), 18, 48, 90, MachineUi.TEXT_ACCENT);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.machine.voucher_output").getString(), 104, 48, 74, MachineUi.TEXT_MUTED);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (mouseX >= leftPos + 92 && mouseX <= leftPos + 100 && mouseY >= topPos + 48 && mouseY <= topPos + 84) {
			guiGraphics.renderTooltip(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), mouseX, mouseY);
		}
		renderTooltip(guiGraphics, mouseX, mouseY);
	}
}
