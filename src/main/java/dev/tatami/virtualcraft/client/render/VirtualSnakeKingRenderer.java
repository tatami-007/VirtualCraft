package dev.tatami.virtualcraft.client.render;

import dev.tatami.virtualcraft.client.model.VirtualSnakeKingGeoModel;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class VirtualSnakeKingRenderer extends GeoEntityRenderer<VirtualSnakeKing> {
	public VirtualSnakeKingRenderer(EntityRendererProvider.Context context) {
		super(context, new VirtualSnakeKingGeoModel());
		withScale(VirtualSnakeKing.RENDER_SCALE);
		shadowRadius = 1.55F * VirtualSnakeKing.GAMEPLAY_SCALE;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new SnakeKingAfterimageLayer(this));
		addRenderLayer(new SnakeKingSignalLayer(this));
	}
}
