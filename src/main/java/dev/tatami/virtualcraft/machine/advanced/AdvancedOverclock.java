package dev.tatami.virtualcraft.machine.advanced;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class AdvancedOverclock {
	private AdvancedOverclock() {
	}

	public static boolean active(Level level, BlockPos pos) {
		return level instanceof ServerLevel serverLevel && ResonanceFieldService.isOverclocked(serverLevel, pos);
	}

	public static int energyCost(int baseCost, boolean active) {
		return active ? AdvancedMachineRules.overclockEnergyCost(baseCost) : baseCost;
	}

	public static int progress(int baseProgress, boolean active, long gameTime) {
		if (!active) {
			return baseProgress;
		}
		return AdvancedMachineRules.overclockProgress(baseProgress, gameTime);
	}
}
