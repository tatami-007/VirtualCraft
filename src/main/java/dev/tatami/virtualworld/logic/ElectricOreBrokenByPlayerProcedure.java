package dev.tatami.virtualworld.logic;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;

public class ElectricOreBrokenByPlayerProcedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (!(world instanceof ServerLevel serverLevel) || entity == null)
			return;
		entity.hurt(new DamageSource(serverLevel.holderOrThrow(DamageTypes.LIGHTNING_BOLT)), 100);
		if (entity instanceof Player player)
			player.displayClientMessage(Component.translatable("info.virtual_world.electrocution"), true);
	}
}
