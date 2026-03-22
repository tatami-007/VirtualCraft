package dev.tatami.virtualcraft.client.screen.widget;

import dev.tatami.virtualcraft.client.screen.MachineUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class FlatEditBox extends EditBox {
	public FlatEditBox(Font font, int x, int y, int width, int height, Component message) {
		super(font, x, y, width, height, message);
		setBordered(false);
		setTextColor(MachineUi.TEXT_PRIMARY);
		setTextColorUneditable(MachineUi.TEXT_MUTED);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int outer = isFocused() ? 0xFF355064 : MachineUi.PANEL_OUTER;
		int inner = isFocused() ? 0xFF142433 : MachineUi.PANEL_FILL;
		int accent = isFocused() ? MachineUi.CYAN : MachineUi.CYAN_DIM;
		guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 4, accent);
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
	}
}
