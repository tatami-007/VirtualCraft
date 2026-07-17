package dev.tatami.virtualcraft.client.model;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class VirtualSnakeKingGeoModel extends GeoModel<VirtualSnakeKing> {
	private static final ResourceLocation MODEL = id("geo/virtual_snake_king.geo.json");
	private static final ResourceLocation TEXTURE = id("textures/entity/virtual_snake_king.png");
	private static final ResourceLocation ANIMATION = id("animations/virtual_snake_king.animation.json");

	@Override
	public ResourceLocation getModelResource(VirtualSnakeKing snake) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(VirtualSnakeKing snake) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(VirtualSnakeKing snake) {
		return ANIMATION;
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path);
	}
}
