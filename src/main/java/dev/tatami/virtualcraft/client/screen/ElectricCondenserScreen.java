package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.client.screen.widget.FlatButton;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ElectricCondenserScreen extends AbstractContainerScreen<ElectricCondenserMenu> {
	private static final int SIDE_CONFIG_BUTTON_ID = 120;
	private Button sideConfigButton;

	public ElectricCondenserScreen(ElectricCondenserMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 220;
		imageHeight = 242;
		titleLabelX = 20;
		titleLabelY = 12;
		inventoryLabelX = 20;
		inventoryLabelY = 118;
	}

	@Override
	protected void init() {
		super.init();
		sideConfigButton = addRenderableWidget(new FlatButton(leftPos + 148, topPos + 12, 54, 18,
				Component.translatable("screen.virtual_craft.electric_condenser_side_config"),
				button -> {
					if (minecraft != null && minecraft.gameMode != null) {
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SIDE_CONFIG_BUTTON_ID);
					}
				}));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		MachineUi.drawWindow(guiGraphics, left, top, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, left + 18, top + 40, 122, 76, false);
		MachineUi.drawPanel(guiGraphics, left + 148, top + 40, 54, 76, true);
		MachineUi.drawPanel(guiGraphics, left + 18, top + 128, 184, 94, true);

		MachineUi.drawSlot(guiGraphics, left + 38, top + 44);
		MachineUi.drawSlot(guiGraphics, left + 38, top + 68);
		MachineUi.drawSlot(guiGraphics, left + 116, top + 56);
		MachineUi.drawSlot(guiGraphics, left + 166, top + 40);
		MachineUi.drawSlot(guiGraphics, left + 166, top + 62);
		MachineUi.drawSlot(guiGraphics, left + 166, top + 84);

		MachineUi.drawHorizontalBar(guiGraphics, left + 70, top + 61, 38, 6, menu.getScaledProgress() / 28.0D, MachineUi.CYAN, 0xFF9BEFFF);
		double energyFraction = menu.getMaxEnergyStored() == 0 ? 0.0D : (double) menu.getEnergyStored() / (double) menu.getMaxEnergyStored();
		MachineUi.drawVerticalBar(guiGraphics, left + 152, top + 42, 8, 54, energyFraction, MachineUi.GREEN, MachineUi.CYAN);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				MachineUi.drawSlot(guiGraphics, left + 20 + column * 18, top + 136 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			MachineUi.drawSlot(guiGraphics, left + 20 + column * 18, top + 194);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachineUi.TEXT_PRIMARY, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachineUi.TEXT_PRIMARY, false);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.machine.input").getString(), 38, 30, 36, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.machine.catalyst").getString(), 34, 94, 50, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.machine.output").getString(), 104, 30, 42, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()).getString(), 20, 106, 182, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.electric_condenser.upgrades", menu.getInstalledSpeedCards()).getString(), 20, 118, 182, MachineUi.TEXT_SUCCESS);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (sideConfigButton != null && sideConfigButton.isHoveredOrFocused()) {
			guiGraphics.renderTooltip(font, Component.translatable("tooltip.virtual_craft.open_side_config"), mouseX, mouseY);
		}
		if (mouseX >= leftPos + 152 && mouseX <= leftPos + 160 && mouseY >= topPos + 42 && mouseY <= topPos + 96) {
			guiGraphics.renderTooltip(font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()), mouseX, mouseY);
		}
		renderTooltip(guiGraphics, mouseX, mouseY);
	}
}
