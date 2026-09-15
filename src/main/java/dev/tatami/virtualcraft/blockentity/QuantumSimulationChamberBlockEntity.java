package dev.tatami.virtualcraft.blockentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import dev.tatami.virtualcraft.block.QuantumSimulationChamberBlock;
import dev.tatami.virtualcraft.machine.advanced.AdvancedMachineLogic;
import dev.tatami.virtualcraft.machine.advanced.ComputeProfile;
import dev.tatami.virtualcraft.machine.advanced.LockedMachineProcess;
import dev.tatami.virtualcraft.machine.advanced.MachineTransaction;
import dev.tatami.virtualcraft.machine.advanced.QuantumComponentTarget;
import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import dev.tatami.virtualcraft.machine.advanced.QuantumSimulationRewards;
import dev.tatami.virtualcraft.machine.advanced.QuantumSimulationRecipe;
import dev.tatami.virtualcraft.menu.QuantumSimulationChamberMenu;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class QuantumSimulationChamberBlockEntity extends BlockEntity implements MenuProvider {
	public static final int STATUS_IDLE = 0;
	public static final int STATUS_RUNNING = 1;
	public static final int STATUS_WAITING_POWER = 2;
	public static final int STATUS_WAITING_COMPUTE = 3;
	public static final int STATUS_NO_RESONANCE = 4;
	public static final int STATUS_COMPUTE_LOW = 5;
	public static final int STATUS_QUEUE_FULL = 6;
	public static final int STATUS_OUTPUT_FULL = 7;
	public static final int OUTPUT_SLOTS = 3;
	private static final int MAX_ENERGY = 2_000_000;
	private static final int MAX_RECEIVE = 32_768;

	private final ChamberEnergyStorage energyStorage = new ChamberEnergyStorage();
	private final List<LockedMachineProcess> processes = new ArrayList<>();
	private final ItemStackHandler outputInventory = new ItemStackHandler(OUTPUT_SLOTS) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return true;
		}
	};
	private final IItemHandler outputAutomation = new IItemHandler() {
		@Override
		public int getSlots() {
			return outputInventory.getSlots();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return outputInventory.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return stack;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return outputInventory.extractItem(slot, amount, simulate);
		}

		@Override
		public int getSlotLimit(int slot) {
			return outputInventory.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}
	};
	private int selectedProgram;
	private int selectedTargetIndex;
	private int status = STATUS_IDLE;
	private final ContainerData data = new SimpleContainerData(18) {
		@Override
		public int get(int index) {
			ComputeProfile profile = computeProfile();
			return switch (index) {
				case 0 -> energyStorage.getEnergyStored();
				case 1 -> energyStorage.getMaxEnergyStored();
				case 2 -> processes.size();
				case 3 -> profile.parallelLanes();
				case 4 -> processes.isEmpty() ? 0 : processes.getFirst().progressPermille();
				case 5 -> selectedProgram;
				case 6 -> profile.cpuCount();
				case 7 -> profile.gpuCount();
				case 8 -> profile.memoryCount();
				case 9 -> profile.onlineRacks();
				case 10 -> level instanceof ServerLevel serverLevel
						? MachineTransaction.availableResonance(serverLevel, worldPosition) : 0;
				case 11 -> selectedTargetIndex;
				case 12 -> status;
				case 13 -> selectedRecipe().map(QuantumSimulationRecipe::durationTicks).orElse(0);
				case 14 -> selectedRecipe().map(recipe -> (int) Math.min(Integer.MAX_VALUE, recipe.totalEnergy()))
						.orElse(0);
				case 15 -> selectedRecipe().map(QuantumSimulationRecipe::resonanceCost).orElse(0);
				case 16 -> selectedRecipe()
						.map(recipe -> Math.min(16, recipe.baseRangeChunks() + profile.additionalRangeChunks()))
						.orElse(0);
				case 17 -> selectedRecipe().map(QuantumSimulationRecipe::minimumCompute).orElse(0);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			if (index == 5) {
				selectedProgram = value;
			} else if (index == 11) {
				selectedTargetIndex = value;
			}
		}
	};

	public QuantumSimulationChamberBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.QUANTUM_SIMULATION_CHAMBER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state,
			QuantumSimulationChamberBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}
		ComputeProfile profile = blockEntity.computeProfile();
		boolean changed = false;
		Iterator<LockedMachineProcess> iterator = blockEntity.processes.iterator();
		while (iterator.hasNext()) {
			LockedMachineProcess process = iterator.next();
			if (profile.onlineRacks() <= 0) {
				blockEntity.status = STATUS_WAITING_COMPUTE;
				continue;
			}
			AdvancedMachineLogic.TickResult result = AdvancedMachineLogic.tick(
					process, profile.cpuCount(), blockEntity.energyStorage::extractEnergy);
			if (result == AdvancedMachineLogic.TickResult.WAITING_FOR_POWER) {
				blockEntity.status = STATUS_WAITING_POWER;
				continue;
			}
			changed = true;
			blockEntity.status = STATUS_RUNNING;
			if (result == AdvancedMachineLogic.TickResult.COMPLETE) {
				List<ItemStack> rewards = QuantumSimulationRewards.create(process);
				if (blockEntity.insertOutputs(rewards, true)) {
					blockEntity.insertOutputs(rewards, false);
					iterator.remove();
				} else {
					blockEntity.status = STATUS_OUTPUT_FULL;
				}
			}
		}
		if (blockEntity.processes.isEmpty() && blockEntity.status < STATUS_NO_RESONANCE) {
			blockEntity.status = STATUS_IDLE;
		}
		blockEntity.updateActiveState(state, !blockEntity.processes.isEmpty());
		if (changed) {
			blockEntity.setChanged();
		}
	}

	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	public ContainerData getData() {
		return data;
	}

	public ItemStackHandler getOutputInventory() {
		return outputInventory;
	}

	public IItemHandler getOutputAutomation() {
		return outputAutomation;
	}

	public boolean hasActiveProcesses() {
		return !processes.isEmpty();
	}

	public boolean startSelectedProgram(ServerPlayer player) {
		ServerLevel serverLevel = player.serverLevel();
		ComputeProfile profile = computeProfile();
		if (processes.size() >= profile.parallelLanes()) {
			status = STATUS_QUEUE_FULL;
			return false;
		}
		QuantumProgram program = QuantumProgram.byIndex(selectedProgram);
		Optional<RecipeHolder<QuantumSimulationRecipe>> holder = recipeFor(serverLevel, program);
		if (holder.isEmpty()) {
			status = STATUS_COMPUTE_LOW;
			return false;
		}
		QuantumSimulationRecipe recipe = holder.get().value();
		if (profile.computeScore() < recipe.minimumCompute() || profile.onlineRacks() <= 0) {
			status = STATUS_COMPUTE_LOW;
			return false;
		}
		Optional<BlockPos> source = MachineTransaction.reserveResonance(
				serverLevel, worldPosition, recipe.resonanceCost());
		if (source.isEmpty()) {
			status = STATUS_NO_RESONANCE;
			return false;
		}
		String targetKey = switch (program) {
			case DEEP_ORE -> selectedTarget().key();
			case RELIC_TOPOLOGY -> selectedComponent().id();
			case STORM_PATH -> "";
		};
		int range = Math.min(16, recipe.baseRangeChunks() + profile.additionalRangeChunks());
		processes.add(new LockedMachineProcess(
				holder.get().id(),
				program,
				player.getUUID(),
				targetKey,
				recipe.durationTicks(),
				recipe.totalEnergy(),
				recipe.resonanceCost(),
				range,
				source.get(),
				profile.cpuCount(),
				profile.gpuCount(),
				profile.memoryCount()));
		status = STATUS_RUNNING;
		setChanged();
		return true;
	}

	public boolean cancelLatest(ServerPlayer player) {
		for (int index = processes.size() - 1; index >= 0; index--) {
			LockedMachineProcess process = processes.get(index);
			if (!process.owner().equals(player.getUUID())) {
				continue;
			}
			processes.remove(index);
			if (!process.isComplete()) {
				MachineTransaction.refundResonance(player.serverLevel(), process.resonanceSource(),
						process.resonanceCost());
			}
			status = processes.isEmpty() ? STATUS_IDLE : STATUS_RUNNING;
			setChanged();
			return true;
		}
		return false;
	}

	public void cycleProgram(int delta) {
		selectedProgram = Math.floorMod(selectedProgram + delta, QuantumProgram.values().length);
		setChanged();
	}

	public void cycleTarget(int delta) {
		int size = switch (QuantumProgram.byIndex(selectedProgram)) {
			case DEEP_ORE -> Math.max(1, materialTargets().size());
			case RELIC_TOPOLOGY -> QuantumComponentTarget.values().length;
			case STORM_PATH -> 1;
		};
		selectedTargetIndex = Math.floorMod(selectedTargetIndex + delta, size);
		setChanged();
	}

	public MiningSurveyTarget selectedTarget() {
		List<MiningSurveyTarget> targets = materialTargets();
		return targets.isEmpty() ? MiningSurveyTarget.defaultTarget()
				: targets.get(Math.floorMod(selectedTargetIndex, targets.size()));
	}

	public QuantumComponentTarget selectedComponent() {
		return QuantumComponentTarget.byIndex(selectedTargetIndex);
	}

	private static List<MiningSurveyTarget> materialTargets() {
		return MiningSurveyTarget.available().stream()
				.filter(target -> !"all".equals(target.key()))
				.toList();
	}

	public ComputeProfile computeProfile() {
		ComputeProfile profile = ComputeProfile.EMPTY;
		if (level == null) {
			return profile;
		}
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction)) instanceof ComputeRackBlockEntity rack) {
				profile = profile.add(rack.profile());
			}
		}
		return profile;
	}

	private Optional<RecipeHolder<QuantumSimulationRecipe>> recipeFor(ServerLevel level, QuantumProgram program) {
		return level.getRecipeManager()
				.getAllRecipesFor(ModRecipeTypes.QUANTUM_SIMULATION_TYPE.get())
				.stream()
				.filter(holder -> holder.value().program() == program)
				.findFirst();
	}

	private Optional<QuantumSimulationRecipe> selectedRecipe() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return recipeFor(serverLevel, QuantumProgram.byIndex(selectedProgram)).map(RecipeHolder::value);
	}

	private boolean insertOutputs(List<ItemStack> rewards, boolean simulate) {
		ItemStackHandler destination = outputInventory;
		if (simulate) {
			destination = new ItemStackHandler(OUTPUT_SLOTS);
			for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
				destination.setStackInSlot(slot, outputInventory.getStackInSlot(slot).copy());
			}
		}
		for (ItemStack reward : rewards) {
			if (!ItemHandlerHelper.insertItemStacked(destination, reward.copy(), false).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private void updateActiveState(BlockState state, boolean active) {
		if (level != null && state.hasProperty(QuantumSimulationChamberBlock.ACTIVE)
				&& state.getValue(QuantumSimulationChamberBlock.ACTIVE) != active) {
			level.setBlock(worldPosition, state.setValue(QuantumSimulationChamberBlock.ACTIVE, active), 3);
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.quantum_simulation_chamber");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new QuantumSimulationChamberMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.put("outputs", outputInventory.serializeNBT(registries));
		tag.putInt("selected_program", selectedProgram);
		tag.putInt("selected_target", selectedTargetIndex);
		ListTag processList = new ListTag();
		processes.forEach(process -> processList.add(process.save()));
		tag.put("processes", processList);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		outputInventory.deserializeNBT(registries, tag.getCompound("outputs"));
		selectedProgram = tag.getInt("selected_program");
		selectedTargetIndex = tag.getInt("selected_target");
		processes.clear();
		ListTag processList = tag.getList("processes", Tag.TAG_COMPOUND);
		for (Tag entry : processList) {
			processes.add(LockedMachineProcess.load((CompoundTag) entry));
		}
	}

	public void dropContents(Level level, BlockPos pos) {
		net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(OUTPUT_SLOTS);
		for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
			container.setItem(slot, outputInventory.getStackInSlot(slot));
		}
		net.minecraft.world.Containers.dropContents(level, pos, container);
	}

	private final class ChamberEnergyStorage extends EnergyStorage {
		private ChamberEnergyStorage() {
			super(MAX_ENERGY, MAX_RECEIVE, 0, 0);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (!simulate && received > 0) {
				setChanged();
			}
			return received;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			int extracted = Math.min(energy, Math.max(0, toExtract));
			if (!simulate && extracted > 0) {
				energy -= extracted;
				setChanged();
			}
			return extracted;
		}
	}
}
