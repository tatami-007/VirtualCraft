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
	private static final ResourceLocation SIGNAL_RED = signalTexture("red", false);
	private static final ResourceLocation SIGNAL_RED_CORE = signalTexture("red", true);
	private static final ResourceLocation SIGNAL_BLUE = signalTexture("blue", false);
	private static final ResourceLocation SIGNAL_BLUE_CORE = signalTexture("blue", true);
	private static final ResourceLocation SIGNAL_WHITE = signalTexture("white", false);
	private static final ResourceLocation SIGNAL_WHITE_CORE = signalTexture("white", true);

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
		float pulse = strength >= 0.70F
				? 0.5F + 0.5F * Mth.sin((snake.getAttackTick() + partialTick) * 2.35F)
				: 0.0F;
		float bodyStrength = Mth.clamp((strength - 0.16F) / 0.84F, 0.0F, 1.0F);
		renderMask(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
				textureFor(signal, false), signalAlpha(bodyStrength, pulse, false));
		renderMask(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
				textureFor(signal, true), signalAlpha(strength, pulse, true));
	}

	private void renderMask(PoseStack poseStack, VirtualSnakeKing snake, BakedGeoModel bakedModel,
			MultiBufferSource bufferSource, float partialTick, int packedOverlay,
			ResourceLocation texture, int color) {
		// RenderType.eyes ignores vertex tint, which made every colored cue appear white.
		RenderType signalType = RenderType.entityTranslucentEmissive(texture);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, snake, signalType,
				bufferSource.getBuffer(signalType), partialTick, 0xF000F0, packedOverlay, color);
	}

	private static int signalAlpha(float strength, float pulse, boolean core) {
		float baseAlpha = core ? 0.48F : 0.28F;
		float peakGain = core ? 0.44F : 0.48F;
		float pulseGain = core ? 0.09F : 0.07F;
		int alpha = Math.round(Mth.clamp(baseAlpha + strength * peakGain + pulse * pulseGain,
				0.0F, core ? 0.96F : 0.82F) * 255.0F);
		return alpha << 24 | 0xFFFFFF;
	}

	private static ResourceLocation textureFor(int signal, boolean core) {
		return switch (signal) {
			case VirtualSnakeKing.SIGNAL_RED -> core ? SIGNAL_RED_CORE : SIGNAL_RED;
			case VirtualSnakeKing.SIGNAL_BLUE -> core ? SIGNAL_BLUE_CORE : SIGNAL_BLUE;
			default -> core ? SIGNAL_WHITE_CORE : SIGNAL_WHITE;
		};
	}

	private static ResourceLocation signalTexture(String color, boolean core) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,
				"textures/entity/virtual_snake_king_signal_" + color + (core ? "_core" : "") + ".png");
	}
}
