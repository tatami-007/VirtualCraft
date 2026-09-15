package dev.tatami.virtualcraft.client.highlight;

import java.util.List;
import java.util.Optional;

import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import dev.tatami.virtualcraft.machine.advanced.QuantumScanService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class QuantumScanClientState {
	private static QuantumProgram program = QuantumProgram.STORM_PATH;
	private static ResourceKey<Level> dimension = Level.OVERWORLD;
	private static List<QuantumScanService.ScanPoint> points = List.of();

	private QuantumScanClientState() {
	}

	public static void set(QuantumProgram newProgram, ResourceKey<Level> newDimension,
			List<QuantumScanService.ScanPoint> newPoints) {
		program = newProgram;
		dimension = newDimension;
		points = List.copyOf(newPoints);
	}

	public static List<Target> targets() {
		return points.stream()
				.map(point -> new Target(program, dimension, point.pos(), point.count()))
				.toList();
	}

	public static void clear() {
		points = List.of();
	}

	public record Target(QuantumProgram program, ResourceKey<Level> dimension, BlockPos pos, int count) {
	}
}
