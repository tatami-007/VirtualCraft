package dev.tatami.virtualcraft.client.render;

import dev.tatami.virtualcraft.client.model.VirtualMushroomKingGeoModel;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Native 6.4375-block model scale; authored feet already start at Y=0. */
public final class VirtualMushroomKingRenderer extends GeoEntityRenderer<VirtualMushroomKing> {
    public VirtualMushroomKingRenderer(EntityRendererProvider.Context context) {
        super(context, new VirtualMushroomKingGeoModel());
        shadowRadius = 2.5F;
        addRenderLayer(new MushroomKingEmissiveLayer(this));
    }

    @Override protected float getDeathMaxRotation(VirtualMushroomKing entity) { return 0; }
}
