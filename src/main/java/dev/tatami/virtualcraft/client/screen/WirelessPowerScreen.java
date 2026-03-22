package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.menu.WirelessPowerMenu;
import dev.tatami.virtualcraft.wireless.LinkCardData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class WirelessPowerScreen extends AbstractContainerScreen<WirelessPowerMenu> {
	private static final int BG = 0xFFF1F5FA;
	private static final int FRAME = 0xFFDCE6F1;
	private static final int PANEL = 0xFFF8FBFF;
	private static final int PANEL_ALT = 0xFFEAF1F8;
	private static final int BORDER = 0xFFB4C3D4;
	private static final int TEXT = 0xFF223347;
	private static final int TEXT_MUTED = 0xFF67798C;
	private static final int TEXT_ACCENT = 0xFF1F996F;

	public WirelessPowerScreen(WirelessPowerMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 300;
		imageHeight = 236;
		titleLabelX = 18;
		titleLabelY = 16;
		inventoryLabelX = 22;
		inventoryLabelY = 132;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
		guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, FRAME);
		drawPanel(guiGraphics, left + 16, top + 28, 124, 98, PANEL);
		drawPanel(guiGraphics, left + 152, top + 28, 132, 98, PANEL);
		drawPanel(guiGraphics, left + 16, top + 136, 268, 84, PANEL_ALT);
		guiGraphics.fill(left + 17, top + 29, left + 139, top + 31, 0xFF7AD0E2);
		guiGraphics.fill(left + 153, top + 29, left + 283, top + 31, 0xFF7AD0E2);
		guiGraphics.fill(left + 17, top + 137, left + 283, top + 139, 0xFFA4B8CA);

		drawSlot(guiGraphics, left + 62, top + 64);

		guiGraphics.fill(left + 168, top + 86, left + 270, top + 98, 0xFFD8E1EB);
		int energyPixels = menu.getMaxEnergyStored() == 0 ? 0 : (int) ((long) menu.getEnergyStored() * 100 / menu.getMaxEnergyStored());
		guiGraphics.fill(left + 169, top + 87, left + 169 + energyPixels, top + 97, 0xFF63D19E);
		guiGraphics.fill(left + 169, top + 87, left + 169 + Math.min(energyPixels, 20), top + 97, 0xFF90DFFF);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				drawSlot(guiGraphics, left + 22 + column * 18, top + 144 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			drawSlot(guiGraphics, left + 22 + column * 18, top + 202);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		ItemStack linkCard = menu.getLinkCard();
		String owner = linkCard.isEmpty() ? Component.translatable("screen.virtual_craft.wireless_power.owner.none").getString() : LinkCardData.ownerName(linkCard);
		if (owner.isBlank()) {
			owner = Component.translatable("screen.virtual_craft.wireless_power.owner.none").getString();
		}

		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.wireless_power.owner", owner).getString(), 24, 38, 102, TEXT_MUTED);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.wireless_power.channel", "CH" + menu.getChannel()).getString(), 24, 54, 102, TEXT_MUTED);
		drawTrimmed(guiGraphics,
				(menu.hasBoundLinkCard()
						? Component.translatable("screen.virtual_craft.wireless_power.status.online")
						: Component.translatable("screen.virtual_craft.wireless_power.status.offline")).getString(),
				24, 70, 102, menu.hasBoundLinkCard() ? TEXT_ACCENT : TEXT_MUTED);

		String linkedLabel = menu.isTower()
				? Component.translatable("screen.virtual_craft.wireless_power.linked.receivers", menu.getLinkedNodeCount()).getString()
				: Component.translatable("screen.virtual_craft.wireless_power.linked.towers", menu.getLinkedNodeCount()).getString();
		drawTrimmed(guiGraphics, linkedLabel, 152, 38, 132, TEXT_MUTED);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.wireless_power.range", 24).getString(), 152, 54, 132, TEXT_MUTED);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.wireless_power.bandwidth", 4096).getString(), 152, 70, 132, TEXT_MUTED);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()).getString(), 152, 102, 132, TEXT_ACCENT);

		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		if (inside(mouseX, mouseY, leftPos + 168, topPos + 86, 102, 12)) {
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

	private void drawTrimmed(GuiGraphics guiGraphics, String text, int x, int y, int width, int color) {
		guiGraphics.drawString(font, trimToWidth(text, width), x, y, color, false);
	}

	private String trimToWidth(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			String candidate = builder.toString() + text.charAt(index);
			if (font.width(candidate) + ellipsisWidth > maxWidth) {
				break;
			}
			builder.append(text.charAt(index));
		}
		return builder + ellipsis;
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}
}
