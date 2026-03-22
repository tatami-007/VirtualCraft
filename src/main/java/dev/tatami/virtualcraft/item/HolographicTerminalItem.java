package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HolographicTerminalItem extends Item {
	public HolographicTerminalItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			MenuProvider provider = new SimpleMenuProvider(
					(containerId, playerInventory, menuPlayer) -> new HolographicShopMenu(containerId, playerInventory, true),
					Component.translatable("item.virtual_craft.holographic_shop"));
			serverPlayer.openMenu(provider, buffer -> buffer.writeBoolean(true));
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
