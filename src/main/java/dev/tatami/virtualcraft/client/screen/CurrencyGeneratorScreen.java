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
	private static final int BG = 0xFFF1F5FA;
	private static final int FRAME = 0xFFDCE6F1;
	private static final int PANEL = 0xFFF8FBFF;
	private static final int PANEL_ALT = 0xFFEAF1F8;
	private static final int BORDER = 0xFFB4C3D4;
	private static final int TEXT = 0xFF223347;
	private static final int TEXT_MUTED = 0xFF67798C;
	private static final int TEXT_ACCENT = 0xFF1F996F;
	private String ownerName = "";
	private Button bindButton;
	private Button modeButton;

	public CurrencyGeneratorScreen(CurrencyGeneratorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 304;
		imageHeight = 236;
		titleLabelX = 18;
		titleLabelY = 16;
		inventoryLabelX = 24;
		inventoryLabelY = 132;
	}

	@Override
	protected void init() {
		super.init();
		bindButton = addRenderableWidget(new FlatButton(leftPos + 188, topPos + 40, 96, 20,
				Component.translatable("screen.virtual_craft.currency_generator.bind"),
				button -> PacketDistributor.sendToServer(new GeneratorActionPayload(menu.containerId, GeneratorActionPayload.Action.BIND_OWNER))));
		modeButton = addRenderableWidget(new FlatButton(leftPos + 188, topPos + 66, 96, 20,
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
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
		guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, FRAME);
		drawPanel(guiGraphics, left + 16, top + 28, 150, 98, PANEL);
		drawPanel(guiGraphics, left + 180, top + 28, 108, 98, PANEL);
		drawPanel(guiGraphics, left + 16, top + 136, 272, 84, PANEL_ALT);
		guiGraphics.fill(left + 17, top + 29, left + 165, top + 31, 0xFF7AD0E2);
		guiGraphics.fill(left + 181, top + 29, left + 287, top + 31, 0xFF7AD0E2);
		guiGraphics.fill(left + 17, top + 137, left + 287, top + 139, 0xFFA4B8CA);

		int slotX = left + 48;
		int slotY = top + 76;
		guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFFB4C3D4);
		guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFFF5F8FC);

		int energyBarX = left + 188;
		int energyBarY = top + 96;
		guiGraphics.fill(energyBarX, energyBarY, energyBarX + 96, energyBarY + 12, 0xFFD8E1EB);
		int energyPixels = menu.getMaxEnergyStored() == 0 ? 0 : (int) ((long) menu.getEnergyStored() * 94 / menu.getMaxEnergyStored());
		guiGraphics.fill(energyBarX + 1, energyBarY + 1, energyBarX + 1 + energyPixels, energyBarY + 11, 0xFF63D19E);
		guiGraphics.fill(energyBarX + 1, energyBarY + 1, energyBarX + 1 + Math.min(energyPixels, 22), energyBarY + 11, 0xFF90DFFF);

		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				drawSlot(guiGraphics, left + 24 + column * 18, top + 144 + row * 18);
			}
		}
		for (int column = 0; column < 9; column++) {
			drawSlot(guiGraphics, left + 24 + column * 18, top + 202);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		refreshButtons();
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.currency_generator.owner", ownerName.isBlank() ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : Component.literal(ownerName)).getString(), 24, 34, 130, TEXT_MUTED);
		CryptoCurrency burning = menu.getCurrentBurnCurrency();
		CryptoCurrency lastCurrency = menu.getLastAutoSelectedCurrency();
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.currency_generator.active_currency", burning == null ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : burning.displayName()).getString(), 24, 52, 130, TEXT_MUTED);
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.currency_generator.last_currency", lastCurrency == null ? Component.translatable("screen.virtual_craft.currency_generator.owner.none") : lastCurrency.displayName()).getString(), 24, 68, 130, TEXT_MUTED);
		if (burning != null) {
			drawTrimmed(guiGraphics, burning.totalEnergyPerItem() + " FE / " + burning.ticker(), 72, 79, 82, TEXT_ACCENT);
		}
		drawTrimmed(guiGraphics, Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()).getString(), 188, 84, 96, TEXT_MUTED);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
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
		if (inside(mouseX, mouseY, leftPos + 188, topPos + 96, 96, 12)) {
			CryptoCurrency burning = menu.getCurrentBurnCurrency();
			guiGraphics.renderComponentTooltip(font, java.util.List.of(
					Component.translatable("screen.virtual_craft.currency_generator.energy", menu.getEnergyStored(), menu.getMaxEnergyStored()),
					Component.literal(burning == null ? "Idle" : "Burning " + burning.ticker()),
					Component.literal("Auto picks the lowest-value available coin")), mouseX, mouseY);
		}
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
