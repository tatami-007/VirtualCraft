package dev.tatami.virtualcraft.client.model;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Static Blockbench rework, without legacy form swaps or breakable-armour bones. */
public final class VirtualMushroomKingGeoModel extends GeoModel<VirtualMushroomKing> {
    private static final ResourceLocation MODEL = id("geo/virtual_mushroom_king.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/mushroom_king/virtual_mushroom_king.png");
    private static final ResourceLocation ANIMATION = id("animations/virtual_mushroom_king.animation.json");

    @Override
    public ResourceLocation getModelResource(VirtualMushroomKing entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(VirtualMushroomKing entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(VirtualMushroomKing entity) {
        return ANIMATION;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path);
    }
}
