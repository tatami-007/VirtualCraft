package dev.tatami.virtualcraft.client.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class FlatEditBox extends EditBox {
	public FlatEditBox(Font font, int x, int y, int width, int height, Component message) {
		super(font, x, y, width, height, message);
		setBordered(false);
		setTextColor(0xFF274159);
		setTextColorUneditable(0xFF7C90A4);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int outer = isFocused() ? 0xFF9CC4D8 : 0xFFC6D4E1;
		int inner = isFocused() ? 0xFFFFFFFF : 0xFFF5F8FC;
		int accent = isFocused() ? 0xFF59BDD6 : 0xFFA5BDCE;
		guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 3, accent);
		super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
	}
}
