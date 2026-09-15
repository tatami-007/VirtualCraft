package dev.tatami.virtualcraft.item;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

final class VirtualArmorSetEffects {
	private static final int REFRESH_INTERVAL = 20;
	private static final int EFFECT_DURATION = 50;

	private VirtualArmorSetEffects() {
	}

	static void tick(LivingEntity wearer, VirtualMetalArmorItem.Style style) {
		if (wearer.level().isClientSide || !wearsFullSet(wearer, style)) {
			return;
		}
		if (style == VirtualMetalArmorItem.Style.MUSHROOM_KING) {
			return;
		}
		if (wearer.tickCount % REFRESH_INTERVAL != 0) {
			return;
		}

		switch (style) {
			case IRON -> {
				apply(wearer, MobEffects.HEALTH_BOOST, 1);
				apply(wearer, MobEffects.DAMAGE_RESISTANCE, 0);
			}
			case SILVER -> {
			}
			case TITANIUM -> {
				apply(wearer, MobEffects.MOVEMENT_SPEED, 0);
				apply(wearer, MobEffects.DIG_SPEED, 1);
				apply(wearer, MobEffects.JUMP, 0);
			}
			case CHROMIUM -> {
				apply(wearer, MobEffects.DAMAGE_RESISTANCE, 1);
				apply(wearer, MobEffects.DAMAGE_BOOST, 0);
				apply(wearer, MobEffects.FIRE_RESISTANCE, 0);
				if (wearer.getHealth() <= wearer.getMaxHealth() * 0.35F) {
					apply(wearer, MobEffects.REGENERATION, 1);
				}
				repairEquippedArmor(wearer);
			}
			case SNAKE_KING -> {
			}
			case MUSHROOM_KING -> {
			}
		}
	}

	private static void repairEquippedArmor(LivingEntity wearer) {
		if (wearer.tickCount % 100 != 0) {
			return;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!slot.isArmor()) {
				continue;
			}
			ItemStack stack = wearer.getItemBySlot(slot);
			if (stack.getItem() instanceof VirtualMetalArmorItem armor
					&& armor.style() == VirtualMetalArmorItem.Style.CHROMIUM
					&& stack.isDamaged()) {
				stack.setDamageValue(Math.max(0, stack.getDamageValue() - 1));
			}
		}
	}

	static boolean wearsFullSet(LivingEntity wearer, VirtualMetalArmorItem.Style style) {
		return matches(wearer.getItemBySlot(EquipmentSlot.HEAD), style)
				&& matches(wearer.getItemBySlot(EquipmentSlot.CHEST), style)
				&& matches(wearer.getItemBySlot(EquipmentSlot.LEGS), style)
				&& matches(wearer.getItemBySlot(EquipmentSlot.FEET), style);
	}

	private static boolean matches(ItemStack stack, VirtualMetalArmorItem.Style style) {
		return stack.getItem() instanceof VirtualMetalArmorItem armor && armor.style() == style;
	}

	private static void apply(LivingEntity wearer, net.minecraft.core.Holder<MobEffect> effect, int amplifier) {
		wearer.addEffect(new MobEffectInstance(
				effect, EFFECT_DURATION, amplifier, true, false, false));
	}
}
