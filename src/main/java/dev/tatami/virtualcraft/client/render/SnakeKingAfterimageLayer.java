package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Three restrained model echoes that make each void dash readable without particle clutter. */
final class SnakeKingAfterimageLayer extends GeoRenderLayer<VirtualSnakeKing> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/virtual_snake_king.png");
	private static final ResourceLocation SUMMON_BLUE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/virtual_snake_king_signal_blue.png");
	private static final ResourceLocation CEREMONY_WHITE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/virtual_snake_king_signal_white.png");

	SnakeKingAfterimageLayer(GeoRenderer<VirtualSnakeKing> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, VirtualSnakeKing snake, BakedGeoModel bakedModel,
			RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		if (snake.getSummonTicks() > 0) {
			float progress = 1.0F - snake.getSummonTicks() / 60.0F;
			renderCeremonyEchoes(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
					progress < 0.62F ? SUMMON_BLUE : CEREMONY_WHITE, progress, false);
			return;
		}
		if (snake.deathTime > 0) {
			float progress = Math.min(1.0F, (snake.deathTime + partialTick) / 90.0F);
			renderCeremonyEchoes(poseStack, snake, bakedModel, bufferSource, partialTick, packedOverlay,
					CEREMONY_WHITE, progress, true);
			return;
		}
		if (snake.getAttackState() != VirtualSnakeKing.ATTACK_VOID_DASH) {
			return;
		}
		int tick = snake.getAttackTick();
		boolean firstDash = tick >= 11 && tick <= 19;
		boolean secondDash = tick >= 36 && tick <= 44;
		if (!firstDash && !secondDash) {
			return;
		}

		RenderType ghostType = RenderType.entityTranslucentEmissive(TEXTURE);
		float phase = firstDash ? (tick - 11 + partialTick) / 8.0F : (tick - 36 + partialTick) / 8.0F;
		for (int echo = 3; echo >= 1; echo--) {
			float alpha = (0.24F - echo * 0.035F) * (0.72F + phase * 0.28F);
			int rgb = echo == 1 ? 0xEFFFFF : echo == 2 ? 0x86DFF2 : 0xD7B85A;
			int color = Math.round(alpha * 255.0F) << 24 | rgb;
			poseStack.pushPose();
			poseStack.translate(0.0, 0.025 * echo, 0.9 * echo);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, snake, ghostType,
					bufferSource.getBuffer(ghostType), partialTick, 0xF000F0, packedOverlay, color);
			poseStack.popPose();
		}
	}

	private void renderCeremonyEchoes(PoseStack poseStack, VirtualSnakeKing snake,
			BakedGeoModel bakedModel, MultiBufferSource bufferSource, float partialTick,
			int packedOverlay, ResourceLocation texture, float progress, boolean expanding) {
		RenderType echoType = RenderType.entityTranslucentEmissive(texture);
		for (int echo = 2; echo >= 1; echo--) {
			float alpha = expanding
					? (0.08F + progress * 0.08F) / echo
					: (0.13F + progress * 0.05F) / echo;
			float scale = 1.0F + echo * (expanding ? 0.018F + progress * 0.025F : 0.016F);
			int color = Math.round(alpha * 255.0F) << 24 | 0xFFFFFF;
			poseStack.pushPose();
			poseStack.translate(0.0, expanding ? progress * 0.035 * echo : -0.025 * echo, 0.0);
			poseStack.scale(scale, scale, scale);
			getRenderer().reRender(bakedModel, poseStack, bufferSource, snake, echoType,
					bufferSource.getBuffer(echoType), partialTick, 0xF000F0, packedOverlay, color);
			poseStack.popPose();
		}
	}
}
