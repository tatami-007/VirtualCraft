package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
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
	private static final int MACHINE_SLOTS = 6;
	private static final int PLAYER_INV_START = MACHINE_SLOTS;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private static final int OPEN_SIDE_CONFIG_BUTTON = 120;
	private static final int INGOT_SLOT_X = 38;
	private static final int INGOT_SLOT_Y = 44;
	private static final int BALL_SLOT_X = 38;
	private static final int BALL_SLOT_Y = 68;
	private static final int OUTPUT_SLOT_X = 116;
	private static final int OUTPUT_SLOT_Y = 56;
	private static final int UPGRADE_ONE_X = 166;
	private static final int UPGRADE_TWO_X = 166;
	private static final int UPGRADE_THREE_X = 166;
	private static final int UPGRADE_ONE_Y = 40;
	private static final int UPGRADE_TWO_Y = 62;
	private static final int UPGRADE_THREE_Y = 84;
	private static final int PLAYER_INVENTORY_X = 20;
	private static final int PLAYER_INVENTORY_Y = 136;
	private static final int HOTBAR_X = 20;
	private static final int HOTBAR_Y = 194;

	private final ElectricCondenserBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(5));
	}

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, ElectricCondenserBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.ELECTRIC_CONDENSER.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;

		ItemStackHandler inventory = blockEntity.getInventory();
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.INGOT_SLOT, INGOT_SLOT_X, INGOT_SLOT_Y));
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.BALL_SLOT, BALL_SLOT_X, BALL_SLOT_Y));
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addUpgradeSlot(inventory, 3, UPGRADE_ONE_X, UPGRADE_ONE_Y);
		addUpgradeSlot(inventory, 4, UPGRADE_TWO_X, UPGRADE_TWO_Y);
		addUpgradeSlot(inventory, 5, UPGRADE_THREE_X, UPGRADE_THREE_Y);

		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	public int getScaledProgress() {
		int progress = data.get(0);
		int maxProgress = data.get(1);
		return progress != 0 && maxProgress != 0 ? progress * 28 / maxProgress : 0;
	}

	public int getEnergyStored() {
		return data.get(2);
	}

	public int getMaxEnergyStored() {
		return data.get(3);
	}

	public int getInstalledSpeedCards() {
		return data.get(4);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == OPEN_SIDE_CONFIG_BUTTON && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(
					new SimpleMenuProvider(
							(containerId, inventory, ignored) -> new ElectricCondenserSideConfigMenu(containerId, inventory, blockEntity, blockEntity.getSideConfigData()),
							net.minecraft.network.chat.Component.translatable("screen.virtual_craft.electric_condenser_side_config")),
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

		if (index == ElectricCondenserBlockEntity.OUTPUT_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			sourceSlot.onQuickCraft(sourceStack, itemStack);
		} else if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(ModItems.SPEED_UPGRADE_CARD.get())) {
			if (!moveItemStackTo(sourceStack, 3, 6, false)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(ModItems.ELECTRIC_BALL.get())) {
			if (!moveItemStackTo(sourceStack, ElectricCondenserBlockEntity.BALL_SLOT, ElectricCondenserBlockEntity.BALL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (ElectricCondenserBlockEntity.isValidIngot(sourceStack)) {
			if (!moveItemStackTo(sourceStack, ElectricCondenserBlockEntity.INGOT_SLOT, ElectricCondenserBlockEntity.INGOT_SLOT + 1, false)) {
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
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.ELECTRIC_CONDENSER.get());
	}

	private static ElectricCondenserBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof ElectricCondenserBlockEntity condenser) {
			return condenser;
		}
		throw new IllegalStateException("Expected electric condenser block entity at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, HOTBAR_X + column * 18, HOTBAR_Y));
		}
	}

	private void addUpgradeSlot(ItemStackHandler inventory, int slot, int x, int y) {
		addSlot(new SlotItemHandler(inventory, slot, x, y) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
	}
}
