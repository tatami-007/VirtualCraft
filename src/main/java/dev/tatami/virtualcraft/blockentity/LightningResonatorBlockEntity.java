package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.machine.advanced.ResonanceFieldService;
import dev.tatami.virtualcraft.menu.LightningResonatorMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.world.LightningForestEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class LightningResonatorBlockEntity extends BlockEntity implements MenuProvider {
	public static final int MAX_RESONANCE = 1000;
	public static final int OVERDRIVE_COST = 200;
	public static final int OVERDRIVE_TICKS = 600;
	public static final int OVERDRIVE_COOLDOWN_TICKS = 1800;
	private static final int MAX_ENERGY = 1_000_000;
	private static final int MAX_RECEIVE = 16_384;
	private static final int ENERGY_PER_TICK = 2_048;
	private static final int POOL_SCAN_INTERVAL = 40;
	private static final int POOL_RADIUS = 8;
	private static final int POOL_VERTICAL_RADIUS = 4;

	private final ResonatorEnergyStorage energyStorage = new ResonatorEnergyStorage();
	private final ContainerData data = new SimpleContainerData(8) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> energyStorage.getEnergyStored();
				case 1 -> energyStorage.getMaxEnergyStored();
				case 2 -> resonance;
				case 3 -> MAX_RESONANCE;
				case 4 -> overdriveTicks;
				case 5 -> cooldownTicks;
				case 6 -> hasThunderPool ? 1 : 0;
				case 7 -> forestEfficiency;
				default -> 0;
			};
		}
	};

	private int resonance;
	private int overdriveTicks;
	private int cooldownTicks;
	private boolean hasThunderPool;
	private int forestEfficiency = 1;

	public LightningResonatorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LIGHTNING_RESONATOR.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, LightningResonatorBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}
		if (blockEntity.overdriveTicks > 0) {
			blockEntity.overdriveTicks--;
			blockEntity.setChanged();
		} else if (blockEntity.cooldownTicks > 0) {
			blockEntity.cooldownTicks--;
			blockEntity.setChanged();
		}

		if (level.getGameTime() % POOL_SCAN_INTERVAL == 0) {
			blockEntity.refreshEnvironment((ServerLevel) level);
		}
		if (!blockEntity.hasThunderPool || blockEntity.resonance >= MAX_RESONANCE
				|| blockEntity.energyStorage.getEnergyStored() < ENERGY_PER_TICK) {
			return;
		}
		blockEntity.energyStorage.consumeEnergy(ENERGY_PER_TICK);
		if (level.getGameTime() % 20L == 0L) {
			blockEntity.addResonance(blockEntity.forestEfficiency);
		}
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level instanceof ServerLevel serverLevel) {
			ResonanceFieldService.register(serverLevel, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		if (level instanceof ServerLevel serverLevel) {
			ResonanceFieldService.unregister(serverLevel, worldPosition);
		}
		super.setRemoved();
	}

	public IEnergyStorage getEnergyStorage(Direction side) {
		return energyStorage;
	}

	public ContainerData getData() {
		return data;
	}

	public int getResonance() {
		return resonance;
	}

	public boolean isOverdriveActive() {
		return overdriveTicks > 0;
	}

	public int extractResonance(int amount, boolean simulate) {
		int extracted = Math.min(Math.max(0, amount), resonance);
		if (!simulate && extracted > 0) {
			resonance -= extracted;
			setChanged();
		}
		return extracted;
	}

	public int addResonance(int amount) {
		int accepted = Math.min(Math.max(0, amount), MAX_RESONANCE - resonance);
		if (accepted > 0) {
			resonance += accepted;
			setChanged();
		}
		return accepted;
	}

	public boolean tryActivateOverdrive() {
		if (overdriveTicks > 0 || cooldownTicks > 0 || resonance < OVERDRIVE_COST) {
			return false;
		}
		resonance -= OVERDRIVE_COST;
		overdriveTicks = OVERDRIVE_TICKS;
		cooldownTicks = OVERDRIVE_COOLDOWN_TICKS;
		setChanged();
		return true;
	}

	private void refreshEnvironment(ServerLevel level) {
		hasThunderPool = findThunderPool(level);
		forestEfficiency = level.getBiome(worldPosition).is(LightningForestEffects.LIGHTNING_FOREST) ? 2 : 1;
		setChanged();
	}

	private boolean findThunderPool(ServerLevel level) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = -POOL_VERTICAL_RADIUS; y <= POOL_VERTICAL_RADIUS; y++) {
			for (int x = -POOL_RADIUS; x <= POOL_RADIUS; x++) {
				for (int z = -POOL_RADIUS; z <= POOL_RADIUS; z++) {
					if (x * x + z * z > POOL_RADIUS * POOL_RADIUS) {
						continue;
					}
					cursor.setWithOffset(worldPosition, x, y, z);
					if (level.hasChunkAt(cursor) && level.getBlockState(cursor).is(ModBlocks.THUNDER_POOL.get())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.lightning_resonator");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new LightningResonatorMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.putInt("resonance", resonance);
		tag.putInt("overdrive_ticks", overdriveTicks);
		tag.putInt("cooldown_ticks", cooldownTicks);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		resonance = Math.min(MAX_RESONANCE, Math.max(0, tag.getInt("resonance")));
		overdriveTicks = Math.max(0, tag.getInt("overdrive_ticks"));
		cooldownTicks = Math.max(0, tag.getInt("cooldown_ticks"));
	}

	private final class ResonatorEnergyStorage extends EnergyStorage {
		private ResonatorEnergyStorage() {
			super(MAX_ENERGY, MAX_RECEIVE, 0, 0);
		}

		private void consumeEnergy(int amount) {
			energy = Math.max(0, energy - amount);
			setChanged();
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (!simulate && received > 0) {
				setChanged();
			}
			return received;
		}
	}
}
