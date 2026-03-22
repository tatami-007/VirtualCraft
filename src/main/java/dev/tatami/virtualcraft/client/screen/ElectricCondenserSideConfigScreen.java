package dev.tatami.virtualcraft.client.screen;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.widget.SpriteIconButton;
import dev.tatami.virtualcraft.menu.ElectricCondenserSideConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ElectricCondenserSideConfigScreen extends AbstractContainerScreen<ElectricCondenserSideConfigMenu> {
	private static final int TOP_BUTTON = 0;
	private static final int BOTTOM_BUTTON = 1;
	private static final int LEFT_BUTTON = 2;
	private static final int RIGHT_BUTTON = 3;
	private static final int FRONT_BUTTON = 4;
	private static final int BACK_BUTTON = 5;
	private static final int AUTO_IMPORT_BUTTON = 100;
	private static final int AUTO_EXPORT_BUTTON = 101;
	private static final int BACK_TO_MAIN_BUTTON = 120;
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "textures/gui/electric_condenser_side_config.png");
	private static final SpriteIconButton.Sprite BUTTON_PURPLE_DARK = new SpriteIconButton.Sprite(16, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_PURPLE_DARK_HOVER = new SpriteIconButton.Sprite(88, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_BLUE_DARK = new SpriteIconButton.Sprite(34, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_BLUE_DARK_HOVER = new SpriteIconButton.Sprite(106, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_RED_DARK = new SpriteIconButton.Sprite(52, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_RED_DARK_HOVER = new SpriteIconButton.Sprite(124, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_ORIGINAL_DARK = new SpriteIconButton.Sprite(70, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_ORIGINAL_DARK_HOVER = new SpriteIconButton.Sprite(142, 216, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_ORIGINAL_LIGHT = new SpriteIconButton.Sprite(70, 196, 18, 20);
	private static final SpriteIconButton.Sprite BUTTON_ORIGINAL_LIGHT_HOVER = new SpriteIconButton.Sprite(142, 196, 18, 20);
	private static final SpriteIconButton.Sprite RECEIVER_ICON = new SpriteIconButton.Sprite(0, 112, 16, 16);
	private static final SpriteIconButton.Sprite SENDER_ICON = new SpriteIconButton.Sprite(16, 112, 16, 16);
	private final Map<Integer, SpriteIconButton> sideButtons = new LinkedHashMap<>();
	private SpriteIconButton autoImportButton;
	private SpriteIconButton autoExportButton;
	private SpriteIconButton backButton;

	public ElectricCondenserSideConfigScreen(ElectricCondenserSideConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 96;
		imageHeight = 101;
		titleLabelX = 6;
		titleLabelY = 6;
	}

	@Override
	protected void init() {
		super.init();
		sideButtons.clear();
		addSideButton(TOP_BUTTON, leftPos + 30, topPos + 18);
		addSideButton(LEFT_BUTTON, leftPos + 9, topPos + 39);
		addSideButton(FRONT_BUTTON, leftPos + 30, topPos + 39);
		addSideButton(RIGHT_BUTTON, leftPos + 51, topPos + 39);
		addSideButton(BOTTOM_BUTTON, leftPos + 30, topPos + 60);
		addSideButton(BACK_BUTTON, leftPos + 51, topPos + 60);
		backButton = addRenderableWidget(new SpriteIconButton(leftPos + 72, topPos + 18, button -> sendButton(BACK_TO_MAIN_BUTTON),
				() -> BUTTON_ORIGINAL_DARK,
				() -> BUTTON_ORIGINAL_DARK_HOVER,
				() -> null,
				() -> Component.literal("<")));
		autoImportButton = addRenderableWidget(createAutoButton(AUTO_IMPORT_BUTTON, leftPos + 72, topPos + 39, true));
		autoExportButton = addRenderableWidget(createAutoButton(AUTO_EXPORT_BUTTON, leftPos + 72, topPos + 60, false));
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
		renderHoveredButtonTooltip(guiGraphics, mouseX, mouseY);
	}

	private SpriteIconButton createModeButton(int id, int x, int y) {
		return new SpriteIconButton(x, y, button -> sendButton(id),
				() -> normalBackgroundFor(id),
				() -> hoverBackgroundFor(id),
				() -> null,
				() -> Component.translatable(getSideShortTranslationKey(id)));
	}

	private SpriteIconButton createAutoButton(int id, int x, int y, boolean isImport) {
		return new SpriteIconButton(x, y, button -> sendButton(id),
				() -> isAutoEnabled(id) ? BUTTON_ORIGINAL_LIGHT : BUTTON_ORIGINAL_DARK,
				() -> isAutoEnabled(id) ? BUTTON_ORIGINAL_LIGHT_HOVER : BUTTON_ORIGINAL_DARK_HOVER,
				() -> isImport ? RECEIVER_ICON : SENDER_ICON,
				Component::empty);
	}

	private SpriteIconButton.Sprite normalBackgroundFor(int id) {
		return switch (menu.getModeLabel(id)) {
			case "I" -> BUTTON_RED_DARK;
			case "O" -> BUTTON_BLUE_DARK;
			case "IO" -> BUTTON_PURPLE_DARK;
			default -> BUTTON_ORIGINAL_DARK;
		};
	}

	private SpriteIconButton.Sprite hoverBackgroundFor(int id) {
		return switch (menu.getModeLabel(id)) {
			case "I" -> BUTTON_RED_DARK_HOVER;
			case "O" -> BUTTON_BLUE_DARK_HOVER;
			case "IO" -> BUTTON_PURPLE_DARK_HOVER;
			default -> BUTTON_ORIGINAL_DARK_HOVER;
		};
	}

	private boolean isAutoEnabled(int id) {
		return id == AUTO_IMPORT_BUTTON ? menu.isAutoImportEnabled() : menu.isAutoExportEnabled();
	}

	private void sendButton(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	private void addSideButton(int id, int x, int y) {
		sideButtons.put(id, addRenderableWidget(createModeButton(id, x, y)));
	}

	private void renderHoveredButtonTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		for (Map.Entry<Integer, SpriteIconButton> entry : sideButtons.entrySet()) {
			if (entry.getValue().isHoveredOrFocused()) {
				guiGraphics.renderTooltip(font,
						Component.translatable("tooltip.virtual_craft.side_tooltip",
								Component.translatable(getSideNameTranslationKey(entry.getKey())),
								menu.getMode(entry.getKey()).displayName()),
						mouseX, mouseY);
				return;
			}
		}
		if (autoImportButton != null && autoImportButton.isHoveredOrFocused()) {
			guiGraphics.renderTooltip(font,
					Component.translatable(menu.isAutoImportEnabled()
							? "tooltip.virtual_craft.auto_import.enabled"
							: "tooltip.virtual_craft.auto_import.disabled"),
					mouseX, mouseY);
			return;
		}
		if (autoExportButton != null && autoExportButton.isHoveredOrFocused()) {
			guiGraphics.renderTooltip(font,
					Component.translatable(menu.isAutoExportEnabled()
							? "tooltip.virtual_craft.auto_export.enabled"
							: "tooltip.virtual_craft.auto_export.disabled"),
					mouseX, mouseY);
			return;
		}
		if (backButton != null && backButton.isHoveredOrFocused()) {
			guiGraphics.renderTooltip(font, Component.translatable("tooltip.virtual_craft.back_to_machine"), mouseX, mouseY);
		}
	}

	private String getSideNameTranslationKey(int id) {
		return switch (id) {
			case TOP_BUTTON -> "tooltip.virtual_craft.side.top";
			case BOTTOM_BUTTON -> "tooltip.virtual_craft.side.bottom";
			case LEFT_BUTTON -> "tooltip.virtual_craft.side.left";
			case RIGHT_BUTTON -> "tooltip.virtual_craft.side.right";
			case FRONT_BUTTON -> "tooltip.virtual_craft.side.front";
			case BACK_BUTTON -> "tooltip.virtual_craft.side.back";
			default -> "tooltip.virtual_craft.side.front";
		};
	}

	private String getSideShortTranslationKey(int id) {
		return switch (id) {
			case TOP_BUTTON -> "tooltip.virtual_craft.side_short.top";
			case BOTTOM_BUTTON -> "tooltip.virtual_craft.side_short.bottom";
			case LEFT_BUTTON -> "tooltip.virtual_craft.side_short.left";
			case RIGHT_BUTTON -> "tooltip.virtual_craft.side_short.right";
			case FRONT_BUTTON -> "tooltip.virtual_craft.side_short.front";
			case BACK_BUTTON -> "tooltip.virtual_craft.side_short.back";
			default -> "tooltip.virtual_craft.side_short.front";
		};
	}
}


