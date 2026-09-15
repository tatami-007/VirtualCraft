package dev.tatami.virtualcraft.client.screen;

import java.util.List;

import dev.tatami.virtualcraft.blockentity.LightningResonatorBlockEntity;
import dev.tatami.virtualcraft.client.screen.widget.IconButton;
import dev.tatami.virtualcraft.menu.LightningResonatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class LightningResonatorScreen extends AbstractContainerScreen<LightningResonatorMenu> {
	private static final ResourceLocation BG = MachineAssets.bgTexture("lightning_resonator");
	private static final int ENERGY_X = 8;
	private static final int ENERGY_Y = 22;
	private static final int BAR_W = 16;
	private static final int BAR_H = 52;
	private static final int RESONANCE_X = 112;
	private IconButton overdriveButton;

	public LightningResonatorScreen(LightningResonatorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
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
		overdriveButton = addRenderableWidget(new IconButton(
				leftPos + 79, topPos + 39, MachineAssets.ICON_OVERCLOCK_U,
				Component.translatable("screen.virtual_craft.lightning_resonator.overdrive"),
				button -> sendButton()));
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean active = menu.overdriveTicks() > 0;
		overdriveButton.setHighlighted(active);
		overdriveButton.active = !active && menu.cooldownTicks() <= 0
				&& menu.resonance() >= LightningResonatorBlockEntity.OVERDRIVE_COST;
		Component tooltip = active
				? Component.translatable("screen.virtual_craft.lightning_resonator.active",
						seconds(menu.overdriveTicks()))
				: menu.cooldownTicks() > 0
						? Component.translatable("screen.virtual_craft.lightning_resonator.cooldown",
								seconds(menu.cooldownTicks()))
						: Component.translatable("screen.virtual_craft.lightning_resonator.overdrive");
		overdriveButton.setTooltip(Tooltip.create(tooltip));
	}

	private void sendButton() {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
					LightningResonatorMenu.ACTIVATE_OVERDRIVE);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		MachineDraw.drawBackground(graphics, leftPos, topPos, imageWidth, imageHeight, BG);
		double energy = menu.maxEnergy() == 0 ? 0.0D : menu.energy() / (double) menu.maxEnergy();
		double resonance = menu.maxResonance() == 0 ? 0.0D : menu.resonance() / (double) menu.maxResonance();
		MachineDraw.drawVerticalBar(graphics, leftPos + ENERGY_X, topPos + ENERGY_Y, BAR_W, BAR_H,
				energy, MachinePalette.RED, MachinePalette.RED);
		MachineDraw.drawVerticalSolidBar(graphics, leftPos + RESONANCE_X, topPos + ENERGY_Y, BAR_W, BAR_H,
				resonance, 0xFFB36D11, 0xFFFFD35A);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
				MachinePalette.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		renderBarTooltips(graphics, mouseX, mouseY);
	}

	private void renderBarTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (hoveredSlot != null) {
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y, BAR_W, BAR_H)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.energy", menu.energy(), menu.maxEnergy())),
					mouseX, mouseY);
		} else if (MachineDraw.contains(mouseX, mouseY,
				leftPos + RESONANCE_X, topPos + ENERGY_Y, BAR_W, BAR_H)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.lightning_resonator.resonance",
							menu.resonance(), menu.maxResonance()),
					Component.translatable(menu.hasPool()
							? "screen.virtual_craft.lightning_resonator.pool_ready"
							: "screen.virtual_craft.lightning_resonator.no_pool"),
					Component.translatable("screen.virtual_craft.lightning_resonator.efficiency",
							menu.efficiency())),
					mouseX, mouseY);
		}
	}

	private static int seconds(int ticks) {
		return (ticks + 19) / 20;
	}
}
