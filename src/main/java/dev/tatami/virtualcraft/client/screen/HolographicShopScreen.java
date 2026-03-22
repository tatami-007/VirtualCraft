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
	private CryptoCurrency miningSettlementCurrency = CryptoCurrency.BTC;
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

	public HolographicShopScreen(HolographicShopMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.playerInventory = playerInventory;
		imageWidth = 556;
		imageHeight = 332;
		titleLabelX = 18;
		titleLabelY = 14;
		inventoryLabelY = 10_000;
	}

	@Override
	protected void init() {
		super.init();
		int navX = sidebarX() + 6;
		int navY = topPos + 86;
		walletTab = addRenderableWidget(new FlatButton(navX, navY, 96, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.page.wallet"),
				button -> setPage(Page.WALLET), () -> currentPage == Page.WALLET, MachineUi.CYAN));
		convertTab = addRenderableWidget(new FlatButton(navX, navY + 24, 96, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.page.convert"),
				button -> setPage(Page.CONVERT), () -> currentPage == Page.CONVERT, MachineUi.CYAN));
		shopTab = addRenderableWidget(new FlatButton(navX, navY + 48, 96, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.page.shop"),
				button -> setPage(Page.SHOP), () -> currentPage == Page.SHOP, MachineUi.CYAN));
		transferTab = addRenderableWidget(new FlatButton(navX, navY + 72, 96, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.page.transfer"),
				button -> setPage(Page.TRANSFER), () -> currentPage == Page.TRANSFER, MachineUi.CYAN));
		miningTab = addRenderableWidget(new FlatButton(navX, navY + 96, 96, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.page.mining"),
				button -> setPage(Page.MINING), () -> currentPage == Page.MINING, MachineUi.GREEN));
		refreshRatesButton = addRenderableWidget(new FlatButton(leftPos + imageWidth - 102, topPos + 14, 84, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.refresh"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.REFRESH_RATES, "", "", 0L, ""))));

		int actionX = actionPanelX() + 8;
		depositAllButton = addRenderableWidget(new FlatButton(actionX, topPos + 104, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.deposit_all"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_ALL, "", "", 0L, ""))));
		depositSelectedButton = addRenderableWidget(new FlatButton(actionX, topPos + 130, 118, 20,
				Component.empty(),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.DEPOSIT_SELECTED, selectedWalletCurrency.id(), "", 0L, ""))));
		walletSlider = addRenderableWidget(new AmountSliderWidget(actionX, topPos + 158, 118, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(selectedWalletCurrency, amount))));
		withdrawItemButton = addRenderableWidget(new FlatButton(actionX, topPos + 186, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.withdraw_item"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_ITEM, selectedWalletCurrency.id(), "", walletSlider.currentValue(), ""))));
		withdrawEmeraldButton = addRenderableWidget(new FlatButton(actionX, topPos + 212, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.withdraw_emeralds"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.WITHDRAW_EMERALDS, CryptoCurrency.USDT.id(), "", walletSlider.currentValue(), ""))));

		convertSourceButton = addRenderableWidget(new FlatButton(actionX, topPos + 104, 118, 20, Component.empty(), button -> {
			convertSourceCurrency = nextCurrency(convertSourceCurrency);
			if (convertSourceCurrency == convertTargetCurrency) {
				convertTargetCurrency = nextCurrency(convertTargetCurrency);
			}
			refreshDynamicState();
		}));
		convertTargetButton = addRenderableWidget(new FlatButton(actionX, topPos + 130, 118, 20, Component.empty(), button -> {
			convertTargetCurrency = nextCurrency(convertTargetCurrency);
			if (convertTargetCurrency == convertSourceCurrency) {
				convertSourceCurrency = nextCurrency(convertSourceCurrency);
			}
			refreshDynamicState();
		}));
		flipConvertButton = addRenderableWidget(new FlatButton(actionX, topPos + 156, 118, 20, Component.literal("<>"), button -> {
			CryptoCurrency previousSource = convertSourceCurrency;
			convertSourceCurrency = convertTargetCurrency;
			convertTargetCurrency = previousSource;
			refreshDynamicState();
		}));
		convertSlider = addRenderableWidget(new AmountSliderWidget(actionX, topPos + 184, 118, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(convertSourceCurrency, amount))));
		convertQuarterButton = addRenderableWidget(new FlatButton(actionX, topPos + 212, 36, 18, Component.literal("25%"), button -> convertSlider.setFraction(0.25D)));
		convertHalfButton = addRenderableWidget(new FlatButton(actionX + 41, topPos + 212, 36, 18, Component.literal("50%"), button -> convertSlider.setFraction(0.50D)));
		convertMaxButton = addRenderableWidget(new FlatButton(actionX + 82, topPos + 212, 36, 18, Component.literal("100%"), button -> convertSlider.setFraction(1.0D)));
		convertSubmitButton = addRenderableWidget(new FlatButton(actionX, topPos + 236, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.convert.submit"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.CONVERT, convertSourceCurrency.id(), convertTargetCurrency.id(), convertSlider.currentValue(), ""))));

		shopSettlementButton = addRenderableWidget(new FlatButton(actionX, topPos + 104, 118, 20, Component.empty(), button -> {
			shopSettlementCurrency = nextCurrency(shopSettlementCurrency);
			refreshDynamicState();
		}));
		shopSlider = addRenderableWidget(new AmountSliderWidget(actionX, topPos + 138, 118, 20, 0L, 0L, 1L, 0L, amount ->
				Component.translatable("screen.virtual_craft.holographic_shop.shop_quantity", amount)));
		buyButton = addRenderableWidget(new FlatButton(actionX, topPos + 168, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.buy"),
				button -> sendShopAction(ShopActionPayload.Action.BUY), () -> false, MachineUi.GREEN));
		sellButton = addRenderableWidget(new FlatButton(actionX, topPos + 194, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.sell"),
				button -> sendShopAction(ShopActionPayload.Action.SELL), () -> false, MachineUi.AMBER));
		previousOffersButton = addRenderableWidget(new FlatButton(actionX, topPos + 222, 56, 20, Component.literal("<"), button -> {
			offerScroll = Math.max(0, offerScroll - VISIBLE_OFFERS);
			selectedOfferIndex = offerScroll;
			refreshDynamicState();
		}));
		nextOffersButton = addRenderableWidget(new FlatButton(actionX + 62, topPos + 222, 56, 20, Component.literal(">"), button -> {
			offerScroll = Math.min(Math.max(0, offers.size() - VISIBLE_OFFERS), offerScroll + VISIBLE_OFFERS);
			selectedOfferIndex = offerScroll;
			refreshDynamicState();
		}));

		targetBox = addRenderableWidget(new FlatEditBox(font, contentX() + 16, topPos + 112, 220, 18, Component.translatable("screen.virtual_craft.holographic_shop.transfer.target")));
		targetBox.setMaxLength(32);
		transferCurrencyButton = addRenderableWidget(new FlatButton(actionX, topPos + 104, 118, 20, Component.empty(), button -> {
			transferCurrency = nextCurrency(transferCurrency);
			refreshDynamicState();
		}));
		transferSlider = addRenderableWidget(new AmountSliderWidget(actionX, topPos + 138, 118, 20, 0L, 0L, 1L, 0L, amount ->
				Component.literal(formatTickerAmount(transferCurrency, amount))));
		sendTransferButton = addRenderableWidget(new FlatButton(actionX, topPos + 168, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.transfer.send"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.TRANSFER, transferCurrency.id(), "", transferSlider.currentValue(), targetBox.getValue().trim()))));

		for (int index = 0; index < 4; index++) {
			final int suggestionIndex = index;
			playerSuggestionButtons.add(addRenderableWidget(new FlatButton(contentX() + 16 + (index % 2) * 112, topPos + 144 + (index / 2) * 24, 104, 20, Component.empty(), button -> {
				if (suggestionIndex < suggestedPlayers.size()) {
					targetBox.setValue(suggestedPlayers.get(suggestionIndex));
				}
			})));
		}

		miningCurrencyButton = addRenderableWidget(new FlatButton(actionX, topPos + 104, 118, 20, Component.empty(), button -> {
			miningSettlementCurrency = nextCurrency(miningSettlementCurrency);
			refreshDynamicState();
		}));
		miningSlider = addRenderableWidget(new AmountSliderWidget(actionX, topPos + 138, 118, 20, 0L, 0L, 1L, 0L, amount ->
				Component.translatable("screen.virtual_craft.holographic_shop.mining.quantity", amount)));
		settleMiningButton = addRenderableWidget(new FlatButton(actionX, topPos + 168, 118, 20,
				Component.translatable("screen.virtual_craft.holographic_shop.mining.settle"),
				button -> PacketDistributor.sendToServer(new WalletActionPayload(menu.containerId, WalletActionPayload.Action.SETTLE_MINING, miningSettlementCurrency.id(), "", miningSlider.currentValue(), "")),
				() -> false, MachineUi.GREEN));

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
		MachineUi.drawWindow(guiGraphics, leftPos, topPos, imageWidth, imageHeight);
		MachineUi.drawPanel(guiGraphics, sidebarX(), topPos + 70, 110, 248, false);
		MachineUi.drawPanel(guiGraphics, contentX(), topPos + 70, 252, 248, false);
		MachineUi.drawPanel(guiGraphics, actionPanelX(), topPos + 70, 134, 248, true);
		renderTopBar(guiGraphics);
		renderSidebarSummary(guiGraphics);
		switch (currentPage) {
			case WALLET -> renderWalletPage(guiGraphics);
			case CONVERT -> renderConvertPage(guiGraphics);
			case SHOP -> renderShopPage(guiGraphics);
			case TRANSFER -> renderTransferPage(guiGraphics);
			case MINING -> renderMiningPage(guiGraphics);
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
				int rowY = topPos + 144 + index * 24;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 228, 20)) {
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
				int rowY = topPos + 108 + index * 26;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 228, 22)) {
					selectedOfferIndex = offerIndex;
					refreshDynamicState();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void renderTopBar(GuiGraphics guiGraphics) {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		guiGraphics.drawString(font, title, leftPos + 18, topPos + 16, MachineUi.TEXT_PRIMARY, false);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.total_value", compactHeaderValue(totalUsdtMinor)).getString(), leftPos + 18, topPos + 31, 220, MachineUi.TEXT_SECONDARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.wallet_address", shortWalletAddress()).getString(), leftPos + 244, topPos + 16, 192, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.rate_status", exchangeSnapshot.status().name()).getString(), leftPos + 244, topPos + 31, 120, statusColor());
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.rate_time", TIME_FORMAT.format(Instant.ofEpochSecond(exchangeSnapshot.updatedAtEpochSeconds()))).getString(), leftPos + 372, topPos + 31, 120, MachineUi.TEXT_MUTED);
	}

	private void renderSidebarSummary(GuiGraphics guiGraphics) {
		MachineUi.drawChip(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.sidebar.system"), sidebarX() + 8, topPos + 222, 94, MachineUi.CYAN, MachineUi.TEXT_ACCENT);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.sidebar.portfolio").getString(), sidebarX() + 10, topPos + 246, 88, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, compactHeaderValue(CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot)), sidebarX() + 10, topPos + 258, 88, MachineUi.TEXT_SUCCESS);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.recent_activity").getString(), sidebarX() + 10, topPos + 278, 88, MachineUi.TEXT_SECONDARY);
		for (int index = 0; index < Math.min(3, recentEntries.size()); index++) {
			MachineUi.drawTrimmedString(guiGraphics, font, formatLedgerEntry(recentEntries.get(index)), sidebarX() + 10, topPos + 290 + index * 10, 88, index == 0 ? MachineUi.TEXT_ACCENT : MachineUi.TEXT_MUTED);
		}
	}

	private void renderWalletPage(GuiGraphics guiGraphics) {
		long selectedBalance = balances[selectedWalletCurrency.ordinal()];
		long selectedUsdt = CurrencyConversionService.convert(selectedWalletCurrency, CryptoCurrency.USDT, selectedBalance, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(selectedWalletCurrency.stack(1), contentX() + 16, topPos + 104);
		MachineUi.drawTrimmedString(guiGraphics, font, selectedWalletCurrency.displayName().getString(), contentX() + 40, topPos + 106, 196, MachineUi.TEXT_PRIMARY);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(selectedWalletCurrency, selectedBalance), contentX() + 16, topPos + 124, 220, MachineUi.TEXT_SUCCESS);
		MachineUi.drawTrimmedString(guiGraphics, font, "~" + CryptoCurrency.USDT.formatFixed(selectedUsdt) + " USDT", contentX() + 16, topPos + 136, 220, MachineUi.TEXT_MUTED);

		for (int index = 0; index < CryptoCurrency.values().length; index++) {
			CryptoCurrency currency = CryptoCurrency.values()[index];
			int rowY = topPos + 144 + index * 24;
			guiGraphics.fill(contentX() + 12, rowY, contentX() + 240, rowY + 20, currency == selectedWalletCurrency ? 0xFF1D2B3B : 0xFF131C28);
			guiGraphics.renderItem(currency.stack(1), contentX() + 18, rowY + 2);
			MachineUi.drawTrimmedString(guiGraphics, font, currency.displayName().getString(), contentX() + 40, rowY + 4, 96, MachineUi.TEXT_PRIMARY);
			MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(currency, balances[index]), contentX() + 132, rowY + 4, 100, currency == selectedWalletCurrency ? MachineUi.TEXT_SUCCESS : MachineUi.TEXT_SECONDARY);
		}

		MachineUi.drawWrappedText(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.selected_hint"), contentX() + 16, topPos + 272, 220, MachineUi.TEXT_MUTED, 2);
	}

	private void renderConvertPage(GuiGraphics guiGraphics) {
		guiGraphics.renderItem(convertSourceCurrency.stack(1), contentX() + 16, topPos + 106);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.convert.from").getString(), contentX() + 40, topPos + 104, 180, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, convertSourceCurrency.displayName().getString(), contentX() + 40, topPos + 116, 180, MachineUi.TEXT_PRIMARY);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(convertSourceCurrency, balances[convertSourceCurrency.ordinal()]), contentX() + 16, topPos + 136, 220, MachineUi.TEXT_SUCCESS);

		guiGraphics.renderItem(convertTargetCurrency.stack(1), contentX() + 16, topPos + 166);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.convert.to").getString(), contentX() + 40, topPos + 164, 180, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, convertTargetCurrency.displayName().getString(), contentX() + 40, topPos + 176, 180, MachineUi.TEXT_PRIMARY);

		long previewAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, convertSlider.currentValue(), exchangeSnapshot, RoundingMode.HALF_UP);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.convert.preview").getString(), contentX() + 16, topPos + 214, 220, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(convertTargetCurrency, previewAmount), contentX() + 16, topPos + 228, 220, MachineUi.TEXT_SUCCESS);
		long rateAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, convertSourceCurrency.wholeUnitMinor(), exchangeSnapshot, RoundingMode.DOWN);
		MachineUi.drawWrappedText(guiGraphics, font, Component.literal("1 " + convertSourceCurrency.ticker() + " = " + convertTargetCurrency.formatCompact(rateAmount) + " " + convertTargetCurrency.ticker()), contentX() + 16, topPos + 250, 220, MachineUi.TEXT_MUTED, 2);
	}

	private void renderShopPage(GuiGraphics guiGraphics) {
		for (int index = 0; index < VISIBLE_OFFERS; index++) {
			int offerIndex = offerScroll + index;
			if (offerIndex >= offers.size()) {
				break;
			}
			ShopOffer offer = offers.get(offerIndex);
			int rowY = topPos + 108 + index * 26;
			guiGraphics.fill(contentX() + 12, rowY, contentX() + 240, rowY + 22, offerIndex == selectedOfferIndex ? 0xFF1D2B3B : 0xFF131C28);
			guiGraphics.renderItem(offer.createStack(1), contentX() + 18, rowY + 3);
			MachineUi.drawTrimmedString(guiGraphics, font, offer.displayName().getString(), contentX() + 40, rowY + 4, 120, MachineUi.TEXT_PRIMARY);
			MachineUi.drawTrimmedString(guiGraphics, font, CryptoCurrency.USDT.formatCompact(offer.priceUsdtMinor()) + " / " + CryptoCurrency.USDT.formatCompact(offer.sellUsdtMinor()) + " USDT", contentX() + 40, rowY + 13, 188, MachineUi.TEXT_MUTED);
		}

		ShopOffer offer = selectedOffer();
		if (offer == null) {
			MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.shop.empty").getString(), contentX() + 16, topPos + 250, 220, MachineUi.TEXT_WARNING);
			return;
		}
		long perUnitMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
		long payoutMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.sellUsdtMinor(), exchangeSnapshot, RoundingMode.DOWN);
		MachineUi.drawTrimmedString(guiGraphics, font, offer.displayName().getString(), contentX() + 16, topPos + 246, 220, MachineUi.TEXT_PRIMARY);
		MachineUi.drawTrimmedString(guiGraphics, font, "Buy: " + formatTickerAmount(shopSettlementCurrency, perUnitMinor), contentX() + 16, topPos + 260, 220, MachineUi.TEXT_SUCCESS);
		MachineUi.drawTrimmedString(guiGraphics, font, "Sell: " + formatTickerAmount(shopSettlementCurrency, payoutMinor), contentX() + 16, topPos + 274, 220, MachineUi.TEXT_SECONDARY);
	}

	private void renderTransferPage(GuiGraphics guiGraphics) {
		guiGraphics.renderItem(transferCurrency.stack(1), contentX() + 16, topPos + 104);
		MachineUi.drawTrimmedString(guiGraphics, font, transferCurrency.displayName().getString(), contentX() + 40, topPos + 106, 196, MachineUi.TEXT_PRIMARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.transfer.balance", formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()])).getString(), contentX() + 16, topPos + 128, 220, MachineUi.TEXT_SUCCESS);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.transfer.target").getString(), contentX() + 16, topPos + 96, 220, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.transfer.quick_label").getString(), contentX() + 16, topPos + 144, 220, MachineUi.TEXT_SECONDARY);
	}

	private void renderMiningPage(GuiGraphics guiGraphics) {
		int vouchers = countHashVouchers();
		long btcMinor = Math.multiplyExact(HashVoucherItem.BTC_PAYOUT_MINOR, Math.max(0, (int) miningSlider.currentValue()));
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, miningSettlementCurrency, btcMinor, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(ModItems.HASH_VOUCHER.toStack(), contentX() + 16, topPos + 106);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.available", vouchers).getString(), contentX() + 40, topPos + 108, 196, MachineUi.TEXT_PRIMARY);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.base", CryptoCurrency.BTC.formatCompact(HashVoucherItem.BTC_PAYOUT_MINOR), CryptoCurrency.BTC.ticker()).getString(), contentX() + 16, topPos + 132, 220, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.preview").getString(), contentX() + 16, topPos + 168, 220, MachineUi.TEXT_MUTED);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(miningSettlementCurrency, payoutMinor), contentX() + 16, topPos + 184, 220, MachineUi.TEXT_SUCCESS);
		MachineUi.drawWrappedText(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.hint"), contentX() + 16, topPos + 212, 220, MachineUi.TEXT_MUTED, 3);
	}

	private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (currentPage == Page.WALLET) {
			for (int index = 0; index < CryptoCurrency.values().length; index++) {
				int rowY = topPos + 144 + index * 24;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 228, 20)) {
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
				int rowY = topPos + 108 + index * 26;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 228, 22)) {
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
		miningTab.active = currentPage != Page.MINING;

		depositSelectedButton.setMessage(Component.translatable("screen.virtual_craft.holographic_shop.deposit_current", Component.literal(selectedWalletCurrency.ticker())));
		convertSourceButton.setMessage(Component.literal(convertSourceCurrency.ticker()));
		convertTargetButton.setMessage(Component.literal(convertTargetCurrency.ticker()));
		shopSettlementButton.setMessage(Component.translatable("screen.virtual_craft.holographic_shop.shop_currency", shopSettlementCurrency.ticker()));
		transferCurrencyButton.setMessage(Component.literal(transferCurrency.ticker()));
		miningCurrencyButton.setMessage(Component.literal(miningSettlementCurrency.ticker()));

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

		int vouchers = countHashVouchers();
		long miningCurrent = miningSlider.currentValue();
		if (miningCurrent <= 0 && vouchers > 0) {
			miningCurrent = 1L;
		}
		miningSlider.configure(vouchers > 0 ? 1L : 0L, vouchers, 1L, Math.min(miningCurrent, vouchers));
		settleMiningButton.active = miningSlider.currentValue() > 0;

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

		boolean miningVisible = currentPage == Page.MINING;
		miningCurrencyButton.visible = miningVisible;
		miningCurrencyButton.active = miningVisible;
		miningSlider.visible = miningVisible;
		miningSlider.active = miningVisible && miningSlider.active;
		settleMiningButton.visible = miningVisible;
		settleMiningButton.active = miningVisible && settleMiningButton.active;
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
			case LIVE -> MachineUi.TEXT_SUCCESS;
			case CACHED -> MachineUi.TEXT_SECONDARY;
			case DEFAULT, STALE -> MachineUi.TEXT_WARNING;
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

	private int sidebarX() {
		return leftPos + 18;
	}

	private int contentX() {
		return leftPos + 140;
	}

	private int actionPanelX() {
		return leftPos + 404;
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
			case "mining_settlement" -> "mine";
			default -> entry.typeId();
		};
		return switch (entry.typeId()) {
			case "swap" -> prefix + " " + formatAmount(entry.primaryCurrencyId(), entry.primaryAmountMinor()) + " > " + formatAmount(entry.secondaryCurrencyId(), entry.secondaryAmountMinor());
			case "transfer", "premium_buy", "premium_sell", "power_link", "mining_settlement" -> prefix + " " + MachineUi.trim(font, entry.detail(), 72);
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
