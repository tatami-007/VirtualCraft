package dev.tatami.virtualcraft.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Plantable mushroom medicine. Use it on valid ground to plant it or eat it
 * directly for its medicinal effect.
 */
public final class MedicinalMushroomItem extends BlockItem {
	private static final int EFFECT_DURATION = 20 * 60;
	private static final float BLUE_MUSHROOM_HEAL = 10.0F;
	private static final float MANDRAKE_ABSORPTION = 10.0F;

	public enum Medicine {
		BLUE,
		POISON,
		NITRIFYING,
		PARALYSIS,
		GHOST_NITRIFYING,
		MANDRAKE,
		HEARTBEAT
	}

	private final Medicine medicine;

	public MedicinalMushroomItem(Block block, Medicine medicine) {
		super(block, new Item.Properties().food(new FoodProperties.Builder()
				.nutrition(0)
				.saturationModifier(0.0F)
				.alwaysEdible()
				.build()));
		this.medicine = medicine;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 24;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide && VirtualArmorSetEffects.wearsFullSet(
				entity, VirtualMetalArmorItem.Style.MUSHROOM_KING)) {
			Medicine resolved = medicine == Medicine.HEARTBEAT
					? Medicine.values()[entity.getRandom().nextInt(Medicine.HEARTBEAT.ordinal())]
					: medicine;
			applyMedicine(entity, resolved);
		}
		return result;
	}

	private static void applyMedicine(LivingEntity entity, Medicine medicine) {
		switch (medicine) {
			case BLUE -> entity.heal(BLUE_MUSHROOM_HEAL);
			case POISON -> entity.addEffect(new MobEffectInstance(
					MobEffects.REGENERATION, EFFECT_DURATION, 0));
			case NITRIFYING -> entity.addEffect(new MobEffectInstance(
					MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 1));
			case PARALYSIS -> entity.addEffect(new MobEffectInstance(
					MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 1));
			case GHOST_NITRIFYING -> {
				entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 1));
				entity.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_DURATION, 1));
			}
			case MANDRAKE -> {
				float existingAbsorption = entity.getAbsorptionAmount();
				entity.setHealth(entity.getMaxHealth());
				entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, EFFECT_DURATION, 2));
				entity.setAbsorptionAmount(Math.max(existingAbsorption, MANDRAKE_ABSORPTION));
			}
			case HEARTBEAT -> throw new IllegalStateException("Heartbeat medicine must resolve first");
		}
	}
}
