package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tatami.virtualcraft.client.model.VirtualMushroomKingGeoModel;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class VirtualMushroomKingRenderer extends GeoEntityRenderer<VirtualMushroomKing> {
	private static final double MODEL_GROUND_OFFSET = 6.0 / 16.0;

	public VirtualMushroomKingRenderer(EntityRendererProvider.Context context) {
		super(context, new VirtualMushroomKingGeoModel());
		shadowRadius = 1.45F;
		addRenderLayer(new MushroomKingEmissiveLayer(this));
		addRenderLayer(new MushroomKingSignalLayer(this));
	}

	@Override
	public void render(VirtualMushroomKing boss, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
		// The authored feet extend six model pixels below y=0. Lift only the visual
		// model so the soles meet the entity ground plane without changing gameplay.
		poseStack.pushPose();
		poseStack.translate(0.0, MODEL_GROUND_OFFSET, 0.0);
		super.render(boss, entityYaw, partialTick, poseStack, buffers, packedLight);
		poseStack.popPose();
	}

	@Override
	public int getPackedOverlay(VirtualMushroomKing boss, float u, float partialTick) {
		int attack = boss.getAttackState();
		if (MushroomKingAttackRules.isKnownAttack(attack)
				&& boss.getAttackDirection() != BossAttackDirection.MID) {
			return OverlayTexture.NO_OVERLAY;
		}
		return super.getPackedOverlay(boss, u, partialTick);
	}
}
