package dev.tatami.virtualcraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Drawing primitives for classic machine screens.
 */
public final class MachineDraw {
	private static final int ICON_BUTTON_CONTENT_Y_OFFSET = -1;
	private static final int SLOT_DARK = 0xFF8D9BA8;
	private static final int SLOT_LIGHT = 0xFFF7FAFC;
	private static final int SLOT_FILL = 0xFFE5EBF0;

	public static void drawBackground(GuiGraphics guiGraphics, int x, int y, int w, int h, ResourceLocation texture) {
		guiGraphics.blit(texture, x, y, 0, 0, w, h, MachineAssets.BG_TEX_SIZE, MachineAssets.BG_TEX_SIZE);
	}

	public static void drawProgressArrow(GuiGraphics guiGraphics, int x, int y, double fraction,
	                                     ResourceLocation bg, int sourceX, int sourceY) {
		int filled = Math.max(0, Math.min(MachineAssets.ARROW_W, (int) Math.round(MachineAssets.ARROW_W * clamp01(fraction))));
		if (filled <= 0) {
			return;
		}
		guiGraphics.blit(bg, x, y, sourceX, sourceY, filled, MachineAssets.ARROW_H, MachineAssets.BG_TEX_SIZE, MachineAssets.BG_TEX_SIZE);
	}

	public static void drawHorizontalBar(GuiGraphics guiGraphics, int x, int y, int w, int height, double fraction,
	                                     ResourceLocation bg, int sourceX, int sourceY) {
		int innerW = w - 2;
		int filled = Math.max(0, Math.min(innerW, (int) Math.round(innerW * clamp01(fraction))));
		if (filled <= 0) {
			return;
		}
		int sourceHeight = Math.min(height - 2, 8);
		guiGraphics.blit(bg, x + 1, y + 1, sourceX, sourceY, filled, sourceHeight, MachineAssets.BG_TEX_SIZE, MachineAssets.BG_TEX_SIZE);
	}

	public static void drawVerticalBar(GuiGraphics guiGraphics, int x, int y, int w, int height, double fraction, int fillColor, int glowColor) {
		int innerH = height - 2;
		int filled = Math.max(0, Math.min(innerH, (int) Math.round(innerH * clamp01(fraction))));
		if (filled <= 0) {
			return;
		}
		int sourceY = MachineAssets.ENERGY_V_H - filled;
		int targetY = y + height - 1 - filled;
		int sourceWidth = Math.min(w - 2, MachineAssets.ENERGY_V_FILL_MAX);
		guiGraphics.blit(MachineAssets.ICONS_TEXTURE, x + 1, targetY, MachineAssets.ENERGY_V_SOURCE_X + 1, sourceY, sourceWidth, filled,
				MachineAssets.ICONS_TEX_W, MachineAssets.ICONS_TEX_H);
	}

	public static void drawVerticalSolidBar(GuiGraphics guiGraphics, int x, int y, int w, int height,
			double fraction, int darkColor, int lightColor) {
		int innerH = height - 2;
		int filled = Math.max(0, Math.min(innerH, (int) Math.round(innerH * clamp01(fraction))));
		if (filled <= 0) {
			return;
		}
		int targetY = y + height - 1 - filled;
		guiGraphics.fill(x + 1, targetY, x + w - 1, y + height - 1, darkColor);
		if (w > 6) {
			guiGraphics.fill(x + 4, targetY, x + w - 4, y + height - 1, lightColor);
		}
	}

	public static void drawItemSlot(GuiGraphics guiGraphics, int x, int y) {
		guiGraphics.fill(x, y, x + 18, y + 18, SLOT_LIGHT);
		guiGraphics.fill(x, y, x + 17, y + 1, SLOT_DARK);
		guiGraphics.fill(x, y, x + 1, y + 17, SLOT_DARK);
		guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
	}

	public static void drawIconButton(GuiGraphics guiGraphics, int x, int y, int iconU, boolean hovered, boolean highlight) {
		drawIconButton(guiGraphics, x, y, iconU, hovered, highlight ? MachineAssets.ICON_BUTTON_BOTH_U : MachineAssets.ICON_BUTTON_NORMAL_U);
	}

	public static void drawIconButton(GuiGraphics guiGraphics, int x, int y, int iconU, boolean hovered, int frameU) {
		drawIconButton(guiGraphics, x, y, iconU, hovered, frameU, MachineAssets.ICON_BUTTON_DARK_V);
	}

	public static void drawIconButton(GuiGraphics guiGraphics, int x, int y, int iconU, boolean hovered, int frameU, int frameV) {
		int hoverOffset = hovered ? 1 : 0;
		drawIconButtonFrame(guiGraphics, x, y, hovered, frameU, frameV);
		int frameX = x - 1;
		int iconX = frameX + (MachineAssets.ICON_BUTTON_W - MachineAssets.ICON_SIZE) / 2;
		int iconY = y + hoverOffset + (MachineAssets.ICON_BUTTON_H - MachineAssets.ICON_SIZE) / 2 + ICON_BUTTON_CONTENT_Y_OFFSET;
		guiGraphics.blit(MachineAssets.ICONS_TEXTURE, iconX, iconY, iconU, MachineAssets.ICON_V, MachineAssets.ICON_SIZE,
				MachineAssets.ICON_SIZE, MachineAssets.ICONS_TEX_W, MachineAssets.ICONS_TEX_H);
	}

	public static void drawIconButtonFrame(GuiGraphics guiGraphics, int x, int y, boolean hovered, int normalFrameU) {
		drawIconButtonFrame(guiGraphics, x, y, hovered, normalFrameU, MachineAssets.ICON_BUTTON_DARK_V);
	}

	public static void drawIconButtonFrame(GuiGraphics guiGraphics, int x, int y, boolean hovered, int normalFrameU, int frameV) {
		int hoverOffset = hovered ? 1 : 0;
		int frameU = hovered ? normalFrameU + MachineAssets.ICON_BUTTON_HOVER_OFFSET : normalFrameU;
		guiGraphics.blit(MachineAssets.ICONS_TEXTURE, x - 1, y + hoverOffset, frameU, frameV, MachineAssets.ICON_BUTTON_W,
				MachineAssets.ICON_BUTTON_H, MachineAssets.ICONS_TEX_W, MachineAssets.ICONS_TEX_H);
	}

	public static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private MachineDraw() {
	}
}
