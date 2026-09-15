package dev.tatami.virtualcraft.machine.advanced;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class LockedMachineProcess {
	private final ResourceLocation recipeId;
	private final QuantumProgram program;
	private final UUID owner;
	private final String targetKey;
	private final int totalTicks;
	private final long totalEnergy;
	private final int resonanceCost;
	private final int rangeChunks;
	private final BlockPos resonanceSource;
	private final int cpuCount;
	private final int gpuCount;
	private final int memoryCount;
	private long workUnits;
	private long consumedEnergy;
	private long runningTicks;
	private long waitingForPowerTicks;

	public LockedMachineProcess(ResourceLocation recipeId, QuantumProgram program, UUID owner, String targetKey,
			int totalTicks, long totalEnergy, int resonanceCost, int rangeChunks, BlockPos resonanceSource,
			int cpuCount, int gpuCount, int memoryCount) {
		this.recipeId = recipeId;
		this.program = program;
		this.owner = owner;
		this.targetKey = targetKey;
		this.totalTicks = totalTicks;
		this.totalEnergy = totalEnergy;
		this.resonanceCost = resonanceCost;
		this.rangeChunks = rangeChunks;
		this.resonanceSource = resonanceSource.immutable();
		this.cpuCount = Math.max(0, cpuCount);
		this.gpuCount = Math.max(0, gpuCount);
		this.memoryCount = Math.max(0, memoryCount);
	}

	public ResourceLocation recipeId() {
		return recipeId;
	}

	public QuantumProgram program() {
		return program;
	}

	public UUID owner() {
		return owner;
	}

	public String targetKey() {
		return targetKey;
	}

	public int totalTicks() {
		return totalTicks;
	}

	public long totalEnergy() {
		return totalEnergy;
	}

	public int resonanceCost() {
		return resonanceCost;
	}

	public int rangeChunks() {
		return rangeChunks;
	}

	public BlockPos resonanceSource() {
		return resonanceSource;
	}

	public int cpuCount() {
		return cpuCount;
	}

	public int gpuCount() {
		return gpuCount;
	}

	public int memoryCount() {
		return memoryCount;
	}

	public int stabilityPermille() {
		return QuantumRewardRules.stabilityPermille(runningTicks, waitingForPowerTicks);
	}

	public long workUnits() {
		return workUnits;
	}

	public long consumedEnergy() {
		return consumedEnergy;
	}

	public long totalWorkUnits() {
		return Math.max(1L, (long) totalTicks * AdvancedMachineLogic.WORK_SCALE);
	}

	public int progressPermille() {
		return (int) Math.min(1000L, workUnits * 1000L / totalWorkUnits());
	}

	void advance(long addedWork, long energy) {
		workUnits = Math.min(totalWorkUnits(), workUnits + Math.max(0L, addedWork));
		consumedEnergy = Math.min(totalEnergy, consumedEnergy + Math.max(0L, energy));
		runningTicks++;
	}

	void recordPowerWait() {
		waitingForPowerTicks++;
	}

	public boolean isComplete() {
		return workUnits >= totalWorkUnits() && consumedEnergy >= totalEnergy;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("recipe", recipeId.toString());
		tag.putString("program", program.getSerializedName());
		tag.putUUID("owner", owner);
		tag.putString("target", targetKey);
		tag.putInt("total_ticks", totalTicks);
		tag.putLong("total_energy", totalEnergy);
		tag.putInt("resonance", resonanceCost);
		tag.putInt("range_chunks", rangeChunks);
		tag.putLong("resonance_source", resonanceSource.asLong());
		tag.putInt("cpu_count", cpuCount);
		tag.putInt("gpu_count", gpuCount);
		tag.putInt("memory_count", memoryCount);
		tag.putLong("work", workUnits);
		tag.putLong("energy", consumedEnergy);
		tag.putLong("running_ticks", runningTicks);
		tag.putLong("power_wait_ticks", waitingForPowerTicks);
		return tag;
	}

	public static LockedMachineProcess load(CompoundTag tag) {
		ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("recipe"));
		QuantumProgram program = QuantumProgram.CODEC.parse(
				com.mojang.serialization.JsonOps.INSTANCE,
				new com.google.gson.JsonPrimitive(tag.getString("program"))).result().orElse(QuantumProgram.DEEP_ORE);
		UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0L, 0L);
		LockedMachineProcess process = new LockedMachineProcess(
				recipe == null ? ResourceLocation.withDefaultNamespace("empty") : recipe,
				program,
				owner,
				tag.getString("target"),
				Math.max(1, tag.getInt("total_ticks")),
				Math.max(1L, tag.getLong("total_energy")),
				Math.max(0, tag.getInt("resonance")),
				Math.max(1, tag.getInt("range_chunks")),
				BlockPos.of(tag.getLong("resonance_source")),
				Math.max(0, tag.getInt("cpu_count")),
				Math.max(0, tag.getInt("gpu_count")),
				Math.max(0, tag.getInt("memory_count")));
		process.workUnits = Math.max(0L, tag.getLong("work"));
		process.consumedEnergy = Math.max(0L, tag.getLong("energy"));
		process.runningTicks = Math.max(0L, tag.getLong("running_ticks"));
		process.waitingForPowerTicks = Math.max(0L, tag.getLong("power_wait_ticks"));
		return process;
	}
}
