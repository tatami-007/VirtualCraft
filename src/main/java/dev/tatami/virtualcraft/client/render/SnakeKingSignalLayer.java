package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

final class SnakeKingSignalLayer extends GeoRenderLayer<VirtualSnakeKing> {
	private static final ResourceLocation SIGNAL_BODY = signalTexture(false);
	private static final ResourceLocation SIGNAL_CORE = signalTexture(true);

	SnakeKingSignalLayer(GeoRenderer<VirtualSnakeKing> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, VirtualSnakeKing snake, BakedGeoModel bakedModel,
			RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		int signal = snake.getSignalType();
		float strength = snake.getSignalStrength();
		if (signal == VirtualSnakeKing.SIGNAL_NONE || strength <= 0.01F) {
			return;
		}

		// The body pass makes the attack direction readable across the entire snake,
		// while the core pass keeps the crown, eyes and hood as the visual focus.
		float pulse = strength >= 0.76F
				? 0.5F + 0.5F * Mth.sin((snake.getAttackTick() + partialTick) * 1.55F)
				: 0.0F;
		float bodyStrength = Mth.clamp((strength - 0.10F) / 0.90F, 0.0F, 1.0F);
		renderMask(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
				SIGNAL_BODY, signalColor(signal, bodyStrength, pulse, false));
		renderMask(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
				SIGNAL_CORE, signalColor(signal, strength, pulse, true));
	}

	private void renderMask(PoseStack poseStack, VirtualSnakeKing snake, BakedGeoModel bakedModel,
			MultiBufferSource bufferSource, float partialTick, int packedOverlay,
			ResourceLocation texture, int color) {
		// RenderType.eyes ignores vertex tint, which made every colored cue appear white.
		RenderType signalType = RenderType.entityTranslucentEmissive(texture);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, snake, signalType,
				bufferSource.getBuffer(signalType), partialTick, 0xF000F0, packedOverlay, color);
	}

	private static int signalColor(int signal, float strength, float pulse, boolean core) {
		float baseAlpha = core ? 0.30F : 0.10F;
		float peakGain = core ? 0.48F : 0.40F;
		float pulseGain = core ? 0.08F : 0.05F;
		int alpha = Math.round(Mth.clamp(baseAlpha + strength * peakGain + pulse * pulseGain,
				0.0F, core ? 0.86F : 0.56F) * 255.0F);
		int rgb = switch (signal) {
			case VirtualSnakeKing.SIGNAL_AMBER -> core ? 0xF2C45B : 0xB87816;
			case VirtualSnakeKing.SIGNAL_BLUE -> core ? 0x84D9F4 : 0x176FAE;
			default -> core ? 0xF2C45B : 0xB87816;
		};
		return alpha << 24 | rgb;
	}

	private static ResourceLocation signalTexture(boolean core) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,
				"textures/entity/snake_king/virtual_snake_king_signal" + (core ? "_core" : "") + ".png");
	}
}
