package dev.tatami.virtualcraft.client.screen;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.CurrencyConversionService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import dev.tatami.virtualcraft.network.ShopActionPayload;
import dev.tatami.virtualcraft.network.SyncShopCatalogPayload;
import dev.tatami.virtualcraft.network.SyncWalletPayload;
import dev.tatami.virtualcraft.network.WalletActionPayload;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.shop.ShopOffer;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.SeekBar;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicShopFragment extends Fragment implements ScreenCallback {
	private enum Page {
		WALLET,
		CONVERT,
		SHOP,
		TRANSFER,
		MINING
	}

	private static final int SURFACE = 0xEAF2F7FC;
	private static final int OVERLAY = 0x20081422;
	private static final int PANEL = 0xD8F9FBFF;
	private static final int PANEL_ALT = 0xCDEBF2F8;
	private static final int PANEL_ACTIVE = 0xE8DDF3FF;
	private static final int PANEL_ROW = 0xD8FFFFFF;
	private static final int PANEL_BUTTON = 0xE8E7F3FB;
	private static final int STROKE = 0x265B7790;
	private static final int TEXT = 0xFF1A2C40;
	private static final int TEXT_DIM = 0xFF6882A0;
	private static final int GREEN = 0xFF10B981;
	private static final int CYAN = 0xFF0EA5E9;
	private static final int AMBER = 0xFFD97706;
	private static final int ACCENT_LINE = 0x300EA5E9;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final HolographicShopMenu menu;
	private final long[] balances = new long[CryptoCurrency.values().length];
	private final List<ShopOffer> offers = new ArrayList<>();
	private final List<SyncWalletPayload.Entry> recentEntries = new ArrayList<>();
	private final List<String> suggestedPlayers = new ArrayList<>();
	private LinearLayout root;
	private LinearLayout navColumn;
	private LinearLayout contentColumn;
	private LinearLayout actionColumn;
	private TextView headerValue;
	private TextView headerStatus;
	private TextView headerAddress;
	private TextView headerTime;
	private String walletAddress = "VC-UNKNOWN";
	private ExchangeRateSnapshot exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private Page currentPage = Page.WALLET;
	private int offerScroll;
	private int selectedOfferIndex;
	private CryptoCurrency selectedWalletCurrency = CryptoCurrency.USDT;
	private CryptoCurrency convertSourceCurrency = CryptoCurrency.USDT;
	private CryptoCurrency convertTargetCurrency = CryptoCurrency.BTC;
	private CryptoCurrency shopSettlementCurrency = CryptoCurrency.USDT;
	private CryptoCurrency transferCurrency = CryptoCurrency.USDT;
	private CryptoCurrency miningSettlementCurrency = CryptoCurrency.BTC;
	private String walletAmountText = "";
	private String convertAmountText = "";
	private String shopQuantityText = "1";
	private String transferAmountText = "";
	private String transferTargetText = "";
	private String miningAmountText = "1";
	private EditText walletAmountInput;
	private EditText convertAmountInput;
	private EditText transferAmountInput;
	private EditText transferTargetInput;
	private EditText miningAmountInput;

	public HolographicShopFragment(HolographicShopMenu menu) {
		this.menu = menu;
	}

	public boolean matchesMenu(int menuId) {
		return menu.containerId == menuId;
	}

	public static HolographicShopFragment fromCurrentScreen(int menuId) {
		if (Minecraft.getInstance().screen instanceof MuiScreen mui && mui.getFragment() instanceof HolographicShopFragment fragment && fragment.matchesMenu(menuId)) {
			return fragment;
		}
		return null;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, icyllis.modernui.util.DataSet savedInstanceState) {
		JeiOverlayController.hideOverlay();
		root = new LinearLayout(requireContext());
		root.setOrientation(LinearLayout.VERTICAL);
		root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		root.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
		root.setBackground(rounded(OVERLAY, 0, 0f, 0));
		root.setPadding(dp(20), dp(20), dp(20), dp(20));
		root.postDelayed(JeiOverlayController::hideOverlay, 250);
		root.postDelayed(JeiOverlayController::hideOverlay, 1000);

		LinearLayout window = new LinearLayout(requireContext());
		window.setOrientation(LinearLayout.VERTICAL);
		window.setBackground(rounded(SURFACE, STROKE, 18f, 1));
		window.setPadding(dp(22), dp(20), dp(22), dp(22));
		root.addView(window, new LinearLayout.LayoutParams(dp(660), dp(560)));

		LinearLayout header = new LinearLayout(requireContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(0, 0, 0, dp(14));
		window.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView title = labelLarge(t("screen.virtual_craft.holographic_shop"));
		header.addView(title, weighted(-2, -2, 1f));

		headerValue = chip();
		header.addView(headerValue, new LinearLayout.LayoutParams(dp(132), dp(30)));
		headerStatus = chip();
		var statusParams = new LinearLayout.LayoutParams(dp(92), dp(30));
		statusParams.setMargins(dp(8), 0, 0, 0);
		header.addView(headerStatus, statusParams);

		LinearLayout meta = new LinearLayout(requireContext());
		meta.setOrientation(LinearLayout.VERTICAL);
		var metaParams = new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT);
		metaParams.setMargins(dp(12), 0, 0, 0);
		header.addView(meta, metaParams);
		headerAddress = labelSmall("");
		headerTime = labelSmall("");
		meta.addView(headerAddress);
		meta.addView(headerTime);

		View separator = new View(requireContext());
		separator.setBackground(rounded(ACCENT_LINE, 0, 0f, 0));
		window.addView(separator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
		window.addView(spacer(dp(14)));

		LinearLayout body = new LinearLayout(requireContext());
		body.setOrientation(LinearLayout.HORIZONTAL);
		window.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		navColumn = column(PANEL_ALT);
		contentColumn = column(PANEL);
		actionColumn = column(PANEL_ALT);

		body.addView(navColumn, new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.MATCH_PARENT));
		var contentParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f);
		contentParams.setMargins(dp(14), 0, dp(14), 0);
		body.addView(contentColumn, contentParams);
		body.addView(actionColumn, new LinearLayout.LayoutParams(dp(188), ViewGroup.LayoutParams.MATCH_PARENT));

		refreshUi();
		PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.REQUEST_SYNC, "", "", 0L, ""));
		PacketDistributor.sendToServer(new ShopActionPayload(menu.containerId, ShopActionPayload.Action.REQUEST_SYNC, 0, 1, CryptoCurrency.USDT.id()));
		return root;
	}

	@Override
	public void onDestroy() {
		JeiOverlayController.restoreOverlay();
		super.onDestroy();
	}

	@Override
	public boolean shouldBlurBackground() {
		return false;
	}

	@Override
	public boolean hasDefaultBackground() {
		return false;
	}

	public void applyWalletSync(long[] balances, String walletAddress, List<SyncWalletPayload.Entry> entries) {
		System.arraycopy(balances, 0, this.balances, 0, Math.min(this.balances.length, balances.length));
		this.walletAddress = walletAddress;
		recentEntries.clear();
		recentEntries.addAll(entries);
		requestUiRefresh();
	}

	public void applyCatalogSync(List<SyncShopCatalogPayload.Entry> entries) {
		offers.clear();
		for (SyncShopCatalogPayload.Entry entry : entries) {
			ShopOffer offer = entry.toOffer();
			if (offer != null) {
				offers.add(offer);
			}
		}
		selectedOfferIndex = Math.min(selectedOfferIndex, Math.max(0, offers.size() - 1));
		offerScroll = Math.min(offerScroll, Math.max(0, offers.size() - 5));
		requestUiRefresh();
	}

	public void applyExchangeSync(ExchangeRateSnapshot snapshot) {
		exchangeSnapshot = snapshot;
		requestUiRefresh();
	}

	private void refreshUi() {
		if (root == null) {
			return;
		}
		stashInputs();
		refreshHeader();
		buildNav();
		buildContent();
		buildAction();
	}

	private void requestUiRefresh() {
		if (root != null) {
			root.post(this::refreshUi);
		}
	}

	private void refreshHeader() {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		headerValue.setText(CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT");
		headerStatus.setText(exchangeSnapshot.status().name());
		headerStatus.setTextColor(statusColor());
		headerAddress.setText(shortWalletAddress());
		headerTime.setText(TIME_FORMAT.format(Instant.ofEpochSecond(exchangeSnapshot.updatedAtEpochSeconds())));
	}

	private void buildNav() {
		navColumn.removeAllViews();
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.wallet"), Page.WALLET), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.convert"), Page.CONVERT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.shop"), Page.SHOP), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.transfer"), Page.TRANSFER), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.mining"), Page.MINING), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		navColumn.addView(spacer(dp(16)));
		navColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.sidebar.portfolio")));
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		TextView totalLabel = labelSmall(CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT");
		totalLabel.setTextColor(GREEN);
		navColumn.addView(totalLabel);
		navColumn.addView(spacer(dp(8)));
		CryptoCurrency summaryCurrency = summaryCurrency();
		navColumn.addView(labelTiny(summaryCurrency.ticker()));
		navColumn.addView(labelSmall(formatTickerAmount(summaryCurrency, balances[summaryCurrency.ordinal()])));
		navColumn.addView(spacer(dp(10)));
		navColumn.addView(labelTiny(shortWalletAddress()));
	}

	private void buildContent() {
		contentColumn.removeAllViews();
		switch (currentPage) {
			case WALLET -> buildWalletContent();
			case CONVERT -> buildConvertContent();
			case SHOP -> buildShopContent();
			case TRANSFER -> buildTransferContent();
			case MINING -> buildMiningContent();
		}
	}

	private void buildAction() {
		actionColumn.removeAllViews();
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.refresh"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.REFRESH_RATES, "", "", 0L, "")), CYAN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(12)));
		switch (currentPage) {
			case WALLET -> buildWalletActions();
			case CONVERT -> buildConvertActions();
			case SHOP -> buildShopActions();
			case TRANSFER -> buildTransferActions();
			case MINING -> buildMiningActions();
		}
	}

	private void buildWalletContent() {
		long selectedBalance = balances[selectedWalletCurrency.ordinal()];
		long selectedUsdt = CurrencyConversionService.convert(selectedWalletCurrency, CryptoCurrency.USDT, selectedBalance, exchangeSnapshot, RoundingMode.DOWN);
		contentColumn.addView(iconRow(selectedWalletCurrency));
		contentColumn.addView(spacer(dp(8)));
		contentColumn.addView(labelTiny(selectedWalletCurrency.displayName().getString()));
		contentColumn.addView(labelValue(formatTickerAmount(selectedWalletCurrency, selectedBalance)));
		contentColumn.addView(labelTiny("~" + CryptoCurrency.USDT.formatFixed(selectedUsdt) + " USDT"));
		contentColumn.addView(spacer(dp(14)));
		ScrollView scroll = new ScrollView(requireContext());
		scroll.setBackground(rounded(PANEL_ROW, 0, 10f, 0));
		LinearLayout list = vertical();
		scroll.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			Button row = navButton(formatTickerAmount(currency, balances[currency.ordinal()]), null);
			applyIcon(row, currency);
			styleButton(row, currency == selectedWalletCurrency ? PANEL_ACTIVE : PANEL_ROW, currency == selectedWalletCurrency ? CYAN : STROKE);
			row.setOnClickListener(v -> {
				selectedWalletCurrency = currency;
				refreshUi();
			});
			list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			list.addView(spacer(dp(4)));
		}
		contentColumn.addView(scroll, weighted(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.6f));
		if (!recentEntries.isEmpty()) {
			contentColumn.addView(spacer(dp(10)));
			contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.recent_activity")));
			contentColumn.addView(spacer(dp(4)));
			ScrollView activityScroll = new ScrollView(requireContext());
			activityScroll.setBackground(rounded(PANEL_ROW, 0, 10f, 0));
			LinearLayout activityList = vertical();
			activityScroll.addView(activityList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			for (int i = 0; i < Math.min(8, recentEntries.size()); i++) {
				activityList.addView(labelTiny(formatLedgerEntry(recentEntries.get(i))));
				activityList.addView(spacer(dp(2)));
			}
			contentColumn.addView(activityScroll, weighted(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.4f));
		}
	}

	private void buildConvertContent() {
		contentColumn.addView(iconRow(convertSourceCurrency));
		contentColumn.addView(spacer(dp(8)));
		contentColumn.addView(card(t("screen.virtual_craft.holographic_shop.convert.from"), convertSourceCurrency.displayName().getString(), formatTickerAmount(convertSourceCurrency, balances[convertSourceCurrency.ordinal()])));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(iconRow(convertTargetCurrency));
		contentColumn.addView(spacer(dp(8)));
		contentColumn.addView(card(t("screen.virtual_craft.holographic_shop.convert.to"), convertTargetCurrency.displayName().getString(), convertTargetCurrency.ticker()));
		contentColumn.addView(spacer(dp(6)));
		long oneSourceMinor = convertSourceCurrency.wholeUnitMinor();
		long rateTargetMinor = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, oneSourceMinor, exchangeSnapshot, RoundingMode.HALF_UP);
		contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.convert.rate", convertSourceCurrency.ticker(), convertTargetCurrency.formatCompact(rateTargetMinor), convertTargetCurrency.ticker())));
		contentColumn.addView(spacer(dp(10)));
		long previewAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, parseAmount(convertSourceCurrency, convertAmountText), exchangeSnapshot, RoundingMode.HALF_UP);
		contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.convert.preview")));
		contentColumn.addView(labelValue(formatTickerAmount(convertTargetCurrency, previewAmount)));
	}

	private void buildShopContent() {
		ScrollView scroll = new ScrollView(requireContext());
		scroll.setBackground(rounded(PANEL_ROW, 0, 10f, 0));
		LinearLayout list = vertical();
		scroll.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		for (int index = 0; index < 5; index++) {
			int offerIndex = offerScroll + index;
			if (offerIndex >= offers.size()) {
				break;
			}
			ShopOffer offer = offers.get(offerIndex);
			Button row = navButton(offer.displayName().getString() + "  " + CryptoCurrency.USDT.formatCompact(offer.priceUsdtMinor()), null);
			ImageDrawable offerIcon = itemIcon(offer.itemId(), 14);
			if (offerIcon != null) {
				row.setCompoundDrawablesRelative(offerIcon, null, null, null);
				row.setCompoundDrawablePadding(dp(6));
			}
			styleButton(row, offerIndex == selectedOfferIndex ? PANEL_ACTIVE : PANEL_ROW, offerIndex == selectedOfferIndex ? CYAN : STROKE);
			int finalOfferIndex = offerIndex;
			row.setOnClickListener(v -> {
				selectedOfferIndex = finalOfferIndex;
				refreshUi();
			});
			list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			list.addView(spacer(dp(4)));
		}
		contentColumn.addView(scroll, weighted(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		ShopOffer offer = selectedOffer();
		if (offer != null) {
			contentColumn.addView(spacer(dp(8)));
			ImageDrawable selectedIcon = itemIcon(offer.itemId(), 24);
			if (selectedIcon != null) {
				ImageView detailIcon = new ImageView(requireContext());
				detailIcon.setImageDrawable(selectedIcon);
				detailIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
				contentColumn.addView(detailIcon);
				contentColumn.addView(spacer(dp(4)));
			}
			contentColumn.addView(labelValue(offer.displayName().getString()));
			long buyMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
			long sellMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.sellUsdtMinor(), exchangeSnapshot, RoundingMode.DOWN);
			contentColumn.addView(spacer(dp(4)));
			TextView buyLabel = labelSmall(t("screen.virtual_craft.holographic_shop.buy") + " " + formatTickerAmount(shopSettlementCurrency, buyMinor));
			buyLabel.setTextColor(GREEN);
			contentColumn.addView(buyLabel);
			TextView sellLabel = labelSmall(t("screen.virtual_craft.holographic_shop.sell") + " " + formatTickerAmount(shopSettlementCurrency, sellMinor));
			sellLabel.setTextColor(AMBER);
			contentColumn.addView(sellLabel);
		}
	}

	private void buildTransferContent() {
		contentColumn.addView(iconRow(transferCurrency));
		contentColumn.addView(spacer(dp(4)));
		contentColumn.addView(labelValue(formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()])));
		contentColumn.addView(spacer(dp(8)));
		contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.transfer.target")));
		transferTargetInput = input(transferTargetText, t("screen.virtual_craft.holographic_shop.transfer.target"));
		contentColumn.addView(transferTargetInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
		contentColumn.addView(spacer(dp(10)));
		refreshPlayerSuggestions();
		for (int index = 0; index < Math.min(4, suggestedPlayers.size()); index++) {
			String suggested = suggestedPlayers.get(index);
			Button button = navButton(suggested, null);
			styleButton(button, PANEL_ROW, STROKE);
			button.setOnClickListener(v -> {
				transferTargetText = suggested;
				refreshUi();
			});
			contentColumn.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
			contentColumn.addView(spacer(dp(4)));
		}
	}

	private void buildMiningContent() {
		int vouchers = countHashVouchers();
		contentColumn.addView(iconRow(miningSettlementCurrency));
		contentColumn.addView(spacer(dp(8)));
		contentColumn.addView(labelTiny(t("item.virtual_craft.hash_voucher")));
		contentColumn.addView(labelValue(t("screen.virtual_craft.holographic_shop.mining.available", vouchers)));
		contentColumn.addView(spacer(dp(4)));
		contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.mining.base", CryptoCurrency.BTC.formatCompact(HashVoucherItem.BTC_PAYOUT_MINOR), CryptoCurrency.BTC.ticker())));
		contentColumn.addView(spacer(dp(10)));
		long btcMinor = Math.multiplyExact(HashVoucherItem.BTC_PAYOUT_MINOR, Math.max(0L, parseWhole(miningAmountText)));
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, miningSettlementCurrency, btcMinor, exchangeSnapshot, RoundingMode.DOWN);
		contentColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.mining.preview")));
		contentColumn.addView(labelValue(formatTickerAmount(miningSettlementCurrency, payoutMinor)));
	}

	private void buildWalletActions() {
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.deposit_all"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_ALL, "", "", 0L, "")), CYAN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.deposit_current", selectedWalletCurrency.ticker()), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_SELECTED, selectedWalletCurrency.id(), "", 0L, "")), CYAN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.transfer.amount")));
		actionColumn.addView(spacer(dp(4)));
		walletAmountInput = input(walletAmountText, selectedWalletCurrency.ticker());
		actionColumn.addView(walletAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.withdraw_item"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_ITEM, selectedWalletCurrency.id(), "", parseAmount(selectedWalletCurrency, walletAmountText), "")), GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		if (selectedWalletCurrency == CryptoCurrency.USDT) {
			actionColumn.addView(spacer(dp(8)));
			actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.withdraw_emeralds"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_EMERALDS, CryptoCurrency.USDT.id(), "", parseAmount(CryptoCurrency.USDT, walletAmountText), "")), AMBER), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		}
	}

	private void buildConvertActions() {
		actionColumn.addView(currencyButton(convertSourceCurrency, c -> convertSourceCurrency = c), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(currencyButton(convertTargetCurrency, c -> convertTargetCurrency = c), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.convert.auto"), () -> {
			long fullBalance = balances[convertSourceCurrency.ordinal()];
			if (fullBalance > 0 && convertSourceCurrency != convertTargetCurrency) {
				PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.CONVERT, convertSourceCurrency.id(), convertTargetCurrency.id(), fullBalance, ""));
			}
		}, CYAN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.transfer.amount")));
		actionColumn.addView(spacer(dp(4)));
		convertAmountInput = input(convertAmountText, convertSourceCurrency.ticker());
		actionColumn.addView(convertAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quickAmountRow(convertSourceCurrency, value -> convertAmountText = value), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.convert.submit"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.CONVERT, convertSourceCurrency.id(), convertTargetCurrency.id(), parseAmount(convertSourceCurrency, convertAmountText), "")), GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
	}

	private void buildShopActions() {
		actionColumn.addView(currencyButton(shopSettlementCurrency, c -> shopSettlementCurrency = c), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.shop_quantity", parseInt(shopQuantityText, 1))));
		actionColumn.addView(spacer(dp(4)));
		actionColumn.addView(quantitySlider(t("screen.virtual_craft.holographic_shop.transfer.amount"), Math.max(1, maxShopQuantity()), parseInt(shopQuantityText, 1), value -> shopQuantityText = Integer.toString(value)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.buy"), () -> sendShopAction(ShopActionPayload.Action.BUY), GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.sell"), () -> sendShopAction(ShopActionPayload.Action.SELL), AMBER), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
	}

	private void buildTransferActions() {
		actionColumn.addView(currencyButton(transferCurrency, c -> transferCurrency = c), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.transfer.amount")));
		actionColumn.addView(spacer(dp(4)));
		transferAmountInput = input(transferAmountText, transferCurrency.ticker());
		actionColumn.addView(transferAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quickAmountRow(transferCurrency, value -> transferAmountText = value), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.transfer.send"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.TRANSFER, transferCurrency.id(), "", parseAmount(transferCurrency, transferAmountText), transferTargetText.trim())), GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
	}

	private void buildMiningActions() {
		actionColumn.addView(currencyButton(miningSettlementCurrency, c -> miningSettlementCurrency = c), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(labelTiny(t("screen.virtual_craft.holographic_shop.mining.quantity", parseWhole(miningAmountText))));
		actionColumn.addView(spacer(dp(4)));
		actionColumn.addView(quantitySlider(t("item.virtual_craft.hash_voucher"), Math.max(1, countHashVouchers()), (int) Math.max(1L, parseWhole(miningAmountText)), value -> miningAmountText = Integer.toString(value)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(accentButton(t("screen.virtual_craft.holographic_shop.mining.settle"), () -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.SETTLE_MINING, miningSettlementCurrency.id(), "", parseWhole(miningAmountText), "")), GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
	}

	private void stashInputs() {
		if (walletAmountInput != null) {
			walletAmountText = walletAmountInput.getText().toString();
		}
		if (convertAmountInput != null) {
			convertAmountText = convertAmountInput.getText().toString();
		}
		if (transferAmountInput != null) {
			transferAmountText = transferAmountInput.getText().toString();
		}
		if (transferTargetInput != null) {
			transferTargetText = transferTargetInput.getText().toString();
		}
		if (miningAmountInput != null) {
			miningAmountText = miningAmountInput.getText().toString();
		}
	}

	private CryptoCurrency summaryCurrency() {
		return switch (currentPage) {
			case CONVERT -> convertSourceCurrency;
			case SHOP -> shopSettlementCurrency;
			case TRANSFER -> transferCurrency;
			case MINING -> miningSettlementCurrency;
			default -> selectedWalletCurrency;
		};
	}

	private Button navButton(String text, Page page) {
		Button button = new Button(requireContext());
		button.setText(text);
		button.setTextSize(15f);
		boolean selected = page != null && page == currentPage;
		button.setTextColor(selected ? TEXT : TEXT_DIM);
		button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
		button.setPadding(dp(14), dp(6), dp(14), dp(6));
		styleButton(button, selected ? PANEL_ACTIVE : PANEL_ROW, selected ? CYAN : STROKE);
		if (page != null) {
			button.setOnClickListener(v -> {
				currentPage = page;
				refreshUi();
			});
		}
		return button;
	}

	private Button accentButton(String text, Runnable action, int accent) {
		Button button = navButton(text, null);
		styleButton(button, PANEL_BUTTON, accent);
		button.setOnClickListener(v -> action.run());
		return button;
	}

	private Button currencyButton(CryptoCurrency currency, java.util.function.Consumer<CryptoCurrency> update) {
		Button button = navButton(currency.displayName().getString(), null);
		applyIcon(button, currency);
		button.setOnClickListener(v -> {
			update.accept(nextCurrency(currency));
			refreshUi();
		});
		return button;
	}

	private EditText input(String value, String hint) {
		EditText input = new EditText(requireContext());
		input.setText(value);
		input.setHint(hint);
		input.setSingleLine();
		input.setTextSize(14f);
		input.setTextColor(TEXT);
		input.setHintTextColor(TEXT_DIM);
		input.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
		input.setPadding(dp(12), dp(6), dp(12), dp(6));
		input.setBackground(rounded(PANEL_ROW, STROKE, 10f, 1));
		return input;
	}

	private TextView labelLarge(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(24f);
		view.setTextColor(TEXT);
		return view;
	}

	private TextView labelValue(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(22f);
		view.setTextColor(GREEN);
		return view;
	}

	private TextView labelSmall(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(14f);
		view.setTextColor(TEXT_DIM);
		return view;
	}

	private TextView labelTiny(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(13f);
		view.setTextColor(TEXT_DIM);
		return view;
	}

	private TextView chip() {
		TextView view = new TextView(requireContext());
		view.setTextSize(14f);
		view.setTextColor(TEXT);
		view.setGravity(Gravity.CENTER);
		view.setBackground(rounded(PANEL_ACTIVE, CYAN, 999f, 1));
		return view;
	}

	private LinearLayout column(int color) {
		LinearLayout layout = vertical();
		layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		layout.setBackground(rounded(color, STROKE, 14f, 1));
		layout.setPadding(dp(12), dp(12), dp(12), dp(12));
		return layout;
	}

	private LinearLayout vertical() {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		return layout;
	}

	private View card(String label, String title, String value) {
		LinearLayout card = vertical();
		card.setBackground(rounded(PANEL_ROW, STROKE, 14f, 1));
		card.setPadding(dp(14), dp(12), dp(14), dp(12));
		card.addView(labelTiny(label));
		card.addView(labelLarge(title));
		card.addView(labelValue(value));
		return card;
	}

	private View spacer(int height) {
		View view = new View(requireContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
		return view;
	}

	private View hSpacer(int width) {
		View view = new View(requireContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
		return view;
	}

	private View iconRow(CryptoCurrency currency) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(rounded(PANEL_ROW, STROKE, 999f, 1));
		row.setPadding(dp(10), dp(8), dp(10), dp(8));
		TextView badge = new TextView(requireContext());
		badge.setText(currency.ticker().substring(0, 1));
		badge.setTextSize(11f);
		badge.setTextColor(0xFFFFFFFF);
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(rounded(currencyColor(currency), 0, 999f, 0));
		badge.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
		row.addView(badge);
		TextView ticker = labelSmall(currency.ticker());
		var tickerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tickerParams.setMargins(dp(8), 0, 0, 0);
		row.addView(ticker, tickerParams);
		return row;
	}

	private View quantitySlider(String label, int max, int current, java.util.function.IntConsumer onChanged) {
		LinearLayout block = vertical();
		TextView value = labelTiny(label + ": " + Math.max(1, current));
		block.addView(value);
		SeekBar seekBar = new SeekBar(requireContext());
		seekBar.setMin(1);
		seekBar.setMax(Math.max(1, max));
		seekBar.setProgress(Math.min(Math.max(1, current), Math.max(1, max)));
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int resolved = Math.max(1, progress);
				value.setText(label + ": " + resolved);
				onChanged.accept(resolved);
			}
		});
		block.addView(seekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return block;
	}

	private View quickAmountRow(CryptoCurrency currency, java.util.function.Consumer<String> onPicked) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(quickAmountButton("25%", currency, 1, 4, onPicked), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		row.addView(hSpacer(dp(5)));
		row.addView(quickAmountButton("50%", currency, 1, 2, onPicked), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		row.addView(hSpacer(dp(5)));
		row.addView(quickAmountButton(t("screen.virtual_craft.holographic_shop.quick_all"), currency, 1, 1, onPicked), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		return row;
	}

	private Button quickAmountButton(String label, CryptoCurrency currency, int numerator, int denominator, java.util.function.Consumer<String> onPicked) {
		Button button = navButton(label, null);
		button.setTextSize(12f);
		button.setGravity(Gravity.CENTER);
		styleButton(button, PANEL_ROW, STROKE);
		button.setOnClickListener(v -> {
			long balance = balances[currency.ordinal()];
			long picked = denominator <= 1 ? balance : Math.max(0L, (balance / denominator) * numerator + ((balance % denominator) * numerator) / denominator);
			onPicked.accept(currency.formatFixed(picked));
			refreshUi();
		});
		return button;
	}

	private LinearLayout.LayoutParams weighted(int width, int height, float weight) {
		return new LinearLayout.LayoutParams(width, height, weight);
	}

	@SuppressWarnings("deprecation")
	private ImageDrawable currencyIcon(CryptoCurrency currency, int sizeDp) {
		try {
			var loc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
					"virtual_craft", "textures/item/" + currency.itemName() + ".png");
			var resource = Minecraft.getInstance().getResourceManager().getResource(loc);
			if (resource.isPresent()) {
				try (java.io.InputStream stream = resource.get().open()) {
					ImageDrawable drawable = new ImageDrawable(stream);
					drawable.setFilter(false);
					int size = dp(sizeDp);
					drawable.setBounds(0, 0, size, size);
					return drawable;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	private ImageDrawable itemIcon(net.minecraft.resources.ResourceLocation itemId, int sizeDp) {
		try {
			var texLoc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
					itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
			var resource = Minecraft.getInstance().getResourceManager().getResource(texLoc);
			if (resource.isPresent()) {
				try (java.io.InputStream stream = resource.get().open()) {
					ImageDrawable drawable = new ImageDrawable(stream);
					drawable.setFilter(false);
					int size = dp(sizeDp);
					drawable.setBounds(0, 0, size, size);
					return drawable;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private ShapeDrawable currencyBadge(CryptoCurrency currency, int sizeDp) {
		ShapeDrawable badge = rounded(currencyColor(currency), 0, 999f, 0);
		int size = dp(sizeDp);
		badge.setSize(size, size);
		badge.setBounds(0, 0, size, size);
		return badge;
	}

	private void applyIcon(TextView view, CryptoCurrency currency) {
		view.setCompoundDrawablesRelative(currencyBadge(currency, 14), null, null, null);
		view.setCompoundDrawablePadding(dp(8));
	}

	private int currencyColor(CryptoCurrency currency) {
		return switch (currency) {
			case BTC -> 0xFFF7931A;
			case ETH -> 0xFF627EEA;
			case USDT -> 0xFF26A17B;
			case SOL -> 0xFF9945FF;
			case DOGE -> 0xFFC2A633;
		};
	}

	private ShapeDrawable rounded(int fill, int stroke, float radiusDp, int strokeWidthDp) {
		ShapeDrawable drawable = new ShapeDrawable();
		drawable.setCornerRadius(dp(radiusDp));
		drawable.setColor(fill);
		if (stroke != 0) {
			drawable.setStroke(Math.max(1, dp(strokeWidthDp)), stroke);
		}
		return drawable;
	}

	private void styleButton(Button button, int fill, int stroke) {
		button.setBackground(rounded(fill, stroke, 10f, 1));
	}

	private void sendShopAction(ShopActionPayload.Action action) {
		ShopOffer offer = selectedOffer();
		if (offer == null) {
			return;
		}
		PacketDistributor.sendToServer(new ShopActionPayload(menu.containerId, action, selectedOfferIndex, Math.max(1, parseInt(shopQuantityText, 1)), shopSettlementCurrency.id()));
	}

	private void refreshPlayerSuggestions() {
		suggestedPlayers.clear();
		String filter = transferTargetText.trim().toLowerCase(Locale.ROOT);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() != null) {
			for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
				String name = info.getProfile().getName();
				if (minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName())) {
					continue;
				}
				if (filter.isBlank() || name.toLowerCase(Locale.ROOT).contains(filter)) {
					suggestedPlayers.add(name);
					if (suggestedPlayers.size() >= 4) {
						break;
					}
				}
			}
		}
	}

	private ShopOffer selectedOffer() {
		if (selectedOfferIndex < 0 || selectedOfferIndex >= offers.size()) {
			return null;
		}
		return offers.get(selectedOfferIndex);
	}

	private int countInventoryItems(ShopOffer offer) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int total = 0;
		for (var stack : player.getInventory().items) {
			if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, offer.createStack(1))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private int currentShopBuyMax() {
		ShopOffer offer = selectedOffer();
		if (offer == null || offer.priceUsdtMinor() <= 0) {
			return 0;
		}
		long settlementCost = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
		if (settlementCost <= 0) {
			return 0;
		}
		return (int) Math.max(0L, balances[shopSettlementCurrency.ordinal()] / settlementCost);
	}

	private int currentShopSellMax() {
		ShopOffer offer = selectedOffer();
		if (offer == null || offer.count() <= 0) {
			return 0;
		}
		return countInventoryItems(offer) / offer.count();
	}

	private int maxShopQuantity() {
		return Math.max(currentShopBuyMax(), currentShopSellMax());
	}

	private int countHashVouchers() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int total = 0;
		for (var stack : player.getInventory().items) {
			if (stack.is(ModItems.HASH_VOUCHER.get())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private long parseAmount(CryptoCurrency currency, String text) {
		return currency.parse(text).orElse(0L);
	}

	private long parseWhole(String text) {
		try {
			return Math.max(0L, Long.parseLong(text.trim()));
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private int parseInt(String text, int fallback) {
		try {
			return Math.max(1, Integer.parseInt(text.trim()));
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private String shortWalletAddress() {
		if (walletAddress == null || walletAddress.isBlank()) {
			return "VC-UNKNOWN";
		}
		return walletAddress.length() <= 13 ? walletAddress : walletAddress.substring(0, 7) + "..." + walletAddress.substring(walletAddress.length() - 4);
	}

	private int statusColor() {
		return switch (exchangeSnapshot.status()) {
			case LIVE -> GREEN;
			case CACHED -> CYAN;
			case DEFAULT, STALE -> AMBER;
		};
	}

	private String formatTickerAmount(CryptoCurrency currency, long minorAmount) {
		return currency.formatCompact(minorAmount) + " " + currency.ticker();
	}

	private String formatLedgerEntry(SyncWalletPayload.Entry entry) {
		String prefix = switch (entry.typeId()) {
			case "deposit" -> "+";
			case "withdraw" -> "-";
			case "swap" -> t("screen.virtual_craft.holographic_shop.ledger.swap");
			case "transfer" -> t("screen.virtual_craft.holographic_shop.ledger.transfer");
			case "premium_buy" -> t("screen.virtual_craft.holographic_shop.ledger.buy");
			case "premium_sell" -> t("screen.virtual_craft.holographic_shop.ledger.sell");
			case "power_link" -> t("screen.virtual_craft.holographic_shop.ledger.link");
			case "mining_settlement" -> t("screen.virtual_craft.holographic_shop.ledger.mine");
			default -> entry.typeId();
		};
		String detail = entry.detail().isBlank() ? formatAmount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) : entry.detail();
		return prefix + " " + detail;
	}

	private String formatAmount(String currencyId, long amountMinor) {
		CryptoCurrency currency = CryptoCurrency.byId(currencyId).orElse(CryptoCurrency.USDT);
		return currency.formatCompact(amountMinor) + " " + currency.ticker();
	}

	private String t(String key, Object... args) {
		return net.minecraft.network.chat.Component.translatable(key, args).getString();
	}

	private CryptoCurrency nextCurrency(CryptoCurrency currency) {
		CryptoCurrency[] currencies = CryptoCurrency.values();
		return currencies[(currency.ordinal() + 1) % currencies.length];
	}

	private int dp(float value) {
		return root == null ? Math.round(value) : root.dp(value);
	}
}
