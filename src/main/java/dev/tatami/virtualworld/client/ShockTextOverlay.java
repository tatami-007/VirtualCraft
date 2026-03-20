package dev.tatami.virtualworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

import dev.tatami.virtualworld.client.render.ShockTextRenderTarget;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class ShockTextOverlay {
	private static final Component TEXT = Component.translatable("info.virtual_world.electrocution");
	private static final long DISPLAY_MILLIS = 1800L;
	private static final int BASELINE_OFFSET = 68;
	private static final long FADE_OUT_MILLIS = 320L;
	private static final float SHAKE_SPEED = 0.018F;
	private static final float SHAKE_AMPLITUDE = 1.6F;
	private static final MultiBufferSource.BufferSource TEXT_BUFFER_SOURCE = MultiBufferSource.immediate(new ByteBufferBuilder(8192));

	private static ShockTextRenderTarget renderTarget;
	private static long visibleUntilMillis;

	private ShockTextOverlay() {
	}

	public static void show() {
		visibleUntilMillis = Util.getMillis() + DISPLAY_MILLIS;
	}

	public static void render(RenderGuiLayerEvent.Post event) {
		if (!VanillaGuiLayers.OVERLAY_MESSAGE.equals(event.getName()))
			return;

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null)
			return;

		long remainingMillis = visibleUntilMillis - Util.getMillis();
		if (remainingMillis <= 0L)
			return;

		ensureRenderTarget(minecraft);

		var mainTarget = minecraft.getMainRenderTarget();
		renderTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
		renderTarget.clear(Minecraft.ON_OSX);

		mainTarget.unbindWrite();
		renderTarget.bindWrite(true);
		drawTextToRenderTarget(minecraft, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight(), remainingMillis);
		renderTarget.unbindWrite();

		mainTarget.bindWrite(true);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		renderTarget.blitToScreen(mainTarget.width, mainTarget.height, Util.getMillis() * 0.001F, fadeAlpha(remainingMillis), false);
	}

	private static void ensureRenderTarget(Minecraft minecraft) {
		var mainTarget = minecraft.getMainRenderTarget();
		if (renderTarget != null && renderTarget.width == mainTarget.width && renderTarget.height == mainTarget.height)
			return;

		if (renderTarget != null)
			renderTarget.destroyBuffers();
		renderTarget = new ShockTextRenderTarget(mainTarget.width, mainTarget.height);
	}

	private static void drawTextToRenderTarget(Minecraft minecraft, int guiWidth, int guiHeight, long remainingMillis) {
		Font font = minecraft.font;
		GuiGraphics guiGraphics = new GuiGraphics(minecraft, TEXT_BUFFER_SOURCE);
		String text = TEXT.getString();
		float startX = (guiWidth - font.width(text)) * 0.5F;
		float baseY = guiHeight - BASELINE_OFFSET;
		float time = Util.getMillis();

		float cursorX = startX;
		for (int index = 0; index < text.length(); index++) {
			String glyph = text.substring(index, index + 1);
			float yOffset = Mth.sin(time * SHAKE_SPEED + index * 0.9F) * SHAKE_AMPLITUDE;
			guiGraphics.drawString(font, glyph, cursorX, baseY + yOffset, 0xFFFFFFFF, false);
			cursorX += font.width(glyph);
		}

		TEXT_BUFFER_SOURCE.endBatch();
	}

	private static float fadeAlpha(long remainingMillis) {
		if (remainingMillis >= FADE_OUT_MILLIS)
			return 1.0F;

		float alpha = Mth.clamp(remainingMillis / (float) FADE_OUT_MILLIS, 0.0F, 1.0F);
		return alpha * alpha * (3.0F - 2.0F * alpha);
	}
}

