package dev.tatami.virtualworld.client.screen;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.client.widget.AECSpriteButton;
import dev.tatami.virtualworld.menu.ElectricCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ElectricCondenserScreen extends AbstractContainerScreen<ElectricCondenserMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualWorld.MODID, "textures/gui/electric_condenser.png");
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_DARK = new AECSpriteButton.Sprite(70, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_DARK_HOVER = new AECSpriteButton.Sprite(142, 216, 18, 20);
	private static final AECSpriteButton.Sprite SIDE_CONFIG_ICON = new AECSpriteButton.Sprite(0, 176, 16, 16);

	public ElectricCondenserScreen(ElectricCondenserMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 176;
		imageHeight = 178;
		inventoryLabelY = 85;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(new AECSpriteButton(leftPos + 153, topPos + 6, button -> {
			if (minecraft != null && minecraft.gameMode != null) {
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 120);
			}
		}, () -> BUTTON_ORIGINAL_DARK, () -> BUTTON_ORIGINAL_DARK_HOVER, () -> SIDE_CONFIG_ICON, Component::empty));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		guiGraphics.blit(TEXTURE, left, top, 0, 0, imageWidth, imageHeight, 256, 256);

		int progress = menu.getScaledProgress();
		if (progress > 0) {
			guiGraphics.blit(TEXTURE, left + 77, top + 34, 199, 0, progress, 33, 256, 256);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
		guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}
}

