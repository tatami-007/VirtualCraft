package dev.tatami.virtualcraft.network;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractType;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.CurrencyConversionService;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.item.HashVoucherItem;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WalletActionPayload(int menuId, Action action, String primaryCurrencyId, String secondaryCurrencyId, long amountMinor, String targetPlayer, String memo) implements CustomPacketPayload {
	public WalletActionPayload {
		if (memo == null) {
			memo = "";
		}
	}

	/** Convenience for existing call-sites that do not supply a memo. */
	public WalletActionPayload(int menuId, Action action, String primaryCurrencyId, String secondaryCurrencyId, long amountMinor, String targetPlayer) {
		this(menuId, action, primaryCurrencyId, secondaryCurrencyId, amountMinor, targetPlayer, "");
	}

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
				buffer.readUtf(64),
				buffer.readUtf(64));
	}

	private static void write(FriendlyByteBuf buffer, WalletActionPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeEnum(payload.action);
		buffer.writeUtf(payload.primaryCurrencyId, 16);
		buffer.writeUtf(payload.secondaryCurrencyId, 16);
		buffer.writeVarLong(payload.amountMinor);
		buffer.writeUtf(payload.targetPlayer, 64);
		buffer.writeUtf(payload.memo, 64);
	}

	public static void handle(WalletActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !HolographicTerminalSession.isOpen(player, payload.menuId())) {
				return;
			}

			ExchangeRateSnapshot snapshot = ExchangeRateService.getCurrentSnapshot();
			ExchangeRateSnapshot currentSnapshot = snapshot;
			int menuId = payload.menuId();
			switch (payload.action) {
				case REQUEST_SYNC -> syncTerminalState(context, menuId, player, currentSnapshot);
				case DEPOSIT_ALL -> {
					depositAll(player);
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case DEPOSIT_SELECTED -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> depositSelected(player, currency, payload.amountMinor));
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case WITHDRAW_ITEM -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> withdrawItems(player, currency, payload.amountMinor));
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case WITHDRAW_EMERALDS -> {
					withdrawEmeralds(player, payload.amountMinor);
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case CONVERT -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(source ->
							CryptoCurrency.byId(payload.secondaryCurrencyId).ifPresent(target ->
									convert(player, source, target, payload.amountMinor, currentSnapshot)));
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case TRANSFER -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency ->
							transfer(player, menuId, currency, payload.amountMinor, payload.targetPlayer, payload.memo, currentSnapshot));
					syncTerminalState(context, menuId, player, currentSnapshot);
				}
				case REFRESH_RATES -> {
					if (!ExchangeRateService.canManualRefresh()) {
						player.displayClientMessage(Component.translatable("message.virtual_craft.exchange.cooldown"), true);
						syncTerminalState(context, menuId, player, currentSnapshot);
						return;
					}
					ExchangeRateSnapshot refreshedSnapshot = ExchangeRateService.refreshNow(player.server);

								if (ExchangeRateService.didLastRefreshFail()) {
									player.displayClientMessage(Component.translatable("message.virtual_craft.exchange.refresh_failed").withStyle(net.minecraft.ChatFormatting.RED), true);
								} else {
									player.displayClientMessage(Component.translatable("message.virtual_craft.exchange.refreshed"), true);
								}

					syncTerminalState(context, menuId, player, refreshedSnapshot);
				}
				case SETTLE_MINING -> {
					CryptoCurrency.byId(payload.primaryCurrencyId).ifPresent(currency -> settleMining(player, currency, payload.amountMinor, currentSnapshot));
					syncTerminalState(context, menuId, player, ExchangeRateService.getCurrentSnapshot());
				}
			}
		});
	}

	private static void syncTerminalState(IPayloadContext context, int menuId, ServerPlayer player, ExchangeRateSnapshot snapshot) {
		HolographicShopSyncDispatcher.pushSnapshot(player, menuId, snapshot);
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

	private static void depositSelected(ServerPlayer player, CryptoCurrency currency, long amountMinor) {
		long deposited = amountMinor > 0
				? removeAndDepositCurrency(player, currency, Math.toIntExact(Math.min(Integer.MAX_VALUE, amountMinor / currency.wholeUnitMinor())))
				: removeAndDepositCurrency(player, currency);
		if (deposited > 0) {
			WalletService.record(player, WalletLedgerType.DEPOSIT, currency, deposited, null, 0L, "inventory");
		}
		if (currency == CryptoCurrency.USDT) {
			int remaining = amountMinor > 0
					? Math.max(0, Math.toIntExact(Math.min(Integer.MAX_VALUE, amountMinor / currency.wholeUnitMinor() - deposited / currency.wholeUnitMinor())))
					: Integer.MAX_VALUE;
			long emeraldDeposit = remaining > 0 ? removeEmeraldsAndDeposit(player, remaining) : 0L;
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
		return removeAndDepositCurrency(player, currency, Integer.MAX_VALUE);
	}

	private static long removeAndDepositCurrency(ServerPlayer player, CryptoCurrency currency, int amount) {
		int removedItems = removeCurrencyItems(player.getInventory(), currency, amount);
		if (removedItems <= 0) {
			return 0L;
		}
		WalletService.depositWholeItem(player, currency, removedItems);
		return Math.multiplyExact(currency.wholeUnitMinor(), removedItems);
	}

	private static long removeEmeraldsAndDeposit(ServerPlayer player) {
		return removeEmeraldsAndDeposit(player, Integer.MAX_VALUE);
	}

	private static long removeEmeraldsAndDeposit(ServerPlayer player, int amount) {
		int removedEmeralds = removeItem(player.getInventory(), Items.EMERALD, amount);
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
		DailyContractService.recordProgress(player, DailyContractType.CONVERT_CURRENCY,
				CurrencyConversionService.convert(source, CryptoCurrency.USDT, sourceMinor, snapshot, RoundingMode.DOWN));
		player.displayClientMessage(Component.translatable("message.virtual_craft.convert.success", source.formatCompact(sourceMinor), source.displayName(), target.formatCompact(convertedMinor), target.displayName()), true);
	}

	private static void transfer(ServerPlayer player, int menuId, CryptoCurrency currency, long amountMinor, String targetName, String rawMemo, ExchangeRateSnapshot snapshot) {
		if (amountMinor <= 0 || targetName.isBlank()) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.invalid"), true);
			return;
		}
		ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
		if (target == null || target == player) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.invalid_target"), true);
			return;
		}
		// 用户要求：转账手续费货币从 SOL 切换到 TRX（波场币）。
		// 通过 WalletEconomyRules.TRANSFER_GAS_CURRENCY 统一引用，未来换币只改这一个常量。
		CryptoCurrency gasCurrency = WalletEconomyRules.TRANSFER_GAS_CURRENCY;
		if (!WalletService.canAfford(player, gasCurrency, WalletEconomyRules.TRANSFER_GAS_MINOR)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", gasCurrency.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR), gasCurrency.ticker()), true);
			return;
		}
		if (!WalletService.withdrawMinorUnits(player, gasCurrency, WalletEconomyRules.TRANSFER_GAS_MINOR)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.gas_missing", gasCurrency.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR), gasCurrency.ticker()), true);
			return;
		}
		if (!WalletService.transfer(player, target, currency, amountMinor)) {
			WalletService.depositMinorUnits(player, gasCurrency, WalletEconomyRules.TRANSFER_GAS_MINOR);
			player.displayClientMessage(Component.translatable("message.virtual_craft.wallet.insufficient"), true);
			return;
		}
		String trimmedMemo = rawMemo == null ? "" : rawMemo.strip();
		if (trimmedMemo.length() > dev.tatami.virtualcraft.wallet.WalletPreferences.MAX_MEMO_LENGTH) {
			trimmedMemo = trimmedMemo.substring(0, dev.tatami.virtualcraft.wallet.WalletPreferences.MAX_MEMO_LENGTH);
		}
		WalletService.record(player, WalletLedgerType.TRANSFER, currency, amountMinor, null, 0L, target.getName().getString(), trimmedMemo);
		WalletService.record(target, WalletLedgerType.TRANSFER, currency, amountMinor, null, 0L, player.getName().getString(), trimmedMemo);
		// Track the recent recipient on the sender's preference attachment so the
		// terminal can offer quick re-pay shortcuts later.
		long usdtEquivalentMinor = dev.tatami.virtualcraft.exchange.CurrencyConversionService.convert(
				currency, CryptoCurrency.USDT, amountMinor, snapshot, RoundingMode.DOWN);
		WalletService.getPreferences(player).touchContact(target.getName().getString(), trimmedMemo, usdtEquivalentMinor);
		player.displayClientMessage(Component.translatable("message.virtual_craft.transfer.sent", currency.formatCompact(amountMinor), currency.displayName(), target.getName()), true);
		target.displayClientMessage(Component.translatable("message.virtual_craft.transfer.received", currency.formatCompact(amountMinor), currency.displayName(), player.getName()), true);
		// 收款方转账推送：只在 target 也打开了终端时推送同步包，让他的 GUI 实时刷新余额/账本。
		// 旧实现靠 {@code containerMenu instanceof HolographicShopMenu}；改造后用 Session 查 target 的活跃 sessionId。
		int targetSessionId = HolographicTerminalSession.getOpenSessionId(target);
		if (targetSessionId != HolographicTerminalSession.NO_SESSION) {
			PacketDistributor.sendToPlayer(target, new SyncWalletPayload(
					targetSessionId,
					WalletService.getWallet(target).snapshot(),
					WalletService.walletAddress(target),
					WalletService.recentEntries(target, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
			PacketDistributor.sendToPlayer(target, SyncExchangePayload.fromSnapshot(targetSessionId, snapshot));
			PacketDistributor.sendToPlayer(target, SyncWalletPreferencesPayload.fromPreferences(targetSessionId, target));
		}
	}

	private static void settleMining(ServerPlayer player, CryptoCurrency payoutCurrency, long voucherCount, ExchangeRateSnapshot snapshot) {
		if (voucherCount <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining.none"), true);
			return;
		}
		VoucherRemoval removal = removeHashVouchers(player.getInventory(), voucherCount);
		if (removal.count() <= 0) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining.none"), true);
			return;
		}
		long totalBtcMinor = removal.totalBtcMinor();
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, payoutCurrency, totalBtcMinor, snapshot, RoundingMode.DOWN);
		if (payoutMinor <= 0) {
			refundHashVouchers(player, removal.removed());
			player.displayClientMessage(Component.translatable("message.virtual_craft.convert.too_small"), true);
			return;
		}
		WalletService.depositMinorUnits(player, payoutCurrency, payoutMinor);
		WalletService.record(player, WalletLedgerType.MINING_SETTLEMENT, payoutCurrency, payoutMinor, CryptoCurrency.BTC, totalBtcMinor, "vouchers:" + removal.count());
		// Aggregate the settlement into the persistent mining stats so the Mining
		// page can show a lifetime dashboard.
		WalletService.getPreferences(player).recordMiningSettlement(payoutCurrency, payoutMinor, totalBtcMinor, removal.count());
		DailyContractService.recordProgress(player, DailyContractType.SETTLE_VOUCHERS, removal.count());
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.virtual_craft.mining.settled",
				removal.count(),
				payoutCurrency.formatCompact(payoutMinor),
				payoutCurrency.ticker()), true);
	}

	private record RemovedVoucher(Item item, int count) {
	}

	private record VoucherRemoval(int count, long totalBtcMinor, List<RemovedVoucher> removed) {
	}

	private static VoucherRemoval removeHashVouchers(Inventory inventory, long requestedCount) {
		int remaining = (int) Math.min(Integer.MAX_VALUE, requestedCount);
		int totalRemoved = 0;
		long totalBtcMinor = 0L;
		List<RemovedVoucher> removed = new ArrayList<>();
		for (Item item : ModItems.hashVoucherItems()) {
			if (!(item instanceof HashVoucherItem voucher) || remaining <= 0) {
				continue;
			}
			int count = removeItem(inventory, item, remaining);
			if (count <= 0) {
				continue;
			}
			removed.add(new RemovedVoucher(item, count));
			totalRemoved += count;
			remaining -= count;
			totalBtcMinor = Math.addExact(totalBtcMinor, Math.multiplyExact(voucher.btcPayoutMinor(), count));
		}
		return new VoucherRemoval(totalRemoved, totalBtcMinor, removed);
	}

	private static void refundHashVouchers(ServerPlayer player, List<RemovedVoucher> removed) {
		for (RemovedVoucher voucher : removed) {
			giveItem(player, new ItemStack(voucher.item()), voucher.count());
		}
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
