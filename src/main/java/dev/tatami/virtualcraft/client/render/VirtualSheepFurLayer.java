package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tatami.virtualcraft.entity.VirtualSheep;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class VirtualSheepFurLayer extends RenderLayer<VirtualSheep, SheepModel<VirtualSheep>> {
	private static final int WHITE_TINT = 0xFFFFFFFF;

	private final SheepFurModel<VirtualSheep> model;

	public VirtualSheepFurLayer(RenderLayerParent<VirtualSheep, SheepModel<VirtualSheep>> renderer, EntityModelSet modelSet) {
		super(renderer);
		this.model = new SheepFurModel<>(modelSet.bakeLayer(ModelLayers.SHEEP_FUR));
	}

	@Override
	public void render(
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			VirtualSheep entity,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch) {
		coloredCutoutModelCopyLayerRender(
				this.getParentModel(),
				this.model,
				VirtualSheepRenderer.TEXTURE,
				poseStack,
				buffer,
				packedLight,
				entity,
				limbSwing,
				limbSwingAmount,
				ageInTicks,
				netHeadYaw,
				headPitch,
				partialTick,
				WHITE_TINT);
	}
}
