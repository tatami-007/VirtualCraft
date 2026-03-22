package dev.tatami.virtualcraft.network;

import java.math.RoundingMode;
import java.util.List;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.CurrencyConversionService;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import dev.tatami.virtualcraft.shop.ShopCatalogLoader;
import dev.tatami.virtualcraft.shop.ShopOffer;
import dev.tatami.virtualcraft.wallet.WalletEconomyRules;
import dev.tatami.virtualcraft.wallet.WalletLedgerType;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShopActionPayload(int menuId, Action action, int offerIndex, int quantity, String settlementCurrencyId) implements CustomPacketPayload {
	public enum Action {
		REQUEST_SYNC,
		BUY,
		SELL
	}

	public static final Type<ShopActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "shop_action"));
	public static final StreamCodec<FriendlyByteBuf, ShopActionPayload> STREAM_CODEC = StreamCodec.of(ShopActionPayload::write, ShopActionPayload::read);

	@Override
	public Type<ShopActionPayload> type() {
		return TYPE;
	}

	private static ShopActionPayload read(FriendlyByteBuf buffer) {
		return new ShopActionPayload(buffer.readVarInt(), buffer.readEnum(Action.class), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(16));
	}

	private static void write(FriendlyByteBuf buffer, ShopActionPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeEnum(payload.action);
		buffer.writeVarInt(payload.offerIndex);
		buffer.writeVarInt(payload.quantity);
		buffer.writeUtf(payload.settlementCurrencyId, 16);
	}

	public static void handle(ShopActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof HolographicShopMenu menu) || menu.containerId != payload.menuId()) {
				return;
			}
			List<ShopOffer> offers = ShopCatalogLoader.load(player.server);
			ExchangeRateSnapshot snapshot = ExchangeRateService.getCurrentSnapshot();
			if (payload.action == Action.REQUEST_SYNC) {
				context.reply(new SyncWalletPayload(
						menu.containerId,
						WalletService.getWallet(player).snapshot(),
						WalletService.walletAddress(player),
						WalletService.recentEntries(player, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
				context.reply(new SyncShopCatalogPayload(menu.containerId, offers.stream().map(SyncShopCatalogPayload.Entry::fromOffer).toList()));
				context.reply(SyncExchangePayload.fromSnapshot(menu.containerId, snapshot));
				return;
			}
			if (payload.offerIndex < 0 || payload.offerIndex >= offers.size()) {
				return;
			}

			CryptoCurrency settlementCurrency = CryptoCurrency.byId(payload.settlementCurrencyId()).orElse(CryptoCurrency.USDT);
			ShopOffer offer = offers.get(payload.offerIndex);
			int quantity = Math.max(1, payload.quantity);
			switch (payload.action) {
				case BUY -> buy(player, offer, quantity, settlementCurrency, snapshot);
				case SELL -> sell(player, offer, quantity, settlementCurrency, snapshot);
				default -> {
				}
			}
			context.reply(new SyncWalletPayload(
					menu.containerId,
					WalletService.getWallet(player).snapshot(),
					WalletService.walletAddress(player),
					WalletService.recentEntries(player, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
			context.reply(SyncExchangePayload.fromSnapshot(menu.containerId, snapshot));
		});
	}

	private static void buy(ServerPlayer player, ShopOffer offer, int quantity, CryptoCurrency settlementCurrency, ExchangeRateSnapshot snapshot) {
		if (!offer.enabled() || offer.priceUsdtMinor() <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.shop.unavailable"), true);
			return;
		}

		long totalUsdtMinor = offer.priceUsdtMinor() * quantity;
		long settlementCostMinor = CurrencyConversionService.convertUsdtMinorTo(settlementCurrency, totalUsdtMinor, snapshot, RoundingMode.UP);
		if (!WalletService.canAfford(player, CryptoCurrency.ETH, WalletEconomyRules.PREMIUM_BUY_GAS_MINOR)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", CryptoCurrency.ETH.formatCompact(WalletEconomyRules.PREMIUM_BUY_GAS_MINOR), CryptoCurrency.ETH.ticker()), true);
			return;
		}
		if (!WalletService.withdrawMinorUnits(player, settlementCurrency, settlementCostMinor)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		if (!WalletService.withdrawMinorUnits(player, CryptoCurrency.ETH, WalletEconomyRules.PREMIUM_BUY_GAS_MINOR)) {
			WalletService.depositMinorUnits(player, settlementCurrency, settlementCostMinor);
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", CryptoCurrency.ETH.formatCompact(WalletEconomyRules.PREMIUM_BUY_GAS_MINOR), CryptoCurrency.ETH.ticker()), true);
			return;
		}

		giveOfferItems(player, offer, quantity);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		WalletService.record(player, WalletLedgerType.PREMIUM_BUY, settlementCurrency, settlementCostMinor, null, 0L, offer.id());
		player.displayClientMessage(Component.translatable("message.virtual_craft.shop.buy", offer.displayName(), settlementCurrency.formatCompact(settlementCostMinor), settlementCurrency.displayName()), true);
	}

	private static void sell(ServerPlayer player, ShopOffer offer, int quantity, CryptoCurrency settlementCurrency, ExchangeRateSnapshot snapshot) {
		if (!offer.enabled() || offer.sellUsdtMinor() <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.shop.unavailable"), true);
			return;
		}

		int requiredCount = offer.count() * quantity;
		if (countItem(player.getInventory(), offer.createStack(1)) < requiredCount) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.shop.missing_items"), true);
			return;
		}
		removeItems(player.getInventory(), offer.createStack(1), requiredCount);
		long payoutMinor = CurrencyConversionService.convertUsdtMinorTo(settlementCurrency, offer.sellUsdtMinor() * quantity, snapshot, RoundingMode.DOWN);
		WalletService.depositMinorUnits(player, settlementCurrency, payoutMinor);
		WalletService.record(player, WalletLedgerType.PREMIUM_SELL, settlementCurrency, payoutMinor, null, 0L, offer.id());
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.virtual_craft.shop.sell", requiredCount, offer.displayName(), settlementCurrency.formatCompact(payoutMinor), settlementCurrency.displayName()), true);
	}

	private static void giveOfferItems(ServerPlayer player, ShopOffer offer, int quantity) {
		int totalItems = offer.count() * quantity;
		ItemStack template = offer.createStack(1);
		if (template.isEmpty()) {
			return;
		}
		int maxStackSize = Math.max(1, template.getMaxStackSize());
		int remaining = totalItems;
		while (remaining > 0) {
			int stackSize = Math.min(maxStackSize, remaining);
			ItemStack stack = template.copy();
			stack.setCount(stackSize);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= stackSize;
		}
	}

	private static int countItem(Inventory inventory, ItemStack target) {
		int total = 0;
		for (ItemStack stack : inventory.items) {
			if (ItemStack.isSameItemSameComponents(stack, target)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void removeItems(Inventory inventory, ItemStack target, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.items.size(); slot++) {
			ItemStack stack = inventory.items.get(slot);
			if (!ItemStack.isSameItemSameComponents(stack, target)) {
				continue;
			}
			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			remaining -= removed;
			if (stack.isEmpty()) {
				inventory.items.set(slot, ItemStack.EMPTY);
			}
			if (remaining <= 0) {
				return;
			}
		}
	}
}
