package dev.tatami.virtualcraft.client.overlay;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import dev.tatami.virtualcraft.VirtualCraft;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class ShockOverlayShaders {
	private static ShaderInstance shockTextBlitShader;

	private ShockOverlayShaders() {
	}

	public static void register(RegisterShadersEvent event) throws IOException {
		event.registerShader(
				new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "shock_text_blit"), DefaultVertexFormat.BLIT_SCREEN),
				shader -> shockTextBlitShader = shader);
	}

	public static ShaderInstance shockTextBlitShader() {
		return shockTextBlitShader;
	}
}
