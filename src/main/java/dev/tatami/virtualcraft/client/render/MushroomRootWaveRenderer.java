package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.MushroomRootWaveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** A low, branching mycelial ridge. It deliberately avoids translucent cones. */
public final class MushroomRootWaveRenderer extends EntityRenderer<MushroomRootWaveEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/mushroom_king/root_wave.png");
	private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

	public MushroomRootWaveRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(MushroomRootWaveEntity entity, float yaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		float scale = entity.visualScale(partialTick);
		int color = FastColor.ARGB32.color(255, 255, 255, 255);
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
		VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
		for (int segment = 0; segment < 6; segment++) {
			poseStack.pushPose();
			float side = ((entity.getVariant() + segment) & 1) == 0 ? -0.12F : 0.12F;
			poseStack.translate(side, 0.03F, segment * 0.48F - 1.18F);
			poseStack.mulPose(Axis.YP.rotationDegrees(((segment & 1) == 0 ? -1.0F : 1.0F)
					* (5.0F + entity.getVariant() * 1.5F)));
			float localScale = scale * (1.0F - segment * 0.055F);
			poseStack.scale(localScale, localScale, localScale);
			drawRootBlock(poseStack.last(), vertices,
					0.34F - segment * 0.018F,
					0.18F + (segment % 3) * 0.07F,
					0.31F, color, packedLight);
			poseStack.popPose();

			if (segment >= 1 && segment <= 4) {
				poseStack.pushPose();
				float branchSide = ((entity.getVariant() + segment) & 1) == 0 ? -1.0F : 1.0F;
				poseStack.translate(branchSide * (0.24F + segment * 0.025F), 0.025F,
						segment * 0.48F - 1.15F);
				poseStack.mulPose(Axis.YP.rotationDegrees(branchSide * (31.0F + segment * 3.0F)));
				poseStack.scale(scale, scale, scale);
				drawRootBlock(poseStack.last(), vertices, 0.18F, 0.12F + segment * 0.018F,
						0.34F, color, packedLight);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void drawRootBlock(PoseStack.Pose pose, VertexConsumer vertices,
			float halfWidth, float height, float halfLength, int color, int packedLight) {
		quad(vertices, pose,
				-halfWidth, height, -halfLength, 0.0F, 0.0F,
				halfWidth, height, -halfLength, 1.0F, 0.0F,
				halfWidth, height, halfLength, 1.0F, 1.0F,
				-halfWidth, height, halfLength, 0.0F, 1.0F, color, packedLight);
		quad(vertices, pose,
				-halfWidth, 0.0F, halfLength, 0.0F, 1.0F,
				halfWidth, 0.0F, halfLength, 1.0F, 1.0F,
				halfWidth, height, halfLength, 1.0F, 0.0F,
				-halfWidth, height, halfLength, 0.0F, 0.0F, color, packedLight);
		quad(vertices, pose,
				halfWidth, 0.0F, -halfLength, 0.0F, 1.0F,
				-halfWidth, 0.0F, -halfLength, 1.0F, 1.0F,
				-halfWidth, height, -halfLength, 1.0F, 0.0F,
				halfWidth, height, -halfLength, 0.0F, 0.0F, color, packedLight);
		quad(vertices, pose,
				-halfWidth, 0.0F, -halfLength, 0.0F, 1.0F,
				-halfWidth, 0.0F, halfLength, 1.0F, 1.0F,
				-halfWidth, height, halfLength, 1.0F, 0.0F,
				-halfWidth, height, -halfLength, 0.0F, 0.0F, color, packedLight);
		quad(vertices, pose,
				halfWidth, 0.0F, halfLength, 0.0F, 1.0F,
				halfWidth, 0.0F, -halfLength, 1.0F, 1.0F,
				halfWidth, height, -halfLength, 1.0F, 0.0F,
				halfWidth, height, halfLength, 0.0F, 0.0F, color, packedLight);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
			float x0, float y0, float z0, float u0, float v0,
			float x1, float y1, float z1, float u1, float v1,
			float x2, float y2, float z2, float u2, float v2,
			float x3, float y3, float z3, float u3, float v3,
			int color, int packedLight) {
		vertex(consumer, pose, x0, y0, z0, u0, v0, color, packedLight);
		vertex(consumer, pose, x1, y1, z1, u1, v1, color, packedLight);
		vertex(consumer, pose, x2, y2, z2, u2, v2, color, packedLight);
		vertex(consumer, pose, x3, y3, z3, u3, v3, color, packedLight);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
			float x, float y, float z, float u, float v, int color, int packedLight) {
		consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	protected int getBlockLightLevel(MushroomRootWaveEntity entity, BlockPos pos) {
		return 15;
	}

	@Override
	public ResourceLocation getTextureLocation(MushroomRootWaveEntity entity) {
		return TEXTURE;
	}
}
