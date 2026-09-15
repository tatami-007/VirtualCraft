package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Textured fungal-rune signal pass rendered after the base model and hurt overlay. */
final class MushroomKingSignalLayer extends GeoRenderLayer<VirtualMushroomKing> {
	private static final ResourceLocation SIGNAL_MASK = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/mushroom_king/virtual_mushroom_king_signal_mask.png");
	private static final ResourceLocation SIGNAL_BODY = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/entity/mushroom_king/virtual_mushroom_king_signal_body.png");

	MushroomKingSignalLayer(GeoRenderer<VirtualMushroomKing> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, VirtualMushroomKing boss, BakedGeoModel bakedModel,
			RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
			float partialTick, int packedLight, int packedOverlay) {
		int attack = boss.getAttackState();
		if (!MushroomKingAttackRules.isKnownAttack(attack)) {
			return;
		}
		BossAttackDirection direction = boss.getAttackDirection();
		boolean ultimate = MushroomKingAttackRules.isUnavoidableAttack(attack);
		if (direction == BossAttackDirection.MID && !ultimate) {
			return;
		}
		float progress = signalProgress(boss, partialTick);
		float pulse = progress > 0.76F
				? 0.5F + 0.5F * Mth.sin((boss.getAttackTick() + partialTick) * 1.15F)
				: 0.0F;
		int color = ultimate ? ultimateSignalColor(boss, progress, pulse)
				: signalColor(direction, progress, pulse);
		int bodyAlpha = Math.round(Mth.clamp((ultimate ? 0.22F : 0.16F)
				+ progress * (ultimate ? 0.52F : 0.48F) + pulse * 0.08F,
				0.0F, ultimate ? 0.82F : 0.74F) * 255.0F);
		RenderType bodyType = RenderType.entityTranslucentEmissive(SIGNAL_BODY);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, boss, bodyType,
				bufferSource.getBuffer(bodyType), partialTick, 0xF000F0,
				OverlayTexture.NO_OVERLAY, bodyAlpha << 24 | color & 0xFFFFFF);
		RenderType signalType = RenderType.entityTranslucentEmissive(SIGNAL_MASK);
		getRenderer().reRender(bakedModel, poseStack, bufferSource, boss, signalType,
				bufferSource.getBuffer(signalType), partialTick, 0xF000F0,
				OverlayTexture.NO_OVERLAY, 0xF0000000 | color & 0xFFFFFF);
	}

	private static int ultimateSignalColor(VirtualMushroomKing boss, float progress, float pulse) {
		float alternation = 0.5F + 0.5F * Mth.sin(boss.getAttackTick() * 0.22F);
		int teal = lerpColor(0x16534E, 0x75F0D6, progress);
		int amber = lerpColor(0x6E3217, 0xFFC96B, progress);
		return 0xFF000000 | lerpColor(teal, amber,
				Mth.clamp(alternation * 0.72F + pulse * 0.28F, 0.0F, 1.0F));
	}

	private static float signalProgress(VirtualMushroomKing boss, float partialTick) {
		var combo = boss.getComboPattern();
		if (combo != null) {
			int stepIndex = combo.cueStepIndex(boss.getAttackTick());
			int cue = combo.cueTick(stepIndex);
			int impact = combo.step(stepIndex).impactTick();
			return Mth.clamp((boss.getAttackTick() + partialTick - cue)
					/ Math.max(1.0F, impact - cue), 0.0F, 1.0F);
		}
		int warning = Math.max(1, MushroomKingAttackRules.warningTicks(boss.getAttackState()));
		return Mth.clamp((boss.getAttackTick() + partialTick) / warning, 0.0F, 1.0F);
	}

	private static int signalColor(BossAttackDirection direction, float progress, float pulse) {
		float intensity = Mth.clamp(0.22F + progress * 0.48F + pulse * 0.22F, 0.0F, 1.0F);
		int rgb = switch (direction) {
			case HIGH -> lerpColor(0x07527C, 0x49CFFF, intensity);
			case MID -> 0xFFF4D2;
			case LOW -> lerpColor(0x805006, 0xFFC83D, intensity);
		};
		return 0xFF000000 | rgb;
	}

	private static int lerpColor(int from, int to, float progress) {
		int red = Mth.lerpInt(progress, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
		int green = Mth.lerpInt(progress, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
		int blue = Mth.lerpInt(progress, from & 0xFF, to & 0xFF);
		return red << 16 | green << 8 | blue;
	}
}
