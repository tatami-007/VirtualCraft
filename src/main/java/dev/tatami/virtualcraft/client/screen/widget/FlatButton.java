package dev.tatami.virtualcraft.client.screen.widget;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class FlatButton extends Button {
	private final BooleanSupplier selectedSupplier;
	private final int accentColor;

	public FlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		this(x, y, width, height, message, onPress, () -> false, 0xFF4DD0E1);
	}

	public FlatButton(int x, int y, int width, int height, Component message, OnPress onPress, BooleanSupplier selectedSupplier, int accentColor) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.selectedSupplier = selectedSupplier;
		this.accentColor = accentColor;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		boolean selected = selectedSupplier.getAsBoolean();
		boolean hovered = isHoveredOrFocused();
		int outer = !active ? 0xFFC8D4E0 : selected ? 0xFFB8DAE6 : 0xFFCAD9E6;
		int inner = !active ? 0xFFE7EDF4 : hovered ? 0xFFF7FBFF : 0xFFF1F6FB;
		int accent = selected ? accentColor : hovered ? 0xFF84B7D2 : 0xFFA2B9CB;
		guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 3, accent);

		int textColor = !active ? 0xFF7C8FA4 : selected ? 0xFF1F3952 : hovered ? 0xFF20384D : 0xFF33485E;
		Component label = getMessage();
		int textX = getX() + width / 2 - Minecraft.getInstance().font.width(label) / 2;
		int textY = getY() + (height - 8) / 2;
		guiGraphics.drawString(Minecraft.getInstance().font, label, textX, textY, textColor, false);
	}
}
