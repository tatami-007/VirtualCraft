package dev.tatami.virtualcraft.client.screen;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.tatami.virtualcraft.client.screen.widget.FlatButton;
import dev.tatami.virtualcraft.menu.ElectricCondenserSideConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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
	private final Map<Integer, Button> sideButtons = new LinkedHashMap<>();
	private Button autoImportButton;
	private Button autoExportButton;
	private Button backButton;

	public ElectricCondenserSideConfigScreen(ElectricCondenserSideConfigMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 180;
		imageHeight = 140;
		titleLabelX = 14;
		titleLabelY = 12;
	}

	@Override
	protected void init() {
		super.init();
		sideButtons.clear();
		addSideButton(TOP_BUTTON, leftPos + 68, topPos + 40);
		addSideButton(LEFT_BUTTON, leftPos + 18, topPos + 64);
		addSideButton(FRONT_BUTTON, leftPos + 68, topPos + 64);
		addSideButton(RIGHT_BUTTON, leftPos + 118, topPos + 64);
		addSideButton(BOTTOM_BUTTON, leftPos + 68, topPos + 88);
		addSideButton(BACK_BUTTON, leftPos + 118, topPos + 88);
		autoImportButton = addRenderableWidget(new FlatButton(leftPos + 18, topPos + 88, 44, 18, Component.empty(), button -> sendButton(AUTO_IMPORT_BUTTON)));
		autoExportButton = addRenderableWidget(new FlatButton(leftPos + 18, topPos + 112, 44, 18, Component.empty(), button -> sendButton(AUTO_EXPORT_BUTTON)));
		backButton = addRenderableWidget(new FlatButton(leftPos + 118, topPos + 112, 44, 18, Component.translatable("screen.virtual_craft.machine.back"), button -> sendButton(BACK_TO_MAIN_BUTTON)));
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		MachineUi.drawWindow(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, leftPos + 12, topPos + 34, 156, 94, false);
		MachineUi.drawChip(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.matrix"), leftPos + 14, topPos + 18, 86, MachineUi.CYAN, MachineUi.TEXT_ACCENT);
		MachineUi.drawChip(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.auto"), leftPos + 106, topPos + 18, 56, MachineUi.GREEN, MachineUi.TEXT_SUCCESS);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(font, title, titleLabelX, titleLabelY, MachineUi.TEXT_PRIMARY, false);
		updateButtonLabels();
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.sides").getString(), 18, 40, 40, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.front_hint").getString(), 18, 54, 90, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.import").getString(), 18, 76, 32, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.side_config.export").getString(), 18, 100, 40, MachineUi.TEXT_SECONDARY);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	private void updateButtonLabels() {
		for (Map.Entry<Integer, Button> entry : sideButtons.entrySet()) {
			entry.getValue().setMessage(Component.literal(shortSide(entry.getKey()) + " " + menu.getModeLabel(entry.getKey())));
		}
		autoImportButton.setMessage(Component.translatable(menu.isAutoImportEnabled() ? "screen.virtual_craft.side_config.import_on" : "screen.virtual_craft.side_config.import_off"));
		autoExportButton.setMessage(Component.translatable(menu.isAutoExportEnabled() ? "screen.virtual_craft.side_config.export_on" : "screen.virtual_craft.side_config.export_off"));
	}

	private void addSideButton(int id, int x, int y) {
		sideButtons.put(id, addRenderableWidget(new FlatButton(x, y, 42, 18, Component.empty(), button -> sendButton(id))));
	}

	private void sendButton(int id) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	private String shortSide(int id) {
		return switch (id) {
			case TOP_BUTTON -> "U";
			case BOTTOM_BUTTON -> "D";
			case LEFT_BUTTON -> "L";
			case RIGHT_BUTTON -> "R";
			case FRONT_BUTTON -> "F";
			case BACK_BUTTON -> "B";
			default -> "?";
		};
	}
}
