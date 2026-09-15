package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.QuantumSimulationChamberBlockEntity;
import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import dev.tatami.virtualcraft.machine.advanced.QuantumComponentTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class QuantumSimulationChamberMenu extends AbstractContainerMenu {
	public static final int PREVIOUS_PROGRAM = 1;
	public static final int NEXT_PROGRAM = 2;
	public static final int PREVIOUS_TARGET = 3;
	public static final int NEXT_TARGET = 4;
	public static final int START = 5;
	public static final int CANCEL = 6;
	private static final int MACHINE_SLOTS = QuantumSimulationChamberBlockEntity.OUTPUT_SLOTS;
	private static final int PLAYER_START = MACHINE_SLOTS;
	private static final int PLAYER_END = PLAYER_START + 36;

	private final QuantumSimulationChamberBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public QuantumSimulationChamberMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, resolve(inventory, buffer.readBlockPos()), new SimpleContainerData(18));
	}

	public QuantumSimulationChamberMenu(int containerId, Inventory inventory,
			QuantumSimulationChamberBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.QUANTUM_SIMULATION_CHAMBER.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = inventory.player.level();
		this.data = data;
		for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
			addSlot(new SlotItemHandler(blockEntity.getOutputInventory(), slot, 116 + slot * 18, 50) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}
			});
		}
		addPlayerSlots(inventory);
		addDataSlots(data);
	}

	public int energy() {
		return data.get(0);
	}

	public int maxEnergy() {
		return data.get(1);
	}

	public int activeTasks() {
		return data.get(2);
	}

	public int maxLanes() {
		return data.get(3);
	}

	public int progressPermille() {
		return data.get(4);
	}

	public QuantumProgram selectedProgram() {
		return QuantumProgram.byIndex(data.get(5));
	}

	public int cpuCount() {
		return data.get(6);
	}

	public int gpuCount() {
		return data.get(7);
	}

	public int memoryCount() {
		return data.get(8);
	}

	public int onlineRacks() {
		return data.get(9);
	}

	public int resonance() {
		return data.get(10);
	}

	public MiningSurveyTarget selectedTarget() {
		var targets = MiningSurveyTarget.available().stream()
				.filter(target -> !"all".equals(target.key()))
				.toList();
		return targets.isEmpty() ? MiningSurveyTarget.defaultTarget()
				: targets.get(Math.floorMod(data.get(11), targets.size()));
	}

	public QuantumComponentTarget selectedComponent() {
		return QuantumComponentTarget.byIndex(data.get(11));
	}

	public ItemStack selectedOutputIcon() {
		return switch (selectedProgram()) {
			case DEEP_ORE -> selectedTarget().icon();
			case RELIC_TOPOLOGY -> selectedComponent().icon();
			case STORM_PATH -> new ItemStack(dev.tatami.virtualcraft.registry.ModItems.QUANTUM_HASH_VOUCHER.get());
		};
	}

	public net.minecraft.network.chat.Component selectedOutputName() {
		return switch (selectedProgram()) {
			case DEEP_ORE -> selectedTarget().name();
			case RELIC_TOPOLOGY -> selectedComponent().displayName();
			case STORM_PATH -> net.minecraft.network.chat.Component.translatable(
					"screen.virtual_craft.quantum_simulation.hash_quality");
		};
	}

	public int status() {
		return data.get(12);
	}

	public int durationTicks() {
		return data.get(13);
	}

	public int totalEnergyCost() {
		return data.get(14);
	}

	public int resonanceCost() {
		return data.get(15);
	}

	public int rangeChunks() {
		return data.get(16);
	}

	public int minimumCompute() {
		return data.get(17);
	}

	public int computeScore() {
		return cpuCount() + gpuCount();
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		switch (id) {
			case PREVIOUS_PROGRAM -> blockEntity.cycleProgram(-1);
			case NEXT_PROGRAM -> blockEntity.cycleProgram(1);
			case PREVIOUS_TARGET -> blockEntity.cycleTarget(-1);
			case NEXT_TARGET -> blockEntity.cycleTarget(1);
			case START -> {
				return blockEntity.startSelectedProgram(serverPlayer);
			}
			case CANCEL -> {
				return blockEntity.cancelLatest(serverPlayer);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot source = slots.get(index);
		if (!source.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = source.getItem();
		ItemStack copy = stack.copy();
		if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			source.set(ItemStack.EMPTY);
		} else {
			source.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
				player, ModBlocks.QUANTUM_SIMULATION_CHAMBER.get());
	}

	private static QuantumSimulationChamberBlockEntity resolve(Inventory inventory, BlockPos pos) {
		BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof QuantumSimulationChamberBlockEntity chamber) {
			return chamber;
		}
		throw new IllegalStateException("Expected quantum simulation chamber at " + pos);
	}

	private void addPlayerSlots(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, 8 + column * 18, 154));
		}
	}
}
