package dev.tatami.virtualcraft.gameplay;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;

import dev.tatami.virtualcraft.network.TriggerShockOverlayPayload;

import net.neoforged.neoforge.network.PacketDistributor;

public class ElectricOreShockHandler {
	public static void apply(LevelAccessor world, Entity entity) {
		if (!(world instanceof ServerLevel serverLevel) || entity == null)
			return;
		entity.hurt(new DamageSource(serverLevel.holderOrThrow(DamageTypes.LIGHTNING_BOLT)), 100);
		if (entity instanceof ServerPlayer player)
			PacketDistributor.sendToPlayer(player, TriggerShockOverlayPayload.INSTANCE);
	}
}


