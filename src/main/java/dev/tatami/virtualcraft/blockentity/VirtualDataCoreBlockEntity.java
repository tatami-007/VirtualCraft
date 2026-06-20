package dev.tatami.virtualcraft.blockentity;

import java.util.Arrays;
import java.util.UUID;

import dev.tatami.virtualcraft.block.VirtualDataGateBlock;
import dev.tatami.virtualcraft.block.VirtualDataRoomNodeBlock.NodeType;
import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractType;
import dev.tatami.virtualcraft.gameplay.VirtualDataIntrusionPattern;
import dev.tatami.virtualcraft.gameplay.VirtualDataRoomPattern;
import dev.tatami.virtualcraft.gameplay.VirtualDataRoomPattern.Target;
import dev.tatami.virtualcraft.gameplay.VirtualDataRoomPattern.TargetType;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModBlocks;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class VirtualDataCoreBlockEntity extends BlockEntity {
	private static final int ACTIVE_TICKS = 120 * 20;
	private static final int SUCCESS_COOLDOWN_TICKS = 12_000;
	private static final int FAIL_COOLDOWN_TICKS = 1_200;
	private static final int MAX_CORROSION = 4;
	private static final int PHASE_IDLE = 0;
	private static final int PHASE_SCAN = 1;
	private static final int PHASE_ROUTE = 2;
	private static final int PHASE_CACHE = 3;
	private static final int PHASE_EXTRACT = 4;
	private static final int CACHE_OPTION_TICKS = 20 * 20;
	private static final int[] NODE_DX = {0, 2, 3, 2, 0, -2, -3, -2};
	private static final int[] NODE_DZ = {-3, -2, 0, 2, 3, 2, 0, -2};

	private UUID owner;
	private int siteIndex;
	private int roomVersion;
	private int depth;
	private boolean boss;
	private long[] controlledGates = new long[0];
	private long activeUntilGameTime;
	private long cooldownUntilGameTime;
	private long cacheUntilGameTime;
	private int phase;
	private int corrosion;
	private int scanMask;
	private int cacheMask;
	private int routeIndex;
	private long intrusionSeed;
	private int[] scanRelays = new int[0];
	private Target[] routeTargets = new Target[0];

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
		if (core.phase == PHASE_CACHE && core.cacheUntilGameTime > 0L && now >= core.cacheUntilGameTime) {
			core.enterExtractPhase();
		}
		if (now % 20L == 0L) {
			core.flashCurrentHint();
		}
		if (now % 40L == 0L && core.phase == PHASE_CACHE) {
			core.corrosionPulse();
		}
	}

	public void initialize(UUID owner, int siteIndex) {
		if (this.owner == null) {
			this.owner = owner;
		}
		this.siteIndex = siteIndex;
		setChanged();
	}

	public void setRoomVersion(int roomVersion) {
		if (this.roomVersion < roomVersion) {
			this.roomVersion = roomVersion;
			setChanged();
		}
	}

	/** Graph distance from the hub — deeper rooms hand out richer rewards. */
	public void setDepth(int depth) {
		if (this.depth != depth) {
			this.depth = depth;
			setChanged();
		}
	}

	/** Marks this core as the hardened firewall (boss) variant: tighter timer, bigger payout. */
	public void setBoss(boolean boss) {
		if (this.boss != boss) {
			this.boss = boss;
			setChanged();
		}
	}

	/**
	 * Packed positions of the corridor gates this core unlocks on completion —
	 * i.e. the doors into the rooms that branch off this one. This is the single
	 * piece of cross-block state the labyrinth needs: clearing a room's core is
	 * what opens the way deeper.
	 */
	public void setControlledGates(long[] gates) {
		long[] next = gates == null ? new long[0] : gates.clone();
		if (!Arrays.equals(this.controlledGates, next)) {
			this.controlledGates = next;
			setChanged();
		}
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
		if (phase == PHASE_EXTRACT && isActive(now)) {
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
		if (!canInteract(player, serverLevel)) {
			return;
		}
		if (nodeIndex < 0 || nodeIndex >= VirtualDataIntrusionPattern.NODE_COUNT) {
			corrupt(player);
			return;
		}
		if (phase == PHASE_ROUTE) {
			handleRouteTarget(player, new Target(TargetType.ANCHOR, nodeIndex));
		} else if (phase == PHASE_EXTRACT) {
			message(player, "message.virtual_craft.virtual_data.extract");
		} else {
			corrupt(player);
		}
		setChanged();
	}

	public void handleRoomNodeInput(Player player, NodeType nodeType, BlockPos nodePos) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!canInteract(player, serverLevel)) {
			return;
		}
		int dx = nodePos.getX() - worldPosition.getX();
		int dy = nodePos.getY() - worldPosition.getY();
		int dz = nodePos.getZ() - worldPosition.getZ();
		switch (nodeType) {
			case RELAY -> handleRelay(player, VirtualDataRoomPattern.nearestRelayIndex(dx, dy, dz));
			case CACHE -> handleCache(player, VirtualDataRoomPattern.nearestCacheIndex(dx, dy, dz));
			case CORRUPTED -> corrupt(player);
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

	private boolean canInteract(Player player, ServerLevel level) {
		if (!canUse(player)) {
			message(player, "message.virtual_craft.virtual_data.wrong_owner");
			return false;
		}
		if (!isActive(level.getGameTime())) {
			message(player, "message.virtual_craft.virtual_data.inactive");
			return false;
		}
		return true;
	}

	private void startIntrusion(ServerLevel level, Player player) {
		long now = level.getGameTime();
		intrusionSeed = worldPosition.asLong()
				^ player.getUUID().getMostSignificantBits()
				^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 13)
				^ siteIndex * 0x9E3779B97F4A7C15L
				^ now * 31L;
		VirtualDataRoomPattern pattern = VirtualDataRoomPattern.create(intrusionSeed);
		scanRelays = pattern.scanRelays();
		routeTargets = pattern.routeTargets();
		scanMask = 0;
		cacheMask = 0;
		routeIndex = 0;
		corrosion = 0;
		phase = PHASE_SCAN;
		activeUntilGameTime = now + activeTicks();
		cacheUntilGameTime = 0L;
		setGatesOpen(false);
		flashAllRelays(ParticleTypes.END_ROD, 8);
		flashScanRelays();
		playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.25F);
		message(player, "message.virtual_craft.virtual_data.scan");
		setChanged();
	}

	private void handleRelay(Player player, int relayIndex) {
		if (phase == PHASE_SCAN) {
			if (!VirtualDataRoomPattern.containsRelay(scanRelays, relayIndex)) {
				corrupt(player);
				return;
			}
			int bit = 1 << relayIndex;
			if ((scanMask & bit) != 0) {
				message(player, "message.virtual_craft.virtual_data.scan");
				return;
			}
			scanMask |= bit;
			flashRelay(relayIndex, ParticleTypes.ELECTRIC_SPARK, 18);
			playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.0F + Integer.bitCount(scanMask) * 0.08F);
			if (scanComplete()) {
				phase = PHASE_ROUTE;
				routeIndex = 0;
				openGate(0);
				flashCurrentRouteTarget();
				playSound(SoundEvents.BEACON_POWER_SELECT, 0.75F, 1.15F);
				message(player, "message.virtual_craft.virtual_data.route");
			}
		} else if (phase == PHASE_ROUTE) {
			handleRouteTarget(player, new Target(TargetType.RELAY, relayIndex));
		} else {
			corrupt(player);
		}
	}

	private void handleRouteTarget(Player player, Target actual) {
		if (routeIndex < 0 || routeIndex >= routeTargets.length || !actual.equals(routeTargets[routeIndex])) {
			routeIndex = 0;
			corrupt(player);
			flashCurrentRouteTarget();
			return;
		}
		flashTarget(actual, ParticleTypes.ELECTRIC_SPARK, 20);
		playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.85F, 1.15F + routeIndex * 0.08F);
		routeIndex++;
		if (routeIndex >= routeTargets.length) {
			phase = PHASE_CACHE;
			cacheUntilGameTime = level == null ? 0L : level.getGameTime() + CACHE_OPTION_TICKS;
			setGatesOpen(true);
			flashAllCaches(ParticleTypes.END_ROD, 16);
			playSound(SoundEvents.BEACON_AMBIENT, 0.8F, 1.4F);
			message(player, "message.virtual_craft.virtual_data.cache");
		} else {
			if (routeIndex == 1) {
				openGate(1);
			} else if (routeIndex == 2) {
				openGate(2);
			}
			flashCurrentRouteTarget();
		}
	}

	private void handleCache(Player player, int cacheIndex) {
		if (phase != PHASE_CACHE) {
			corrupt(player);
			return;
		}
		int bit = 1 << cacheIndex;
		if ((cacheMask & bit) != 0) {
			message(player, "message.virtual_craft.virtual_data.cache");
			return;
		}
		cacheMask |= bit;
		flashCache(cacheIndex, ParticleTypes.HAPPY_VILLAGER, 18);
		playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7F, 1.35F);
		if (Integer.bitCount(cacheMask) >= VirtualDataRoomPattern.CACHE_COUNT) {
			enterExtractPhase();
		} else {
			corrosionPulse();
			if (level != null && level.random.nextFloat() < 0.35F) {
				corrupt(player);
			}
		}
	}

	private void enterExtractPhase() {
		phase = PHASE_EXTRACT;
		cacheUntilGameTime = 0L;
		setGatesOpen(true);
		flashCore(ParticleTypes.HAPPY_VILLAGER, 24);
		playSound(SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.35F);
		if (level != null && level.getNearestPlayer(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 10.0D, false) instanceof Player player) {
			message(player, "message.virtual_craft.virtual_data.extract");
		}
		setChanged();
	}

	private void corrosionPulse() {
		flashAllCorrupted(ParticleTypes.SMOKE, 8);
		playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.45F, 0.65F);
	}

	private void corrupt(Player player) {
		corrosion++;
		flashAllCorrupted(ParticleTypes.SMOKE, 10);
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
			setGatesOpen(false);
			flashAllCorrupted(ParticleTypes.SMOKE, 18);
			playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 0.7F);
			if (level.getNearestPlayer(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, 10.0D, false) instanceof Player player) {
				message(player, key);
			}
			setChanged();
		}
	}

	private void complete(ServerLevel level, Player player) {
		long remaining = Math.max(0L, activeUntilGameTime - level.getGameTime());
		int openedCaches = Integer.bitCount(cacheMask);
		// Deeper rooms (and the firewall boss) scale the payout: +1 per two
		// levels of depth, +2 for the boss, on top of the base performance
		// multiplier — so pushing further into the labyrinth is worth it.
		int depthBonus = Math.min(4, depth / 2);
		int bossBonus = boss ? 2 : 0;
		int multiplier = Math.max(1, Math.min(12,
				VirtualDataRoomPattern.rewardMultiplier(corrosion, remaining, activeTicks(), openedCaches)
						+ depthBonus + bossBonus));
		int fragments = (4 + level.random.nextInt(4)) * multiplier;
		give(player, new ItemStack(ModItems.DATA_FRAGMENT.get(), fragments));
		rollVoucher(level, player, 0.002F * multiplier, ModItems.GENESIS_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.025F * multiplier, ModItems.QUANTUM_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.18F * multiplier, ModItems.ENCRYPTED_HASH_VOUCHER.get());
		rollVoucher(level, player, 0.70F * multiplier, ModItems.EXPLORER_HASH_VOUCHER.get());
		if (corrosion == 0 && openedCaches == VirtualDataRoomPattern.CACHE_COUNT) {
			rollVoucher(level, player, 0.10F, ModItems.ENCRYPTED_HASH_VOUCHER.get());
			rollVoucher(level, player, 0.03F, ModItems.QUANTUM_HASH_VOUCHER.get());
		}
		if (boss) {
			rollVoucher(level, player, 0.50F, ModItems.QUANTUM_HASH_VOUCHER.get());
			rollVoucher(level, player, 0.08F, ModItems.GENESIS_HASH_VOUCHER.get());
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			DailyContractService.recordProgress(serverPlayer, DailyContractType.DECODE_RELIC, 1L);
		}
		cooldownUntilGameTime = level.getGameTime() + SUCCESS_COOLDOWN_TICKS;
		resetActive();
		setGatesOpen(false);
		// Clearing this room is the "key" that unseals the corridors leading
		// deeper — the firewall boss instead just frees its rich dead-end vault.
		openControlledGates();
		flashAllRelays(ParticleTypes.HAPPY_VILLAGER, 14);
		flashAllCaches(ParticleTypes.HAPPY_VILLAGER, 14);
		playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.35F);
		String key = boss
				? "message.virtual_craft.virtual_data.firewall_breached"
				: "message.virtual_craft.virtual_data.complete";
		player.displayClientMessage(Component.translatable(key, fragments, multiplier).withStyle(boss ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
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
		cacheUntilGameTime = 0L;
		phase = PHASE_IDLE;
		corrosion = 0;
		scanMask = 0;
		cacheMask = 0;
		routeIndex = 0;
		intrusionSeed = 0L;
		scanRelays = new int[0];
		routeTargets = new Target[0];
	}

	private boolean scanComplete() {
		for (int relay : scanRelays) {
			if ((scanMask & (1 << relay)) == 0) {
				return false;
			}
		}
		return scanRelays.length > 0;
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
			case PHASE_SCAN -> "message.virtual_craft.virtual_data.scan";
			case PHASE_ROUTE -> "message.virtual_craft.virtual_data.route";
			case PHASE_CACHE -> "message.virtual_craft.virtual_data.cache";
			case PHASE_EXTRACT -> "message.virtual_craft.virtual_data.extract";
			default -> "message.virtual_craft.virtual_data.ready";
		};
	}

	private void flashCurrentHint() {
		// Ambient "alive" pulse on the core itself. Deliberately does NOT keep
		// re-revealing the current scan/route answer the way the old build did —
		// the answer now flashes exactly once when each phase begins, turning the
		// intrusion into a real memory puzzle instead of follow-the-particles.
		if (phase == PHASE_EXTRACT) {
			flashCore(ParticleTypes.HAPPY_VILLAGER, 6);
		} else {
			flashCore(ParticleTypes.ELECTRIC_SPARK, 4);
		}
	}

	private long activeTicks() {
		return boss ? (ACTIVE_TICKS * 2L) / 3L : ACTIVE_TICKS;
	}

	private void openControlledGates() {
		if (level == null) {
			return;
		}
		for (long packed : controlledGates) {
			BlockPos gate = BlockPos.of(packed);
			BlockState state = level.getBlockState(gate);
			if (state.is(ModBlocks.VIRTUAL_DATA_GATE.get()) && !state.getValue(VirtualDataGateBlock.OPEN)) {
				level.setBlock(gate, state.setValue(VirtualDataGateBlock.OPEN, true), 3);
			}
		}
	}

	private void flashScanRelays() {
		for (int relay : scanRelays) {
			if ((scanMask & (1 << relay)) == 0) {
				flashRelay(relay, ParticleTypes.END_ROD, 8);
			}
		}
	}

	private void flashCurrentRouteTarget() {
		if (routeIndex >= 0 && routeIndex < routeTargets.length) {
			flashTarget(routeTargets[routeIndex], ParticleTypes.END_ROD, 12);
		}
	}

	private void flashTarget(Target target, ParticleOptions particle, int count) {
		if (target.type() == TargetType.ANCHOR) {
			flashAnchor(target.index(), particle, count);
		} else {
			flashRelay(target.index(), particle, count);
		}
	}

	private void flashAllRelays(ParticleOptions particle, int count) {
		for (int index = 0; index < VirtualDataRoomPattern.RELAY_COUNT; index++) {
			flashRelay(index, particle, count);
		}
	}

	private void flashAllCaches(ParticleOptions particle, int count) {
		for (int index = 0; index < VirtualDataRoomPattern.CACHE_COUNT; index++) {
			if ((cacheMask & (1 << index)) == 0) {
				flashCache(index, particle, count);
			}
		}
	}

	private void flashAllCorrupted(ParticleOptions particle, int count) {
		for (int index = 0; index < VirtualDataRoomPattern.CORRUPTED_COUNT; index++) {
			flashOffset(VirtualDataRoomPattern.corruptedOffset(index), particle, count);
		}
	}

	private void flashRelay(int relayIndex, ParticleOptions particle, int count) {
		flashOffset(VirtualDataRoomPattern.relayOffset(relayIndex), particle, count);
	}

	private void flashCache(int cacheIndex, ParticleOptions particle, int count) {
		flashOffset(VirtualDataRoomPattern.cacheOffset(cacheIndex), particle, count);
	}

	private void flashAnchor(int nodeIndex, ParticleOptions particle, int count) {
		int node = Math.floorMod(nodeIndex, VirtualDataIntrusionPattern.NODE_COUNT);
		BlockPos anchor = anchorPos(worldPosition, node);
		flashBlock(anchor, particle, count);
	}

	private void flashOffset(VirtualDataRoomPattern.Offset offset, ParticleOptions particle, int count) {
		flashBlock(worldPosition.offset(offset.dx(), offset.dy(), offset.dz()), particle, count);
	}

	private void flashBlock(BlockPos pos, ParticleOptions particle, int count) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					particle,
					pos.getX() + 0.5D,
					pos.getY() + 0.75D,
					pos.getZ() + 0.5D,
					count,
					0.18D,
					0.20D,
					0.18D,
					0.01D);
		}
	}

	private void flashCore(ParticleOptions particle, int count) {
		flashBlock(worldPosition, particle, count);
	}

	private void setGatesOpen(boolean open) {
		for (int index = 0; index < VirtualDataRoomPattern.GATE_COUNT; index++) {
			setGateOpen(index, open);
		}
	}

	private void openGate(int index) {
		setGateOpen(index, true);
	}

	private void setGateOpen(int index, boolean open) {
		if (level == null) {
			return;
		}
		VirtualDataRoomPattern.Offset offset = VirtualDataRoomPattern.gateOffset(index);
		BlockPos pos = worldPosition.offset(offset.dx(), offset.dy(), offset.dz());
		BlockState state = level.getBlockState(pos);
		if (state.is(ModBlocks.VIRTUAL_DATA_GATE.get()) && state.getValue(VirtualDataGateBlock.OPEN) != open) {
			level.setBlock(pos, state.setValue(VirtualDataGateBlock.OPEN, open), 3);
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

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
		tag.putInt("site_index", siteIndex);
		tag.putInt("room_version", roomVersion);
		tag.putInt("depth", depth);
		tag.putBoolean("boss", boss);
		tag.putLongArray("controlled_gates", controlledGates);
		tag.putLong("active_until", activeUntilGameTime);
		tag.putLong("cooldown_until", cooldownUntilGameTime);
		tag.putLong("cache_until", cacheUntilGameTime);
		tag.putInt("phase", phase);
		tag.putInt("corrosion", corrosion);
		tag.putInt("scan_mask", scanMask);
		tag.putInt("cache_mask", cacheMask);
		tag.putInt("route_index", routeIndex);
		tag.putLong("intrusion_seed", intrusionSeed);
		tag.putIntArray("scan_relays", scanRelays);
		tag.putIntArray("route_types", VirtualDataRoomPattern.routeTypes(routeTargets));
		tag.putIntArray("route_indexes", VirtualDataRoomPattern.routeIndexes(routeTargets));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		siteIndex = tag.getInt("site_index");
		roomVersion = tag.getInt("room_version");
		depth = tag.getInt("depth");
		boss = tag.getBoolean("boss");
		controlledGates = tag.getLongArray("controlled_gates");
		activeUntilGameTime = tag.getLong("active_until");
		cooldownUntilGameTime = tag.getLong("cooldown_until");
		cacheUntilGameTime = tag.getLong("cache_until");
		phase = tag.getInt("phase");
		corrosion = tag.getInt("corrosion");
		scanMask = tag.getInt("scan_mask");
		cacheMask = tag.getInt("cache_mask");
		routeIndex = tag.getInt("route_index");
		intrusionSeed = tag.getLong("intrusion_seed");
		scanRelays = tag.getIntArray("scan_relays");
		routeTargets = VirtualDataRoomPattern.routeTargets(tag.getIntArray("route_types"), tag.getIntArray("route_indexes"));
		if (!isValidState()) {
			resetActive();
		} else if (phase == PHASE_IDLE) {
			resetActive();
		}
	}

	private boolean isValidState() {
		if (phase < PHASE_IDLE || phase > PHASE_EXTRACT || corrosion < 0 || corrosion > MAX_CORROSION) {
			return false;
		}
		if (phase == PHASE_IDLE) {
			return true;
		}
		if (scanRelays.length != VirtualDataRoomPattern.SCAN_COUNT
				|| routeTargets.length < VirtualDataRoomPattern.MIN_ROUTE_COUNT
				|| routeTargets.length > VirtualDataRoomPattern.MAX_ROUTE_COUNT) {
			return false;
		}
		return Arrays.stream(scanRelays).allMatch(relay -> relay >= 0 && relay < VirtualDataRoomPattern.RELAY_COUNT)
				&& Arrays.stream(routeTargets).allMatch(this::validTarget)
				&& routeIndex >= 0
				&& routeIndex <= routeTargets.length;
	}

	private boolean validTarget(Target target) {
		if (target == null) {
			return false;
		}
		int bound = target.type() == TargetType.RELAY ? VirtualDataRoomPattern.RELAY_COUNT : VirtualDataIntrusionPattern.NODE_COUNT;
		return target.index() >= 0 && target.index() < bound;
	}
}
