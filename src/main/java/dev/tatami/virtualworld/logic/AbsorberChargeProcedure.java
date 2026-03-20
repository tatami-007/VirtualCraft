package dev.tatami.virtualworld.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import dev.tatami.virtualworld.util.OreClusterFinder;

public class AbsorberChargeProcedure {
	public static double execute(Entity entity, ItemStack itemstack) {
		if (!(entity instanceof LivingEntity livingEntity))
			return 0;
		if (itemstack.getItem() != livingEntity.getUseItem().getItem()) {
			return 0;
		}

		var targetedOre = OreClusterFinder.findTargetedOre(livingEntity.level(), livingEntity, AbsorberReleaseProcedure.getOreScanRange());
		if (targetedOre == null) {
			return 0;
		}

		int clusterSize = OreClusterFinder.collectConnectedOres(livingEntity.level(), targetedOre, AbsorberReleaseProcedure.getMaxConnectedOres()).size();
		return AbsorberReleaseProcedure.getAbsorptionCapacity(livingEntity.getTicksUsingItem()) >= clusterSize ? 1 : 0;
	}
}

