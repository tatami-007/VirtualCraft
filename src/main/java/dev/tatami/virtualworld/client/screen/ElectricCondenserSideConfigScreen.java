package dev.tatami.virtualworld.client.screen;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.client.widget.AECSpriteButton;
import dev.tatami.virtualworld.menu.ElectricCondenserSideConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ElectricCondenserSideConfigScreen extends AbstractContainerScreen<ElectricCondenserSideConfigMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualWorld.MODID, "textures/gui/electric_condenser_side_config.png");
	private static final AECSpriteButton.Sprite BUTTON_PURPLE_DARK = new AECSpriteButton.Sprite(16, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_PURPLE_DARK_HOVER = new AECSpriteButton.Sprite(88, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_BLUE_DARK = new AECSpriteButton.Sprite(34, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_BLUE_DARK_HOVER = new AECSpriteButton.Sprite(106, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_RED_DARK = new AECSpriteButton.Sprite(52, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_RED_DARK_HOVER = new AECSpriteButton.Sprite(124, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_DARK = new AECSpriteButton.Sprite(70, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_DARK_HOVER = new AECSpriteButton.Sprite(142, 216, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_LIGHT = new AECSpriteButton.Sprite(70, 196, 18, 20);
	private static final AECSpriteButton.Sprite BUTTON_ORIGINAL_LIGHT_HOVER = new AECSpriteButton.Sprite(142, 196, 18, 20);
	private static final AECSpriteButton.Sprite RECEIVER_ICON = new AECSpriteButton.Sprite(0, 112, 16, 16);
	private static final AECSpriteButton.Sprite SENDER_ICON = new AECSpriteButton.Sprite(16, 112, 16, 16);

	public ElectricCondenserSideConfigScreen(ElectricCondenserSideConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 96;
		imageHeight = 101;
		titleLabelX = 8;
		titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		addRenderableWidget(createModeButton(0, leftPos + 39, topPos + 20));
		addRenderableWidget(createModeButton(1, leftPos + 39, topPos + 64));
		addRenderableWidget(createModeButton(2, leftPos + 18, topPos + 42));
		addRenderableWidget(createModeButton(3, leftPos + 60, topPos + 42));
		addRenderableWidget(createModeButton(4, leftPos + 39, topPos + 42));
		addRenderableWidget(createModeButton(5, leftPos + 18, topPos + 64));
		addRenderableWidget(createAutoButton(100, leftPos + 8, topPos + 84, true));
		addRenderableWidget(createAutoButton(101, leftPos + 29, topPos + 84, false));
		addRenderableWidget(new AECSpriteButton(leftPos + 72, topPos + 6, button -> sendButton(120),
				() -> BUTTON_ORIGINAL_DARK,
				() -> BUTTON_ORIGINAL_DARK_HOVER,
				() -> null,
				() -> Component.literal("<")));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private AECSpriteButton createModeButton(int id, int x, int y) {
		return new AECSpriteButton(x, y, button -> sendButton(id),
				() -> normalBackgroundFor(id),
				() -> hoverBackgroundFor(id),
				() -> null,
				() -> Component.literal(menu.getModeLabel(id)));
	}

	private AECSpriteButton createAutoButton(int id, int x, int y, boolean isImport) {
		return new AECSpriteButton(x, y, button -> sendButton(id),
				() -> isAutoEnabled(id) ? BUTTON_ORIGINAL_LIGHT : BUTTON_ORIGINAL_DARK,
				() -> isAutoEnabled(id) ? BUTTON_ORIGINAL_LIGHT_HOVER : BUTTON_ORIGINAL_DARK_HOVER,
				() -> isImport ? RECEIVER_ICON : SENDER_ICON,
				Component::empty);
	}

	private AECSpriteButton.Sprite normalBackgroundFor(int id) {
		return switch (menu.getModeLabel(id)) {
			case "I" -> BUTTON_RED_DARK;
			case "O" -> BUTTON_BLUE_DARK;
			case "IO" -> BUTTON_PURPLE_DARK;
			default -> BUTTON_ORIGINAL_DARK;
		};
	}

	private AECSpriteButton.Sprite hoverBackgroundFor(int id) {
		return switch (menu.getModeLabel(id)) {
			case "I" -> BUTTON_RED_DARK_HOVER;
			case "O" -> BUTTON_BLUE_DARK_HOVER;
			case "IO" -> BUTTON_PURPLE_DARK_HOVER;
			default -> BUTTON_ORIGINAL_DARK_HOVER;
		};
	}

	private boolean isAutoEnabled(int id) {
		return id == 100 ? menu.isAutoImportEnabled() : menu.isAutoExportEnabled();
	}

	private void sendButton(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}
}
