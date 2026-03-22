package dev.tatami.virtualcraft.client.screen.widget;

import java.util.function.BooleanSupplier;

import dev.tatami.virtualcraft.client.screen.MachineUi;
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
		int outer = !active ? 0xFF24303C : selected ? 0xFF375065 : hovered ? 0xFF304454 : MachineUi.PANEL_OUTER;
		int inner = !active ? 0xFF10161E : selected ? 0xFF182533 : hovered ? 0xFF15202C : MachineUi.PANEL_FILL;
		int accent = !active ? 0xFF394959 : selected ? accentColor : hovered ? 0xFF53C5DA : MachineUi.CYAN_DIM;
		guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 4, accent);

		int textColor = !active ? MachineUi.TEXT_MUTED : selected ? MachineUi.TEXT_PRIMARY : hovered ? 0xFFE7FBFF : MachineUi.TEXT_SECONDARY;
		Component label = Component.literal(MachineUi.trim(Minecraft.getInstance().font, getMessage().getString(), width - 10));
		int textX = getX() + width / 2 - Minecraft.getInstance().font.width(label) / 2;
		int textY = getY() + (height - 8) / 2;
		guiGraphics.drawString(Minecraft.getInstance().font, label, textX, textY, textColor, false);
	}
}
