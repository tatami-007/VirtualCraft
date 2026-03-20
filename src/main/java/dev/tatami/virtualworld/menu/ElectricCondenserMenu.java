package dev.tatami.virtualworld.menu;

import dev.tatami.virtualworld.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualworld.core.definitions.VWBlocks;
import dev.tatami.virtualworld.core.definitions.VWItems;
import dev.tatami.virtualworld.core.definitions.VWMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ElectricCondenserMenu extends AbstractContainerMenu {
	private static final int INGOT_SLOT = 0;
	private static final int BALL_SLOT = 1;
	private static final int OUTPUT_SLOT = 2;
	private static final int MACHINE_SLOTS = 3;
	private static final int PLAYER_INV_START = MACHINE_SLOTS;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private static final int OPEN_SIDE_CONFIG_BUTTON = 120;

	private final ElectricCondenserBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(2));
	}

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, ElectricCondenserBlockEntity blockEntity, ContainerData data) {
		super(VWMenuTypes.ELECTRIC_CONDENSER.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;

		ItemStackHandler inventory = blockEntity.getInventory();
		addSlot(new SlotItemHandler(inventory, INGOT_SLOT, 53, 22));
		addSlot(new SlotItemHandler(inventory, BALL_SLOT, 53, 40));
		addSlot(new SlotItemHandler(inventory, OUTPUT_SLOT, 109, 41) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});

		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	public int getScaledProgress() {
		int progress = data.get(0);
		int maxProgress = data.get(1);
		return progress != 0 && maxProgress != 0 ? progress * 22 / maxProgress : 0;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == OPEN_SIDE_CONFIG_BUTTON && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(
					new SimpleMenuProvider(
							(containerId, inventory, ignored) -> new ElectricCondenserSideConfigMenu(containerId, inventory, blockEntity, blockEntity.getSideConfigData()),
							net.minecraft.network.chat.Component.translatable("screen.virtual_world.electric_condenser_side_config")),
					buffer -> buffer.writeBlockPos(blockEntity.getBlockPos()));
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
		ItemStack itemStack = sourceStack.copy();

		if (index == OUTPUT_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			sourceSlot.onQuickCraft(sourceStack, itemStack);
		} else if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(VWItems.ELECTRIC_BALL.get())) {
			if (!moveItemStackTo(sourceStack, BALL_SLOT, BALL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (ElectricCondenserBlockEntity.isValidIngot(sourceStack)) {
			if (!moveItemStackTo(sourceStack, INGOT_SLOT, INGOT_SLOT + 1, false)) {
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
		return itemStack;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, VWBlocks.ELECTRIC_CONDENSER.get());
	}

	private static ElectricCondenserBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof ElectricCondenserBlockEntity condenser) {
			return condenser;
		}
		throw new IllegalStateException("Expected voltaic forge block entity at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, 8 + column * 18, 154));
		}
	}
}

