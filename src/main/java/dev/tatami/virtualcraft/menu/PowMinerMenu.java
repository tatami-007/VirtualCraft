package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.PowMinerBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
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

@SuppressWarnings("this-escape") // addSlot() in the constructor is the required AbstractContainerMenu pattern.
public class PowMinerMenu extends AbstractContainerMenu {
	private static final int OUTPUT_SLOT = 0;
	private static final int PLAYER_INV_START = 1;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private static final int OPEN_SIDE_CONFIG_BUTTON = 120;
	private final PowMinerBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public PowMinerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(4));
	}

	public PowMinerMenu(int containerId, Inventory playerInventory, PowMinerBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.POW_MINER.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;

		// v20.13 OUTPUT 槽 Y 25→32 — widget 整体 +7 让 ENERGY 在 widget 区域 Y 15..82 居中
		// 现 OUTPUT 占 Y 32..50，ENERGY 占 Y 22..74，ARROW (62, 56)..64：ENERGY 上下边缘各 10px 对称
		addSlot(new SlotItemHandler(blockEntity.getInventory(), OUTPUT_SLOT, 79, 32) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	/** 进度返回 [0..36]：贴图横向算力进度条宽 36 像素。 */
	public int getScaledProgress() {
		int progress = data.get(0);
		int maxProgress = data.get(1);
		return progress != 0 && maxProgress != 0 ? progress * 36 / maxProgress : 0;
	}

	public int getEnergyStored() {
		return data.get(2);
	}

	public int getMaxEnergyStored() {
		return data.get(3);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == OPEN_SIDE_CONFIG_BUTTON && player instanceof ServerPlayer serverPlayer) {
			SideConfigMenu.openFor(serverPlayer, blockEntity);
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot sourceSlot = slots.get(index);
		if (!sourceSlot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack sourceStack = sourceSlot.getItem();
		ItemStack copy = sourceStack.copy();

		if (index == OUTPUT_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			sourceSlot.onQuickCraft(sourceStack, copy);
		} else if (sourceStack.is(ModItems.HASH_VOUCHER.get())) {
			if (!moveItemStackTo(sourceStack, OUTPUT_SLOT, OUTPUT_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < PLAYER_INV_END) {
			if (!moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(sourceStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
			return ItemStack.EMPTY;
		}

		if (sourceStack.isEmpty()) {
			sourceSlot.set(ItemStack.EMPTY);
		} else {
			sourceSlot.setChanged();
		}
		sourceSlot.onTake(player, sourceStack);
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.POW_MINER.get());
	}

	private static PowMinerBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof PowMinerBlockEntity miner) {
			return miner;
		}
		throw new IllegalStateException("Expected PoW miner block entity at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		// v16 imageHeight=178 背包下移 12px：(8, 96)
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		// v16 imageHeight=178 Hotbar 下移 12px：(8, 154)
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 154));
		}
	}
}
