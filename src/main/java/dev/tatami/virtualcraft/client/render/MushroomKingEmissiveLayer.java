package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Depth-tested, constant-brightness eyes: two quads instead of a second body pass. */
final class MushroomKingEmissiveLayer extends GeoRenderLayer<VirtualMushroomKing> {
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            VirtualCraft.MODID, "geo/virtual_mushroom_king_eyes.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VirtualCraft.MODID, "textures/entity/mushroom_king/virtual_mushroom_king_emissive.png");

    MushroomKingEmissiveLayer(GeoRenderer<VirtualMushroomKing> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, VirtualMushroomKing entity, BakedGeoModel body,
            RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
            float partialTick, int packedLight, int packedOverlay) {
        BakedGeoModel eyes = GeckoLibCache.getBakedModels().get(EYES);
        if (eyes == null) {
            return;
        }
        RenderType emissive = RenderType.entityTranslucentEmissive(TEXTURE);
        for (String name : new String[]{"root", "hips", "torso", "head", "left_eye", "right_eye"}) {
            var source = body.getBone(name);
            var destination = eyes.getBone(name);
            if (source.isPresent() && destination.isPresent()) {
                var from = source.get();
                var to = destination.get();
                to.updateRotation(from.getRotX(), from.getRotY(), from.getRotZ());
                to.updatePosition(from.getPosX(), from.getPosY(), from.getPosZ());
                to.setScaleX(from.getScaleX());
                to.setScaleY(from.getScaleY());
                to.setScaleZ(from.getScaleZ());
            }
        }
        getRenderer().reRender(eyes, poseStack, buffers, entity, emissive,
                EffectBloom.buffer(buffers, emissive), partialTick, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
