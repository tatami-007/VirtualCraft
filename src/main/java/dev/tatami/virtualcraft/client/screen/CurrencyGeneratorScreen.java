package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.client.screen.widget.FlatButton;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.menu.CurrencyGeneratorMenu;
import dev.tatami.virtualcraft.network.GeneratorActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CurrencyGeneratorScreen extends AbstractContainerScreen<CurrencyGeneratorMenu> {
	private String ownerName = "";
	private Button bindButton;
	private Button modeButton;

	public CurrencyGeneratorScreen(CurrencyGeneratorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 304;
		imageHeight = 236;
		titleLabelX = 18;
		titleLabelY = 12;
		inventoryLabelX = 24;
		inventoryLabelY = 132;
	}

	@Override
	protected void init() {
		super.init();
		bindButton = addRenderableWidget(new FlatButton(leftPos + 184, topPos + 58, 102, 20,
				Component.translatable("screen.virtual_craft.currency_generator.bind"),
				button -> PacketDistributor.sendToServer(new GeneratorActionPayload(menu.containerId, GeneratorActionPayload.Action.BIND_OWNER))));
		modeButton = addRenderableWidget(new FlatButton(leftPos + 184, topPos + 84, 102, 20,
				Component.empty(),
				button -> PacketDistributor.sendToServer(new GeneratorActionPayload(menu.containerId, GeneratorActionPayload.Action.CYCLE_MODE))));
		refreshButtons();
		PacketDistributor.sendToServer(new GeneratorActionPayload(menu.containerId, GeneratorActionPayload.Action.REQUEST_SYNC));
	}

	public boolean matchesMenu(int menuId) {
		return menu.containerId == menuId;
	}

	public void applyGeneratorSync(String ownerName) {
		this.ownerName = ownerName;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		MachineUi.drawWindow(guiGraphics, left, top, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 42, 150, 86, false);
		MachineUi.drawPanel(guiGraphics, left + 180, top + 42, 108, 86, true);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 136, 272, 84, true);

		MachineUi.drawSlot(guiGraphics, left + 48, top + 76);
		double energyFraction = menu.getMaxEnergyStored() == 0 ? 0.0D : (double) menu.getEnergyStored() / (double) menu.getMaxEnergyStored();
		MachineUi.drawHorizontalBar(guiGraphics, left + 188, top + 96, 96, 10, energyFraction, MachineUi.GREEN, MachineUi.CYAN);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				MachineUi.drawSlot(guiGraphics, left + 24 + column * 18, top + 144 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			MachineUi.drawSlot(guiGraphics, left + 24 + column * 18, top + 202);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		refreshButtons();
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachineUi.TEXT_PRIMARY, false);
		MachineUi.drawChip(guiGraphics, font, Component.translatable("tooltip.virtual_craft.generator.mode." + menu.getFundingMode().name().toLowerCase()), 184, 34, 102, MachineUi.AMBER, MachineUi.TEXT_WARNING);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.owner", ownerName.isBlank() ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : Component.literal(ownerName)).getString(), 24, 36, 132, MachineUi.TEXT_SECONDARY);

		CryptoCurrency burning = menu.getCurrentBurnCurrency();
		CryptoCurrency lastCurrency = menu.getLastAutoSelectedCurrency();
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.active_currency", burning == null ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : burning.displayName()).getString(), 24, 54, 132, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.last_currency", lastCurrency == null ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : lastCurrency.displayName()).getString(), 24, 70, 132, MachineUi.TEXT_MUTED);
		if (burning != null) {
			MachineUi.drawTrimmedString(guiGraphics, font, burning.totalEnergyPerItem() + " FE / " + burning.ticker(), 72, 82, 82, MachineUi.TEXT_SUCCESS);
		}

		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()).getString(), 188, 82, 96, MachineUi.TEXT_SECONDARY);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachineUi.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderCustomTooltip(guiGraphics, mouseX, mouseY);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private void refreshButtons() {
		if (modeButton != null) {
			modeButton.setMessage(menu.getFundingMode().displayName());
		}
	}

	private void renderCustomTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (inside(mouseX, mouseY, leftPos + 48, topPos + 76, 16, 16)) {
			CryptoCurrency burning = menu.getCurrentBurnCurrency();
			if (burning != null) {
				guiGraphics.renderComponentTooltip(font, java.util.List.of(
						burning.displayName(),
						Component.literal(burning.totalEnergyPerItem() + " FE total"),
						Component.literal(burning.energyPerTick() + " FE/t for " + burning.burnTimeTicks() + " ticks")), mouseX, mouseY);
			}
		}
		if (inside(mouseX, mouseY, leftPos + 188, topPos + 96, 96, 10)) {
			CryptoCurrency burning = menu.getCurrentBurnCurrency();
			guiGraphics.renderComponentTooltip(font, java.util.List.of(
					Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()),
					Component.literal(burning == null ? "Idle" : "Burning " + burning.ticker()),
					Component.literal("Auto picks the lowest-value available coin")), mouseX, mouseY);
		}
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}
}
