package dev.tatami.virtualcraft.client.screen.holographic;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.network.SyncWalletPayload;
import dev.tatami.virtualcraft.shop.ShopOffer;

import java.util.List;
import java.util.function.Function;

final class HolographicText {
	private HolographicText() {
	}

	static String tickerAmount(CryptoCurrency currency, long minorAmount) {
		return currency.formatCompact(minorAmount) + " " + currency.ticker();
	}

	static String amount(String currencyId, long amountMinor) {
		CryptoCurrency currency = CryptoCurrency.byId(currencyId).orElse(CryptoCurrency.USDT);
		return tickerAmount(currency, amountMinor);
	}

	static String ledgerTitle(SyncWalletPayload.Entry entry, Function<String, String> translate) {
		return switch (entry.typeId()) {
			case "deposit" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.deposit");
			case "withdraw" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.withdraw");
			case "swap" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.swap");
			case "transfer" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.transfer");
			case "premium_buy" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.buy");
			case "premium_sell" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.sell");
			case "power_link" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.link");
			case "mining_settlement" -> translate.apply("screen.virtual_craft.holographic_shop.ledger.mine");
			default -> entry.typeId();
		};
	}

	static String ledgerDetail(SyncWalletPayload.Entry entry, List<ShopOffer> offers, Function<String, String> translate) {
		return switch (entry.typeId()) {
			case "deposit", "withdraw" -> amount(entry.primaryCurrencyId(), entry.primaryAmountMinor());
			case "swap" -> amount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) + " -> " + amount(entry.secondaryCurrencyId(), entry.secondaryAmountMinor());
			case "transfer" -> amount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) + " · " + safeLedgerName(entry.detail(), translate);
			case "premium_buy", "premium_sell" -> friendlyOfferName(entry.detail(), offers, translate) + " · " + amount(entry.primaryCurrencyId(), entry.primaryAmountMinor());
			case "power_link" -> safePowerLinkDetail(entry.detail(), translate);
			case "mining_settlement" -> amount(entry.primaryCurrencyId(), entry.primaryAmountMinor());
			default -> entry.detail().isBlank() ? amount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) : safeLedgerName(entry.detail(), translate);
		};
	}

	static String safePowerLinkDetail(String detail, Function<String, String> translate) {
		if (detail == null || detail.isBlank()) {
			return translate.apply("screen.virtual_craft.holographic_shop.ledger.link");
		}
		int channelIndex = detail.indexOf("CH");
		if (channelIndex >= 0) {
			return detail.substring(channelIndex).trim();
		}
		return translate.apply("screen.virtual_craft.holographic_shop.ledger.link");
	}

	static String safeLedgerName(String detail, Function<String, String> translate) {
		if (detail == null || detail.isBlank()) {
			return "-";
		}
		if (detail.startsWith("shares:")) {
			return translate.apply("screen.virtual_craft.holographic_shop.ledger.mine");
		}
		return detail;
	}

	static String friendlyOfferName(String offerId, List<ShopOffer> offers, Function<String, String> translate) {
		if (offerId == null || offerId.isBlank()) {
			return translate.apply("screen.virtual_craft.holographic_shop.shop.empty");
		}
		for (ShopOffer offer : offers) {
			if (offer.id().equals(offerId)) {
				return offer.displayName().getString();
			}
		}
		return offerId.replace('_', ' ');
	}
}
