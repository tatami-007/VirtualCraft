package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.wallet.WalletLedgerType;
import dev.tatami.virtualcraft.wallet.WalletService;
import dev.tatami.virtualcraft.wireless.LinkCardData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class LinkCardItem extends Item {
	private static final int MAX_CHANNEL = 9;

	public LinkCardItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (!LinkCardData.hasOwner(stack)) {
			LinkCardData.bind(stack, player.getUUID(), player.getGameProfile().getName());
			WalletService.record(player, WalletLedgerType.POWER_LINK, CryptoCurrency.USDT, 0L, null, 0L, "bind CH" + LinkCardData.channel(stack));
			player.displayClientMessage(Component.translatable("message.virtual_craft.link_card.bound", LinkCardData.channel(stack)), true);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		if (LinkCardData.ownerUuid(stack).filter(uuid -> uuid.equals(player.getUUID())).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.link_card.owner_locked"), true);
			return InteractionResultHolder.fail(stack);
		}

		int step = player.isShiftKeyDown() ? -1 : 1;
		int nextChannel = Math.floorMod(LinkCardData.channel(stack) + step, MAX_CHANNEL + 1);
		LinkCardData.setChannel(stack, nextChannel);
		WalletService.record(player, WalletLedgerType.POWER_LINK, CryptoCurrency.USDT, 0L, null, 0L, "channel CH" + nextChannel);
		player.displayClientMessage(Component.translatable("message.virtual_craft.link_card.channel", nextChannel), true);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.link_card.channel", LinkCardData.channel(stack)));
		tooltipComponents.add(Component.translatable("tooltip.virtual_craft.link_card.channel_hint"));
		if (LinkCardData.hasOwner(stack)) {
			tooltipComponents.add(Component.translatable("tooltip.virtual_craft.link_card.owner", LinkCardData.ownerName(stack)));
		}
	}
}
