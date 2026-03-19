package dev.tatami.virtualworld.logic;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;

import java.util.List;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.core.definitions.VWBlocks;
import dev.tatami.virtualworld.core.definitions.VWItems;
import dev.tatami.virtualworld.util.ElectricOreClusters;

public class ElectricAbsorberReleaseProcedure {
	private static final int CHARGE_TICKS_PER_ORE = 8;
	private static final int LIGHTNING_RANGE = 10;
	private static final int ORE_SCAN_RANGE = 5;
	private static final int OFFHAND_COST = 2;
	private static final int ITEM_COOLDOWN_TICKS = 100;
	private static final int MAX_CONNECTED_ORES = 128;
	private static final int BLOCKS_PER_TICK = 8;

	public static void execute(Level world, double x, double y, double z, Entity entity, ItemStack itemstack) {
		if (!(world instanceof ServerLevel serverLevel) || !(entity instanceof LivingEntity livingEntity))
			return;
		if (livingEntity.getTicksUsingItem() < CHARGE_TICKS_PER_ORE)
			return;

		CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putBoolean("isGlowing", false));

		BlockPos targetOre = ElectricOreClusters.findTargetedOre(serverLevel, livingEntity, ORE_SCAN_RANGE);
		if (targetOre != null) {
			collectTargetOreCluster(serverLevel, targetOre, livingEntity.getTicksUsingItem(), itemstack);
			return;
		}

		if (triggerLightningAttack(serverLevel, livingEntity, itemstack))
			return;
	}

	public static int getAbsorptionCapacity(int useTicks) {
		return Math.max(0, useTicks / CHARGE_TICKS_PER_ORE);
	}

	public static int getOreScanRange() {
		return ORE_SCAN_RANGE;
	}

	public static int getMaxConnectedOres() {
		return MAX_CONNECTED_ORES;
	}

	private static boolean triggerLightningAttack(ServerLevel level, LivingEntity user, ItemStack absorber) {
		ItemStack offhandItem = user.getOffhandItem();
		if (offhandItem.getItem() != VWItems.ELECTRIC_BALL.get() || offhandItem.getCount() < OFFHAND_COST)
			return false;

		Entity target = level.getEntitiesOfClass(Monster.class, AABB.ofSize(user.position(), LIGHTNING_RANGE * 2.0, LIGHTNING_RANGE * 2.0, LIGHTNING_RANGE * 2.0))
				.stream()
				.min((left, right) -> Double.compare(left.distanceToSqr(user), right.distanceToSqr(user)))
				.orElse(null);
		if (target == null)
			return false;

		LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
		if (lightning != null) {
			lightning.moveTo(target.getX(), target.getY(), target.getZ());
			level.addFreshEntity(lightning);
		}

		target.hurt(new DamageSource(level.holderOrThrow(DamageTypes.LIGHTNING_BOLT), user), 1);
		offhandItem.shrink(OFFHAND_COST);
		absorber.hurtAndBreak(1, level, null, ignored -> {
		});
		if (user instanceof Player player) {
			player.getCooldowns().addCooldown(absorber.getItem(), ITEM_COOLDOWN_TICKS);
		}
		return true;
	}

	private static void collectTargetOreCluster(ServerLevel level, BlockPos targetOre, int useTicks, ItemStack absorber) {
		List<BlockPos> connectedOres = ElectricOreClusters.collectConnectedOres(level, targetOre, MAX_CONNECTED_ORES);
		int absorbCount = Math.min(connectedOres.size(), getAbsorptionCapacity(useTicks));
		if (absorbCount <= 0) {
			return;
		}

		absorber.hurtAndBreak(absorbCount, level, null, ignored -> {
		});
		processAbsorptionBatch(level, connectedOres.subList(0, absorbCount), targetOre, 0, absorbCount);
	}

	private static void processAbsorptionBatch(ServerLevel level, List<BlockPos> oresToAbsorb, BlockPos origin, int startIndex, int totalCount) {
		int endIndex = Math.min(startIndex + BLOCKS_PER_TICK, oresToAbsorb.size());
		for (int i = startIndex; i < endIndex; i++) {
			level.destroyBlock(oresToAbsorb.get(i), false);
		}

		if (endIndex < oresToAbsorb.size()) {
			VirtualWorld.queueServerWork(1, () -> processAbsorptionBatch(level, oresToAbsorb, origin, endIndex, totalCount));
			return;
		}

		spawnElectricBalls(level, origin, totalCount);
	}

	private static void spawnElectricBalls(ServerLevel level, BlockPos origin, int count) {
		int remaining = count;
		while (remaining > 0) {
			int stackSize = Math.min(remaining, VWItems.ELECTRIC_BALL.get().getDefaultInstance().getMaxStackSize());
			ItemEntity entityToSpawn = new ItemEntity(level, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5, new ItemStack(VWItems.ELECTRIC_BALL.get(), stackSize));
			entityToSpawn.setPickUpDelay(10);
			level.addFreshEntity(entityToSpawn);
			remaining -= stackSize;
		}
	}
}
