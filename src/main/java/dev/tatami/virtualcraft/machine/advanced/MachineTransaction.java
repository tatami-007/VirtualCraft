package dev.tatami.virtualcraft.machine.advanced;

import java.util.Comparator;
import java.util.Optional;

import dev.tatami.virtualcraft.blockentity.LightningResonatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class MachineTransaction {
	private static final int RESONANCE_LINK_RADIUS = 12;

	private MachineTransaction() {
	}

	public static Optional<BlockPos> reserveResonance(ServerLevel level, BlockPos machinePos, int amount) {
		return ResonanceFieldService.positions(level).stream()
				.filter(pos -> pos.distSqr(machinePos) <= RESONANCE_LINK_RADIUS * RESONANCE_LINK_RADIUS)
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(machinePos)))
				.filter(pos -> level.getBlockEntity(pos) instanceof LightningResonatorBlockEntity resonator
						&& resonator.extractResonance(amount, true) == amount)
				.filter(pos -> {
					LightningResonatorBlockEntity resonator =
							(LightningResonatorBlockEntity) level.getBlockEntity(pos);
					return resonator != null && resonator.extractResonance(amount, false) == amount;
				})
				.findFirst()
				.map(BlockPos::immutable);
	}

	public static int availableResonance(ServerLevel level, BlockPos machinePos) {
		int total = 0;
		for (BlockPos pos : ResonanceFieldService.positions(level)) {
			if (pos.distSqr(machinePos) <= RESONANCE_LINK_RADIUS * RESONANCE_LINK_RADIUS
					&& level.getBlockEntity(pos) instanceof LightningResonatorBlockEntity resonator) {
				total = Math.min(Integer.MAX_VALUE, total + resonator.getResonance());
			}
		}
		return total;
	}

	public static void refundResonance(ServerLevel level, BlockPos source, int amount) {
		if (amount > 0 && level.getBlockEntity(source) instanceof LightningResonatorBlockEntity resonator) {
			resonator.addResonance(amount);
		}
	}
}
