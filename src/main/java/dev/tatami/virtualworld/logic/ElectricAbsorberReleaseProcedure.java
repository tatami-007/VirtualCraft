package dev.tatami.virtualworld.logic;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext;
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

import java.util.Comparator;

import dev.tatami.virtualworld.core.definitions.VWBlocks;
import dev.tatami.virtualworld.core.definitions.VWItems;

public class ElectricAbsorberReleaseProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
		if (entity == null)
			return;
		double chargeSeconds = 0;
		double targetX = 0;
		double targetY = 0;
		double targetZ = 0;
		chargeSeconds = (entity instanceof LivingEntity _entUseTicks0 ? _entUseTicks0.getTicksUsingItem() : 0) / 20d;
		if (chargeSeconds >= 1) {
			{
				final String _tagName = "isGlowing";
				final boolean _tagValue = false;
				CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putBoolean(_tagName, _tagValue));
			}
				if ((entity instanceof LivingEntity _livEnt ? _livEnt.getOffhandItem() : ItemStack.EMPTY).getItem() == VWItems.ELECTRIC_BALL.get()) {
					{
						final Vec3 _center = new Vec3(x, y, z);
						for (Entity nearbyEntity : world.getEntitiesOfClass(Entity.class, new AABB(_center, _center).inflate(20 / 2d), e -> true).stream().sorted(Comparator.comparingDouble(_entcnd -> _entcnd.distanceToSqr(_center))).toList()) {
							if (nearbyEntity instanceof Monster) {
								if (world instanceof ServerLevel _level) {
									LightningBolt entityToSpawn = EntityType.LIGHTNING_BOLT.create(_level);
									entityToSpawn.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(nearbyEntity.getX(), nearbyEntity.getY(), nearbyEntity.getZ())));
									_level.addFreshEntity(entityToSpawn);
								}
								nearbyEntity.hurt(new DamageSource(world.holderOrThrow(DamageTypes.LIGHTNING_BOLT), entity), 1);
								(entity instanceof LivingEntity _livEnt ? _livEnt.getOffhandItem() : ItemStack.EMPTY).shrink(2);
								if (world instanceof ServerLevel _level) {
									itemstack.hurtAndBreak(1, _level, null, _stkprov -> {
									});
								}
								if (entity instanceof Player _player)
									_player.getCooldowns().addCooldown(itemstack.getItem(), 100);
							}
						}
					}
			}
			targetX = entity.level().clip(new ClipContext(entity.getEyePosition(1f), entity.getEyePosition(1f).add(entity.getViewVector(1f).scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity)).getBlockPos().getX();
			targetY = entity.level().clip(new ClipContext(entity.getEyePosition(1f), entity.getEyePosition(1f).add(entity.getViewVector(1f).scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity)).getBlockPos().getY();
			targetZ = entity.level().clip(new ClipContext(entity.getEyePosition(1f), entity.getEyePosition(1f).add(entity.getViewVector(1f).scale(5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity)).getBlockPos().getZ();
			if ((world.getBlockState(BlockPos.containing(targetX, targetY, targetZ))).getBlock() == VWBlocks.ELECTRIC_ORE.get()
					|| (world.getBlockState(BlockPos.containing(targetX, targetY, targetZ))).getBlock() == VWBlocks.DEEPSLATE_ELECTRIC_ORE.get()) {
				world.destroyBlock(BlockPos.containing(targetX, targetY, targetZ), false);
				if (world instanceof ServerLevel _level) {
					itemstack.hurtAndBreak(1, _level, null, _stkprov -> {
					});
				}
				if (world instanceof ServerLevel _level) {
					ItemEntity entityToSpawn = new ItemEntity(_level, targetX, targetY, targetZ, new ItemStack(VWItems.ELECTRIC_BALL.get()));
					entityToSpawn.setPickUpDelay(10);
					_level.addFreshEntity(entityToSpawn);
				}
			}
		}
	}
}
