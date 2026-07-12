package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.PowMinerBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import dev.tatami.virtualcraft.item.PowerCellItem;
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
	private static final int BASIC_OUTPUT_SLOT = PowMinerBlockEntity.BASIC_OUTPUT_SLOT;
	private static final int RARE_OUTPUT_SLOT = PowMinerBlockEntity.RARE_OUTPUT_SLOT;
	private static final int OUTPUT_SLOT_COUNT = 2;
	private static final int MACHINE_SLOT_COUNT = 3;
	private static final int PLAYER_INV_START = MACHINE_SLOT_COUNT;
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

		// 双输出：基础凭证保留原槽位；稀有凭证进入右侧槽，避免稀有产物掉地导致自动化链断开。
		addOutputSlot(BASIC_OUTPUT_SLOT, 69, 32);
		addOutputSlot(RARE_OUTPUT_SLOT, 91, 32);
		addSlot(new SlotItemHandler(blockEntity.getInventory(), PowMinerBlockEntity.POWER_CELL_SLOT, 8, 76));
		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	private void addOutputSlot(int slot, int x, int y) {
		addSlot(new SlotItemHandler(blockEntity.getInventory(), slot, x, y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
	}

	/** 进度返回 [0..52]：贴图横向算力进度条宽 52 像素。 */
	public int getScaledProgress() {
		int progress = data.get(0);
		int maxProgress = data.get(1);
		return progress != 0 && maxProgress != 0 ? progress * 52 / maxProgress : 0;
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

		if (index >= 0 && index < MACHINE_SLOT_COUNT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			if (index < OUTPUT_SLOT_COUNT) {
				sourceSlot.onQuickCraft(sourceStack, copy);
			}
		} else if (PowerCellItem.isPowerCell(sourceStack)) {
			if (!moveItemStackTo(sourceStack, PowMinerBlockEntity.POWER_CELL_SLOT, PowMinerBlockEntity.POWER_CELL_SLOT + 1, false)) {
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
