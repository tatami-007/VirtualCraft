package dev.tatami.virtualcraft.client.model;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib model with independent breakable spore-armour visibility. */
public final class VirtualMushroomKingGeoModel extends GeoModel<VirtualMushroomKing> {
	private static final ResourceLocation MODEL = id("geo/virtual_mushroom_king.geo.json");
	private static final ResourceLocation TEXTURE = id("textures/entity/mushroom_king/virtual_mushroom_king.png");
	private static final ResourceLocation RED_TEXTURE =
			id("textures/entity/mushroom_king/virtual_mushroom_king_red.png");
	private static final ResourceLocation GREEN_TEXTURE =
			id("textures/entity/mushroom_king/virtual_mushroom_king_green.png");
	private static final ResourceLocation PURPLE_TEXTURE =
			id("textures/entity/mushroom_king/virtual_mushroom_king_purple.png");
	private static final ResourceLocation ANIMATION = id("animations/virtual_mushroom_king.animation.json");

	@Override
	public ResourceLocation getModelResource(VirtualMushroomKing animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(VirtualMushroomKing animatable) {
		return switch (animatable.getForm()) {
			case VirtualMushroomKing.FORM_RED -> RED_TEXTURE;
			case VirtualMushroomKing.FORM_GREEN -> GREEN_TEXTURE;
			case VirtualMushroomKing.FORM_PURPLE -> PURPLE_TEXTURE;
			default -> TEXTURE;
		};
	}

	@Override
	public ResourceLocation getAnimationResource(VirtualMushroomKing animatable) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(VirtualMushroomKing boss, long instanceId,
			AnimationState<VirtualMushroomKing> state) {
		super.setCustomAnimations(boss, instanceId, state);
		setPlateHidden("plate_shoulder_left", shouldHide(boss, 0, boss.getLeftShoulderArmor()));
		setPlateHidden("plate_shoulder_right", shouldHide(boss, 1, boss.getRightShoulderArmor()));
		setPlateHidden("plate_chest", shouldHide(boss, 2, boss.getChestArmor()));
		setPlateHidden("plate_back", shouldHide(boss, 3, boss.getBackArmor()));
	}

	private static boolean shouldHide(VirtualMushroomKing boss, int plate, int durability) {
		return durability <= 0 && (boss.getBreakingPlate() != plate || boss.getPlateBreakTicks() <= 0);
	}

	private void setPlateHidden(String boneName, boolean hidden) {
		getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path);
	}
}
