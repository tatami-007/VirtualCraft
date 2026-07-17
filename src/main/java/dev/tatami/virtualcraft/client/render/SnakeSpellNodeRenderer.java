package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.entity.SnakeSpellNode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Visible spell volume for delayed snake attacks; damage still remains server-authoritative. */
public final class SnakeSpellNodeRenderer extends EntityRenderer<SnakeSpellNode> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/effect/snake_telegraph.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

	public SnakeSpellNodeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getBlockLightLevel(SnakeSpellNode entity, BlockPos pos) {
		return 15;
	}

	@Override
	public void render(SnakeSpellNode entity, float yaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int packedLight) {
		float fuse = Math.max(1.0F, entity.getFuseTicks());
		float progress = Mth.clamp((entity.tickCount + partialTick) / fuse, 0.0F, 1.0F);
		float pulse = 0.82F + 0.18F * Mth.sin((entity.tickCount + partialTick) * 0.65F);
		float radius = entity.getEffectRadius() * (0.72F + progress * 0.28F);
		int[] color = color(entity.getAttackDirection());
		int alpha = Math.round((0.56F + progress * 0.36F) * pulse * 255.0F);
		VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);

		poseStack.pushPose();
		poseStack.translate(0.0, 0.055, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 2.8F));
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		poseStack.scale(radius, radius, radius);
		drawQuad(vertices, poseStack.last(), color[0], color[1], color[2], alpha);
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate(0.0, 0.28 + progress * 0.38, 0.0);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		float crest = 0.42F + progress * 0.32F;
		poseStack.scale(crest, crest, crest);
		drawQuad(vertices, poseStack.last(), 255, 245, 218, Math.min(255, alpha + 28));
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static int[] color(BossAttackDirection direction) {
		return switch (direction) {
			case HIGH -> new int[]{32, 176, 255};
			case MID -> new int[]{255, 232, 166};
			case LOW -> new int[]{238, 48, 52};
		};
	}

	private static void drawQuad(VertexConsumer consumer, PoseStack.Pose pose,
			int red, int green, int blue, int alpha) {
		vertex(consumer, pose, -1.0F, -1.0F, 0.0F, 1.0F, red, green, blue, alpha);
		vertex(consumer, pose, 1.0F, -1.0F, 1.0F, 1.0F, red, green, blue, alpha);
		vertex(consumer, pose, 1.0F, 1.0F, 1.0F, 0.0F, red, green, blue, alpha);
		vertex(consumer, pose, -1.0F, 1.0F, 0.0F, 0.0F, red, green, blue, alpha);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
			float u, float v, int red, int green, int blue, int alpha) {
		consumer.addVertex(pose, x, y, 0.0F).setColor(red, green, blue, alpha).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public ResourceLocation getTextureLocation(SnakeSpellNode entity) {
		return TEXTURE;
	}
}
