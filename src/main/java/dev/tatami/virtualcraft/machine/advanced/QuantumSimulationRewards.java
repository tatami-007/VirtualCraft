package dev.tatami.virtualcraft.machine.advanced;

import java.util.List;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public final class QuantumSimulationRewards {
	private QuantumSimulationRewards() {
	}

	public static List<ItemStack> create(LockedMachineProcess process) {
		return switch (process.program()) {
			case DEEP_ORE -> List.of(materialReward(process));
			case RELIC_TOPOLOGY -> List.of(componentReward(process));
			case STORM_PATH -> List.of(hashReward(process));
		};
	}

	private static ItemStack materialReward(LockedMachineProcess process) {
		ItemStack output = MiningSurveyTarget.byKey(process.targetKey())
				.map(MiningSurveyTarget::icon)
				.filter(stack -> !stack.isEmpty())
				.orElseGet(() -> new ItemStack(ModItems.VIRTUAL_IRON_ORE.get()));
		output = output.copy();
		output.setCount(QuantumRewardRules.materialYield(
				process.memoryCount(), process.stabilityPermille()));
		return output;
	}

	private static ItemStack componentReward(LockedMachineProcess process) {
		ItemStack output = QuantumComponentTarget.byId(process.targetKey()).icon();
		output.setCount(QuantumRewardRules.componentYield(
				process.memoryCount(), process.stabilityPermille()));
		return output;
	}

	private static ItemStack hashReward(LockedMachineProcess process) {
		QuantumRewardRules.VoucherTier tier = QuantumRewardRules.hashTier(
				process.cpuCount(), process.gpuCount(), process.memoryCount(), process.stabilityPermille());
		ItemStack output = switch (tier) {
			case BASIC -> new ItemStack(ModItems.HASH_VOUCHER.get());
			case EXPLORER -> new ItemStack(ModItems.EXPLORER_HASH_VOUCHER.get());
			case ENCRYPTED -> new ItemStack(ModItems.ENCRYPTED_HASH_VOUCHER.get());
			case QUANTUM -> new ItemStack(ModItems.QUANTUM_HASH_VOUCHER.get());
			case GENESIS -> new ItemStack(ModItems.GENESIS_HASH_VOUCHER.get());
		};
		output.setCount(QuantumRewardRules.hashYield(process.gpuCount(), process.stabilityPermille(), tier));
		return output;
	}
}
