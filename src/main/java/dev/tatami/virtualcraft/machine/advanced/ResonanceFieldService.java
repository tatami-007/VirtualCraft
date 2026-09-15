package dev.tatami.virtualcraft.machine.advanced;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.tatami.virtualcraft.blockentity.LightningResonatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ResonanceFieldService {
	public static final int FIELD_RADIUS = 12;
	private static final Map<ServerLevel, Set<BlockPos>> RESONATORS = new HashMap<>();

	private ResonanceFieldService() {
	}

	public static void register(ServerLevel level, BlockPos pos) {
		RESONATORS.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
	}

	public static void unregister(ServerLevel level, BlockPos pos) {
		Set<BlockPos> positions = RESONATORS.get(level);
		if (positions != null) {
			positions.remove(pos);
			if (positions.isEmpty()) {
				RESONATORS.remove(level);
			}
		}
	}

	public static boolean isOverclocked(ServerLevel level, BlockPos machinePos) {
		for (BlockPos pos : positions(level)) {
			if (pos.distSqr(machinePos) <= FIELD_RADIUS * FIELD_RADIUS
					&& level.getBlockEntity(pos) instanceof LightningResonatorBlockEntity resonator
					&& resonator.isOverdriveActive()) {
				return true;
			}
		}
		return false;
	}

	public static void receiveLightning(ServerLevel level, BlockPos strikePos) {
		for (BlockPos pos : positions(level)) {
			if (pos.distSqr(strikePos) <= 16 * 16
					&& level.getBlockEntity(pos) instanceof LightningResonatorBlockEntity resonator) {
				resonator.addResonance(250);
			}
		}
	}

	public static Set<BlockPos> positions(ServerLevel level) {
		Set<BlockPos> positions = RESONATORS.get(level);
		return positions == null ? Set.of() : Set.copyOf(positions);
	}
}
