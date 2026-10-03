package dev.tatami.virtualcraft.gameplay;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.BossPostureRules;
import dev.tatami.virtualcraft.boss.PostureBoss;
import dev.tatami.virtualcraft.boss.SnakeKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.item.VirtualSwordItem;
import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import dev.tatami.virtualcraft.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VirtualWeaponParryHandler {
	private static final int REPEAT_GUARD_TICKS = 4;
	private static final double FACING_DOT_MINIMUM = 0.20;
	private static final Map<UUID, Integer> LAST_PARRY_TICK = new HashMap<>();

	private VirtualWeaponParryHandler() {
	}

	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)
				|| event.getSource().getEntity() == null
				|| event.getSource().getEntity() == player
				|| event.getSource().getEntity() instanceof VirtualSnakeKing
				|| event.getSource().getEntity() instanceof VirtualMushroomKing
				|| !isBlocking(player, event.getSource())) {
			return;
		}

		Entity attacker = event.getSource().getEntity();
		if (attacker instanceof PostureBoss postureBoss) {
			if (postureBoss.getAttackDirection() != BossAttackDirection.MID) {
				return;
			}
			if (isParrying(player, event.getSource())) {
				postureBoss.damagePosture(BossPostureRules.SEGMENT_SIZE, player);
			} else {
				event.setCanceled(true);
				failParry(player, attacker);
				return;
			}
		} else if (!isParrying(player, event.getSource())) {
			event.setCanceled(true);
			failParry(player, attacker);
			return;
		}
		event.setCanceled(true);
		completeParry(player, attacker);
	}

	public static boolean isParrying(Player player, DamageSource source) {
		int window = source.getEntity() instanceof VirtualSnakeKing
				? SnakeKingAttackRules.MID_PARRY_WINDOW_TICKS
				: BossPostureRules.PARRY_WINDOW_TICKS;
		return isBlocking(player, source)
				&& player.getTicksUsingItem() >= 0
				&& player.getTicksUsingItem() < window
				&& player.tickCount - LAST_PARRY_TICK.getOrDefault(
						player.getUUID(), Integer.MIN_VALUE / 2) >= REPEAT_GUARD_TICKS;
	}

	public static boolean isBlocking(Player player, DamageSource source) {
		return player.isUsingItem()
				&& player.getUseItem().getItem() instanceof VirtualSwordItem
				&& isFacingAttack(player, source);
	}

	public static void completeParry(Player player, Entity attacker) {
		InteractionHand hand = player.getUsedItemHand();
		ItemStack sword = player.getUseItem();
		EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
				? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
		sword.hurtAndBreak(1, player, slot);
		LAST_PARRY_TICK.put(player.getUUID(), player.tickCount);

		if (player.level() instanceof ServerLevel level) {
			Vec3 impact = attacker == null
					? player.getEyePosition()
					: player.getEyePosition().add(attacker.getEyePosition()).scale(0.5);
			level.sendParticles(ModParticleTypes.SNAKE_PARRY_FLASH.get(),
					impact.x, impact.y, impact.z, 16, 0.32, 0.38, 0.32, 0.025);
			if (player instanceof ServerPlayer serverPlayer) {
				PacketDistributor.sendToPlayer(serverPlayer, new SnakeCombatFeedbackPayload(
						attacker == null ? -1 : attacker.getId(),
						SnakeCombatFeedbackPayload.WEAPON_PARRY, 0.72F));
			}
		}
	}

	public static void failParry(Player player, Entity attacker) {
		ItemStack sword = player.getUseItem();
		if (!(sword.getItem() instanceof VirtualSwordItem)) {
			return;
		}
		sword.hurtAndBreak(2, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND
				? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		player.stopUsingItem();
		player.getCooldowns().addCooldown(sword.getItem(), 100);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ModParticleTypes.SNAKE_SHARD.get(),
					player.getX(), player.getY() + player.getBbHeight() * 0.58, player.getZ(),
					5, 0.34, 0.3, 0.34, 0.055);
			level.playSound(null, player.blockPosition(), ModSounds.SNAKE_KING_SHIELD_BREAK.get(),
					SoundSource.PLAYERS, 1.15F, 0.78F);
			if (player instanceof ServerPlayer serverPlayer) {
				PacketDistributor.sendToPlayer(serverPlayer, new SnakeCombatFeedbackPayload(
						attacker == null ? -1 : attacker.getId(),
						SnakeCombatFeedbackPayload.SHIELD_BREAK, 0.55F));
			}
		}
	}

	private static boolean isFacingAttack(Player player, DamageSource source) {
		Vec3 sourcePosition = source.getSourcePosition();
		if (sourcePosition == null && source.getDirectEntity() != null) {
			sourcePosition = source.getDirectEntity().position();
		}
		if (sourcePosition == null) {
			return false;
		}
		Vec3 towardSource = sourcePosition.subtract(player.getEyePosition()).multiply(1.0, 0.0, 1.0);
		Vec3 view = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		return towardSource.lengthSqr() > 1.0E-6 && view.lengthSqr() > 1.0E-6
				&& towardSource.normalize().dot(view.normalize()) >= FACING_DOT_MINIMUM;
	}
}
