package dev.tatami.virtualcraft.gameplay;

import dev.tatami.virtualcraft.item.VirtualMetalArmorItem;
import dev.tatami.virtualcraft.registry.ModMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

public final class SnakeKingBlessingHandler {
	public static final int REQUIRED_HITS = 10;
	public static final float DAMAGE_MULTIPLIER = 1.5F;
	public static final int DURATION_TICKS = 1200;
	public static final int COOLDOWN_TICKS = 600;
	private static final String HIT_COUNT_KEY = "virtual_craft.snake_king_blessing_hits";
	private static final String ACTIVE_UNTIL_KEY = "virtual_craft.snake_king_blessing_active_until";
	private static final String COOLDOWN_UNTIL_KEY = "virtual_craft.snake_king_blessing_cooldown_until";
	private static final DustParticleOptions BLESSING_GOLD =
			new DustParticleOptions(new Vector3f(0.95F, 0.66F, 0.16F), 1.15F);

	private SnakeKingBlessingHandler() {
	}

	public static void onDamageDealt(LivingDamageEvent.Post event) {
		if (event.getNewDamage() <= 0.0F
				|| !(event.getSource().getEntity() instanceof ServerPlayer attacker)
				|| event.getEntity() == attacker
				|| !wearsFullSet(attacker)) {
			return;
		}

		CompoundTag data = attacker.getPersistentData();
		long now = attacker.serverLevel().getGameTime();
		if (now < data.getLong(ACTIVE_UNTIL_KEY)) {
			showEmpoweredHit(event);
			return;
		}
		if (now < data.getLong(COOLDOWN_UNTIL_KEY)) {
			return;
		}
		int previousHits = data.getInt(HIT_COUNT_KEY);
		int hits = Math.min(REQUIRED_HITS, previousHits + 1);
		data.putInt(HIT_COUNT_KEY, hits);
		if (previousHits < REQUIRED_HITS && hits == REQUIRED_HITS) {
			activateBlessing(attacker, now);
		}
		if (attacker.hasEffect(ModMobEffects.SNAKE_KING_BLESSING)) {
			showEmpoweredHit(event);
		}
	}

	public static void amplifyDamage(LivingIncomingDamageEvent event) {
		if (event.getSource().getEntity() instanceof Player attacker
				&& attacker != event.getEntity()
				&& attacker.hasEffect(ModMobEffects.SNAKE_KING_BLESSING)) {
			event.setAmount(event.getAmount() * DAMAGE_MULTIPLIER);
		}
	}

	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (player.level().isClientSide || player.tickCount % 10 != 0) {
			return;
		}

		if (!wearsFullSet(player)) {
			clearBlessingState(player);
			player.removeEffect(ModMobEffects.SNAKE_KING_BLESSING);
			return;
		}

		CompoundTag data = player.getPersistentData();
		long now = player.level().getGameTime();
		long activeUntil = data.getLong(ACTIVE_UNTIL_KEY);
		if (activeUntil > now) {
			int remaining = (int)Math.min(Integer.MAX_VALUE, activeUntil - now);
			MobEffectInstance current = player.getEffect(ModMobEffects.SNAKE_KING_BLESSING);
			if (current == null || current.getDuration() < remaining - 20) {
				applyBlessing(player, remaining);
			}
			return;
		}
		if (activeUntil > 0L) {
			data.remove(ACTIVE_UNTIL_KEY);
			player.removeEffect(ModMobEffects.SNAKE_KING_BLESSING);
		}
		if (data.getLong(COOLDOWN_UNTIL_KEY) <= now) {
			data.remove(COOLDOWN_UNTIL_KEY);
		}
	}

	public static boolean wearsFullSet(Player player) {
		return isSnakeKingPiece(player.getItemBySlot(EquipmentSlot.HEAD))
				&& isSnakeKingPiece(player.getItemBySlot(EquipmentSlot.CHEST))
				&& isSnakeKingPiece(player.getItemBySlot(EquipmentSlot.LEGS))
				&& isSnakeKingPiece(player.getItemBySlot(EquipmentSlot.FEET));
	}

	private static boolean isSnakeKingPiece(ItemStack stack) {
		return stack.getItem() instanceof VirtualMetalArmorItem armor
				&& armor.style() == VirtualMetalArmorItem.Style.SNAKE_KING;
	}

	private static void applyBlessing(Player player, int duration) {
		player.addEffect(new MobEffectInstance(
				ModMobEffects.SNAKE_KING_BLESSING,
				duration,
				0,
				false,
				false,
				false));
	}

	private static void activateBlessing(ServerPlayer player, long now) {
		CompoundTag data = player.getPersistentData();
		data.putInt(HIT_COUNT_KEY, 0);
		data.putLong(ACTIVE_UNTIL_KEY, now + DURATION_TICKS);
		data.putLong(COOLDOWN_UNTIL_KEY, now + DURATION_TICKS + COOLDOWN_TICKS);
		applyBlessing(player, DURATION_TICKS);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 1.0F, 1.18F);
		level.sendParticles(BLESSING_GOLD, player.getX(), player.getY() + 1.0, player.getZ(),
				24, 0.65, 0.9, 0.65, 0.05);
	}

	private static void clearBlessingState(Player player) {
		CompoundTag data = player.getPersistentData();
		data.remove(HIT_COUNT_KEY);
		data.remove(ACTIVE_UNTIL_KEY);
		data.remove(COOLDOWN_UNTIL_KEY);
	}

	private static void showEmpoweredHit(LivingDamageEvent.Post event) {
		if (!(event.getEntity().level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(BLESSING_GOLD, event.getEntity().getX(),
				event.getEntity().getY() + event.getEntity().getBbHeight() * 0.55,
				event.getEntity().getZ(), 5, 0.25, 0.35, 0.25, 0.02);
	}
}
