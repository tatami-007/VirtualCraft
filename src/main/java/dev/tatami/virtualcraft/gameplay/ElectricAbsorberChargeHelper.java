package dev.tatami.virtualcraft.gameplay;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import dev.tatami.virtualcraft.util.OreClusterFinder;

public class ElectricAbsorberChargeHelper {
	public static double getModelChargeValue(Entity entity, ItemStack itemstack) {
		if (!(entity instanceof LivingEntity livingEntity))
			return 0;
		if (itemstack.getItem() != livingEntity.getUseItem().getItem()) {
			return 0;
		}

		var targetedOre = OreClusterFinder.findTargetedOre(livingEntity.level(), livingEntity, ElectricAbsorberReleaseHandler.getOreScanRange());
		if (targetedOre == null) {
			return 0;
		}

		int clusterSize = OreClusterFinder.collectConnectedOres(livingEntity.level(), targetedOre, ElectricAbsorberReleaseHandler.getMaxConnectedOres()).size();
		return ElectricAbsorberReleaseHandler.getAbsorptionCapacity(livingEntity.getTicksUsingItem()) >= clusterSize ? 1 : 0;
	}
}


