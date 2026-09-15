package dev.tatami.virtualcraft.block;

import dev.tatami.virtualcraft.item.VirtualArmorProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class ThunderPoolBlock extends LiquidBlock {
	public ThunderPoolBlock(FlowingFluid fluid, BlockBehaviour.Properties properties) {
		super(fluid, properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!level.isClientSide && entity instanceof LivingEntity living && living.tickCount % 10 == 0) {
			if (living instanceof Player player) {
				if (player.isCreative() || player.isSpectator()) {
					return;
				}
				float damage = VirtualArmorProtection.scaleLightningDamage(player, 6.0F);
				if (damage <= 0.0F) {
					return;
				}
				player.hurt(level.damageSources().lightningBolt(), damage);
				VirtualArmorProtection.applyConductivePenalty(player, 1);
				return;
			}
			living.hurt(level.damageSources().lightningBolt(), 2.0F);
		}
	}
}
