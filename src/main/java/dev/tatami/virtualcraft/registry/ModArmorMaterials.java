package dev.tatami.virtualcraft.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualcraft.VirtualCraft;

public final class ModArmorMaterials {
	private ModArmorMaterials() {
	}

	public static final DeferredRegister<ArmorMaterial> REGISTRY =
			DeferredRegister.create(Registries.ARMOR_MATERIAL, VirtualCraft.MODID);

	/**
	 * Baseline heavy set: iron-gray plates, graphite joints, cyan data channels.
	 * Later virtual metals must declare their own profile instead of inheriting
	 * these values or silently becoming a recolored iron set.
	 */
	public static final ArmorSetProfile VIRTUAL_IRON_PROFILE =
			new ArmorSetProfile(32, 16, 3, 6, 8, 3, 8, 4.0F, 0.05F);
	public static final ArmorSetProfile VIRTUAL_SILVER_PROFILE =
			new ArmorSetProfile(38, 18, 4, 7, 9, 4, 9, 4.0F, 0.08F);
	public static final ArmorSetProfile VIRTUAL_TITANIUM_PROFILE =
			new ArmorSetProfile(45, 20, 5, 8, 10, 5, 10, 5.0F, 0.15F);
	public static final ArmorSetProfile VIRTUAL_CHROMIUM_PROFILE =
			new ArmorSetProfile(60, 24, 7, 10, 12, 7, 12, 7.0F, 0.25F);
	public static final ArmorSetProfile VIRTUAL_SNAKE_KING_PROFILE = VIRTUAL_CHROMIUM_PROFILE;
	public static final ArmorSetProfile VIRTUAL_MUSHROOM_KING_PROFILE =
			new ArmorSetProfile(72, 28, 8, 11, 13, 8, 13, 8.0F, 0.30F);

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_IRON = register(
			"virtual_iron", VIRTUAL_IRON_PROFILE, SoundEvents.ARMOR_EQUIP_IRON,
			() -> Ingredient.of(ModItems.VIRTUAL_IRON_INGOT.get()));
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_SILVER = register(
			"virtual_silver", VIRTUAL_SILVER_PROFILE, SoundEvents.ARMOR_EQUIP_CHAIN,
			() -> Ingredient.of(ModItems.VIRTUAL_SILVER_INGOT.get()));
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_TITANIUM = register(
			"virtual_titanium", VIRTUAL_TITANIUM_PROFILE, SoundEvents.ARMOR_EQUIP_DIAMOND,
			() -> Ingredient.of(ModItems.VIRTUAL_TITANIUM_INGOT.get()));
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_CHROMIUM = register(
			"virtual_chromium", VIRTUAL_CHROMIUM_PROFILE, SoundEvents.ARMOR_EQUIP_NETHERITE,
			() -> Ingredient.of(ModItems.VIRTUAL_CHROMIUM_INGOT.get()));
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_SNAKE_KING = register(
			"virtual_snake_king", VIRTUAL_SNAKE_KING_PROFILE, SoundEvents.ARMOR_EQUIP_NETHERITE,
			() -> Ingredient.of(ModItems.SNAKE_SCALE.get()));
	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> VIRTUAL_MUSHROOM_KING = register(
			"virtual_mushroom_king", VIRTUAL_MUSHROOM_KING_PROFILE, SoundEvents.ARMOR_EQUIP_NETHERITE,
			() -> Ingredient.of(ModItems.MUSHROOM_KING_SPORE.get()));

	private static DeferredHolder<ArmorMaterial, ArmorMaterial> register(String name,
			ArmorSetProfile profile, Holder<SoundEvent> equipSound, Supplier<Ingredient> repairIngredient) {
		return REGISTRY.register(name, () -> new ArmorMaterial(
				profile.defenseValues(), profile.enchantmentValue(), equipSound, repairIngredient,
				List.of(new ArmorMaterial.Layer(
						ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, name))),
				profile.toughness(), profile.knockbackResistance()));
	}

	public record ArmorSetProfile(int durabilityMultiplier, int enchantmentValue,
			int bootsDefense, int leggingsDefense, int chestplateDefense, int helmetDefense,
			int bodyDefense, float toughness, float knockbackResistance) {
		public Map<ArmorItem.Type, Integer> defenseValues() {
		EnumMap<ArmorItem.Type, Integer> values = new EnumMap<>(ArmorItem.Type.class);
			values.put(ArmorItem.Type.BOOTS, bootsDefense);
			values.put(ArmorItem.Type.LEGGINGS, leggingsDefense);
			values.put(ArmorItem.Type.CHESTPLATE, chestplateDefense);
			values.put(ArmorItem.Type.HELMET, helmetDefense);
			values.put(ArmorItem.Type.BODY, bodyDefense);
			return values;
		}
	}
}
