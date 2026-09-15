package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.SnakeFangProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class SnakeFangRenderer extends EntityRenderer<SnakeFangProjectile> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/snake_king/snake_fang.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

	public SnakeFangRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected int getBlockLightLevel(SnakeFangProjectile entity, BlockPos pos) {
		return 15;
	}

	@Override
	public void render(SnakeFangProjectile entity, float yaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int packedLight) {
		poseStack.pushPose();
		poseStack.scale(0.85F, 0.85F, 0.85F);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
		vertex(vertices, pose, -0.5F, -0.5F, 0.0F, 1.0F);
		vertex(vertices, pose, 0.5F, -0.5F, 1.0F, 1.0F);
		vertex(vertices, pose, 0.5F, 0.5F, 1.0F, 0.0F);
		vertex(vertices, pose, -0.5F, 0.5F, 0.0F, 0.0F);
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v) {
		consumer.addVertex(pose, x, y, 0.0F).setColor(-1).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public ResourceLocation getTextureLocation(SnakeFangProjectile entity) {
		return TEXTURE;
	}
}
