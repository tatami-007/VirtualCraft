package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.MushroomSporeMassEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/** Compact rotating sprite cluster reads as a heavy spore pod rather than a beam. */
public final class MushroomSporeMassRenderer extends EntityRenderer<MushroomSporeMassEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/mushroom_king/spore_mass.png");
	private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

	public MushroomSporeMassRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(MushroomSporeMassEntity entity, float yaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		float scale = entity.visualScale(partialTick);
		int color = FastColor.ARGB32.color(255, 255, 255, 255);
		poseStack.pushPose();
		poseStack.translate(0.0, 0.55, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 7.0F));
		poseStack.scale(scale, scale, scale);
		VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
		drawPodLayer(poseStack, vertices, color, 1.0F, 0.0F, 0.0F, 0.0F);
		drawPodLayer(poseStack, vertices, color, 0.62F, 0.52F, 0.18F, -0.14F);
		drawPodLayer(poseStack, vertices, color, 0.56F, -0.46F, -0.24F, 0.2F);
		float orbit = (entity.tickCount + partialTick) * 0.16F;
		for (int satellite = 0; satellite < 5; satellite++) {
			double angle = orbit + Math.PI * 2.0 * satellite / 5.0;
			float radius = satellite % 2 == 0 ? 0.94F : 0.76F;
			drawPodLayer(poseStack, vertices, color,
					0.20F + (satellite % 3) * 0.035F,
					(float)Math.cos(angle) * radius,
					-0.08F + (satellite % 2) * 0.36F,
					(float)Math.sin(angle) * radius);
		}
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void drawPodLayer(PoseStack stack, VertexConsumer vertices, int color,
			float scale, float x, float y, float z) {
		stack.pushPose();
		stack.translate(x, y, z);
		stack.scale(scale, scale, scale);
		drawBillboard(stack, vertices, color);
		stack.mulPose(Axis.YP.rotationDegrees(60.0F));
		drawBillboard(stack, vertices, color);
		stack.mulPose(Axis.YP.rotationDegrees(60.0F));
		drawBillboard(stack, vertices, color);
		stack.popPose();
	}

	private static void drawBillboard(PoseStack stack, VertexConsumer vertices, int color) {
		PoseStack.Pose pose = stack.last();
		vertex(vertices, pose, -0.7F, -0.7F, 0.0F, 1.0F, color);
		vertex(vertices, pose, 0.7F, -0.7F, 1.0F, 1.0F, color);
		vertex(vertices, pose, 0.7F, 0.7F, 1.0F, 0.0F, color);
		vertex(vertices, pose, -0.7F, 0.7F, 0.0F, 0.0F, color);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
			float x, float y, float u, float v, int color) {
		consumer.addVertex(pose, x, y, 0.0F).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	protected int getBlockLightLevel(MushroomSporeMassEntity entity, BlockPos pos) {
		return 15;
	}

	@Override
	public ResourceLocation getTextureLocation(MushroomSporeMassEntity entity) {
		return TEXTURE;
	}
}
