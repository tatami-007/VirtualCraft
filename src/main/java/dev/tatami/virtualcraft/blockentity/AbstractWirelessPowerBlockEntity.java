package dev.tatami.virtualcraft.blockentity;

import java.util.UUID;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.menu.WirelessPowerMenu;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.wallet.WalletLedgerType;
import dev.tatami.virtualcraft.wallet.WalletService;
import dev.tatami.virtualcraft.wireless.LinkCardData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class AbstractWirelessPowerBlockEntity extends BlockEntity implements MenuProvider {
	public static final int LINK_SLOT = 0;

	private final ItemStackHandler inventory = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			handleLinkCardUpdate();
			setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.is(ModItems.LINK_CARD.get());
		}
	};
	private final ContainerData data = new SimpleContainerData(5) {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> getEnergyStorage(null).getEnergyStored();
				case 1 -> getEnergyStorage(null).getMaxEnergyStored();
				case 2 -> hasBoundLinkCard() ? 1 : 0;
				case 3 -> getLinkedNodeCount();
				case 4 -> linkChannel();
				default -> 0;
			};
		}
	};
	private CardSnapshot lastCardSnapshot = CardSnapshot.empty();
	private boolean suppressCardEvents;

	protected AbstractWirelessPowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
		super(type, pos, blockState);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		lastCardSnapshot = CardSnapshot.capture(getLinkCard());
		if (level != null && !level.isClientSide) {
			registerNode();
		}
	}

	@Override
	public void setRemoved() {
		if (level != null && !level.isClientSide) {
			unregisterNode();
		}
		super.setRemoved();
	}

	public ItemStackHandler getInventory() {
		return inventory;
	}

	public ContainerData getData() {
		return data;
	}

	public ItemStack getLinkCard() {
		return inventory.getStackInSlot(LINK_SLOT);
	}

	public boolean hasBoundLinkCard() {
		ItemStack stack = getLinkCard();
		return !stack.isEmpty() && LinkCardData.hasOwner(stack);
	}

	public UUID linkOwnerUuid() {
		return LinkCardData.ownerUuid(getLinkCard()).orElse(null);
	}

	public String linkOwnerName() {
		return LinkCardData.ownerName(getLinkCard());
	}

	public int linkChannel() {
		return LinkCardData.channel(getLinkCard());
	}

	public void dropContents(Level level, BlockPos pos) {
		SimpleContainer container = new SimpleContainer(inventory.getSlots());
		for (int slot = 0; slot < inventory.getSlots(); slot++) {
			container.setItem(slot, inventory.getStackInSlot(slot));
		}
		Containers.dropContents(level, pos, container);
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new WirelessPowerMenu(containerId, playerInventory, this, data);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		suppressCardEvents = true;
		inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		suppressCardEvents = false;
		lastCardSnapshot = CardSnapshot.capture(getLinkCard());
	}

	public abstract IEnergyStorage getEnergyStorage(net.minecraft.core.Direction side);

	protected abstract int getLinkedNodeCount();

	protected abstract String ledgerRole();

	protected abstract void registerNode();

	protected abstract void unregisterNode();

	private void handleLinkCardUpdate() {
		if (suppressCardEvents) {
			return;
		}
		CardSnapshot current = CardSnapshot.capture(getLinkCard());
		if (level != null && !level.isClientSide && !lastCardSnapshot.equals(current)) {
			recordLinkEvent(lastCardSnapshot, current);
		}
		lastCardSnapshot = current;
	}

	private void recordLinkEvent(CardSnapshot previous, CardSnapshot current) {
		UUID ownerUuid = current.ownerUuid != null ? current.ownerUuid : previous.ownerUuid;
		if (ownerUuid == null || level == null || level.getServer() == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
		if (owner == null) {
			return;
		}

		String detail;
		if (previous.present && !current.present) {
			detail = ledgerRole() + " remove CH" + previous.channel;
		} else if (!previous.present && current.present) {
			detail = ledgerRole() + " insert CH" + current.channel;
		} else if (previous.channel != current.channel) {
			detail = ledgerRole() + " channel CH" + current.channel;
		} else {
			detail = ledgerRole() + " refresh CH" + current.channel;
		}
		WalletService.record(owner, WalletLedgerType.POWER_LINK, CryptoCurrency.USDT, 0L, null, 0L, detail);
	}

	private record CardSnapshot(boolean present, UUID ownerUuid, int channel) {
		private static CardSnapshot capture(ItemStack stack) {
			return new CardSnapshot(!stack.isEmpty(), LinkCardData.ownerUuid(stack).orElse(null), LinkCardData.channel(stack));
		}

		private static CardSnapshot empty() {
			return new CardSnapshot(false, null, 0);
		}
	}
}
