package dev.tatami.virtualcraft.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public final class ModToolTiers {
	private ModToolTiers() {
	}

	public static final Tier VIRTUAL_IRON = new SimpleTier(
			BlockTags.INCORRECT_FOR_IRON_TOOL, 1050, 7.5F, 3.0F, 16,
			() -> Ingredient.of(ModItems.VIRTUAL_IRON_INGOT.get()));
	public static final Tier VIRTUAL_SILVER = new SimpleTier(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1700, 8.5F, 3.75F, 22,
			() -> Ingredient.of(ModItems.VIRTUAL_SILVER_INGOT.get()));
	public static final Tier VIRTUAL_TITANIUM = new SimpleTier(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2800, 10.0F, 4.5F, 20,
			() -> Ingredient.of(ModItems.VIRTUAL_TITANIUM_INGOT.get()));
	public static final Tier VIRTUAL_CHROMIUM = new SimpleTier(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4200, 11.0F, 5.5F, 24,
			() -> Ingredient.of(ModItems.VIRTUAL_CHROMIUM_INGOT.get()));
	public static final Tier VIRTUAL_SNAKE_KING = new SimpleTier(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4200, 11.0F, 5.5F, 24,
			() -> Ingredient.of(ModItems.SNAKE_SCALE.get()));
	public static final Tier VIRTUAL_MUSHROOM_KING = new SimpleTier(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 5200, 12.0F, 6.5F, 28,
			() -> Ingredient.of(ModItems.MUSHROOM_KING_SPORE.get()));
}
