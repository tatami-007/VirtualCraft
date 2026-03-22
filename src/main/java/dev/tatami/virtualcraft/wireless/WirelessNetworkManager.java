package dev.tatami.virtualcraft.wireless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.tatami.virtualcraft.blockentity.AbstractWirelessPowerBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerReceiverBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class WirelessNetworkManager {
	private static final int RANGE_BLOCKS = 24;
	private static final Map<ResourceKey<Level>, Set<WirelessPowerTowerBlockEntity>> TOWERS = new java.util.HashMap<>();
	private static final Map<ResourceKey<Level>, Set<WirelessPowerReceiverBlockEntity>> RECEIVERS = new java.util.HashMap<>();

	private WirelessNetworkManager() {
	}

	public static void registerTower(WirelessPowerTowerBlockEntity tower) {
		register(TOWERS, tower);
	}

	public static void unregisterTower(WirelessPowerTowerBlockEntity tower) {
		unregister(TOWERS, tower);
	}

	public static void registerReceiver(WirelessPowerReceiverBlockEntity receiver) {
		register(RECEIVERS, receiver);
	}

	public static void unregisterReceiver(WirelessPowerReceiverBlockEntity receiver) {
		unregister(RECEIVERS, receiver);
	}

	public static List<WirelessPowerReceiverBlockEntity> findReceivers(Level level, WirelessPowerTowerBlockEntity tower) {
		return findMatches(level, tower, RECEIVERS);
	}

	public static List<WirelessPowerTowerBlockEntity> findTowers(Level level, WirelessPowerReceiverBlockEntity receiver) {
		return findMatches(level, receiver, TOWERS);
	}

	private static <T extends AbstractWirelessPowerBlockEntity> void register(Map<ResourceKey<Level>, Set<T>> buckets, T node) {
		if (node.getLevel() == null) {
			return;
		}
		buckets.computeIfAbsent(node.getLevel().dimension(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(node);
	}

	private static <T extends AbstractWirelessPowerBlockEntity> void unregister(Map<ResourceKey<Level>, Set<T>> buckets, T node) {
		if (node.getLevel() == null) {
			return;
		}
		Set<T> nodes = buckets.get(node.getLevel().dimension());
		if (nodes != null) {
			nodes.remove(node);
			if (nodes.isEmpty()) {
				buckets.remove(node.getLevel().dimension());
			}
		}
	}

	private static <S extends AbstractWirelessPowerBlockEntity, T extends AbstractWirelessPowerBlockEntity> List<T> findMatches(Level level, S source, Map<ResourceKey<Level>, Set<T>> buckets) {
		Set<T> candidates = buckets.get(level.dimension());
		if (candidates == null || !source.hasBoundLinkCard()) {
			return List.of();
		}

		UUID ownerUuid = source.linkOwnerUuid();
		if (ownerUuid == null) {
			return List.of();
		}

		List<T> matches = new ArrayList<>();
		java.util.Iterator<T> iterator = candidates.iterator();
		while (iterator.hasNext()) {
			T candidate = iterator.next();
			if (candidate == null || candidate.isRemoved() || candidate.getLevel() != level) {
				iterator.remove();
				continue;
			}
			if (candidate.hasBoundLinkCard()
					&& ownerUuid.equals(candidate.linkOwnerUuid())
					&& source.linkChannel() == candidate.linkChannel()
					&& source.getBlockPos().distSqr(candidate.getBlockPos()) <= RANGE_BLOCKS * RANGE_BLOCKS) {
				matches.add(candidate);
			}
		}

		matches.sort((left, right) -> Double.compare(source.getBlockPos().distSqr(left.getBlockPos()), source.getBlockPos().distSqr(right.getBlockPos())));
		return matches;
	}
}
