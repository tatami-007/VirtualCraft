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

/** Faceted spore pods with orbiting kernels and impact-driven expansion/fade. */
public final class MushroomSporeMassRenderer extends EntityRenderer<MushroomSporeMassEntity> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/effect/mushroom_king/spore_pod.png");
	private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);
	private static final RenderType HALO = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID,"textures/effect/mushroom_king/spore_orb.png"));

	public MushroomSporeMassRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(MushroomSporeMassEntity entity, float yaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		float scale = entity.visualScale(partialTick);
		float alpha=entity.visualAlpha(partialTick);
		int color = FastColor.ARGB32.color(Math.round(255*alpha), 255, 255, 255);
		poseStack.pushPose();
		poseStack.translate(0.0, 0.55, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 2.2F));
		poseStack.scale(scale, scale, scale);
		VertexConsumer vertices = buffers.getBuffer(RENDER_TYPE);
		drawPodLayer(poseStack, vertices, color, 1.0F, 0.0F, 0.0F, 0.0F);
		drawPodLayer(poseStack, vertices, color, 0.42F, 0.42F, -0.22F, -0.14F);
		drawPodLayer(poseStack, vertices, color, 0.31F, -0.38F, -0.32F, 0.2F);
		for (int satellite = 0; satellite < 5; satellite++) {
			double angle = satellite * 2.399963;
			float radius = satellite % 2 == 0 ? 0.53F : 0.42F;
			drawPodLayer(poseStack, vertices, color,
					0.065F + (satellite % 3) * 0.025F,
					(float)Math.cos(angle) * radius,
					-0.08F + (satellite % 2) * 0.36F,
					(float)Math.sin(angle) * radius);
		}
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.translate(0,.55,0);
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		float radius=scale*0.65F;
		PoseStack.Pose haloPose=poseStack.last();
		VertexConsumer light=buffers.getBuffer(HALO);
		int haloColor=FastColor.ARGB32.color(Math.round(24*alpha),223,157,178);
		haloVertex(light,haloPose,-radius,-radius,0,1,haloColor);
		haloVertex(light,haloPose,-radius,radius,0,0,haloColor);
		haloVertex(light,haloPose,radius,radius,1,0,haloColor);
		haloVertex(light,haloPose,radius,-radius,1,1,haloColor);
		poseStack.popPose();
		super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
	}

	private static void haloVertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v,int color) {
		out.addVertex(pose,x,y,0).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(0xF000F0).setNormal(pose,0,0,-1);
	}

	private static void drawPodLayer(PoseStack stack, VertexConsumer vertices, int color,
			float scale, float x, float y, float z) {
		stack.pushPose();
		stack.translate(x, y, z);
		stack.scale(scale, scale, scale);
		drawHull(stack, vertices, color);
		stack.popPose();
	}

	private static void drawHull(PoseStack stack, VertexConsumer vertices, int color) {
		PoseStack.Pose pose = stack.last();
		float[] heights={-.65F,-.12F,.08F,.52F}, radii={.09F,.60F,.69F,.05F};
		for(int band=0;band<3;band++) for(int face=0;face<8;face++) {
			double a=face*Math.PI/4, next=(face+1)*Math.PI/4;
			hullVertex(vertices,pose,a,radii[band],heights[band],face/8F,band/3F,color);
			hullVertex(vertices,pose,next,radii[band],heights[band],(face+1)/8F,band/3F,color);
			hullVertex(vertices,pose,next,radii[band+1],heights[band+1],(face+1)/8F,(band+1)/3F,color);
			hullVertex(vertices,pose,a,radii[band+1],heights[band+1],face/8F,(band+1)/3F,color);
		}
	}

	private static void hullVertex(VertexConsumer consumer, PoseStack.Pose pose,
			double angle, float radius, float y, float u, float v, int color) {
		float normalLength=(float)Math.sqrt(.64F+y*y);
		float shade=(float)(.55+.45*Math.max(0,Math.cos(angle)*-.35+Math.sin(angle)*.39+y*.6));
		int shaded=FastColor.ARGB32.color(FastColor.ARGB32.alpha(color),
				(int)(FastColor.ARGB32.red(color)*shade),(int)(FastColor.ARGB32.green(color)*shade),
				(int)(FastColor.ARGB32.blue(color)*shade));
		consumer.addVertex(pose, (float)Math.cos(angle)*radius, y, (float)Math.sin(angle)*radius).setColor(shaded).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
				.setNormal(pose, (float)Math.cos(angle)*.8F/normalLength, y/normalLength,
						(float)Math.sin(angle)*.8F/normalLength);
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
