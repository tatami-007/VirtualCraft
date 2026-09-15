package dev.tatami.virtualcraft.client.screen;

import java.util.List;
import java.util.Locale;

import dev.tatami.virtualcraft.blockentity.QuantumSimulationChamberBlockEntity;
import dev.tatami.virtualcraft.client.screen.widget.IconButton;
import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import dev.tatami.virtualcraft.menu.QuantumSimulationChamberMenu;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class QuantumSimulationChamberScreen
		extends AbstractContainerScreen<QuantumSimulationChamberMenu> {
	private static final ResourceLocation BG = MachineAssets.bgTexture("quantum_simulation_chamber");
	private static final int ENERGY_X = 8;
	private static final int ENERGY_Y = 22;
	private static final int ENERGY_W = 16;
	private static final int ENERGY_H = 52;
	private static final int PROGRAM_X = 79;
	private static final int PROGRAM_Y = 28;
	private static final int TARGET_X = 79;
	private static final int TARGET_Y = 50;
	private static final int PROGRESS_X = 47;
	private static final int PROGRESS_Y = 72;
	private static final int PROGRESS_W = 82;
	private IconButton previousTarget;
	private IconButton nextTarget;
	private IconButton startButton;
	private IconButton cancelButton;

	public QuantumSimulationChamberScreen(QuantumSimulationChamberMenu menu, Inventory inventory,
			Component title) {
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
		addRenderableWidget(icon(47, 28, MachineAssets.ICON_BACK_U,
				"screen.virtual_craft.quantum_simulation.previous_program",
				QuantumSimulationChamberMenu.PREVIOUS_PROGRAM));
		addRenderableWidget(icon(111, 28, MachineAssets.ICON_NEXT_U,
				"screen.virtual_craft.quantum_simulation.next_program",
				QuantumSimulationChamberMenu.NEXT_PROGRAM));
		previousTarget = addRenderableWidget(icon(57, 50, MachineAssets.ICON_BACK_U,
				"screen.virtual_craft.quantum_simulation.previous_target",
				QuantumSimulationChamberMenu.PREVIOUS_TARGET));
		nextTarget = addRenderableWidget(icon(97, 50, MachineAssets.ICON_NEXT_U,
				"screen.virtual_craft.quantum_simulation.next_target",
				QuantumSimulationChamberMenu.NEXT_TARGET));
		startButton = addRenderableWidget(icon(134, 28, MachineAssets.ICON_START_U,
				"screen.virtual_craft.quantum_simulation.start", QuantumSimulationChamberMenu.START));
		cancelButton = addRenderableWidget(icon(152, 28, MachineAssets.ICON_DISABLE_U,
				"screen.virtual_craft.quantum_simulation.cancel", QuantumSimulationChamberMenu.CANCEL));
	}

	private IconButton icon(int x, int y, int iconU, String tooltipKey, int action) {
		return new IconButton(leftPos + x, topPos + y, iconU, Component.translatable(tooltipKey),
				button -> sendButton(action));
	}

	private void sendButton(int action) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		boolean targetProgram = menu.selectedProgram() != QuantumProgram.STORM_PATH;
		previousTarget.visible = targetProgram;
		nextTarget.visible = targetProgram;
		startButton.active = menu.activeTasks() < menu.maxLanes()
				&& menu.onlineRacks() > 0
				&& menu.computeScore() >= menu.minimumCompute()
				&& menu.resonance() >= menu.resonanceCost();
		startButton.setHighlighted(menu.status() == QuantumSimulationChamberBlockEntity.STATUS_RUNNING);
		cancelButton.active = menu.activeTasks() > 0;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		MachineDraw.drawBackground(graphics, leftPos, topPos, imageWidth, imageHeight, BG);
		double energy = menu.maxEnergy() == 0 ? 0.0D : menu.energy() / (double) menu.maxEnergy();
		MachineDraw.drawVerticalBar(graphics, leftPos + ENERGY_X, topPos + ENERGY_Y,
				ENERGY_W, ENERGY_H, energy, MachinePalette.RED, MachinePalette.RED);
		MachineDraw.drawHorizontalBar(graphics, leftPos + PROGRESS_X, topPos + PROGRESS_Y,
				PROGRESS_W, 8, menu.progressPermille() / 1000.0D, BG, 180, 0);
		graphics.renderItem(programIcon(), leftPos + PROGRAM_X, topPos + PROGRAM_Y);
		graphics.renderItem(menu.selectedOutputIcon(), leftPos + TARGET_X, topPos + TARGET_Y);
	}

	private ItemStack programIcon() {
		return switch (menu.selectedProgram()) {
			case DEEP_ORE -> new ItemStack(ModItems.RAW_VIRTUAL_IRON.get());
			case RELIC_TOPOLOGY -> new ItemStack(ModItems.CPU.get());
			case STORM_PATH -> new ItemStack(ModItems.QUANTUM_HASH_VOUCHER.get());
		};
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
				MachinePalette.TEXT_PRIMARY, false);
		Component program = Component.translatable(
				"program.virtual_craft." + menu.selectedProgram().getSerializedName());
		graphics.drawCenteredString(font, program, 88, 17, MachinePalette.TEXT_PRIMARY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		renderMachineTooltips(graphics, mouseX, mouseY);
	}

	private void renderMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
		if (hoveredSlot != null) {
			if (hoveredSlot.index < QuantumSimulationChamberBlockEntity.OUTPUT_SLOTS
					&& !hoveredSlot.hasItem()) {
				graphics.renderComponentTooltip(font, List.of(Component.translatable(
						"screen.virtual_craft.quantum_simulation.output_slot")), mouseX, mouseY);
			}
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + ENERGY_X, topPos + ENERGY_Y,
				ENERGY_W, ENERGY_H)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("screen.virtual_craft.machine.energy",
							menu.energy(), menu.maxEnergy())), mouseX, mouseY);
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + PROGRAM_X, topPos + PROGRAM_Y, 16, 16)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("program.virtual_craft."
							+ menu.selectedProgram().getSerializedName()),
					Component.translatable("screen.virtual_craft.quantum_simulation.cost",
							seconds(menu.durationTicks()), compact(menu.totalEnergyCost()),
							menu.resonanceCost()),
					Component.translatable("screen.virtual_craft.quantum_simulation.compute",
							menu.computeScore(), menu.minimumCompute(), menu.maxLanes()),
					Component.translatable("screen.virtual_craft.quantum_simulation.output",
							menu.selectedOutputName())),
					mouseX, mouseY);
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + TARGET_X, topPos + TARGET_Y, 16, 16)) {
			graphics.renderComponentTooltip(font, List.of(menu.selectedOutputName()), mouseX, mouseY);
			return;
		}
		if (MachineDraw.contains(mouseX, mouseY, leftPos + PROGRESS_X, topPos + PROGRESS_Y,
				PROGRESS_W, 8)) {
			graphics.renderComponentTooltip(font, List.of(statusText(),
					Component.literal(menu.progressPermille() / 10 + "%")), mouseX, mouseY);
		}
	}

	private Component statusText() {
		String key = switch (menu.status()) {
			case QuantumSimulationChamberBlockEntity.STATUS_RUNNING ->
					"screen.virtual_craft.quantum_simulation.running";
			case QuantumSimulationChamberBlockEntity.STATUS_WAITING_POWER ->
					"screen.virtual_craft.quantum_simulation.waiting_power";
			case QuantumSimulationChamberBlockEntity.STATUS_WAITING_COMPUTE ->
					"screen.virtual_craft.quantum_simulation.waiting_compute";
			case QuantumSimulationChamberBlockEntity.STATUS_NO_RESONANCE ->
					"screen.virtual_craft.quantum_simulation.no_resonance";
			case QuantumSimulationChamberBlockEntity.STATUS_COMPUTE_LOW ->
					"screen.virtual_craft.quantum_simulation.compute_low";
			case QuantumSimulationChamberBlockEntity.STATUS_QUEUE_FULL ->
					"screen.virtual_craft.quantum_simulation.queue_full";
			case QuantumSimulationChamberBlockEntity.STATUS_OUTPUT_FULL ->
					"screen.virtual_craft.quantum_simulation.output_full";
			default -> "screen.virtual_craft.quantum_simulation.idle";
		};
		return Component.translatable(key);
	}

	private static String compact(int value) {
		if (value >= 1_000_000) {
			return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0D);
		}
		if (value >= 1_000) {
			return String.format(Locale.ROOT, "%.1fk", value / 1_000.0D);
		}
		return Integer.toString(value);
	}

	private static int seconds(int ticks) {
		return (ticks + 19) / 20;
	}
}
