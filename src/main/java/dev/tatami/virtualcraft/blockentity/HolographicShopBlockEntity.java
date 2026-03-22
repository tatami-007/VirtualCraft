package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HolographicShopBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
	public HolographicShopBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.HOLOGRAPHIC_SHOP.get(), pos, blockState);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.virtual_craft.holographic_shop");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new HolographicShopMenu(containerId, playerInventory, worldPosition);
	}
}
