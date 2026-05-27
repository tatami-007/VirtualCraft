package dev.tatami.virtualcraft.blockentity;

import java.util.Arrays;
import java.util.UUID;

import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractType;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class VirtualDataCoreBlockEntity extends BlockEntity {
	private static final int ACTIVE_TICKS = 90 * 20;
	private static final int COOLDOWN_TICKS = 12_000;
	private static final int SEQUENCE_LENGTH = 4;
	private static final Direction[] SEQUENCE_DIRECTIONS = {
			Direction.NORTH,
			Direction.EAST,
			Direction.SOUTH,
			Direction.WEST
	};

	private UUID owner;
	private int siteIndex;
	private long activeUntilGameTime;
	private long cooldownUntilGameTime;
	private int inputIndex;
	private int[] sequence = new int[0];

	public VirtualDataCoreBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.VIRTUAL_DATA_CORE.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, VirtualDataCoreBlockEntity core) {
		if (level.isClientSide) {
			return;
		}
		long now = level.getGameTime();
		if (core.isActive(now)) {
			if (now % 20L == 0L) {
				core.flashExpectedAnchor();
			}
			return;
		}
		if (core.activeUntilGameTime > 0L && now >= core.activeUntilGameTime) {
			core.resetActive();
			core.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.5F, 0.7F);
			core.setChanged();
		}
	}

	public void initialize(UUID owner, int siteIndex) {
		if (this.owner == null) {
			this.owner = owner;
		}
		this.siteIndex = siteIndex;
		setChanged();
	}

	public void tryStart(Player player, ItemStack catalyst) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!canUse(player)) {
			message(player, "message.virtual_craft.virtual_data.wrong_owner");
			return;
		}
		long now = serverLevel.getGameTime();
		if (isActive(now)) {
			showSequence(player);
			return;
		}
		if (cooldownUntilGameTime > now) {
			long seconds = Math.max(1L, (cooldownUntilGameTime - now + 19L) / 20L);
			player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!player.getAbilities().instabuild) {
			catalyst.shrink(1);
		}
		startSequence(serverLevel, player);
	}

	public void showStatus(Player player) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long now = serverLevel.getGameTime();
		if (isActive(now)) {
			showSequence(player);
		} else if (cooldownUntilGameTime > now) {
			long seconds = Math.max(1L, (cooldownUntilGameTime - now + 19L) / 20L);
			player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
		} else {
			message(player, "message.virtual_craft.virtual_data.ready");
		}
	}

	public void handleAnchorInput(Player player, Direction direction) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!canUse(player)) {
			message(player, "message.virtual_craft.virtual_data.wrong_owner");
			return;
		}
		long now = serverLevel.getGameTime();
		if (!isActive(now)) {
			message(player, "message.virtual_craft.virtual_data.inactive");
			return;
		}
		int actual = directionIndex(direction);
		int expected = sequence[inputIndex];
		if (actual != expected) {
			inputIndex = 0;
			flashAllAnchors(ParticleTypes.SMOKE);
			playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.55F);
			message(player, "message.virtual_craft.virtual_data.wrong");
			setChanged();
			return;
		}
		inputIndex++;
		flashAnchor(direction, ParticleTypes.ELECTRIC_SPARK, 16);
		playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 0.9F + inputIndex * 0.12F);
		if (inputIndex >= sequence.length) {
			complete(serverLevel, player);
		} else {
			showSequence(player);
		}
		setChanged();
	}

	private void startSequence(ServerLevel level, Player player) {
		long now = level.getGameTime();
		sequence = new int[SEQUENCE_LENGTH];
		long seed = worldPosition.asLong() ^ player.getUUID().getMostSignificantBits() ^ (now * 31L);
		for (int i = 0; i < sequence.length; i++) {
			seed = mix(seed + i);
			sequence[i] = Math.floorMod((int) seed, SEQUENCE_DIRECTIONS.length);
		}
		inputIndex = 0;
		activeUntilGameTime = now + ACTIVE_TICKS;
		flashAllAnchors(ParticleTypes.END_ROD);
		playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.25F);
		message(player, "message.virtual_craft.virtual_data.started");
		showSequence(player);
		setChanged();
	}

	private void complete(ServerLevel level, Player player) {
		int fragments = 3 + level.random.nextInt(3);
		give(player, new ItemStack(ModItems.DATA_FRAGMENT.get(), fragments));
		rollVoucher(level, player, 0.0015F, ModItems.GENESIS_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.02F, ModItems.QUANTUM_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.15F, ModItems.ENCRYPTED_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.60F, ModItems.EXPLORER_HASH_VOUCHER.get());
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			DailyContractService.recordProgress(serverPlayer, DailyContractType.DECODE_RELIC, 1L);
		}
		cooldownUntilGameTime = level.getGameTime() + COOLDOWN_TICKS;
		resetActive();
		flashAllAnchors(ParticleTypes.HAPPY_VILLAGER);
		playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.35F);
		player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.complete", fragments).withStyle(ChatFormatting.AQUA), true);
		setChanged();
	}

	private void rollVoucher(ServerLevel level, Player player, float chance, Item item) {
		if (level.random.nextFloat() < chance) {
			give(player, new ItemStack(item));
		}
	}

	private void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		}
	}

	private boolean isActive(long now) {
		return activeUntilGameTime > now && sequence.length == SEQUENCE_LENGTH;
	}

	private void resetActive() {
		activeUntilGameTime = 0L;
		inputIndex = 0;
		sequence = new int[0];
	}

	private boolean canUse(Player player) {
		return owner == null || player.getUUID().equals(owner) || player.getAbilities().instabuild;
	}

	private void showSequence(Player player) {
		player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.sequence", sequenceText()).withStyle(ChatFormatting.AQUA), true);
	}

	private String sequenceText() {
		if (sequence.length == 0) {
			return "-";
		}
		StringBuilder builder = new StringBuilder(sequence.length * 2);
		for (int i = 0; i < sequence.length; i++) {
			if (i > 0) {
				builder.append(' ');
			}
			builder.append(directionGlyph(SEQUENCE_DIRECTIONS[sequence[i]]));
		}
		return builder.toString();
	}

	private static String directionGlyph(Direction direction) {
		return switch (direction) {
			case NORTH -> "N";
			case EAST -> "E";
			case SOUTH -> "S";
			case WEST -> "W";
			default -> "?";
		};
	}

	private void flashExpectedAnchor() {
		if (sequence.length == 0 || inputIndex >= sequence.length) {
			return;
		}
		flashAnchor(SEQUENCE_DIRECTIONS[sequence[inputIndex]], ParticleTypes.ELECTRIC_SPARK, 8);
	}

	private void flashAllAnchors(net.minecraft.core.particles.ParticleOptions particle) {
		for (Direction direction : SEQUENCE_DIRECTIONS) {
			flashAnchor(direction, particle, 10);
		}
	}

	private void flashAnchor(Direction direction, net.minecraft.core.particles.ParticleOptions particle, int count) {
		if (level instanceof ServerLevel serverLevel) {
			BlockPos anchor = anchorPos(direction);
			serverLevel.sendParticles(
					particle,
					anchor.getX() + 0.5D,
					anchor.getY() + 0.75D,
					anchor.getZ() + 0.5D,
					count,
					0.18D,
					0.20D,
					0.18D,
					0.01D);
		}
	}

	private BlockPos anchorPos(Direction direction) {
		return worldPosition.relative(direction, 3);
	}

	private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
		}
	}

	private static void message(Player player, String key) {
		player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static int directionIndex(Direction direction) {
		for (int i = 0; i < SEQUENCE_DIRECTIONS.length; i++) {
			if (SEQUENCE_DIRECTIONS[i] == direction) {
				return i;
			}
		}
		return 0;
	}

	private static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
		tag.putInt("site_index", siteIndex);
		tag.putLong("active_until", activeUntilGameTime);
		tag.putLong("cooldown_until", cooldownUntilGameTime);
		tag.putInt("input_index", inputIndex);
		tag.putIntArray("sequence", sequence);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		siteIndex = tag.getInt("site_index");
		activeUntilGameTime = tag.getLong("active_until");
		cooldownUntilGameTime = tag.getLong("cooldown_until");
		inputIndex = tag.getInt("input_index");
		sequence = tag.getIntArray("sequence");
		if (sequence.length != SEQUENCE_LENGTH || Arrays.stream(sequence).anyMatch(index -> index < 0 || index >= SEQUENCE_DIRECTIONS.length)) {
			resetActive();
		}
	}
}
