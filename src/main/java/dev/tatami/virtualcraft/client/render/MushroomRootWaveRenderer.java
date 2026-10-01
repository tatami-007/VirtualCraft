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
import org.joml.Vector3f;

/** A low, branching mycelial ridge. It deliberately avoids translucent cones. */
public final class MushroomRootWaveRenderer extends EntityRenderer<MushroomRootWaveEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/effect/mushroom_king/root_bark.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);
	private static final RenderType VEINS = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID,"textures/effect/mushroom_king/root_veins.png"));

	public MushroomRootWaveRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(MushroomRootWaveEntity entity, float yaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		float scale = Math.max(0, entity.visualScale(partialTick));
		if (scale < 0.001F) return;
		float footprint = entity.visualFootprintScale();
		float alpha=entity.visualAlpha(partialTick);
		int barkColor = FastColor.ARGB32.color(Math.round(255*alpha), 255, 255, 255);
		int veinColor=FastColor.ARGB32.color(Math.round(35*alpha),92,255,210);
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
		// Finish each material before requesting another buffer; a buffer switch may flush it.
		for(int pass=0;pass<2;pass++) {
		VertexConsumer vertices = buffers.getBuffer(pass==0?RENDER_TYPE:VEINS);
		int color=pass==0?barkColor:veinColor;
		int materialLight=pass==0?packedLight:0xF000F0;
		float shell=pass==0?1:1.025F;
		for (int segment = 0; segment < 6; segment++) {
			poseStack.pushPose();
			float side = ((entity.getVariant() + segment) & 1) == 0 ? -0.12F : 0.12F;
			poseStack.translate(side, 0.03F, segment * 0.48F - 1.18F);
			poseStack.mulPose(Axis.YP.rotationDegrees(((segment & 1) == 0 ? -1.0F : 1.0F)
					* (5.0F + entity.getVariant() * 1.5F)));
			float localScale = scale * (1.0F - segment * 0.055F)*shell;
			float localFootprint = footprint * (1.0F - segment * 0.055F) * shell;
			poseStack.scale(localFootprint, localScale, localFootprint);
			drawRootBlock(poseStack.last(), vertices,
					0.34F - segment * 0.018F,
					0.30F + (segment % 3) * 0.10F,
					0.31F, color, materialLight);
			poseStack.popPose();

			if (segment >= 1 && segment <= 4) {
				poseStack.pushPose();
				float branchSide = ((entity.getVariant() + segment) & 1) == 0 ? -1.0F : 1.0F;
				poseStack.translate(branchSide * (0.24F + segment * 0.025F), 0.025F,
						segment * 0.48F - 1.15F);
				poseStack.mulPose(Axis.YP.rotationDegrees(branchSide * (31.0F + segment * 3.0F)));
				poseStack.scale(footprint*shell, scale*shell, footprint*shell);
				drawRootBlock(poseStack.last(), vertices, 0.18F, 0.12F + segment * 0.018F,
						0.34F, color, materialLight);
				poseStack.popPose();
			}
		}
		}
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void drawRootBlock(PoseStack.Pose pose, VertexConsumer vertices,
			float halfWidth, float height, float halfLength, int color, int packedLight) {
		// Six-sided tapering root with an offset crest. Replaces flat cuboid slabs.
		for(int face=0;face<6;face++) {
			double a=face*Math.PI/3, next=(face+1)*Math.PI/3;
			float x0=(float)Math.cos(a)*halfWidth, z0=(float)Math.sin(a)*halfLength;
			float x1=(float)Math.cos(next)*halfWidth, z1=(float)Math.sin(next)*halfLength;
			quad(vertices,pose,
					x0,0,z0,0,1, x1,0,z1,1,1,
					x1*.22F,height,z1*.4F+.09F,1,0,
					x0*.22F,height,z0*.4F+.09F,0,0,color,packedLight);
		}
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
			float x0, float y0, float z0, float u0, float v0,
			float x1, float y1, float z1, float u1, float v1,
			float x2, float y2, float z2, float u2, float v2,
			float x3, float y3, float z3, float u3, float v3,
			int color, int packedLight) {
		Vector3f normal=new Vector3f(x2-x0,y2-y0,z2-z0)
				.cross(new Vector3f(x1-x0,y1-y0,z1-z0)).normalize();
		vertex(consumer, pose, x0, y0, z0, u0, v0, color, packedLight, normal);
		vertex(consumer, pose, x1, y1, z1, u1, v1, color, packedLight, normal);
		vertex(consumer, pose, x2, y2, z2, u2, v2, color, packedLight, normal);
		vertex(consumer, pose, x3, y3, z3, u3, v3, color, packedLight, normal);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
			float x, float y, float z, float u, float v, int color, int packedLight, Vector3f normal) {
		consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
				.setNormal(pose, normal.x, normal.y, normal.z);
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
