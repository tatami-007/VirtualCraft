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
	private static final int BG = 0xE60B0E13;
	private static final int WINDOW = 0xD1111720;
	private static final int HEADER = 0xD417202A;
	private static final int PANEL = 0xC3151B23;
	private static final int PANEL_SOFT = 0xA712161D;
	private static final int PANEL_ROW = 0x8A10161D;
	private static final int ROW_ACTIVE = 0xD2213244;
	private static final int LINE = 0xAA304052;
	private static final int TEXT = 0xFFF2F6FB;
	private static final int TEXT_SOFT = 0xFFD4DEE7;
	private static final int TEXT_MUTE = 0xFF8FA0B2;
	private static final int CYAN = 0xFF6DDFF6;
	private static final int GREEN = 0xFF78E3B0;
	private static final int AMBER = 0xFFFFC978;
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
		int left = leftPos;
		int top = topPos;
		guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
		guiGraphics.fill(left + 1, top + 1, left + imageWidth - 1, top + imageHeight - 1, WINDOW);
		guiGraphics.fill(left + 1, top + 1, left + imageWidth - 1, top + 50, HEADER);
		drawGlassPanel(guiGraphics, sidebarX(), top + 64, 106, 232, PANEL_SOFT);
		drawGlassPanel(guiGraphics, contentX(), top + 64, 252, 232, PANEL);
		drawGlassPanel(guiGraphics, actionPanelX(), top + 64, 126, 232, PANEL_SOFT);
		guiGraphics.fill(left + 16, top + 56, left + imageWidth - 16, top + 57, LINE);
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
		guiGraphics.drawString(font, title, leftPos + 18, topPos + 16, TEXT, false);
		drawChip(guiGraphics, leftPos + 18, topPos + 30, 124, compactHeaderValue(totalUsdtMinor), GREEN, TEXT);
		drawChip(guiGraphics, leftPos + 148, topPos + 30, 112, exchangeSnapshot.status().name(), statusColor(), TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, shortWalletAddress(), leftPos + 272, topPos + 34, 160, TEXT_SOFT);
		MachineUi.drawTrimmedString(guiGraphics, font, TIME_FORMAT.format(Instant.ofEpochSecond(exchangeSnapshot.updatedAtEpochSeconds())), leftPos + 438, topPos + 34, 80, TEXT_MUTE);
	}

	private void renderSidebarSummary(GuiGraphics guiGraphics) {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.sidebar.portfolio").getString(), sidebarX() + 12, topPos + 196, 84, TEXT_MUTE);
		MachineUi.drawTrimmedString(guiGraphics, font, CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT", sidebarX() + 12, topPos + 208, 84, GREEN);
		CryptoCurrency summaryCurrency = switch (currentPage) {
			case CONVERT -> convertSourceCurrency;
			case SHOP -> shopSettlementCurrency;
			case TRANSFER -> transferCurrency;
			case MINING -> miningSettlementCurrency;
			default -> selectedWalletCurrency;
		};
		guiGraphics.renderItem(summaryCurrency.stack(1), sidebarX() + 12, topPos + 226);
		MachineUi.drawTrimmedString(guiGraphics, font, summaryCurrency.ticker(), sidebarX() + 34, topPos + 228, 60, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(summaryCurrency, balances[summaryCurrency.ordinal()]), sidebarX() + 12, topPos + 244, 84, TEXT_SOFT);
		if (!recentEntries.isEmpty()) {
			MachineUi.drawTrimmedString(guiGraphics, font, formatLedgerEntry(recentEntries.get(0)), sidebarX() + 12, topPos + 264, 84, TEXT_MUTE);
		}
	}

	private void renderWalletPage(GuiGraphics guiGraphics) {
		long selectedBalance = balances[selectedWalletCurrency.ordinal()];
		long selectedUsdt = CurrencyConversionService.convert(selectedWalletCurrency, CryptoCurrency.USDT, selectedBalance, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(selectedWalletCurrency.stack(1), contentX() + 16, topPos + 92);
		MachineUi.drawTrimmedString(guiGraphics, font, selectedWalletCurrency.displayName().getString(), contentX() + 40, topPos + 94, 196, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(selectedWalletCurrency, selectedBalance), contentX() + 16, topPos + 114, 220, GREEN);
		MachineUi.drawTrimmedString(guiGraphics, font, "~" + CryptoCurrency.USDT.formatFixed(selectedUsdt) + " USDT", contentX() + 16, topPos + 126, 220, TEXT_MUTE);

		for (int index = 0; index < CryptoCurrency.values().length; index++) {
			CryptoCurrency currency = CryptoCurrency.values()[index];
			int rowY = topPos + 136 + index * 24;
			guiGraphics.fill(contentX() + 12, rowY, contentX() + 242, rowY + 20, currency == selectedWalletCurrency ? ROW_ACTIVE : PANEL_ROW);
			guiGraphics.renderItem(currency.stack(1), contentX() + 18, rowY + 2);
			MachineUi.drawTrimmedString(guiGraphics, font, currency.ticker(), contentX() + 40, rowY + 6, 44, TEXT);
			MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(currency, balances[index]), contentX() + 92, rowY + 6, 146, currency == selectedWalletCurrency ? GREEN : TEXT_SOFT);
		}
		if (!recentEntries.isEmpty()) {
			int activityY = topPos + 136 + CryptoCurrency.values().length * 24 + 6;
			MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.recent_activity").getString(), contentX() + 16, activityY, 220, TEXT_MUTE);
			for (int i = 0; i < Math.min(4, recentEntries.size()); i++) {
				MachineUi.drawTrimmedString(guiGraphics, font, formatLedgerEntry(recentEntries.get(i)), contentX() + 16, activityY + 12 + i * 10, 220, TEXT_MUTE);
			}
		}
	}

	private void renderConvertPage(GuiGraphics guiGraphics) {
		drawSection(guiGraphics, contentX() + 12, topPos + 92, 230, 56, Component.translatable("screen.virtual_craft.holographic_shop.convert.from"), convertSourceCurrency, formatTickerAmount(convertSourceCurrency, balances[convertSourceCurrency.ordinal()]));
		drawSection(guiGraphics, contentX() + 12, topPos + 160, 230, 56, Component.translatable("screen.virtual_craft.holographic_shop.convert.to"), convertTargetCurrency, convertTargetCurrency.displayName().getString());

		long oneSourceMinor = convertSourceCurrency.wholeUnitMinor();
		long rateTargetMinor = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, oneSourceMinor, exchangeSnapshot, RoundingMode.HALF_UP);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.convert.rate", convertSourceCurrency.ticker(), convertTargetCurrency.formatCompact(rateTargetMinor), convertTargetCurrency.ticker()).getString(), contentX() + 16, topPos + 222, 220, TEXT_MUTE);

		long previewAmount = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, convertSlider.currentValue(), exchangeSnapshot, RoundingMode.HALF_UP);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.convert.preview").getString(), contentX() + 16, topPos + 236, 220, TEXT_MUTE);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(convertTargetCurrency, previewAmount), contentX() + 16, topPos + 250, 220, GREEN);
	}

	private void renderShopPage(GuiGraphics guiGraphics) {
		for (int index = 0; index < VISIBLE_OFFERS; index++) {
			int offerIndex = offerScroll + index;
			if (offerIndex >= offers.size()) {
				break;
			}
			ShopOffer offer = offers.get(offerIndex);
			int rowY = topPos + 100 + index * 26;
			guiGraphics.fill(contentX() + 12, rowY, contentX() + 242, rowY + 22, offerIndex == selectedOfferIndex ? ROW_ACTIVE : PANEL_ROW);
			guiGraphics.renderItem(offer.createStack(1), contentX() + 18, rowY + 3);
			MachineUi.drawTrimmedString(guiGraphics, font, offer.displayName().getString(), contentX() + 40, rowY + 5, 118, TEXT);
			MachineUi.drawTrimmedString(guiGraphics, font, CryptoCurrency.USDT.formatCompact(offer.priceUsdtMinor()) + " / " + CryptoCurrency.USDT.formatCompact(offer.sellUsdtMinor()), contentX() + 160, rowY + 5, 78, TEXT_MUTE);
		}

		ShopOffer offer = selectedOffer();
		if (offer == null) {
			MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.shop.empty").getString(), contentX() + 16, topPos + 240, 220, AMBER);
			return;
		}
		long perUnitMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
		long payoutMinor = CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.sellUsdtMinor(), exchangeSnapshot, RoundingMode.DOWN);
		MachineUi.drawTrimmedString(guiGraphics, font, offer.displayName().getString(), contentX() + 16, topPos + 238, 220, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, "Buy " + formatTickerAmount(shopSettlementCurrency, perUnitMinor), contentX() + 16, topPos + 252, 220, GREEN);
		MachineUi.drawTrimmedString(guiGraphics, font, "Sell " + formatTickerAmount(shopSettlementCurrency, payoutMinor), contentX() + 16, topPos + 266, 220, TEXT_SOFT);
	}

	private void renderTransferPage(GuiGraphics guiGraphics) {
		guiGraphics.renderItem(transferCurrency.stack(1), contentX() + 16, topPos + 92);
		MachineUi.drawTrimmedString(guiGraphics, font, transferCurrency.displayName().getString(), contentX() + 40, topPos + 94, 196, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()]), contentX() + 16, topPos + 114, 220, GREEN);
	}

	private void renderMiningPage(GuiGraphics guiGraphics) {
		int vouchers = countHashVouchers();
		long btcMinor = Math.multiplyExact(HashVoucherItem.BTC_PAYOUT_MINOR, Math.max(0, (int) miningSlider.currentValue()));
		long payoutMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, miningSettlementCurrency, btcMinor, exchangeSnapshot, RoundingMode.DOWN);
		guiGraphics.renderItem(ModItems.HASH_VOUCHER.toStack(), contentX() + 16, topPos + 92);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.available", vouchers).getString(), contentX() + 40, topPos + 94, 196, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.base", CryptoCurrency.BTC.formatCompact(HashVoucherItem.BTC_PAYOUT_MINOR), CryptoCurrency.BTC.ticker()).getString(), contentX() + 16, topPos + 118, 220, TEXT_MUTE);
		MachineUi.drawTrimmedString(guiGraphics, font, Component.translatable("screen.virtual_craft.holographic_shop.mining.preview").getString(), contentX() + 16, topPos + 154, 220, TEXT_MUTE);
		MachineUi.drawTrimmedString(guiGraphics, font, formatTickerAmount(miningSettlementCurrency, payoutMinor), contentX() + 16, topPos + 168, 220, GREEN);
	}

	private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (currentPage == Page.WALLET) {
			for (int index = 0; index < CryptoCurrency.values().length; index++) {
				int rowY = topPos + 136 + index * 24;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 230, 20)) {
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
				int rowY = topPos + 100 + index * 26;
				if (inside(mouseX, mouseY, contentX() + 12, rowY, 230, 22)) {
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
		withdrawEmeraldButton.active = withdrawEmeraldButton.visible;

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
		return leftPos + 132;
	}

	private int actionPanelX() {
		return leftPos + 408;
	}

	private void drawGlassPanel(GuiGraphics guiGraphics, int x, int y, int width, int height, int fill) {
		guiGraphics.fill(x, y, x + width, y + height, 0xAA222D38);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, LINE);
	}

	private void drawChip(GuiGraphics guiGraphics, int x, int y, int width, String text, int accent, int color) {
		guiGraphics.fill(x, y, x + width, y + 16, 0xAA11161D);
		guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 15, 0x99161B23);
		guiGraphics.fill(x + 1, y + 1, x + 4, y + 15, accent);
		MachineUi.drawTrimmedString(guiGraphics, font, text, x + 8, y + 4, width - 10, color);
	}

	private void drawSection(GuiGraphics guiGraphics, int x, int y, int width, int height, Component label, CryptoCurrency currency, String value) {
		guiGraphics.fill(x, y, x + width, y + height, PANEL_ROW);
		guiGraphics.renderItem(currency.stack(1), x + 10, y + 18);
		MachineUi.drawTrimmedString(guiGraphics, font, label.getString(), x + 34, y + 10, width - 44, TEXT_MUTE);
		MachineUi.drawTrimmedString(guiGraphics, font, currency.displayName().getString(), x + 34, y + 22, width - 44, TEXT);
		MachineUi.drawTrimmedString(guiGraphics, font, value, x + 10, y + 42, width - 20, GREEN);
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
