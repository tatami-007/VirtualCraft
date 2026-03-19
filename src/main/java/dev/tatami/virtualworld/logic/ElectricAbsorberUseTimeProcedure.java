package dev.tatami.virtualworld.logic;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import dev.tatami.virtualworld.util.ElectricOreClusters;

public class ElectricAbsorberUseTimeProcedure {
	public static double execute(Entity entity, ItemStack itemstack) {
		if (!(entity instanceof LivingEntity livingEntity))
			return 0;
		if (itemstack.getItem() != livingEntity.getUseItem().getItem()) {
			return 0;
		}

		var targetedOre = ElectricOreClusters.findTargetedOre(livingEntity.level(), livingEntity, ElectricAbsorberReleaseProcedure.getOreScanRange());
		if (targetedOre == null) {
			return 0;
		}

		int clusterSize = ElectricOreClusters.collectConnectedOres(livingEntity.level(), targetedOre, ElectricAbsorberReleaseProcedure.getMaxConnectedOres()).size();
		return ElectricAbsorberReleaseProcedure.getAbsorptionCapacity(livingEntity.getTicksUsingItem()) >= clusterSize ? 1 : 0;
	}
}
