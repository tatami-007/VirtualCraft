package dev.tatami.virtualcraft.network;

import java.math.RoundingMode;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.CurrencyConversionService;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import dev.tatami.virtualcraft.registry.ModItems;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WalletActionPayload(int menuId, Action action, String primaryCurrencyId, String secondaryCurrencyId, long amountMinor, String targetPlayer) implements CustomPacketPayload {
	public enum Action {
		REQUEST_SYNC,
		DEPOSIT_ALL,
		DEPOSIT_SELECTED,
		WITHDRAW_ITEM,
		WITHDRAW_EMERALDS,
		CONVERT,
		TRANSFER,
		REFRESH_RATES,
		SETTLE_MINING
	}

	public static final Type<WalletActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "wallet_action"));
	public static final StreamCodec<FriendlyByteBuf, WalletActionPayload> STREAM_CODEC = StreamCodec.of(WalletActionPayload::write, WalletActionPayload::read);

	@Override
	public Type<WalletActionPayload> type() {
		return TYPE;
	}

	private static WalletActionPayload read(FriendlyByteBuf buffer) {
		return new WalletActionPayload(
				buffer.readVarInt(),
				buffer.readEnum(Action.class),
				buffer.readUtf(16),
				buffer.readUtf(16),
				buffer.readVarLong(),
				buffer.readUtf(64));
	}

	private static void write(FriendlyByteBuf buffer, WalletActionPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeEnum(payload.action);
		buffer.writeUtf(payload.primaryCurrencyId, 16);
		buffer.writeUtf(payload.secondaryCurrencyId, 16);
		buffer.writeVarLong(payload.amountMinor);
		buffer.writeUtf(payload.targetPlayer, 64);
	}

	public static void handle(WalletActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof HolographicShopMenu menu) || menu.containerId != payload.menuId()) {
				return;
			}

			ExchangeRateSnapshot snapshot = ExchangeRateService.getCurrentSnapshot();
			ExchangeRateSnapshot currentSnapshot = snapshot;
			switch (payload.action) {
				case REQUEST_SYNC -> syncTerminalState(context, menu.containerId, player, currentSnapshot);
				case DEPOSIT_ALL -> {
					depositAll(player);
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case DEPOSIT_SELECTED -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> depositSelected(player, currency));
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case WITHDRAW_ITEM -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> withdrawItems(player, currency, payload.amountMinor));
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case WITHDRAW_EMERALDS -> {
					withdrawEmeralds(player, payload.amountMinor);
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case CONVERT -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(source ->
							CryptoCurrency.byId(payload.secondaryCurrencyId).ifPresent(target ->
									convert(player, source, target, payload.amountMinor, currentSnapshot)));
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case TRANSFER -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency ->
							transfer(player, menu.containerId, currency, payload.amountMinor, payload.targetPlayer, currentSnapshot));
					syncTerminalState(context, menu.containerId, player, currentSnapshot);
				}
				case REFRESH_RATES -> {
					if (!ExchangeRateService.canManualRefresh()) {
						player.displayClientMessage(Component.translatable("message.virtual_craft.exchange.cooldown"), true);
						syncTerminalState(context, menu.containerId, player, currentSnapshot);
						return;
					}
					ExchangeRateSnapshot refreshedSnapshot = ExchangeRateService.refreshNow(player.server);
					player.displayClientMessage(Component.translatable("message.virtual_craft.exchange.refreshed"), true);
					syncTerminalState(context, menu.containerId, player, refreshedSnapshot);
				}
				case SETTLE_MINING -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> settleMining(player, currency, payload.amountMinor, currentSnapshot));
					syncTerminalState(context, menu.containerId, player, ExchangeRateService.getCurrentSnapshot());
				}
			}
		});
	}

	private static void syncTerminalState(IPayloadContext context, int menuId, ServerPlayer player, ExchangeRateSnapshot snapshot) {
		context.reply(new SyncWalletPayload(
				menuId,
				WalletService.getWallet(player).snapshot(),
				WalletService.walletAddress(player),
				WalletService.recentEntries(player, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
		context.reply(SyncExchangePayload.fromSnapshot(menuId, snapshot));
	}

	private static void depositAll(ServerPlayer player) {
		long totalDeposited = 0L;
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			long deposited = removeAndDepositCurrency(player, currency);
			if (deposited > 0) {
				WalletService.record(player, WalletLedgerType.DEPOSIT, currency, deposited, null, 0L, "inventory");
			}
			totalDeposited += deposited;
		}
		long emeraldDeposit = removeEmeraldsAndDeposit(player);
		if (emeraldDeposit > 0) {
			WalletService.record(player, WalletLedgerType.DEPOSIT, CryptoCurrency.USDT, emeraldDeposit, null, 0L, "emerald");
		}
		totalDeposited += emeraldDeposit;
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		if (totalDeposited > 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.deposit_all"), true);
		}
	}

	private static void depositSelected(ServerPlayer player, CryptoCurrency currency) {
		long deposited = removeAndDepositCurrency(player, currency);
		if (deposited > 0) {
			WalletService.record(player, WalletLedgerType.DEPOSIT, currency, deposited, null, 0L, "inventory");
		}
		if (currency == CryptoCurrency.USDT) {
			long emeraldDeposit = removeEmeraldsAndDeposit(player);
			if (emeraldDeposit > 0) {
				WalletService.record(player, WalletLedgerType.DEPOSIT, CryptoCurrency.USDT, emeraldDeposit, null, 0L, "emerald");
			}
			deposited += emeraldDeposit;
		}
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		if (deposited <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.nothing_to_deposit"), true);
			return;
		}
		player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.deposit_selected", currency.displayName()), true);
	}

	private static long removeAndDepositCurrency(ServerPlayer player, CryptoCurrency currency) {
		int removedItems = removeCurrencyItems(player.getInventory(), currency, Integer.MAX_VALUE);
		if (removedItems <= 0) {
			return 0L;
		}
		WalletService.depositWholeItem(player, currency, removedItems);
		return Math.multiplyExact(currency.wholeUnitMinor(), removedItems);
	}

	private static long removeEmeraldsAndDeposit(ServerPlayer player) {
		int removedEmeralds = removeItem(player.getInventory(), Items.EMERALD, Integer.MAX_VALUE);
		if (removedEmeralds <= 0) {
			return 0L;
		}
		WalletService.depositMinorUnits(player, CryptoCurrency.USDT, Math.multiplyExact(CryptoCurrency.USDT.wholeUnitMinor(), removedEmeralds));
		return Math.multiplyExact(CryptoCurrency.USDT.wholeUnitMinor(), removedEmeralds);
	}

	private static void withdrawItems(ServerPlayer player, CryptoCurrency currency, long amountMinor) {
		long wholeItems = amountMinor / currency.wholeUnitMinor();
		if (wholeItems <= 0 || !WalletService.withdrawWholeItem(player, currency, wholeItems)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		WalletService.record(player, WalletLedgerType.WITHDRAW, currency, Math.multiplyExact(wholeItems, currency.wholeUnitMinor()), null, 0L, "item");
		giveCurrencyItems(player, currency, wholeItems);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.withdraw", wholeItems, currency.displayName()), true);
	}

	private static void withdrawEmeralds(ServerPlayer player, long amountMinor) {
		long emeraldCount = amountMinor / CryptoCurrency.USDT.wholeUnitMinor();
		long requiredUsdtMinor = Math.multiplyExact(emeraldCount, CryptoCurrency.USDT.wholeUnitMinor());
		if (emeraldCount <= 0 || !WalletService.withdrawMinorUnits(player, CryptoCurrency.USDT, requiredUsdtMinor)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		WalletService.record(player, WalletLedgerType.WITHDRAW, CryptoCurrency.USDT, requiredUsdtMinor, null, 0L, "emerald");
		giveItem(player, new ItemStack(Items.EMERALD), emeraldCount);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.withdraw_emeralds", emeraldCount), true);
	}

	private static void convert(ServerPlayer player, CryptoCurrency source, CryptoCurrency target, long sourceMinor, ExchangeRateSnapshot snapshot) {
		if (source == target) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.convert.same_currency"), true);
			return;
		}
		if (sourceMinor <= 0 || !WalletService.withdrawMinorUnits(player, source, sourceMinor)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		long convertedMinor = CurrencyConversionService.convert(source, target, sourceMinor, snapshot, RoundingMode.HALF_UP);
		if (convertedMinor <= 0) {
			WalletService.depositMinorUnits(player, source, sourceMinor);
			player.displayClientMessage(Component.translatable("message.virtual_craft.convert.too_small"), true);
			return;
		}
		WalletService.depositMinorUnits(player, target, convertedMinor);
		WalletService.record(player, WalletLedgerType.SWAP, source, sourceMinor, target, convertedMinor, "");
		player.displayClientMessage(Component.translatable("message.virtual_craft.convert.success", source.formatCompact(sourceMinor), source.displayName(), target.formatCompact(convertedMinor), target.displayName()), true);
	}

	private static void transfer(ServerPlayer player, int menuId, CryptoCurrency currency, long amountMinor, String targetName, ExchangeRateSnapshot snapshot) {
		if (amountMinor <= 0 || targetName.isBlank()) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.invalid"), true);
			return;
		}
		ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
		if (target == null || target == player) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.invalid_target"), true);
			return;
		}
		if (!WalletService.canAfford(player, CryptoCurrency.SOL, WalletEconomyRules.TRANSFER_GAS_MINOR)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", CryptoCurrency.SOL.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR), CryptoCurrency.SOL.ticker()), true);
			return;
		}
		if (!WalletService.withdrawMinorUnits(player, CryptoCurrency.SOL, WalletEconomyRules.TRANSFER_GAS_MINOR)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", CryptoCurrency.SOL.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR), CryptoCurrency.SOL.ticker()), true);
			return;
		}
		if (!WalletService.transfer(player, target, currency, amountMinor)) {
			WalletService.depositMinorUnits(player, CryptoCurrency.SOL, WalletEconomyRules.TRANSFER_GAS_MINOR);
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		WalletService.record(player, WalletLedgerType.TRANSFER, currency, amountMinor, null, 0L, target.getName().getString());
		WalletService.record(target, WalletLedgerType.TRANSFER, currency, amountMinor, null, 0L, player.getName().getString());
		player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.sent", currency.formatCompact(amountMinor), currency.displayName(), target.getName()), true);
		target.displayClientMessage(Component.translatable("message.virtual_craft.transfer.received", currency.formatCompact(amountMinor), currency.displayName(), player.getName()), true);
		if (target.containerMenu instanceof HolographicShopMenu targetMenu) {
			PacketDistributor.sendToPlayer(target, new SyncWalletPayload(
					targetMenu.containerId,
					WalletService.getWallet(target).snapshot(),
					WalletService.walletAddress(target),
					WalletService.recentEntries(target, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
			PacketDistributor.sendToPlayer(target, SyncExchangePayload.fromSnapshot(targetMenu.containerId, snapshot));
		}
	}

	private static void settleMining(ServerPlayer player, CryptoCurrency payoutCurrency, long voucherCount, ExchangeRateSnapshot snapshot) {
		if (voucherCount <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining.none"), true);
			return;
		}
		int removed = removeItem(player.getInventory(), ModItems.HASH_VOUCHER.get(), (int) Math.min(Integer.MAX_VALUE, voucherCount));
		if (removed <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining.none"), true);
			return;
		}
		long totalBtcMinor = Math.multiplyExact(HashVoucherItem.BTC_PAYOUT_MINOR, removed);
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, payoutCurrency, totalBtcMinor, snapshot, RoundingMode.DOWN);
		if (payoutMinor <= 0) {
			giveItem(player, new ItemStack(ModItems.HASH_VOUCHER.get()), removed);
			player.displayClientMessage(Component.translatable("message.virtual_craft.convert.too_small"), true);
			return;
		}
		WalletService.depositMinorUnits(player, payoutCurrency, payoutMinor);
		WalletService.record(player, WalletLedgerType.MINING_SETTLEMENT, payoutCurrency, payoutMinor, CryptoCurrency.BTC, totalBtcMinor, "shares:" + removed);
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.virtual_craft.mining.settled",
				removed,
				payoutCurrency.formatCompact(payoutMinor),
				payoutCurrency.ticker()), true);
	}

	private static int removeCurrencyItems(Inventory inventory, CryptoCurrency currency, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.items.size(); slot++) {
			ItemStack stack = inventory.items.get(slot);
			if (!currency.matches(stack)) {
				continue;
			}
			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			remaining -= removed;
			if (stack.isEmpty()) {
				inventory.items.set(slot, ItemStack.EMPTY);
			}
			if (remaining <= 0) {
				break;
			}
		}
		return amount - remaining;
	}

	private static int removeItem(Inventory inventory, net.minecraft.world.item.Item item, int amount) {
		int remaining = amount;
		for (int slot = 0; slot < inventory.items.size(); slot++) {
			ItemStack stack = inventory.items.get(slot);
			if (!stack.is(item)) {
				continue;
			}
			int removed = Math.min(remaining, stack.getCount());
			stack.shrink(removed);
			remaining -= removed;
			if (stack.isEmpty()) {
				inventory.items.set(slot, ItemStack.EMPTY);
			}
			if (remaining <= 0) {
				break;
			}
		}
		return amount - remaining;
	}

	private static void giveCurrencyItems(ServerPlayer player, CryptoCurrency currency, long amount) {
		giveItem(player, currency.stack(1), amount);
	}

	private static void giveItem(ServerPlayer player, ItemStack template, long amount) {
		long remaining = amount;
		while (remaining > 0) {
			int stackSize = (int) Math.min(template.getMaxStackSize(), remaining);
			ItemStack stack = template.copy();
			stack.setCount(stackSize);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= stackSize;
		}
	}
}
