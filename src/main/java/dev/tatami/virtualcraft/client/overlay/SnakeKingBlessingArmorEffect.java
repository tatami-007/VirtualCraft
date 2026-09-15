package dev.tatami.virtualcraft.client.overlay;

import dev.tatami.virtualcraft.registry.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Text-free gold pulse layered directly onto the vanilla armor bar. */
public final class SnakeKingBlessingArmorEffect {
	private static final int ARMOR_POINTS = 10;
	private static final int ICON_STEP = 8;

	private SnakeKingBlessingArmorEffect() {
	}

	public static void render(RenderGuiLayerEvent.Post event) {
		if (!VanillaGuiLayers.ARMOR_LEVEL.equals(event.getName())) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null
				|| minecraft.player.isCreative() || minecraft.player.isSpectator()
				|| !minecraft.player.hasEffect(ModMobEffects.SNAKE_KING_BLESSING)) {
			return;
		}

		GuiGraphics graphics = event.getGuiGraphics();
		int left = graphics.guiWidth() / 2 - 91;
		// ARMOR_LEVEL has already advanced leftHeight, so recover the row it just drew.
		int top = graphics.guiHeight() - minecraft.gui.leftHeight + 10;
		float time = minecraft.player.tickCount
				+ minecraft.getTimer().getGameTimeDeltaPartialTick(false);
		float pulse = 0.5F + 0.5F * Mth.sin(time * 0.18F);
		int railAlpha = 52 + Math.round(pulse * 34.0F);
		int rail = railAlpha << 24 | 0xD6A83A;

		graphics.fill(left - 1, top - 2, left + 81, top - 1, rail);
		graphics.fill(left - 2, top - 1, left, top + 3, rail);
		graphics.fill(left + 80, top - 1, left + 82, top + 3, rail);

		int traveling = Math.floorMod((int)(time / 2.0F), ARMOR_POINTS);
		for (int point = 0; point < ARMOR_POINTS; point++) {
			int x = left + point * ICON_STEP;
			int distance = Math.min(Math.floorMod(point - traveling, ARMOR_POINTS),
					Math.floorMod(traveling - point, ARMOR_POINTS));
			int alpha = Math.max(18, 150 - distance * 34);
			int gold = alpha << 24 | 0xF2C85B;
			graphics.fill(x + 1, top + 8, x + 7, top + 9, gold);
			if (distance == 0) {
				graphics.fill(x + 3, top - 3, x + 5, top, 0xEFFFF1B0);
				graphics.fill(x + 2, top - 2, x + 6, top - 1, 0xBFFFF1B0);
			}
		}
	}
}
