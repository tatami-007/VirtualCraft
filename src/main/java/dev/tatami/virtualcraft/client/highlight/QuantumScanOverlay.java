package dev.tatami.virtualcraft.client.highlight;

import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class QuantumScanOverlay {
	private static final int MARGIN = 20;

	public void render(RenderGuiLayerEvent.Post event) {
		if (!VanillaGuiLayers.OVERLAY_MESSAGE.equals(event.getName())) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		var targets = QuantumScanClientState.targets();
		if (player == null || targets.isEmpty()) {
			return;
		}
		int rendered = 0;
		for (QuantumScanClientState.Target target : targets) {
			if (player.level().dimension() == target.dimension() && rendered++ < 5) {
				draw(event.getGuiGraphics(), player, target, rendered - 1);
			}
		}
	}

	private void draw(GuiGraphics graphics, LocalPlayer player, QuantumScanClientState.Target target, int index) {
		Vec3 offset = Vec3.atCenterOf(target.pos()).subtract(player.getEyePosition());
		double distance = offset.length();
		if (distance < 0.01D) {
			return;
		}
		Vec3 direction = offset.normalize();
		Vec3 look = player.getViewVector(1.0F).normalize();
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (right.lengthSqr() < 0.0001D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();
		double scale = Math.max(0.25D, look.dot(direction) + 0.55D);
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		int x = Mth.clamp(centerX + (int) (right.dot(direction) / scale * graphics.guiWidth() * 0.4D),
				MARGIN, graphics.guiWidth() - MARGIN);
		int y = Mth.clamp(centerY - (int) (up.dot(direction) / scale * graphics.guiHeight() * 0.4D)
				+ index * 12,
				MARGIN, graphics.guiHeight() - MARGIN);
		String glyph = Math.abs(x - centerX) > Math.abs(y - centerY)
				? (x >= centerX ? "\u25b6" : "\u25c0")
				: (y >= centerY ? "\u25bc" : "\u25b2");
		String label = label(target.program()) + "  " + Math.round(distance) + "m"
				+ (target.count() > 1 ? "  ×" + target.count() : "");
		int width = Minecraft.getInstance().font.width(label);
		graphics.fill(x - width / 2 - 5, y + 4, x + width / 2 + 5, y + 18, 0x780B1722);
		graphics.drawCenteredString(Minecraft.getInstance().font, glyph, x, y - 8, 0xFF74E7FF);
		graphics.drawCenteredString(Minecraft.getInstance().font, label, x, y + 7, 0xFFE9FAFF);
	}

	private String label(QuantumProgram program) {
		return net.minecraft.network.chat.Component.translatable(
				"program.virtual_craft." + program.getSerializedName()).getString();
	}
}
