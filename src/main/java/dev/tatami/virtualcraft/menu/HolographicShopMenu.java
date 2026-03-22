package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class HolographicShopMenu extends AbstractContainerMenu {
	private static final int HIDDEN_SLOT_X = -1000;
	private static final int HIDDEN_SLOT_Y = -1000;
	private final boolean itemMode;
	private final BlockPos blockPos;

	public HolographicShopMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, extraData.readBoolean(), extraData.readableBytes() >= 8 ? extraData.readBlockPos() : BlockPos.ZERO);
	}

	public HolographicShopMenu(int containerId, Inventory playerInventory, BlockPos blockPos) {
		this(containerId, playerInventory, false, blockPos);
	}

	public HolographicShopMenu(int containerId, Inventory playerInventory, boolean itemMode) {
		this(containerId, playerInventory, itemMode, BlockPos.ZERO);
	}

	private HolographicShopMenu(int containerId, Inventory playerInventory, boolean itemMode, BlockPos blockPos) {
		super(ModMenuTypes.HOLOGRAPHIC_SHOP.get(), containerId);
		this.itemMode = itemMode;
		this.blockPos = blockPos;
		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
	}

	public BlockPos getBlockPos() {
		return blockPos;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		if (itemMode) {
			return player.isAlive();
		}
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), blockPos), player, ModBlocks.HOLOGRAPHIC_SHOP.get());
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, HIDDEN_SLOT_X, HIDDEN_SLOT_Y));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, HIDDEN_SLOT_X, HIDDEN_SLOT_Y));
		}
	}
}
