package dev.tatami.virtualcraft.client.render;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSheep;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class VirtualSheepRenderer extends MobRenderer<VirtualSheep, SheepModel<VirtualSheep>> {
	static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "textures/entity/virtual_sheep.png");

	public VirtualSheepRenderer(EntityRendererProvider.Context context) {
		super(context, new SheepModel<>(context.bakeLayer(ModelLayers.SHEEP)), 0.7F);
		this.addLayer(new VirtualSheepFurLayer(this, context.getModelSet()));
	}

	@Override
	public ResourceLocation getTextureLocation(VirtualSheep entity) {
		return TEXTURE;
	}
}
