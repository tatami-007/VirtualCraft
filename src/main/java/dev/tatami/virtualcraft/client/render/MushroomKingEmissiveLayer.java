package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

final class MushroomKingEmissiveLayer extends GeoRenderLayer<VirtualMushroomKing> {
	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/mushroom_king/virtual_mushroom_king_emissive.png");

	MushroomKingEmissiveLayer(GeoRenderer<VirtualMushroomKing> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, VirtualMushroomKing mushroomKing, BakedGeoModel bakedModel,
			RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		float pulse = 0.82F + 0.18F * Mth.sin((mushroomKing.tickCount + partialTick) * 0.12F);
		int alpha = Math.round(pulse * 255.0F);
		RenderType emissive = RenderType.entityTranslucentEmissive(TEXTURE);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, mushroomKing, emissive,
				bufferSource.getBuffer(emissive), partialTick, 0xF000F0, OverlayTexture.NO_OVERLAY,
				alpha << 24 | 0xFFFFFF);
	}
}
