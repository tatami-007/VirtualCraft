package dev.tatami.virtualworld.client.render;

import java.util.Objects;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.tatami.virtualworld.client.ShockOverlayShaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

public final class ShockTextRenderTarget extends RenderTarget {
	public ShockTextRenderTarget(int width, int height) {
		super(false);
		this.resize(width, height, Minecraft.ON_OSX);
		this.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
	}

	public void blitToScreen(int width, int height, float timeSeconds, float alpha, boolean disableBlend) {
		RenderSystem.assertOnRenderThread();
		GlStateManager._colorMask(true, true, true, false);
		GlStateManager._disableDepthTest();
		GlStateManager._depthMask(false);
		GlStateManager._viewport(0, 0, width, height);
		if (disableBlend) {
			GlStateManager._disableBlend();
		}

		Minecraft minecraft = Minecraft.getInstance();
		ShaderInstance shader = Objects.requireNonNullElse(ShockOverlayShaders.shockTextBlitShader(), minecraft.gameRenderer.blitShader);
		shader.setSampler("DiffuseSampler", this.getColorTextureId());
		shader.safeGetUniform("Time").set(timeSeconds);
		shader.safeGetUniform("Alpha").set(alpha);
		shader.apply();
		BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
		bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
		bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
		bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
		bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
		BufferUploader.draw(bufferBuilder.buildOrThrow());
		shader.clear();
		GlStateManager._depthMask(true);
		GlStateManager._colorMask(true, true, true, true);
	}
}

