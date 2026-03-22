package dev.tatami.virtualcraft.blockentity;

import java.util.UUID;
import java.util.List;

import javax.annotation.Nullable;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.machine.GeneratorFundingMode;
import dev.tatami.virtualcraft.menu.CurrencyGeneratorMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CurrencyGeneratorBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
	private static final int FUEL_SLOT = 0;
	private static final int MAX_ENERGY = 200_000_000;
	private static final int MAX_EXTRACT = 131_072;
	private static final List<CryptoCurrency> WALLET_PRIORITY = List.of(
			CryptoCurrency.DOGE,
			CryptoCurrency.SOL,
			CryptoCurrency.USDT,
			CryptoCurrency.ETH,
			CryptoCurrency.BTC);
	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return CryptoCurrency.byItem(stack) != null;
		}
	};
	private final GeneratorEnergyStorage energyStorage = new GeneratorEnergyStorage();
	private final ContainerData data = new SimpleContainerData(7) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> energyStorage.getEnergyStored();
				case 1 -> energyStorage.getMaxEnergyStored();
				case 2 -> burnTimeRemaining;
				case 3 -> burnTimeTotal;
				case 4 -> fundingMode.ordinal();
				case 5 -> currentBurnCurrency == null ? -1 : currentBurnCurrency.ordinal();
				case 6 -> lastAutoSelectedCurrency == null ? -1 : lastAutoSelectedCurrency.ordinal();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 2 -> burnTimeRemaining = value;
				case 3 -> burnTimeTotal = value;
				case 4 -> fundingMode = GeneratorFundingMode.values()[Math.max(0, Math.min(GeneratorFundingMode.values().length - 1, value))];
				case 5 -> currentBurnCurrency = value < 0 ? null : CryptoCurrency.values()[Math.max(0, Math.min(CryptoCurrency.values().length - 1, value))];
				case 6 -> lastAutoSelectedCurrency = value < 0 ? null : CryptoCurrency.values()[Math.max(0, Math.min(CryptoCurrency.values().length - 1, value))];
				default -> {
				}
			}
		}
	};

	private int burnTimeRemaining;
	private int burnTimeTotal;
	private GeneratorFundingMode fundingMode = GeneratorFundingMode.ITEM_ONLY;
	@Nullable
	private CryptoCurrency currentBurnCurrency;
	@Nullable
	private CryptoCurrency lastAutoSelectedCurrency;
	@Nullable
	private UUID ownerUuid;
	private String ownerName = "";

	public CurrencyGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.CURRENCY_GENERATOR.get(), pos, blockState);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, CurrencyGeneratorBlockEntity blockEntity) {
		if (level.isClientSide) {
			return;
		}

		boolean changed = false;
		if (blockEntity.burnTimeRemaining > 0 && blockEntity.currentBurnCurrency != null) {
			int generated = blockEntity.energyStorage.addEnergy(blockEntity.currentBurnCurrency.energyPerTick());
			if (generated > 0) {
				blockEntity.burnTimeRemaining--;
				changed = true;
				if (blockEntity.burnTimeRemaining <= 0) {
					blockEntity.burnTimeRemaining = 0;
					blockEntity.burnTimeTotal = 0;
					blockEntity.currentBurnCurrency = null;
				}
			}
		}

		if (blockEntity.burnTimeRemaining == 0 && blockEntity.energyStorage.getEnergyStored() < blockEntity.energyStorage.getMaxEnergyStored()) {
			changed |= blockEntity.tryStartBurn(level);
		}
		if (blockEntity.energyStorage.getEnergyStored() > 0) {
			changed |= blockEntity.pushEnergyToNeighbors(level, pos);
		}

		if (changed) {
			blockEntity.setChanged();
		}
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
		return energyStorage;
	}

	public ContainerData getData() {
		return data;
	}

	public GeneratorFundingMode getFundingMode() {
		return fundingMode;
	}

	public void cycleFundingMode(Player player) {
		if (canConfigure(player)) {
			fundingMode = fundingMode.next();
			setChanged();
		}
	}

	@Nullable
	public CryptoCurrency getCurrentBurnCurrency() {
		return currentBurnCurrency;
	}

	@Nullable
	public CryptoCurrency getLastAutoSelectedCurrency() {
		return lastAutoSelectedCurrency;
	}

	@Nullable
	public UUID getOwnerUuid() {
		return ownerUuid;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public boolean bindOwner(Player player) {
		if (ownerUuid == null || ownerUuid.equals(player.getUUID())) {
			ownerUuid = player.getUUID();
			ownerName = player.getGameProfile().getName();
			setChanged();
			return true;
		}
		return false;
	}

	public boolean canConfigure(Player player) {
		return ownerUuid == null || ownerUuid.equals(player.getUUID());
	}

	public void dropContents(Level level, BlockPos pos) {
		SimpleContainer container = new SimpleContainer(inventory.getSlots());
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			container.setItem(slot, inventory.getStackInSlot(slot));
		}
		Containers.dropContents(level, pos, container);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.currency_generator");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new CurrencyGeneratorMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.put("energy", energyStorage.serializeNBT(registries));
		tag.putInt("burn_time_remaining", burnTimeRemaining);
		tag.putInt("burn_time_total", burnTimeTotal);
		tag.putString("funding_mode", fundingMode.name());
		if (currentBurnCurrency != null) {
			tag.putString("current_burn_currency", currentBurnCurrency.name());
		}
		if (lastAutoSelectedCurrency != null) {
			tag.putString("last_auto_selected_currency", lastAutoSelectedCurrency.name());
		}
		if (ownerUuid != null) {
			tag.putUUID("owner_uuid", ownerUuid);
		}
		if (!ownerName.isEmpty()) {
			tag.putString("owner_name", ownerName);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		energyStorage.deserializeNBT(registries, tag.get("energy"));
		burnTimeRemaining = tag.getInt("burn_time_remaining");
		burnTimeTotal = tag.getInt("burn_time_total");
		try {
			fundingMode = GeneratorFundingMode.valueOf(tag.getString("funding_mode"));
		} catch (IllegalArgumentException exception) {
			fundingMode = GeneratorFundingMode.ITEM_ONLY;
		}
		currentBurnCurrency = null;
		if (tag.contains("current_burn_currency")) {
			try {
				currentBurnCurrency = CryptoCurrency.valueOf(tag.getString("current_burn_currency"));
			} catch (IllegalArgumentException ignored) {
			}
		}
		lastAutoSelectedCurrency = null;
		if (tag.contains("last_auto_selected_currency")) {
			try {
				lastAutoSelectedCurrency = CryptoCurrency.valueOf(tag.getString("last_auto_selected_currency"));
			} catch (IllegalArgumentException ignored) {
			}
		}
		ownerUuid = tag.hasUUID("owner_uuid") ? tag.getUUID("owner_uuid") : null;
		ownerName = tag.getString("owner_name");
	}

	private boolean tryStartBurn(Level level) {
		return switch (fundingMode) {
			case ITEM_ONLY -> consumeItemFuel();
			case WALLET_ONLY -> consumeWalletFuel(level);
			case HYBRID -> consumeItemFuel() || consumeWalletFuel(level);
		};
	}

	private boolean consumeItemFuel() {
		ItemStack stack = inventory.getStackInSlot(FUEL_SLOT);
		CryptoCurrency currency = CryptoCurrency.byItem(stack);
		if (currency == null) {
			return false;
		}
		inventory.extractItem(FUEL_SLOT, 1, false);
		lastAutoSelectedCurrency = currency;
		startBurn(currency);
		return true;
	}

	private boolean consumeWalletFuel(Level level) {
		if (ownerUuid == null || level.getServer() == null) {
			return false;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
		if (owner == null) {
			return false;
		}
		CryptoCurrency currency = resolveWalletFuelCurrency(owner, ExchangeRateService.getCurrentSnapshot());
		if (currency == null || !WalletService.withdrawWholeItem(owner, currency, 1)) {
			return false;
		}
		lastAutoSelectedCurrency = currency;
		startBurn(currency);
		return true;
	}

	@Nullable
	private CryptoCurrency resolveWalletFuelCurrency(ServerPlayer owner, ExchangeRateSnapshot snapshot) {
		CryptoCurrency bestCurrency = null;
		java.math.BigDecimal bestQuote = null;
		for (CryptoCurrency currency : WALLET_PRIORITY) {
			if (!WalletService.canAfford(owner, currency, currency.wholeUnitMinor())) {
				continue;
			}
			java.math.BigDecimal quote = snapshot.quoteToUsdt(currency);
			if (bestCurrency == null || quote.compareTo(bestQuote) < 0) {
				bestCurrency = currency;
				bestQuote = quote;
			}
		}
		return bestCurrency;
	}

	private void startBurn(CryptoCurrency currency) {
		currentBurnCurrency = currency;
		burnTimeTotal = currency.burnTimeTicks();
		burnTimeRemaining = burnTimeTotal;
	}

	private boolean pushEnergyToNeighbors(Level level, BlockPos pos) {
		boolean moved = false;
		for (Direction direction : Direction.values()) {
			if (energyStorage.getEnergyStored() <= 0) {
				break;
			}
			IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(direction), direction.getOpposite());
			if (target == null || !target.canReceive()) {
				continue;
			}
			int offered = Math.min(MAX_EXTRACT, energyStorage.getEnergyStored());
			int accepted = target.receiveEnergy(offered, false);
			if (accepted > 0) {
				energyStorage.extractEnergy(accepted, false);
				moved = true;
			}
		}
		return moved;
	}

	private final class GeneratorEnergyStorage extends EnergyStorage {
		private GeneratorEnergyStorage() {
			super(MAX_ENERGY, 0, MAX_EXTRACT, 0);
		}

		private int addEnergy(int amount) {
			if (amount <= 0) {
				return 0;
			}
			int accepted = Math.min(capacity - energy, amount);
			if (accepted > 0) {
				energy += accepted;
			}
			return accepted;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			int extracted = super.extractEnergy(toExtract, simulate);
			if (!simulate && extracted > 0) {
				setChanged();
			}
			return extracted;
		}
	}
}
