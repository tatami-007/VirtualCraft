package dev.tatami.virtualcraft.client.screen;

import java.util.List;

import dev.tatami.virtualcraft.blockentity.ComputeRackBlockEntity;
import dev.tatami.virtualcraft.menu.ComputeRackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ComputeRackScreen extends AbstractContainerScreen<ComputeRackMenu> {
	private static final ResourceLocation BG = MachineAssets.bgTexture("compute_rack");

	public ComputeRackScreen(ComputeRackMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 176;
		imageHeight = 178;
		titleLabelX = 8;
		titleLabelY = 6;
		inventoryLabelX = 8;
		inventoryLabelY = 84;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		MachineDraw.drawBackground(graphics, leftPos, topPos, imageWidth, imageHeight, BG);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, MachinePalette.TEXT_PRIMARY, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
				MachinePalette.TEXT_PRIMARY, false);
		graphics.drawCenteredString(font, Component.translatable("screen.virtual_craft.compute_rack.power"),
				25, 29, MachinePalette.TEXT_PRIMARY);
		graphics.drawCenteredString(font, "CPU", 64, 29, MachinePalette.TEXT_PRIMARY);
		graphics.drawCenteredString(font, "GPU", 106, 29, MachinePalette.TEXT_PRIMARY);
		graphics.drawCenteredString(font, "RAM", 148, 29, MachinePalette.TEXT_PRIMARY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		renderModuleTooltip(graphics, mouseX, mouseY);
	}

	private void renderModuleTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		if (hoveredSlot == null && MachineDraw.contains(mouseX, mouseY,
				leftPos + 8, topPos + 22, 160, 56)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable(menu.isOnline()
							? "screen.virtual_craft.compute_rack.online"
							: "screen.virtual_craft.compute_rack.offline"),
					Component.literal("CPU " + menu.cpuCount() + "  GPU " + menu.gpuCount()
							+ "  RAM " + menu.memoryCount() + "  ×" + menu.lanes()),
					Component.translatable("screen.virtual_craft.compute_rack.effects")),
					mouseX, mouseY);
			return;
		}
		if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= ComputeRackBlockEntity.SLOT_COUNT) {
			return;
		}
		String key;
		if (hoveredSlot.index == ComputeRackBlockEntity.POWER_SLOT) {
			key = "screen.virtual_craft.compute_rack.slot_power";
		} else if (hoveredSlot.index < ComputeRackBlockEntity.CPU_SLOT_END) {
			key = "screen.virtual_craft.compute_rack.slot_cpu";
		} else if (hoveredSlot.index < ComputeRackBlockEntity.GPU_SLOT_END) {
			key = "screen.virtual_craft.compute_rack.slot_gpu";
		} else {
			key = "screen.virtual_craft.compute_rack.slot_memory";
		}
		graphics.renderComponentTooltip(font, List.of(Component.translatable(key)), mouseX, mouseY);
	}
}
