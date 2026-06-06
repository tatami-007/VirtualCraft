package dev.tatami.virtualcraft.blockentity;

import java.util.Arrays;
import java.util.UUID;

import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractType;
import dev.tatami.virtualcraft.gameplay.VirtualDataIntrusionPattern;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
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
	private static final int ACTIVE_TICKS = 75 * 20;
	private static final int SUCCESS_COOLDOWN_TICKS = 12_000;
	private static final int FAIL_COOLDOWN_TICKS = 1_200;
	private static final int MAX_CORROSION = 3;
	private static final int PHASE_IDLE = 0;
	private static final int PHASE_CALIBRATION = 1;
	private static final int PHASE_ROUTING = 2;
	private static final int PHASE_SUBMIT = 3;
	private static final int NODE_RADIUS = 3;
	private static final int[] NODE_DX = {0, 2, 3, 2, 0, -2, -3, -2};
	private static final int[] NODE_DZ = {-3, -2, 0, 2, 3, 2, 0, -2};

	private UUID owner;
	private int siteIndex;
	private long activeUntilGameTime;
	private long cooldownUntilGameTime;
	private int phase;
	private int corrosion;
	private int calibrationMask;
	private int routeIndex;
	private int[] calibrationNodes = new int[0];
	private int[] routeNodes = new int[0];

	public VirtualDataCoreBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.VIRTUAL_DATA_CORE.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, VirtualDataCoreBlockEntity core) {
		if (level.isClientSide) {
			return;
		}
		long now = level.getGameTime();
		if (!core.isActive(now)) {
			if (core.activeUntilGameTime > 0L && now >= core.activeUntilGameTime) {
				core.fail("message.virtual_craft.virtual_data.fail_timeout");
			}
			return;
		}
		if (now % 10L == 0L) {
			core.flashCurrentHint();
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
			showStatus(player);
			return;
		}
		if (cooldownUntilGameTime > now) {
			showCooldown(player, now);
			return;
		}
		if (!player.getAbilities().instabuild) {
			catalyst.shrink(1);
		}
		startIntrusion(serverLevel, player);
	}

	public void showStatus(Player player) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		long now = serverLevel.getGameTime();
		if (phase == PHASE_SUBMIT && isActive(now)) {
			complete(serverLevel, player);
		} else if (isActive(now)) {
			message(player, phaseMessageKey());
		} else if (cooldownUntilGameTime > now) {
			showCooldown(player, now);
		} else {
			message(player, "message.virtual_craft.virtual_data.ready");
		}
	}

	public void handleAnchorInput(Player player, int nodeIndex) {
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
		if (nodeIndex < 0 || nodeIndex >= VirtualDataIntrusionPattern.NODE_COUNT) {
			corrupt(player);
			return;
		}
		if (phase == PHASE_CALIBRATION) {
			handleCalibration(player, nodeIndex);
		} else if (phase == PHASE_ROUTING) {
			handleRouting(player, nodeIndex);
		} else if (phase == PHASE_SUBMIT) {
			message(player, "message.virtual_craft.virtual_data.submit");
		}
		setChanged();
	}

	public static BlockPos anchorPos(BlockPos corePos, int nodeIndex) {
		int node = Math.floorMod(nodeIndex, VirtualDataIntrusionPattern.NODE_COUNT);
		return corePos.offset(NODE_DX[node], 0, NODE_DZ[node]);
	}

	public static int nearestNodeIndex(BlockPos corePos, BlockPos anchorPos) {
		int best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (int node = 0; node < VirtualDataIntrusionPattern.NODE_COUNT; node++) {
			BlockPos expected = anchorPos(corePos, node);
			long dx = expected.getX() - anchorPos.getX();
			long dy = expected.getY() - anchorPos.getY();
			long dz = expected.getZ() - anchorPos.getZ();
			long distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance) {
				best = node;
				bestDistance = distance;
			}
		}
		return best;
	}

	private void startIntrusion(ServerLevel level, Player player) {
		long now = level.getGameTime();
		long seed = worldPosition.asLong() ^ player.getUUID().getMostSignificantBits() ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 13) ^ (now * 31L);
		VirtualDataIntrusionPattern pattern = VirtualDataIntrusionPattern.create(seed);
		calibrationNodes = pattern.calibrationNodes();
		routeNodes = pattern.routeNodes();
		calibrationMask = 0;
		routeIndex = 0;
		corrosion = 0;
		phase = PHASE_CALIBRATION;
		activeUntilGameTime = now + ACTIVE_TICKS;
		flashAllAnchors(ParticleTypes.END_ROD, 8);
		flashCalibrationNodes();
		playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.25F);
		message(player, "message.virtual_craft.virtual_data.calibrate");
		setChanged();
	}

	private void handleCalibration(Player player, int nodeIndex) {
		if (!contains(calibrationNodes, nodeIndex)) {
			corrupt(player);
			return;
		}
		int bit = 1 << nodeIndex;
		if ((calibrationMask & bit) != 0) {
			message(player, "message.virtual_craft.virtual_data.calibrate");
			return;
		}
		calibrationMask |= bit;
		flashAnchor(nodeIndex, ParticleTypes.ELECTRIC_SPARK, 18);
		playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.0F + Integer.bitCount(calibrationMask) * 0.08F);
		if (calibrationComplete()) {
			phase = PHASE_ROUTING;
			routeIndex = 0;
			flashAnchor(routeNodes[0], ParticleTypes.END_ROD, 18);
			playSound(SoundEvents.BEACON_POWER_SELECT, 0.75F, 1.15F);
			message(player, "message.virtual_craft.virtual_data.route");
		}
	}

	private void handleRouting(Player player, int nodeIndex) {
		if (routeIndex < 0 || routeIndex >= routeNodes.length || nodeIndex != routeNodes[routeIndex]) {
			routeIndex = 0;
			corrupt(player);
			return;
		}
		flashAnchor(nodeIndex, ParticleTypes.ELECTRIC_SPARK, 20);
		playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.85F, 1.15F + routeIndex * 0.08F);
		routeIndex++;
		if (routeIndex >= routeNodes.length) {
			phase = PHASE_SUBMIT;
			flashCore(ParticleTypes.HAPPY_VILLAGER, 24);
			playSound(SoundEvents.BEACON_AMBIENT, 0.8F, 1.4F);
			message(player, "message.virtual_craft.virtual_data.submit");
		} else {
			flashAnchor(routeNodes[routeIndex], ParticleTypes.END_ROD, 16);
		}
	}

	private void corrupt(Player player) {
		corrosion++;
		flashAllAnchors(ParticleTypes.SMOKE, 8);
		playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.75F, 0.55F);
		player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.corrupt", corrosion, MAX_CORROSION).withStyle(ChatFormatting.RED), true);
		if (corrosion >= MAX_CORROSION) {
			fail("message.virtual_craft.virtual_data.fail_corrupt");
		}
	}

	private void fail(String key) {
		if (level instanceof ServerLevel) {
			activeUntilGameTime = 0L;
			cooldownUntilGameTime = level.getGameTime() + FAIL_COOLDOWN_TICKS;
			resetActive();
			flashAllAnchors(ParticleTypes.SMOKE, 14);
			playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 0.7F);
			if (level.getNearestPlayer(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 8.0D, false) instanceof Player player) {
				message(player, key);
			}
			setChanged();
		}
	}

	private void complete(ServerLevel level, Player player) {
		long remaining = Math.max(0L, activeUntilGameTime - level.getGameTime());
		int multiplier = VirtualDataIntrusionPattern.rewardMultiplier(corrosion, remaining, ACTIVE_TICKS);
		int fragments = (3 + level.random.nextInt(3)) * multiplier;
		give(player, new ItemStack(ModItems.DATA_FRAGMENT.get(), fragments));
		rollVoucher(level, player, 0.0015F * multiplier, ModItems.GENESIS_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.02F * multiplier, ModItems.QUANTUM_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.15F * multiplier, ModItems.ENCRYPTED_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.60F * multiplier, ModItems.EXPLORER_HASH_VOUCHER.get());
		if (corrosion == 0) {
			rollVoucher(level, player, 0.20F, ModItems.EXPLORER_HASH_VOUCHER.get());
			rollVoucher(level, player, 0.06F, ModItems.ENCRYPTED_HASH_VOUCHER.get());
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			DailyContractService.recordProgress(serverPlayer, DailyContractType.DECODE_RELIC, 1L);
		}
		cooldownUntilGameTime = level.getGameTime() + SUCCESS_COOLDOWN_TICKS;
		resetActive();
		flashAllAnchors(ParticleTypes.HAPPY_VILLAGER, 14);
		playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.35F);
		player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.complete", fragments, multiplier).withStyle(ChatFormatting.AQUA), true);
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
		return activeUntilGameTime > now && phase != PHASE_IDLE;
	}

	private void resetActive() {
		activeUntilGameTime = 0L;
		phase = PHASE_IDLE;
		corrosion = 0;
		calibrationMask = 0;
		routeIndex = 0;
		calibrationNodes = new int[0];
		routeNodes = new int[0];
	}

	private boolean calibrationComplete() {
		for (int node : calibrationNodes) {
			if ((calibrationMask & (1 << node)) == 0) {
				return false;
			}
		}
		return calibrationNodes.length > 0;
	}

	private boolean canUse(Player player) {
		return owner == null || player.getUUID().equals(owner) || player.getAbilities().instabuild;
	}

	private void showCooldown(Player player, long now) {
		long seconds = Math.max(1L, (cooldownUntilGameTime - now + 19L) / 20L);
		player.displayClientMessage(Component.translatable("message.virtual_craft.virtual_data.cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
	}

	private String phaseMessageKey() {
		return switch (phase) {
			case PHASE_CALIBRATION -> "message.virtual_craft.virtual_data.calibrate";
			case PHASE_ROUTING -> "message.virtual_craft.virtual_data.route";
			case PHASE_SUBMIT -> "message.virtual_craft.virtual_data.submit";
			default -> "message.virtual_craft.virtual_data.ready";
		};
	}

	private void flashCurrentHint() {
		if (phase == PHASE_CALIBRATION) {
			flashCalibrationNodes();
		} else if (phase == PHASE_ROUTING && routeIndex >= 0 && routeIndex < routeNodes.length) {
			flashAnchor(routeNodes[routeIndex], ParticleTypes.END_ROD, 8);
		} else if (phase == PHASE_SUBMIT) {
			flashCore(ParticleTypes.ELECTRIC_SPARK, 8);
		}
	}

	private void flashCalibrationNodes() {
		for (int node : calibrationNodes) {
			if ((calibrationMask & (1 << node)) == 0) {
				flashAnchor(node, ParticleTypes.END_ROD, 8);
			}
		}
	}

	private void flashAllAnchors(ParticleOptions particle, int count) {
		for (int node = 0; node < VirtualDataIntrusionPattern.NODE_COUNT; node++) {
			flashAnchor(node, particle, count);
		}
	}

	private void flashAnchor(int nodeIndex, ParticleOptions particle, int count) {
		if (level instanceof ServerLevel serverLevel) {
			BlockPos anchor = anchorPos(worldPosition, nodeIndex);
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

	private void flashCore(ParticleOptions particle, int count) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					particle,
					worldPosition.getX() + 0.5D,
					worldPosition.getY() + 0.85D,
					worldPosition.getZ() + 0.5D,
					count,
					0.24D,
					0.28D,
					0.24D,
					0.01D);
		}
	}

	private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
		}
	}

	private static void message(Player player, String key) {
		player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
	}

	private static boolean contains(int[] values, int target) {
		for (int value : values) {
			if (value == target) {
				return true;
			}
		}
		return false;
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
		tag.putInt("phase", phase);
		tag.putInt("corrosion", corrosion);
		tag.putInt("calibration_mask", calibrationMask);
		tag.putInt("route_index", routeIndex);
		tag.putIntArray("calibration_nodes", calibrationNodes);
		tag.putIntArray("route_nodes", routeNodes);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		siteIndex = tag.getInt("site_index");
		activeUntilGameTime = tag.getLong("active_until");
		cooldownUntilGameTime = tag.getLong("cooldown_until");
		phase = tag.getInt("phase");
		corrosion = tag.getInt("corrosion");
		calibrationMask = tag.getInt("calibration_mask");
		routeIndex = tag.getInt("route_index");
		calibrationNodes = tag.getIntArray("calibration_nodes");
		routeNodes = tag.getIntArray("route_nodes");
		if (!isValidState()) {
			resetActive();
		} else if (phase == PHASE_IDLE) {
			resetActive();
		}
	}

	private boolean isValidState() {
		if (phase < PHASE_IDLE || phase > PHASE_SUBMIT || corrosion < 0 || corrosion > MAX_CORROSION) {
			return false;
		}
		if (phase == PHASE_IDLE) {
			return true;
		}
		if (calibrationNodes.length != VirtualDataIntrusionPattern.CALIBRATION_COUNT
				|| routeNodes.length < VirtualDataIntrusionPattern.MIN_ROUTE_COUNT
				|| routeNodes.length > VirtualDataIntrusionPattern.MAX_ROUTE_COUNT) {
			return false;
		}
		return Arrays.stream(calibrationNodes).allMatch(this::validNode)
				&& Arrays.stream(routeNodes).allMatch(this::validNode)
				&& routeIndex >= 0
				&& routeIndex <= routeNodes.length;
	}

	private boolean validNode(int node) {
		return node >= 0 && node < VirtualDataIntrusionPattern.NODE_COUNT;
	}
}
