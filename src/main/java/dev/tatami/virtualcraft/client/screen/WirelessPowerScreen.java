package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.menu.WirelessPowerMenu;
import dev.tatami.virtualcraft.wireless.LinkCardData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class WirelessPowerScreen extends AbstractContainerScreen<WirelessPowerMenu> {
	public WirelessPowerScreen(WirelessPowerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 300;
		imageHeight = 236;
		titleLabelX = 18;
		titleLabelY = 12;
		inventoryLabelX = 22;
		inventoryLabelY = 132;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		MachineUi.drawWindow(guiGraphics, left, top, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 42, 124, 84, false);
		MachineUi.drawPanel(guiGraphics, left + 152, top + 42, 132, 84, true);
		MachineUi.drawPanel(guiGraphics, left + 16, top + 136, 268, 84, true);

		MachineUi.drawSlot(guiGraphics, left + 62, top + 64);
		MachineUi.drawChip(guiGraphics, font, Component.translatable(menu.isTower() ? "screen.virtual_craft.wireless_power.mode.source" : "screen.virtual_craft.wireless_power.mode.sink"), left + 168, top + 52, 100, MachineUi.CYAN, MachineUi.TEXT_ACCENT);
		MachineUi.drawChip(guiGraphics, font, Component.literal(menu.hasBoundLinkCard() ? "ONLINE" : "OFFLINE"), left + 168, top + 74, 100, menu.hasBoundLinkCard() ? MachineUi.GREEN : MachineUi.AMBER, menu.hasBoundLinkCard() ? MachineUi.TEXT_SUCCESS : MachineUi.TEXT_WARNING);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				MachineUi.drawSlot(guiGraphics, left + 22 + column * 18, top + 144 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			MachineUi.drawSlot(guiGraphics, left + 22 + column * 18, top + 202);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		ItemStack linkCard = menu.getLinkCard();
		String owner = linkCard.isEmpty() ? Component.translatable("screen.virtual_craft.wireless_power.owner.none").getString() : LinkCardData.ownerName(linkCard);
		if (owner.isBlank()) {
			owner = Component.translatable("screen.virtual_craft.wireless_power.owner.none").getString();
		}

		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachineUi.TEXT_PRIMARY, false);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.owner", owner).getString(), 24, 40, 108, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.channel", "CH" + menu.getChannel()).getString(), 24, 58, 108, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable(menu.hasBoundLinkCard() ? "screen.virtual_craft.wireless_power.status.online" : "screen.virtual_craft.wireless_power.status.offline").getString(), 24, 76, 108, menu.hasBoundLinkCard() ? MachineUi.TEXT_SUCCESS : MachineUi.TEXT_WARNING);

		String linkedLabel = menu.isTower()
				? Component.translatable("screen.virtual_craft.wireless_power.linked.receivers", menu.getLinkedNodeCount()).getString()
				: Component.translatable("screen.virtual_craft.wireless_power.linked.towers", menu.getLinkedNodeCount()).getString();
		MachineUi.drawTrimmedString(guiGraphics, font, linkedLabel, 152, 104, 132, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.range", 24).getString(), 152, 118, 132, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.capacity_direct").getString(), 22, 146, 120, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.bandwidth_unbounded").getString(), 22, 160, 120, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.wireless_power.transfer_tick", menu.getLiveTransferAmount()).getString(), 22, 174, 164, MachineUi.TEXT_ACCENT);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable(menu.isTower() ? "screen.virtual_craft.wireless_power.hint.source" : "screen.virtual_craft.wireless_power.hint.sink").getString(), 152, 146, 124, MachineUi.TEXT_MUTED);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MachineUi.TEXT_PRIMARY, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (inside(mouseX, mouseY, leftPos + 152, topPos + 42, 132, 84)) {
			guiGraphics.renderTooltip(font, Component.translatable("screen.virtual_craft.wireless_power.capacity_direct"), mouseX, mouseY);
		}
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}
}
