package dev.tatami.virtualcraft.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Shared insulation rules for lightning-forest and thunder-pool hazards. */
public final class VirtualArmorProtection {
	public static final int REQUIRED_PIECES = 4;

	private VirtualArmorProtection() {
	}

	public static int countPieces(LivingEntity wearer) {
		int pieces = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor() && isVirtualArmor(wearer.getItemBySlot(slot))) {
				pieces++;
			}
		}
		return pieces;
	}

	public static boolean isFullyInsulated(LivingEntity wearer) {
		return countPieces(wearer) >= REQUIRED_PIECES;
	}

	public static float scaleLightningDamage(LivingEntity wearer, float unprotectedDamage) {
		int missingPieces = REQUIRED_PIECES - Math.min(REQUIRED_PIECES, countPieces(wearer));
		return unprotectedDamage * missingPieces / REQUIRED_PIECES;
	}

	public static void applyConductivePenalty(LivingEntity wearer, int amplifier) {
		wearer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, amplifier, true, false, true));
		wearer.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, amplifier, true, false, true));
	}

	private static boolean isVirtualArmor(ItemStack stack) {
		return stack.getItem() instanceof VirtualMetalArmorItem;
	}
}
