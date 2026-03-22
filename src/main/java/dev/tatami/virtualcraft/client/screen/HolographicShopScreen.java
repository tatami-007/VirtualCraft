package dev.tatami.virtualcraft.client.screen;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.tatami.virtualcraft.client.screen.widget.AmountSliderWidget;
import dev.tatami.virtualcraft.client.screen.widget.FlatButton;
import dev.tatami.virtualcraft.client.screen.widget.FlatEditBox;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicShopScreen extends AbstractContainerScreen<HolographicShopMenu> {
	private enum Page {
		WALLET,
		CONVERT,
		SHOP,
		TRANSFER,
		MINING
	}

	private static final int VISIBLE_OFFERS = 5;
	private static final int PANEL_BG = 0xFFF8FBFF;
	private static final int PANEL_ALT = 0xFFEAF1F8;
	private static final int PANEL_ACTIVE = 0xFFDCEAF5;
	private static final int BORDER = 0xFFB5C5D5;
	private static final int TEXT_PRIMARY = 0xFF223347;
	private static final int TEXT_SECONDARY = 0xFF48749B;
	private static final int TEXT_ACCENT = 0xFF228C67;
	private static final int TEXT_MUTED = 0xFF6E8296;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private final Inventory playerInventory;
	private final long[] balances = new long[CryptoCurrency.values().length];
	private final List<ShopOffer> offers = new ArrayList<>();
	private final List<SyncWalletPayload.Entry> recentEntries = new ArrayList<>();
	private final List<Button> playerSuggestionButtons = new ArrayList<>();
	private final List<String> suggestedPlayers = new ArrayList<>();
	private ExchangeRateSnapshot exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private String walletAddress = "VC-UNKNOWN";
	private Page currentPage = Page.WALLET;
	private int offerScroll;
	private int selectedOfferIndex;
	private CryptoCurrency selectedWalletCurrency = CryptoCurrency.USDT;
	private CryptoCurrency convertSourceCurrency = CryptoCurrency.USDT;
	private CryptoCurrency convertTargetCurrency = CryptoCurrency.BTC;
	private CryptoCurrency shopSettlementCurrency = CryptoCurrency.USDT;
	private CryptoCurrency transferCurrency = CryptoCurrency.USDT;
	private Button walletTab;
	private Button convertTab;
	private Button shopTab;
	private Button transferTab;
	private Button miningTab;
	private Button refreshRatesButton;
	private Button depositAllButton;
	private Button depositSelectedButton;
	private Button withdrawItemButton;
	private Button withdrawEmeraldButton;
	private Button convertSourceButton;
	private Button convertTargetButton;
	private Button flipConvertButton;
	private Button convertSubmitButton;
	private Button convertQuarterButton;
	private Button convertHalfButton;
	private Button convertMaxButton;
	private Button shopSettlementButton;
	private Button buyButton;
	private Button sellButton;
	private Button previousOffersButton;
	private Button nextOffersButton;
	private Button transferCurrencyButton;
	private Button sendTransferButton;
	private Button miningCurrencyButton;
	private Button settleMiningButton;
	private AmountSliderWidget walletSlider;
	private AmountSliderWidget convertSlider;
	private AmountSliderWidget shopSlider;
	private AmountSliderWidget transferSlider;
	private AmountSliderWidget miningSlider;
	private EditBox targetBox;
	private CryptoCurrency miningSettlementCurrency = CryptoCurrency.BTC;

	public HolographicShopScreen(HolographicShopMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.playerInventory = playerInventory;
		imageWidth = 476;
		imageHeight = 304;
		titleLabelX = 18;
		titleLabelY = 16;
		inventoryLabelY = 10_000;
	}

	@Override
	protected void init() {
		super.init();
		int tabY = topPos + 62;
		walletTab = addRenderableWidget(new FlatButton(leftPos + 16, tabY, 84, 22,
				Component.translatable("screen.virtual_craft.holographic_shop.page.wallet"),
				button -> setPage(Page.WALLET), () -> currentPage == Page.WALLET, 0xFF52D3E0));
		convertTab = addRenderableWidget(new FlatButton(leftPos + 104, tabY, 84, 22,
				Component.translatable("screen.virtual_craft.holographic_shop.page.convert"),
				button -> setPage(Page.CONVERT), () -> currentPage == Page.CONVERT, 0xFF52D3E0));
		shopTab = addRenderableWidget(new FlatButton(leftPos + 192, tabY, 84, 22,
				Component.translatable("screen.virtual_craft.holographic_shop.page.shop"),
				button -> setPage(Page.SHOP), () -> currentPage == Page.SHOP, 0xFF52D3E0));
		transferTab = addRenderableWidget(new FlatButton(leftPos + 280, tabY, 84, 22,
				Component.translatable("screen.virtual_craft.holographic_shop.page.transfer"),
				button -> setPage(Page.TRANSFER), () -> currentPage == Page.TRANSFER, 0xFF52D3E0));
		miningTab = addRenderableWidget(new FlatButton(leftPos + 368, tabY, 92, 22,
				Component.translatable("screen.virtual_craft.holographic_shop.page.mining"),
				button -> setPage(Page.MINING), () -> currentPage == Page.MINING, 0xFF52D3E0));
		refreshRatesButton = addRenderableWidget(new FlatButton(leftPos + 402, topPos + 14, 56, 18,
				Component.translatable("screen.virtual_craft.holographic_shop.refresh"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.REFRESH_RATES, "", "", 0L, "")),
				() -> false, 0xFF52D3E0));

		depositAllButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 108, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.deposit_all"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_ALL, "", "", 0L, ""))));
		depositSelectedButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 132, 102, 20,
				Component.empty(),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_SELECTED, selectedWalletCurrency.id(), "", 0L, ""))));
		walletSlider = addRenderableWidget(new AmountSliderWidget(rightPanelX(), topPos + 164, 102, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(selectedWalletCurrency, amount))));
		withdrawItemButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 188, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.withdraw_item"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_ITEM, selectedWalletCurrency.id(), "", walletSlider.currentValue(), ""))));
		withdrawEmeraldButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 212, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.withdraw_emeralds"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_EMERALDS, CryptoCurrency.USDT.id(), "", walletSlider.currentValue(), ""))));

		convertSourceButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 108, 102, 20, Component.empty(), button -> {
			convertSourceCurrency = nextCurrency(convertSourceCurrency);
			if (convertSourceCurrency == convertTargetCurrency) {
				convertTargetCurrency = nextCurrency(convertTargetCurrency);
			}
			refreshDynamicState();
		}));
		convertTargetButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 132, 102, 20, Component.empty(), button -> {
			convertTargetCurrency = nextCurrency(convertTargetCurrency);
			if (convertTargetCurrency == convertSourceCurrency) {
				convertSourceCurrency = nextCurrency(convertSourceCurrency);
			}
			refreshDynamicState();
		}));
		flipConvertButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 156, 102, 20, Component.literal("<>"), button -> {
			CryptoCurrency previousSource = convertSourceCurrency;
			convertSourceCurrency = convertTargetCurrency;
			convertTargetCurrency = previousSource;
			refreshDynamicState();
		}));
		convertSlider = addRenderableWidget(new AmountSliderWidget(rightPanelX(), topPos + 182, 102, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(convertSourceCurrency, amount))));
		convertQuarterButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 206, 32, 18, Component.literal("25%"), button -> convertSlider.setFraction(0.25D)));
		convertHalfButton = addRenderableWidget(new FlatButton(rightPanelX() + 35, topPos + 206, 32, 18, Component.literal("50%"), button -> convertSlider.setFraction(0.50D)));
		convertMaxButton = addRenderableWidget(new FlatButton(rightPanelX() + 70, topPos + 206, 32, 18, Component.literal("100%"), button -> convertSlider.setFraction(1.0D)));
		convertSubmitButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 228, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.convert.submit"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.CONVERT, convertSourceCurrency.id(), convertTargetCurrency.id(), convertSlider.currentValue(), ""))));

		shopSettlementButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 108, 102, 20, Component.empty(), button -> {
			shopSettlementCurrency = nextCurrency(shopSettlementCurrency);
			refreshDynamicState();
		}));
		shopSlider = addRenderableWidget(new AmountSliderWidget(rightPanelX(), topPos + 144, 102, 20, 0L, 0L, 1L, 0L, amount ->
				Component.translatable("screen.virtual_craft.holographic_shop.shop_quantity", amount)));
		buyButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 176, 102, 20, Component.translatable("screen.virtual_craft.holographic_shop.buy"), button -> sendShopAction(ShopActionPayload.Action.BUY)));
		sellButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 200, 102, 20, Component.translatable("screen.virtual_craft.holographic_shop.sell"), button -> sendShopAction(ShopActionPayload.Action.SELL)));
		previousOffersButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 228, 48, 20, Component.literal("<"), button -> {
			offerScroll = Math.max(0, offerScroll - VISIBLE_OFFERS);
			selectedOfferIndex = offerScroll;
			refreshDynamicState();
		}));
		nextOffersButton = addRenderableWidget(new FlatButton(rightPanelX() + 54, topPos + 228, 48, 20, Component.literal(">"), button -> {
			offerScroll = Math.min(Math.max(0, offers.size() - VISIBLE_OFFERS), offerScroll + VISIBLE_OFFERS);
			selectedOfferIndex = offerScroll;
			refreshDynamicState();
		}));

		targetBox = addRenderableWidget(new FlatEditBox(font, centerPanelX() + 16, topPos + 120, 148, 18, Component.translatable("screen.virtual_craft.holographic_shop.transfer.target")));
		targetBox.setMaxLength(32);
		transferCurrencyButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 108, 102, 20, Component.empty(), button -> {
			transferCurrency = nextCurrency(transferCurrency);
			refreshDynamicState();
		}));
		transferSlider = addRenderableWidget(new AmountSliderWidget(rightPanelX(), topPos + 144, 102, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(transferCurrency, amount))));
		sendTransferButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 176, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.transfer.send"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.TRANSFER, transferCurrency.id(), "", transferSlider.currentValue(), targetBox.getValue().trim()))));

		for (int index = 0; index < 4; index++) {
			final int suggestionIndex = index;
			playerSuggestionButtons.add(addRenderableWidget(new FlatButton(centerPanelX() + 16 + (index % 2) * 76, topPos + 146 + (index / 2) * 22, 70, 20, Component.empty(), button -> {
				if (suggestionIndex < suggestedPlayers.size()) {
					targetBox.setValue(suggestedPlayers.get(suggestionIndex));
				}
			})));
		}

		miningCurrencyButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 108, 102, 20, Component.empty(), button -> {
			miningSettlementCurrency = nextCurrency(miningSettlementCurrency);
			refreshDynamicState();
		}));
		miningSlider = addRenderableWidget(new AmountSliderWidget(rightPanelX(), topPos + 144, 102, 20, 0L, 0L, 1L, 0L, amount ->
				Component.translatable("screen.virtual_craft.holographic_shop.mining.quantity", amount)));
		settleMiningButton = addRenderableWidget(new FlatButton(rightPanelX(), topPos + 176, 102, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.mining.settle"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.SETTLE_MINING, miningSettlementCurrency.id(), "", miningSlider.currentValue(), ""))));

		refreshDynamicState();
		setPage(Page.WALLET);
		PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.REQUEST_SYNC, "", "", 0L, ""));
		PacketDistributor.sendToServer(new ShopActionPayload(menu.containerId, ShopActionPayload.Action.REQUEST_SYNC, 0, 1, CryptoCurrency.USDT.id()));
	}

	public boolean matchesMenu(int menuId) {
		return menu.containerId == menuId;
	}

	public void applyWalletSync(long[] balances, String walletAddress, List<SyncWalletPayload.Entry> entries) {
		System.arraycopy(balances, 0, this.balances, 0, Math.min(this.balances.length, balances.length));
		this.walletAddress = walletAddress;
		recentEntries.clear();
		recentEntries.addAll(entries);
		refreshDynamicState();
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
		offerScroll = Math.min(offerScroll, Math.max(0, offers.size() - VISIBLE_OFFERS));
		refreshDynamicState();
	}

	public void applyExchangeSync(ExchangeRateSnapshot snapshot) {
		this.exchangeSnapshot = snapshot;
		refreshDynamicState();
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int left = leftPos;
		int top = topPos;
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFFF1F6FB);
		guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, 0xFFE3EBF4);
		guiGraphics.fill(left + 14, top + 14, left + imageWidth - 14, top + 48, PANEL_ACTIVE);
		guiGraphics.fill(left + 14, top + 90, left + imageWidth - 14, top + imageHeight - 14, 0xFFF6FAFD);
		drawPanel(guiGraphics, leftPanelX(), top + 102, 156, 170, PANEL_BG);
		drawPanel(guiGraphics, centerPanelX(), top + 102, 160, 170, PANEL_BG);
		drawPanel(guiGraphics, rightPanelX() - 6, top + 102, 114, 170, PANEL_BG);
		guiGraphics.fill(leftPanelX() + 1, top + 103, leftPanelX() + 155, top + 105, 0xFF79CDE0);
		guiGraphics.fill(centerPanelX() + 1, top + 103, centerPanelX() + 159, top + 105, 0xFF79CDE0);
		guiGraphics.fill(rightPanelX() - 5, top + 103, rightPanelX() + 107, top + 105, 0xFFA3B8CA);

		renderTopBar(guiGraphics, left, top);
		switch (currentPage) {
			case WALLET -> renderWalletPage(guiGraphics, left, top);
			case CONVERT -> renderConvertPage(guiGraphics, left, top);
			case SHOP -> renderShopPage(guiGraphics, left, top);
			case TRANSFER -> renderTransferPage(guiGraphics, left, top);
			case MINING -> renderMiningPage(guiGraphics, left, top);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		renderHoverTooltip(guiGraphics, mouseX, mouseY);
		renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (currentPage == Page.WALLET) {
			for (int index = 0; index < CryptoCurrency.values().length; index++) {
				int rowX = leftPanelX() + 8;
				int rowY = topPos + 94 + index * 30;
				if (inside(mouseX, mouseY, rowX, rowY, 138, 26)) {
					selectedWalletCurrency = CryptoCurrency.values()[index];
					refreshDynamicState();
					return true;
				}
			}
		}
		if (currentPage == Page.SHOP) {
			for (int index = 0; index < VISIBLE_OFFERS; index++) {
				int offerIndex = offerScroll + index;
				if (offerIndex >= offers.size()) {
					break;
				}
				int rowY = topPos + 94 + index * 30;
				if (inside(mouseX, mouseY, leftPanelX() + 8, rowY, 138, 26)) {
					selectedOfferIndex = offerIndex;
					refreshDynamicState();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void renderTopBar(GuiGraphics guiGraphics, int left, int top) {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		guiGraphics.drawString(font, title, left + 20, top + 18, TEXT_PRIMARY, false);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.total_value", compactHeaderValue(totalUsdtMinor)).getString(), left + 20, top + 32, 158, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.wallet_address", shortWalletAddress()).getString(), left + 182, top + 32, 116, TEXT_MUTED);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.rate_status", exchangeSnapshot.status().name()).getString(), left + 306, top + 18, 90, statusColor());
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.rate_time", TIME_FORMAT.format(Instant.ofEpochSecond(exchangeSnapshot.updatedAtEpochSeconds()))).getString(), left + 306, top + 32, 92, TEXT_MUTED);
	}

	private void renderWalletPage(GuiGraphics guiGraphics, int left, int top) {
		for (int index = 0; index < CryptoCurrency.values().length; index++) {
			CryptoCurrency currency = CryptoCurrency.values()[index];
			int rowY = top + 110 + index * 30;
			guiGraphics.fill(leftPanelX() + 8, rowY, leftPanelX() + 148, rowY + 26, currency == selectedWalletCurrency ? PANEL_ACTIVE : PANEL_ALT);
			guiGraphics.renderItem(currency.stack(1), leftPanelX() + 14, rowY + 5);
			drawTrimmedString(guiGraphics, currency.displayName().getString(), leftPanelX() + 36, rowY + 5, 104, TEXT_PRIMARY);
			drawTrimmedString(guiGraphics, formatTickerAmount(currency, balances[index]), leftPanelX() + 36, rowY + 15, 104, TEXT_ACCENT);
		}

		long selectedBalance = balances[selectedWalletCurrency.ordinal()];
		long selectedUsdt = CurrencyConversionService.convert(selectedWalletCurrency, CryptoCurrency.USDT, selectedBalance, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(selectedWalletCurrency.stack(1), centerPanelX() + 16, top + 114);
		drawTrimmedString(guiGraphics, selectedWalletCurrency.displayName().getString(), centerPanelX() + 40, top + 115, 112, TEXT_PRIMARY);
		drawTrimmedString(guiGraphics, formatTickerAmount(selectedWalletCurrency, selectedBalance), centerPanelX() + 16, top + 138, 140, TEXT_ACCENT);
		drawTrimmedString(guiGraphics, "~" + CryptoCurrency.USDT.formatFixed(selectedUsdt) + " USDT", centerPanelX() + 16, top + 156, 140, TEXT_MUTED);
		if (selectedWalletCurrency == CryptoCurrency.USDT) {
			drawTrimmedString(guiGraphics, "Stablecoin pegged to USD", centerPanelX() + 16, top + 176, 140, TEXT_MUTED);
		} else {
			long quoteMinor = CurrencyConversionService.convert(selectedWalletCurrency, CryptoCurrency.USDT, selectedWalletCurrency.wholeUnitMinor(), exchangeSnapshot, RoundingMode.DOWN);
			drawTrimmedString(guiGraphics, "1 " + selectedWalletCurrency.ticker() + " = " + CryptoCurrency.USDT.formatCompact(quoteMinor) + " USDT", centerPanelX() + 16, top + 176, 140, TEXT_MUTED);
		}
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.selected_hint").getString(), centerPanelX() + 16, top + 196, 140, TEXT_MUTED);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.recent_activity").getString(), centerPanelX() + 16, top + 216, 140, TEXT_SECONDARY);
		for (int index = 0; index < Math.min(5, recentEntries.size()); index++) {
			drawTrimmedString(guiGraphics, formatLedgerEntry(recentEntries.get(index)), centerPanelX() + 16, top + 228 + index * 8, 140, index == 0 ? TEXT_ACCENT : TEXT_MUTED);
		}
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.quick_withdraw").getString(), rightPanelX(), top + 154, 102, TEXT_SECONDARY);
	}

	private void renderConvertPage(GuiGraphics guiGraphics, int left, int top) {
		guiGraphics.renderItem(convertSourceCurrency.stack(1), leftPanelX() + 16, top + 116);
		guiGraphics.renderItem(convertTargetCurrency.stack(1), centerPanelX() + 16, top + 116);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.convert.from").getString(), leftPanelX() + 16, top + 108, 120, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, convertSourceCurrency.displayName().getString(), leftPanelX() + 40, top + 116, 96, TEXT_PRIMARY);
		drawTrimmedString(guiGraphics, formatTickerAmount(convertSourceCurrency, balances[convertSourceCurrency.ordinal()]), leftPanelX() + 16, top + 142, 120, TEXT_ACCENT);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.convert.to").getString(), centerPanelX() + 16, top + 108, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, convertTargetCurrency.displayName().getString(), centerPanelX() + 40, top + 116, 120, TEXT_PRIMARY);

		long previewAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, convertSlider.currentValue(), exchangeSnapshot, RoundingMode.HALF_UP);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.convert.preview").getString(), centerPanelX() + 16, top + 142, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, formatTickerAmount(convertTargetCurrency, previewAmount), centerPanelX() + 16, top + 162, 140, TEXT_ACCENT);
		long rateAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, convertSourceCurrency.wholeUnitMinor(), exchangeSnapshot, RoundingMode.DOWN);
		drawTrimmedString(guiGraphics, "1 " + convertSourceCurrency.ticker() + " = " + convertTargetCurrency.formatCompact(rateAmount) + " " + convertTargetCurrency.ticker(), centerPanelX() + 16, top + 188, 140, TEXT_MUTED);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.convert.auto").getString(), rightPanelX(), top + 104, 102, TEXT_SECONDARY);
	}

	private void renderShopPage(GuiGraphics guiGraphics, int left, int top) {
		for (int index = 0; index < VISIBLE_OFFERS; index++) {
			int offerIndex = offerScroll + index;
			if (offerIndex >= offers.size()) {
				break;
			}
			ShopOffer offer = offers.get(offerIndex);
			int rowY = top + 110 + index * 30;
			guiGraphics.fill(leftPanelX() + 8, rowY, leftPanelX() + 148, rowY + 26, offerIndex == selectedOfferIndex ? PANEL_ACTIVE : PANEL_ALT);
			guiGraphics.renderItem(offer.createStack(1), leftPanelX() + 14, rowY + 5);
			drawTrimmedString(guiGraphics, offer.displayName().getString(), leftPanelX() + 36, rowY + 5, 104, TEXT_PRIMARY);
			drawTrimmedString(guiGraphics, CryptoCurrency.USDT.formatCompact(offer.priceUsdtMinor()) + " / " + CryptoCurrency.USDT.formatCompact(offer.sellUsdtMinor()) + " USDT", leftPanelX() + 36, rowY + 15, 104, TEXT_MUTED);
		}

		ShopOffer offer = selectedOffer();
		if (offer == null) {
			drawTrimmedString(guiGraphics, "No offers loaded", centerPanelX() + 16, top + 116, 140, TEXT_MUTED);
			return;
		}
		guiGraphics.renderItem(offer.createStack(1), centerPanelX() + 16, top + 116);
		drawTrimmedString(guiGraphics, offer.displayName().getString(), centerPanelX() + 40, top + 116, 118, TEXT_PRIMARY);
		drawTrimmedString(guiGraphics, "Base: " + CryptoCurrency.USDT.formatFixed(offer.priceUsdtMinor()) + " USDT", centerPanelX() + 16, top + 142, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, "Sell: " + CryptoCurrency.USDT.formatFixed(offer.sellUsdtMinor()) + " USDT", centerPanelX() + 16, top + 162, 140, TEXT_SECONDARY);
		long perUnitMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
		long payoutMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.sellUsdtMinor(), exchangeSnapshot, RoundingMode.DOWN);
		drawTrimmedString(guiGraphics, "Buy: " + formatTickerAmount(shopSettlementCurrency, perUnitMinor), centerPanelX() + 16, top + 190, 140, TEXT_ACCENT);
		drawTrimmedString(guiGraphics, "Sell: " + formatTickerAmount(shopSettlementCurrency, payoutMinor), centerPanelX() + 16, top + 210, 140, TEXT_MUTED);
	}

	private void renderTransferPage(GuiGraphics guiGraphics, int left, int top) {
		guiGraphics.renderItem(transferCurrency.stack(1), leftPanelX() + 16, top + 116);
		drawTrimmedString(guiGraphics, transferCurrency.displayName().getString(), leftPanelX() + 40, top + 116, 102, TEXT_PRIMARY);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.transfer.balance", formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()])).getString(), leftPanelX() + 16, top + 142, 122, TEXT_ACCENT);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.transfer.target").getString(), centerPanelX() + 16, top + 104, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.transfer.quick_label").getString(), centerPanelX() + 16, top + 168, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, "Amount", rightPanelX(), top + 134, 102, TEXT_SECONDARY);
	}

	private void renderMiningPage(GuiGraphics guiGraphics, int left, int top) {
		int vouchers = countHashVouchers();
		long btcMinor = Math.multiplyExact(HashVoucherItem.BTC_PAYOUT_MINOR, Math.max(0, (int) miningSlider.currentValue()));
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, miningSettlementCurrency, btcMinor, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(ModItems.HASH_VOUCHER.toStack(), leftPanelX() + 16, top + 116);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.mining.available", vouchers).getString(), leftPanelX() + 40, top + 116, 104, TEXT_PRIMARY);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.mining.base", CryptoCurrency.BTC.formatCompact(HashVoucherItem.BTC_PAYOUT_MINOR), CryptoCurrency.BTC.ticker()).getString(), leftPanelX() + 16, top + 142, 120, TEXT_MUTED);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.mining.preview").getString(), centerPanelX() + 16, top + 116, 140, TEXT_SECONDARY);
		drawTrimmedString(guiGraphics, formatTickerAmount(miningSettlementCurrency, payoutMinor), centerPanelX() + 16, top + 140, 140, TEXT_ACCENT);
		drawTrimmedString(guiGraphics, Component.translatable("screen.virtual_craft.holographic_shop.mining.hint").getString(), centerPanelX() + 16, top + 168, 140, TEXT_MUTED);
	}

	private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (currentPage == Page.WALLET) {
			for (int index = 0; index < CryptoCurrency.values().length; index++) {
				int rowX = leftPanelX() + 8;
				int rowY = topPos + 94 + index * 30;
				if (inside(mouseX, mouseY, rowX, rowY, 138, 26)) {
					CryptoCurrency currency = CryptoCurrency.values()[index];
					long usdtValue = CurrencyConversionService.convert(currency, CryptoCurrency.USDT, balances[index], exchangeSnapshot, RoundingMode.DOWN);
					guiGraphics.renderComponentTooltip(font, List.of(
							currency.displayName(),
							Component.literal(formatTickerAmount(currency, balances[index])),
							Component.literal("~" + CryptoCurrency.USDT.formatFixed(usdtValue) + " USDT")), mouseX, mouseY);
					return;
				}
			}
		}

		if (currentPage == Page.SHOP) {
			for (int index = 0; index < VISIBLE_OFFERS; index++) {
				int offerIndex = offerScroll + index;
				if (offerIndex >= offers.size()) {
					break;
				}
				int rowX = leftPanelX() + 8;
				int rowY = topPos + 94 + index * 30;
				if (inside(mouseX, mouseY, rowX, rowY, 138, 26)) {
					ShopOffer offer = offers.get(offerIndex);
					guiGraphics.renderComponentTooltip(font, List.of(
							offer.displayName(),
							Component.literal("Buy " + CryptoCurrency.USDT.formatFixed(offer.priceUsdtMinor()) + " USDT"),
							Component.literal("Sell " + CryptoCurrency.USDT.formatFixed(offer.sellUsdtMinor()) + " USDT")), mouseX, mouseY);
					return;
				}
			}
		}
	}

	private void refreshDynamicState() {
		if (walletTab == null) {
			return;
		}
		walletTab.active = currentPage != Page.WALLET;
		convertTab.active = currentPage != Page.CONVERT;
		shopTab.active = currentPage != Page.SHOP;
		transferTab.active = currentPage != Page.TRANSFER;
		depositSelectedButton.setMessage(Component.translatable("screen.virtual_craft.holographic_shop.deposit_current", Component.literal(selectedWalletCurrency.ticker())));
		convertSourceButton.setMessage(Component.literal(convertSourceCurrency.ticker()));
		convertTargetButton.setMessage(Component.literal(convertTargetCurrency.ticker()));
		shopSettlementButton.setMessage(Component.translatable("screen.virtual_craft.holographic_shop.shop_currency", shopSettlementCurrency.ticker()));
		transferCurrencyButton.setMessage(Component.literal(transferCurrency.ticker()));

		long walletMax = wholeMinorBalance(selectedWalletCurrency);
		long walletCurrent = walletSlider.currentValue();
		if (walletCurrent <= 0 && walletMax > 0) {
			walletCurrent = Math.min(selectedWalletCurrency.wholeUnitMinor(), walletMax);
		}
		walletSlider.configure(walletMax > 0 ? selectedWalletCurrency.wholeUnitMinor() : 0L, walletMax, selectedWalletCurrency.wholeUnitMinor(), Math.min(walletCurrent, walletMax));
		withdrawItemButton.active = walletMax > 0;
		withdrawEmeraldButton.active = selectedWalletCurrency == CryptoCurrency.USDT && walletMax > 0;

		long convertMax = balances[convertSourceCurrency.ordinal()];
		long convertStep = convertSourceCurrency.scale() <= 2 ? 1L : Math.max(1L, convertSourceCurrency.wholeUnitMinor() / 100_000L);
		long convertCurrent = convertSlider.currentValue();
		if (convertCurrent <= 0 && convertMax > 0) {
			convertCurrent = Math.min(convertSourceCurrency.wholeUnitMinor(), convertMax);
		}
		convertSlider.configure(convertMax > 0 ? 1L : 0L, convertMax, convertStep, Math.min(convertCurrent, convertMax));
		convertSubmitButton.active = convertSourceCurrency != convertTargetCurrency && convertSlider.currentValue() > 0;

		long shopMax = maxShopQuantity();
		long shopCurrent = shopSlider.currentValue();
		if (shopCurrent <= 0 && shopMax > 0) {
			shopCurrent = 1L;
		}
		shopSlider.configure(shopMax > 0 ? 1L : 0L, shopMax, 1L, Math.min(shopCurrent, shopMax));
		buyButton.active = currentShopBuyMax() > 0 && selectedOffer() != null;
		sellButton.active = currentShopSellMax() > 0 && selectedOffer() != null;
		previousOffersButton.active = offerScroll > 0;
		nextOffersButton.active = offerScroll + VISIBLE_OFFERS < offers.size();

		long transferMax = balances[transferCurrency.ordinal()];
		long transferStep = transferCurrency.scale() <= 2 ? 1L : Math.max(1L, transferCurrency.wholeUnitMinor() / 100_000L);
		long transferCurrent = transferSlider.currentValue();
		if (transferCurrent <= 0 && transferMax > 0) {
			transferCurrent = Math.min(transferCurrency.wholeUnitMinor(), transferMax);
		}
		transferSlider.configure(transferMax > 0 ? 1L : 0L, transferMax, transferStep, Math.min(transferCurrent, transferMax));
		sendTransferButton.active = transferSlider.currentValue() > 0 && !targetBox.getValue().trim().isBlank();
		refreshPlayerSuggestions();

		boolean walletVisible = currentPage == Page.WALLET;
		depositAllButton.visible = walletVisible;
		depositAllButton.active = walletVisible;
		depositSelectedButton.visible = walletVisible;
		depositSelectedButton.active = walletVisible;
		walletSlider.visible = walletVisible;
		walletSlider.active = walletVisible && walletSlider.active;
		withdrawItemButton.visible = walletVisible;
		withdrawItemButton.active = walletVisible && withdrawItemButton.active;
		withdrawEmeraldButton.visible = walletVisible && selectedWalletCurrency == CryptoCurrency.USDT;
		withdrawEmeraldButton.active = withdrawEmeraldButton.visible && withdrawEmeraldButton.active;

		boolean convertVisible = currentPage == Page.CONVERT;
		convertSourceButton.visible = convertVisible;
		convertSourceButton.active = convertVisible;
		convertTargetButton.visible = convertVisible;
		convertTargetButton.active = convertVisible;
		flipConvertButton.visible = convertVisible;
		flipConvertButton.active = convertVisible;
		convertSlider.visible = convertVisible;
		convertSlider.active = convertVisible && convertSlider.active;
		convertQuarterButton.visible = convertVisible;
		convertQuarterButton.active = convertVisible && convertSlider.active;
		convertHalfButton.visible = convertVisible;
		convertHalfButton.active = convertVisible && convertSlider.active;
		convertMaxButton.visible = convertVisible;
		convertMaxButton.active = convertVisible && convertSlider.active;
		convertSubmitButton.visible = convertVisible;
		convertSubmitButton.active = convertVisible && convertSubmitButton.active;

		boolean shopVisible = currentPage == Page.SHOP;
		shopSettlementButton.visible = shopVisible;
		shopSettlementButton.active = shopVisible;
		shopSlider.visible = shopVisible;
		shopSlider.active = shopVisible && shopSlider.active;
		buyButton.visible = shopVisible;
		buyButton.active = shopVisible && buyButton.active;
		sellButton.visible = shopVisible;
		sellButton.active = shopVisible && sellButton.active;
		previousOffersButton.visible = shopVisible;
		previousOffersButton.active = shopVisible && previousOffersButton.active;
		nextOffersButton.visible = shopVisible;
		nextOffersButton.active = shopVisible && nextOffersButton.active;

		boolean transferVisible = currentPage == Page.TRANSFER;
		targetBox.visible = transferVisible;
		targetBox.setEditable(transferVisible);
		transferCurrencyButton.visible = transferVisible;
		transferCurrencyButton.active = transferVisible;
		transferSlider.visible = transferVisible;
		transferSlider.active = transferVisible && transferSlider.active;
		sendTransferButton.visible = transferVisible;
		sendTransferButton.active = transferVisible && sendTransferButton.active;
		for (Button button : playerSuggestionButtons) {
			button.visible = transferVisible;
			button.active = transferVisible && !button.getMessage().getString().isBlank();
		}
	}

	private void setPage(Page page) {
		currentPage = page;
		refreshDynamicState();
	}

	private void sendShopAction(ShopActionPayload.Action action) {
		if (selectedOffer() == null) {
			return;
		}
		PacketDistributor.sendToServer(new ShopActionPayload(menu.containerId, action, selectedOfferIndex, (int) Math.max(1L, shopSlider.currentValue()), shopSettlementCurrency.id()));
	}

	private void refreshPlayerSuggestions() {
		suggestedPlayers.clear();
		String filter = targetBox.getValue().trim().toLowerCase(Locale.ROOT);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() != null) {
			for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
				String name = info.getProfile().getName();
				if (minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName())) {
					continue;
				}
				if (filter.isBlank() || name.toLowerCase(Locale.ROOT).contains(filter)) {
					suggestedPlayers.add(name);
					if (suggestedPlayers.size() >= playerSuggestionButtons.size()) {
						break;
					}
				}
			}
		}
		for (int index = 0; index < playerSuggestionButtons.size(); index++) {
			Button button = playerSuggestionButtons.get(index);
			button.setMessage(Component.literal(index < suggestedPlayers.size() ? suggestedPlayers.get(index) : ""));
		}
	}

	private ShopOffer selectedOffer() {
		if (selectedOfferIndex < 0 || selectedOfferIndex >= offers.size()) {
			return null;
		}
		return offers.get(selectedOfferIndex);
	}

	private long currentShopBuyMax() {
		ShopOffer offer = selectedOffer();
		if (offer == null || offer.priceUsdtMinor() <= 0) {
			return 0L;
		}
		long settlementCost = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
		if (settlementCost <= 0) {
			return 0L;
		}
		return balances[shopSettlementCurrency.ordinal()] / settlementCost;
	}

	private long currentShopSellMax() {
		ShopOffer offer = selectedOffer();
		if (offer == null || offer.count() <= 0) {
			return 0L;
		}
		return countInventoryItems(offer) / offer.count();
	}

	private long maxShopQuantity() {
		return Math.max(currentShopBuyMax(), currentShopSellMax());
	}

	private int countInventoryItems(ShopOffer offer) {
		int total = 0;
		for (int slot = 0; slot < playerInventory.items.size(); slot++) {
			if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(playerInventory.items.get(slot), offer.createStack(1))) {
				total += playerInventory.items.get(slot).getCount();
			}
		}
		return total;
	}

	private long wholeMinorBalance(CryptoCurrency currency) {
		return balances[currency.ordinal()] / currency.wholeUnitMinor() * currency.wholeUnitMinor();
	}

	private long sliderMinorStep(CryptoCurrency currency) {
		return Math.max(1L, currency.wholeUnitMinor() / 1000L);
	}

	private String compactHeaderValue(long totalUsdtMinor) {
		return CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " " + CryptoCurrency.USDT.ticker();
	}

	private String shortWalletAddress() {
		if (walletAddress == null || walletAddress.isBlank()) {
			return "VC-UNKNOWN";
		}
		if (walletAddress.length() <= 13) {
			return walletAddress;
		}
		return walletAddress.substring(0, 7) + "..." + walletAddress.substring(walletAddress.length() - 4);
	}

	private int statusColor() {
		return switch (exchangeSnapshot.status()) {
			case LIVE -> TEXT_ACCENT;
			case CACHED -> TEXT_SECONDARY;
			case DEFAULT, STALE -> 0xFFC97A3D;
		};
	}

	private int countHashVouchers() {
		int total = 0;
		for (int slot = 0; slot < playerInventory.items.size(); slot++) {
			if (playerInventory.items.get(slot).is(ModItems.HASH_VOUCHER.get())) {
				total += playerInventory.items.get(slot).getCount();
			}
		}
		return total;
	}

	private int leftPanelX() {
		return leftPos + 18;
	}

	private int centerPanelX() {
		return leftPos + 182;
	}

	private int rightPanelX() {
		return leftPos + 360;
	}

	private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
		guiGraphics.fill(x, y, x + width, y + height, BORDER);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
	}

	private void drawTrimmedString(GuiGraphics guiGraphics, String text, int x, int y, int width, int color) {
		guiGraphics.drawString(font, trimToWidth(text, width), x, y, color, false);
	}

	private String trimToWidth(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			String candidate = builder.toString() + text.charAt(index);
			if (font.width(candidate) + ellipsisWidth > maxWidth) {
				break;
			}
			builder.append(text.charAt(index));
		}
		return builder + ellipsis;
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private String formatTickerAmount(CryptoCurrency currency, long minorAmount) {
		return currency.formatCompact(minorAmount) + " " + currency.ticker();
	}

	private String formatLedgerEntry(SyncWalletPayload.Entry entry) {
		String prefix = switch (entry.typeId()) {
			case "deposit" -> "+";
			case "withdraw" -> "-";
			case "swap" -> "swap";
			case "transfer" -> "send";
			case "premium_buy" -> "buy";
			case "premium_sell" -> "sell";
			case "power_link" -> "link";
			default -> entry.typeId();
		};
		return switch (entry.typeId()) {
			case "swap" -> prefix + " " + formatAmount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) + " > " + formatAmount(entry.secondaryCurrencyId(), entry.secondaryAmountMinor());
			case "transfer", "premium_buy", "premium_sell", "power_link" -> prefix + " " + trimToWidth(entry.detail(), 72);
			default -> prefix + " " + formatAmount(entry.primaryCurrencyId(), entry.primaryAmountMinor());
		};
	}

	private String formatAmount(String currencyId, long amountMinor) {
		CryptoCurrency currency = CryptoCurrency.byId(currencyId).orElse(CryptoCurrency.USDT);
		return currency.formatCompact(amountMinor) + " " + currency.ticker();
	}

	private static CryptoCurrency nextCurrency(CryptoCurrency currency) {
		CryptoCurrency[] currencies = CryptoCurrency.values();
		return currencies[(currency.ordinal() + 1) % currencies.length];
	}
}
