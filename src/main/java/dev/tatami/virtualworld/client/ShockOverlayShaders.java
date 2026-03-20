package dev.tatami.virtualworld.client;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import dev.tatami.virtualworld.VirtualWorld;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class ShockOverlayShaders {
	private static ShaderInstance shockTextBlitShader;

	private ShockOverlayShaders() {
	}

	public static void register(RegisterShadersEvent event) throws IOException {
		event.registerShader(
				new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(VirtualWorld.MODID, "shock_text_blit"), DefaultVertexFormat.BLIT_SCREEN),
				shader -> shockTextBlitShader = shader);
	}

	public static ShaderInstance shockTextBlitShader() {
		return shockTextBlitShader;
	}
}

