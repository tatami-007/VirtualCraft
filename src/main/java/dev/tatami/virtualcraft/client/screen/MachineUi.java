package dev.tatami.virtualcraft.client.screen;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class MachineUi {
	public static final int SCREEN_BG = 0xFF0B1017;
	public static final int WINDOW_OUTER = 0xFF26303B;
	public static final int WINDOW_INNER = 0xFF121A24;
	public static final int HEADER_FILL = 0xFF162230;
	public static final int PANEL_OUTER = 0xFF2B3743;
	public static final int PANEL_FILL = 0xFF101722;
	public static final int PANEL_FILL_ALT = 0xFF131C28;
	public static final int SLOT_OUTER = 0xFF3A4B5C;
	public static final int SLOT_FILL = 0xFF182331;
	public static final int TRACK = 0xFF1D2937;
	public static final int CYAN = 0xFF3DD6E9;
	public static final int CYAN_DIM = 0xFF23414A;
	public static final int GREEN = 0xFF3EDC94;
	public static final int GREEN_DIM = 0xFF1F4337;
	public static final int AMBER = 0xFFE0AF54;
	public static final int AMBER_DIM = 0xFF4C3B1A;
	public static final int TEXT_PRIMARY = 0xFFF2F7FB;
	public static final int TEXT_SECONDARY = 0xFFB7C7D8;
	public static final int TEXT_MUTED = 0xFF7F92A6;
	public static final int TEXT_ACCENT = 0xFF7DE7F0;
	public static final int TEXT_SUCCESS = 0xFF7FF0B5;
	public static final int TEXT_WARNING = 0xFFFFD27A;

	private MachineUi() {
	}

	public static void drawWindow(GuiGraphics guiGraphics, int x, int y, int width, int height) {
		guiGraphics.fill(x, y, x + width, y + height, WINDOW_OUTER);
		guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, WINDOW_INNER);
		guiGraphics.fill(x + 2, y + 2, x + width - 2, y + 30, HEADER_FILL);
		guiGraphics.fill(x + 2, y + 30, x + width - 2, y + 31, CYAN_DIM);
	}

	public static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean alternate) {
		guiGraphics.fill(x, y, x + width, y + height, PANEL_OUTER);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, alternate ? PANEL_FILL_ALT : PANEL_FILL);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 3, alternate ? GREEN : CYAN);
	}

	public static void drawSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_OUTER);
		guiGraphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
	}

	public static void drawHorizontalBar(GuiGraphics guiGraphics, int x, int y, int width, int height, double fraction, int fillColor, int glowColor) {
		int clampedWidth = Math.max(0, Math.min(width, (int) Math.round(width * Math.max(0.0D, Math.min(1.0D, fraction)))));
		guiGraphics.fill(x, y, x + width, y + height, TRACK);
		if (clampedWidth > 0) {
			guiGraphics.fill(x, y, x + clampedWidth, y + height, fillColor);
			guiGraphics.fill(x, y, x + Math.min(clampedWidth, Math.max(4, width / 4)), y + height, glowColor);
		}
	}

	public static void drawVerticalBar(GuiGraphics guiGraphics, int x, int y, int width, int height, double fraction, int fillColor, int glowColor) {
		int clampedHeight = Math.max(0, Math.min(height, (int) Math.round(height * Math.max(0.0D, Math.min(1.0D, fraction)))));
		guiGraphics.fill(x, y, x + width, y + height, TRACK);
		if (clampedHeight > 0) {
			guiGraphics.fill(x, y + height - clampedHeight, x + width, y + height, fillColor);
			guiGraphics.fill(x, y + height - clampedHeight, x + width, y + height - Math.max(1, clampedHeight / 2), glowColor);
		}
	}

	public static void drawChip(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int width, int accentColor, int textColor) {
		guiGraphics.fill(x, y, x + width, y + 16, PANEL_OUTER);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 15, PANEL_FILL_ALT);
		guiGraphics.fill(x + 1, y + 1, x + 4, y + 15, accentColor);
		guiGraphics.drawString(font, trim(font, text.getString(), width - 10), x + 8, y + 4, textColor, false);
	}

	public static void drawTrimmedString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int width, int color) {
		guiGraphics.drawString(font, trim(font, text, width), x, y, color, false);
	}

	public static void drawWrappedText(GuiGraphics guiGraphics, Font font, Component text, int x, int y, int width, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(text, width);
		for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
			guiGraphics.drawString(font, lines.get(index), x, y + index * 9, color, false);
		}
	}

	public static String trim(Font font, String text, int maxWidth) {
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
}
