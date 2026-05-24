package dev.tatami.virtualcraft.client.screen;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture locations, atlas coordinates, and shared machine GUI dimensions.
 */
public final class MachineAssets {
	public static final ResourceLocation ICONS_TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "textures/gui/icons.png");

	static final int ICONS_TEX_W = 256;
	static final int ICONS_TEX_H = 256;

	public static final int BG_TEX_SIZE = 256;
	public static final int ARROW_W = 22;
	public static final int ARROW_H = 16;

	static final int ENERGY_V_H = 64;
	static final int ENERGY_V_SOURCE_X = 240;
	static final int ENERGY_V_SOURCE_W = 16;
	static final int ENERGY_V_FILL_MAX = ENERGY_V_SOURCE_W - 2;

	public static final int ICON_GEAR_U = 0;
	public static final int ICON_BACK_U = 16;
	public static final int ICON_INPUT_U = 48;
	public static final int ICON_OUTPUT_U = 64;
	public static final int ICON_DISABLE_U = 80;
	public static final int ICON_V = 40;
	public static final int ICON_SIZE = 16;

	public static final int ICON_BUTTON_W = 18;
	public static final int ICON_BUTTON_H = 20;
	public static final int ICON_BUTTON_DARK_V = 130;
	public static final int ICON_BUTTON_LIGHT_V = 150;
	public static final int ICON_BUTTON_BOTH_U = 0;
	public static final int ICON_BUTTON_NORMAL_U = 54;
	static final int ICON_BUTTON_HOVER_OFFSET = 72;

	public static ResourceLocation bgTexture(String name) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "textures/gui/" + name + ".png");
	}

	private MachineAssets() {
	}
}
