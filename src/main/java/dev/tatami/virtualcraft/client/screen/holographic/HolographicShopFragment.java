package dev.tatami.virtualcraft.client.screen.holographic;

import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.ACCENT_LINE;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.AMBER;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.CYAN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.GREEN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.HEADER_REFRESH_WIDTH_DP;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.ICON_LARGE_DP;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.ICON_SMALL_DP;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.MAX_ACTIVITY_ROWS;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.OVERLAY;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ACTIVE;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ALT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_BUTTON;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_PICKER;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ROW;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_SOFT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PICKER_SCRIM;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.QUICK_BUTTON_HEIGHT_DP;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT_DIM;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT_SOFT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TIME_FORMAT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.WINDOW;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.WINDOW_STROKE;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import dev.tatami.virtualcraft.client.screen.holographic.HoloScrollView;
import dev.tatami.virtualcraft.client.screen.holographic.HoloSeekBar;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicIconCache;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.CurrencyConversionService;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.network.CloseHolographicTerminalPayload;
import dev.tatami.virtualcraft.network.ShopActionPayload;
import dev.tatami.virtualcraft.network.SyncShopCatalogPayload;
import dev.tatami.virtualcraft.network.SyncWalletPayload;
import dev.tatami.virtualcraft.network.WalletActionPayload;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.shop.ShopOffer;
import dev.tatami.virtualcraft.wallet.WalletEconomyRules;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.SeekBar;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicShopFragment extends Fragment implements ScreenCallback {
	private static final int AMOUNT_SLIDER_STEPS = 1000;

	/**
	 * 服务端 {@code HolographicTerminalSession} 分配的会话 ID。所有出站 ActionPayload 的
	 * {@code menuId} 字段（wire schema 没改名，语义已改）都用它填充——服务端用同一个值
	 * 做 {@code session.isOpen(player, menuId)} 校验，自然过滤掉过期或伪造的请求。
	 * 旧实现里这个值是 {@code sessionId}；现在与 vanilla menu 系统完全脱钩。
	 */
	private final int sessionId;
	/** {@code true}=Holographic Terminal 物品打开，{@code false}=Holographic Shop 方块打开。 */
	private final boolean itemMode;
	/** 方块版终端的方块坐标；物品模式下为 {@link BlockPos#ZERO}。后续若 GUI 内要显示 "在 X/Y/Z 的全息商店" 等信息可用。 */
	private final BlockPos blockPos;
	private final long[] balances = new long[CryptoCurrency.values().length];
	private final List<ShopOffer> offers = new ArrayList<>();
	private final List<SyncWalletPayload.Entry> recentEntries = new ArrayList<>();
	private final List<String> suggestedPlayers = new ArrayList<>();

	// --- Ledger browser cache (populated by SyncWalletLedgerPagePayload) ---
	private static final int LEDGER_PAGE_SIZE = 10;
	private final List<SyncWalletPayload.Entry> ledgerPageEntries = new ArrayList<>();
	private final List<String> ledgerTypeFilters = new ArrayList<>();
	private final List<String> ledgerCurrencyFilters = new ArrayList<>();
	private int ledgerPageOffset;
	private int ledgerPageTotal;
	private boolean walletLedgerBrowserOpen;
	private String ledgerTypeFilterId = "";
	private String ledgerCurrencyFilterId = "";

	// --- Persisted preference cache (populated by SyncWalletPreferencesPayload) ---
	private final List<dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.Contact> recentContacts = new ArrayList<>();
	private final java.util.LinkedHashSet<String> favoriteOfferIds = new java.util.LinkedHashSet<>();
	private dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.MiningStatsEntry miningStatsCache = dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.MiningStatsEntry.EMPTY;

	private FrameLayout root;
	private LinearLayout navColumn;
	private LinearLayout contentColumn;
	private LinearLayout actionColumn;
	/**
	 * 商品悬停详情 tooltip 的宿主。贴在 {@link #root} 顶层（FrameLayout），
	 * {@code View.GONE} 默认隐藏，被鼠标 hover 某个 {@code offerRow} 时生成该
	 * offer 的详情卡 ({@link #shopDetailCard}) 作为唯一子 view。不为别的 page
	 * 复用——仅 shop 列表需要，创建一次、复用多次。
	 */
	private FrameLayout shopTooltipOverlay;
	private FrameLayout contentFrame;
	private FrameLayout pickerOverlay;
	private TextView headerValue;
	private TextView headerStatus;
	private TextView headerAddress;
	private TextView headerTime;

	private String walletAddress = "VC-UNKNOWN";
	private ExchangeRateSnapshot exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	private Page currentPage = Page.WALLET;
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
	private String shopSearchText = "";
	private ShopSortMode shopSortMode = ShopSortMode.DEFAULT;
	private WalletActionMode walletActionMode = WalletActionMode.WITHDRAW;
	private ShopTradeMode shopTradeMode = ShopTradeMode.BUY;
	private boolean shopFavoritesOnly;
	private String transferAmountText = "";
	private String transferTargetText = "";
	private String transferMemoText = "";
	private String miningAmountText = "0";

	private EditText walletAmountInput;
	private EditText convertAmountInput;
	private EditText transferAmountInput;
	private EditText transferTargetInput;
	private EditText transferMemoInput;
	private EditText shopSearchInput;
	private EditText miningAmountInput;
	/**
	 * Text overrides scheduled for the next {@link #stashInputs()} pass. Using an explicit
	 * map is more robust than the previous {@code skipNextInputStash} boolean because a
	 * single refresh may intentionally update multiple inputs at once.
	 */
	private final EnumMap<FocusTarget, String> pendingInputOverrides = new EnumMap<>(FocusTarget.class);
	private PickerTarget pickerTarget = PickerTarget.NONE;
	private boolean convertReverseQuote;

	// --- Refresh debounce ---
	// Multiple sync packets (wallet, exchange, preferences, catalog) routinely
	// arrive in the same client tick after openMenu, and each used to drive a
	// full view-tree rebuild via root.post(). Collapsing them into a single
	// pass removes the "flash of old layout" players saw immediately after
	// opening the terminal, and roughly halves the worst-case rebuild cost.
	private boolean refreshScheduled;
	private boolean refreshIncludeHeader;
	private FocusTarget refreshFocusTarget = FocusTarget.NONE;

	public HolographicShopFragment(int sessionId, boolean itemMode, BlockPos blockPos) {
		this.sessionId = sessionId;
		this.itemMode = itemMode;
		this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
		// Pre-fill from the most recent snapshot the client has seen so the
		// very first frame of the terminal already shows real numbers,
		// instead of "0 / VC-UNKNOWN / empty offers" while we wait for the
		// freshly-pushed sync packets to be parsed on the network thread.
		// HolographicShopClientCache is a process-wide singleton populated
		// by applyXxxSync() below, so re-opens within the same session are
		// essentially instantaneous.
		long[] cachedBalances = HolographicShopClientCache.balancesCopy();
		System.arraycopy(cachedBalances, 0, this.balances, 0, Math.min(this.balances.length, cachedBalances.length));
		this.walletAddress = HolographicShopClientCache.walletAddress();
		this.recentEntries.addAll(HolographicShopClientCache.recentEntries());
		this.exchangeSnapshot = HolographicShopClientCache.exchangeSnapshot();
		this.offers.addAll(HolographicShopClientCache.offers());
		dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload cachedPrefs = HolographicShopClientCache.preferences();
		if (cachedPrefs != null) {
			this.recentContacts.addAll(cachedPrefs.recentContacts());
			this.favoriteOfferIds.addAll(cachedPrefs.favoriteOfferIds());
			this.miningStatsCache = cachedPrefs.miningStats();
		}
	}

	public boolean matchesMenu(int menuId) {
		// 名字保留 "matchesMenu" 是为了让 SyncPayload 客户端 hook 不需要改字段名；
		// 实际语义现在是 "该 sync 包是否对应当前打开的会话"。
		return this.sessionId == menuId;
	}

	public static HolographicShopFragment fromCurrentScreen(int menuId) {
		if (Minecraft.getInstance().screen instanceof MuiScreen mui && mui.getFragment() instanceof HolographicShopFragment fragment && fragment.matchesMenu(menuId)) {
			return fragment;
		}
		return null;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, icyllis.modernui.util.DataSet savedInstanceState) {
		root = new FrameLayout(requireContext());
		root.setBackground(rounded(OVERLAY, 0, 0f, 0));
		root.setPadding(dp(18), dp(18), dp(18), dp(18));

		LinearLayout window = vertical();
		// P3: 主窗体切换到 acrylic（底色 + 描边 + 玻璃高光线）
		window.setBackground(HolographicTheme.acrylic(WINDOW, WINDOW_STROKE, 12f));
		window.setPadding(dp(18), dp(18), dp(18), dp(18));
		// 整体加大给中文翻译足够空间：
		// 主窗 dp(744)×dp(560) → dp(880)×dp(620)
		FrameLayout.LayoutParams windowParams = new FrameLayout.LayoutParams(dp(880), dp(620));
		windowParams.gravity = Gravity.CENTER;
		windowParams.setMargins(0, 0, dp(24), 0);
		root.addView(window, windowParams);

		window.addView(buildHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		window.addView(spacer(dp(14)));

		View divider = new View(requireContext());
		divider.setBackground(rounded(ACCENT_LINE, 0, 0f, 0));
		window.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
		window.addView(spacer(dp(14)));

		LinearLayout body = new LinearLayout(requireContext());
		body.setOrientation(LinearLayout.HORIZONTAL);
		window.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		navColumn = vertical();
		navColumn.setPadding(dp(12), dp(12), dp(12), dp(12));
		navColumn.setBackground(HolographicTheme.acrylic(PANEL_ALT, WINDOW_STROKE, 10f));
		// nav 侧栏 dp(126)→dp(146)，给钱包地址 5+…+3 足够一行宽。
		body.addView(navColumn, new LinearLayout.LayoutParams(dp(146), ViewGroup.LayoutParams.MATCH_PARENT));

		contentFrame = new FrameLayout(requireContext());
		contentFrame.setBackground(HolographicTheme.acrylic(PANEL, WINDOW_STROKE, 10f));
		var contentParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		contentParams.setMargins(dp(12), 0, dp(12), 0);
		body.addView(contentFrame, contentParams);

		ScrollView contentScroll = new HoloScrollView(requireContext());
		wireScrollFocus(contentScroll);
		contentColumn = vertical();
		contentColumn.setPadding(dp(14), dp(14), dp(14), dp(14));
		contentScroll.addView(contentColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentFrame.addView(contentScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		pickerOverlay = new FrameLayout(requireContext());
		pickerOverlay.setBackground(rounded(PICKER_SCRIM, 0, 10f, 0));
		pickerOverlay.setVisibility(View.GONE);
		contentFrame.addView(pickerOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		// shopTooltipOverlay：列表 hover 商品时弹出详情。加在 root 顶层 (不是 contentFrame)
		// 可以湢出 contentScroll 的 clip、甚至覆盖 actionShell。默认 GONE。不 setClickable
		// 让鼠标事件穿透到下面的 row——不拦 hover，才能复用 row 自身的 HOVER_EXIT
		// 事件隐藏 tooltip。
		shopTooltipOverlay = new FrameLayout(requireContext());
		shopTooltipOverlay.setVisibility(View.GONE);
		root.addView(shopTooltipOverlay, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		FrameLayout actionShell = new FrameLayout(requireContext());
		actionShell.setBackground(HolographicTheme.acrylic(PANEL_ALT, WINDOW_STROKE, 10f));
		// action 右栏 dp(236)→dp(280)，给 quickAmount 按钮 + help text 多个中文字空间。
		body.addView(actionShell, new LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT));

		ScrollView actionScroll = new HoloScrollView(requireContext());
		wireScrollFocus(actionScroll);
		actionColumn = vertical();
		actionColumn.setPadding(dp(14), dp(14), dp(14), dp(14));
		actionScroll.addView(actionColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionShell.addView(actionScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		refreshHeader();
		refreshPage(FocusTarget.NONE);
		// Note: the server pushes the initial wallet/exchange/preferences/catalog
		// snapshot itself right after openMenu (see HolographicShopSyncDispatcher),
		// so we no longer need a client → server REQUEST_SYNC round-trip here.
		// Removing it eliminates the most visible "blank window for ~RTT" delay
		// that used to be the dominant cost of opening the Holographic Terminal.
		return root;
	}

	@Override
	public void onDestroy() {
		// 通知服务端清理会话，让后续乱序到达的 ActionPayload 被 session.isOpen 拦下来。
		// 用 sendToServer 而非 reply：onDestroy 上下文里没有 IPayloadContext 可用。
		PacketDistributor.sendToServer(new CloseHolographicTerminalPayload(sessionId));
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
		// Persist into the process-wide cache so the next openMenu paints real
		// numbers on the very first frame, before this packet's successor would
		// have arrived.
		HolographicShopClientCache.storeWallet(balances, walletAddress, entries);
		if (currentPage == Page.TRANSFER) {
			// A fresh balance push is a natural point to refresh the online player roster too.
			refreshPlayerSuggestions();
		}
		requestRefresh(true, captureFocusTarget());
	}

	public void applyCatalogSync(List<SyncShopCatalogPayload.Entry> entries) {
		offers.clear();
		for (SyncShopCatalogPayload.Entry entry : entries) {
			ShopOffer offer = entry.toOffer();
			if (offer != null) {
				offers.add(offer);
			}
		}
		selectedOfferIndex = clampInt(selectedOfferIndex, 0, Math.max(0, offers.size() - 1));
		HolographicShopClientCache.storeCatalog(entries);
		requestRefresh(false, captureFocusTarget());
	}

	public void applyExchangeSync(ExchangeRateSnapshot snapshot) {
		exchangeSnapshot = snapshot;
		HolographicShopClientCache.storeExchange(snapshot);
		requestRefresh(true, captureFocusTarget());
	}

	/**
	 * Stub that accepts a filtered / paged ledger slice from the server. M3 B1 will
	 * attach a full ledger browser UI to this callback; for now we keep the payload
	 * plumbing so the network side can already be validated.
	 */
	public void applyLedgerPage(int offset,
			int totalMatching,
			List<SyncWalletPayload.Entry> entries,
			List<String> typeFilters,
			List<String> currencyFilters) {
		ledgerPageOffset = Math.max(0, offset);
		ledgerPageTotal = Math.max(0, totalMatching);
		ledgerPageEntries.clear();
		if (entries != null) {
			ledgerPageEntries.addAll(entries);
		}
		ledgerTypeFilters.clear();
		if (typeFilters != null) {
			ledgerTypeFilters.addAll(typeFilters);
		}
		ledgerCurrencyFilters.clear();
		if (currencyFilters != null) {
			ledgerCurrencyFilters.addAll(currencyFilters);
		}
		if (currentPage == Page.WALLET) {
			requestRefresh(false, captureFocusTarget());
		}
	}

	/**
	 * Stub that receives the player's persistent preferences (favorite offers,
	 * recent transfer contacts, aggregated mining statistics). The data is stored
	 * for later consumption by the M3 feature pages; the existing view is only
	 * refreshed when it would visibly change something.
	 */
	public void applyPreferencesSync(dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload payload) {
		if (payload == null) {
			return;
		}
		recentContacts.clear();
		recentContacts.addAll(payload.recentContacts());
		favoriteOfferIds.clear();
		favoriteOfferIds.addAll(payload.favoriteOfferIds());
		miningStatsCache = payload.miningStats();
		HolographicShopClientCache.storePreferences(payload);
		if (currentPage == Page.SHOP || currentPage == Page.TRANSFER || currentPage == Page.MINING) {
			requestRefresh(false, captureFocusTarget());
		}
	}

	private View buildHeader() {
		LinearLayout header = new LinearLayout(requireContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		LinearLayout titleBlock = vertical();
		TextView title = titleText(t("screen.virtual_craft.holographic_shop"));
		TextView subtitle = bodyText(t("screen.virtual_craft.holographic_shop.sidebar.system"));
		subtitle.setTextColor(TEXT_SOFT);
		titleBlock.addView(title);
		titleBlock.addView(subtitle);
		header.addView(titleBlock, weighted(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		Button refreshButton = actionButton(t("screen.virtual_craft.holographic_shop.refresh"), CYAN, () ->
				PacketDistributor.sendToServer(new WalletActionPayload(sessionId, WalletActionPayload.Action.REFRESH_RATES, "", "", 0L, "")));
		var refreshParams = new LinearLayout.LayoutParams(dp(HEADER_REFRESH_WIDTH_DP), dp(30));
		refreshParams.setMargins(0, 0, dp(10), 0);
		header.addView(refreshButton, refreshParams);

		headerValue = chip();
		header.addView(headerValue, new LinearLayout.LayoutParams(dp(170), dp(30)));

		headerStatus = chip();
		var statusParams = new LinearLayout.LayoutParams(dp(96), dp(30));
		statusParams.setMargins(dp(8), 0, 0, 0);
		header.addView(headerStatus, statusParams);

		LinearLayout meta = vertical();
		meta.setGravity(Gravity.END);
		var metaParams = new LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT);
		metaParams.setMargins(dp(12), 0, 0, 0);
		header.addView(meta, metaParams);

		headerAddress = bodyText("");
		headerAddress.setGravity(Gravity.END);
		// 用户要求：右上角地址不要“省略号”。bodyText 默认 setSingleLine(true)+ellipsize.END，
		// 13 字符 "VC-XXXXXXXXXX" 装不下就裁成 "VC-38...1F6" 。查询 dp(170) meta
		// 宽度 × 14sp 下单行戒是装不下，改为多行 wrap：取消单行限、取消省略号、最多 2 行。
		headerAddress.setSingleLine(false);
		headerAddress.setEllipsize(null);
		headerAddress.setMaxLines(2);
		// A1: clicking the wallet address copies it to the system clipboard so
		// players can easily paste it elsewhere (web wallets, chat, etc.).
		headerAddress.setOnClickListener(v -> {
			if (walletAddress == null || walletAddress.isBlank()) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			mc.keyboardHandler.setClipboard(walletAddress);
			if (mc.player != null) {
				mc.player.displayClientMessage(
						net.minecraft.network.chat.Component.translatable(
								"message.virtual_craft.wallet.address_copied"),
						true);
			}
		});
		headerTime = hintText("");
		headerTime.setGravity(Gravity.END);
		meta.addView(headerAddress);
		meta.addView(headerTime);
		return header;
	}

	private void refreshHeader() {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		headerValue.setText(CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT");
		headerStatus.setText(rateStatusLabel());
		headerStatus.setTextColor(rateStatusColor());
		// 原用 shortWalletAddress() 主动取中间裁出 "VC-38...1F6" 省略号。用户明确要求不要省略号，
		// 改走完整 displayWalletAddress（上文 onCreateView 里 headerAddress 已 setMaxLines(2)+不裁断）。
		headerAddress.setText(displayWalletAddress());
		headerTime.setText(TIME_FORMAT.format(Instant.ofEpochSecond(exchangeSnapshot.updatedAtEpochSeconds())));
	}

	/**
	 * Schedules a deferred rebuild of the view tree. Multiple calls landing
	 * in the same event-loop cycle are collapsed into a single rebuild that
	 * observes the union of their {@code includeHeader} flags and the most
	 * specific (non-NONE) focus target. This avoids the visible "flash of
	 * intermediate layout" when the server pushes wallet / exchange /
	 * preferences / catalog snapshots back-to-back right after openMenu.
	 */
	private void requestRefresh(boolean includeHeader, FocusTarget focusTarget) {
		if (root == null) {
			return;
		}
		refreshIncludeHeader |= includeHeader;
		if (focusTarget != FocusTarget.NONE) {
			// Last explicit focus request wins. Callers that did not care
			// about focus (pass NONE) inherit whichever focus an earlier or
			// later in-flight caller asked for.
			refreshFocusTarget = focusTarget;
		}
		if (refreshScheduled) {
			return;
		}
		refreshScheduled = true;
		root.post(() -> {
			boolean headerWanted = refreshIncludeHeader;
			FocusTarget focus = refreshFocusTarget;
			refreshScheduled = false;
			refreshIncludeHeader = false;
			refreshFocusTarget = FocusTarget.NONE;
			// Fragment may have been detached between scheduling and running
			// this runnable (screen closed mid-tick). Bail silently in that
			// case instead of touching a dead view tree.
			if (root == null) {
				return;
			}
			if (headerWanted) {
				refreshHeader();
			}
			refreshPage(focus);
		});
	}

	private void refreshPage(FocusTarget focusTarget) {
		if (root == null) {
			return;
		}
		stashInputs();
		refreshNav();
		buildContentPage();
		buildActionPage();
		refreshPicker();
		restoreFocus(focusTarget);
	}

	private void refreshNav() {
		navColumn.removeAllViews();
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.wallet"), Page.WALLET), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.convert"), Page.CONVERT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.shop"), Page.SHOP), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.transfer"), Page.TRANSFER), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navButton(t("screen.virtual_craft.holographic_shop.page.mining"), Page.MINING), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(14)));
		navColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.sidebar.portfolio"), null));

		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		LinearLayout portfolioCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		portfolioCard.setPadding(dp(10), dp(10), dp(10), dp(10));
		// 不用 titleText（它硬编码 setMaxLines(1)+setEllipsize(END)，导致 “92590.55 U…”被截）。
		// inline 创建 + setSingleLine(false)/setEllipsize(null)，长数字可 wrap 2 行完整显示。
		TextView totalValue = new TextView(requireContext());
		totalValue.setText(CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT");
		totalValue.setTextSize(15f);
		totalValue.setTextColor(GREEN);
		totalValue.setSingleLine(false);
		totalValue.setEllipsize(null);
		totalValue.setMaxLines(2);
		portfolioCard.addView(totalValue);
		navColumn.addView(portfolioCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		navColumn.addView(spacer(dp(10)));
		LinearLayout metaCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		metaCard.setPadding(dp(10), dp(10), dp(10), dp(10));
		// label 与 value 原本字号仅差 1sp（hintText 12 vs bodyText 13），纵向密集时视觉层次模糊。
		// 把 value 提到 14sp 并在 label-value 之间加 dp(3) spacer，节奏明显更舒服。
		metaCard.addView(hintText(t("screen.virtual_craft.holographic_shop.wallet.address_label")));
		metaCard.addView(spacer(dp(3)));
		// 地址显示策略：sidebar metaCard 位置宽裕，不再用 shortWalletAddress() 的 5+…+3 缩写，
		// 改画完整 13 字符 "VC-XXXXXXXXXX"。允许 wrap 到 2 行（bodyText 默认 setSingleLine 覆盖），
		// 这样字号 14sp 下 dp(126) 带不下也不会用 "…" 截断，而是自然换行。
		// header 区的 headerAddress 仍用 shortWalletAddress（那里空间小，代变试缩写可接受）。
		TextView addressValue = bodyText(displayWalletAddress());
		addressValue.setTextSize(14f);
		addressValue.setSingleLine(false);
		addressValue.setEllipsize(null);
		addressValue.setMaxLines(2);
		metaCard.addView(addressValue);
		metaCard.addView(spacer(dp(8)));
		metaCard.addView(hintText(t("screen.virtual_craft.holographic_shop.rate_status_short")));
		metaCard.addView(spacer(dp(3)));
		TextView rateValue = bodyText(rateStatusLabel());
		rateValue.setTextSize(14f);
		rateValue.setTextColor(rateStatusColor());
		metaCard.addView(rateValue);
		navColumn.addView(metaCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private void buildContentPage() {
		contentColumn.removeAllViews();
		walletAmountInput = null;
		convertAmountInput = null;
		transferAmountInput = null;
		transferTargetInput = null;
		transferMemoInput = null;
		shopSearchInput = null;
		miningAmountInput = null;

		switch (currentPage) {
			case WALLET -> buildWalletContent();
			case CONVERT -> buildConvertContent();
			case SHOP -> buildShopContent();
			case TRANSFER -> buildTransferContent();
			case MINING -> buildMiningContent();
		}
	}

	private void buildActionPage() {
		actionColumn.removeAllViews();
		actionColumn.addView(sectionHeader(actionTitle(), actionSubtitle()));
		actionColumn.addView(spacer(dp(10)));

		switch (currentPage) {
			case WALLET -> buildWalletActions();
			case CONVERT -> buildConvertActions();
			case SHOP -> buildShopActions();
			case TRANSFER -> buildTransferActions();
			case MINING -> buildMiningActions();
		}
	}

	private void refreshPicker() {
		pickerOverlay.removeAllViews();
		if (pickerTarget == PickerTarget.NONE) {
			pickerOverlay.setVisibility(View.GONE);
			return;
		}
		pickerOverlay.setVisibility(View.VISIBLE);
		pickerOverlay.setOnClickListener(v -> {
			pickerTarget = PickerTarget.NONE;
			refreshPicker();
		});
		if (pickerTarget == PickerTarget.TRANSFER_PLAYER) {
			refreshPlayerSuggestions();
			refreshPlayerPicker();
			return;
		}

		LinearLayout pickerCard = card(PANEL_PICKER, CYAN, 10f);
		pickerCard.setOrientation(LinearLayout.VERTICAL);
		pickerCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		FrameLayout.LayoutParams pickerParams = new FrameLayout.LayoutParams(dp(360), dp(420));
		pickerParams.gravity = Gravity.CENTER;
		pickerOverlay.addView(pickerCard, pickerParams);

		pickerCard.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.currency_picker"), pickerPrompt()));
		pickerCard.addView(spacer(dp(10)));

		ScrollView scroll = new HoloScrollView(requireContext());
		wireScrollFocus(scroll);
		LinearLayout list = vertical();
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			list.addView(currencyRow(currency, currency.displayName().getString(),
					formatTickerAmount(currency, balances[currency.ordinal()]),
					t("screen.virtual_craft.holographic_shop.rate_status", rateStatusLabel()),
					currency == pickerCurrentCurrency(),
					() -> applyCurrencySelection(currency)));
			list.addView(spacer(dp(6)));
		}
		scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		pickerCard.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		pickerCard.addView(spacer(dp(10)));
		pickerCard.addView(actionButton(t("screen.virtual_craft.holographic_shop.currency_picker_close"), CYAN, () -> {
			pickerTarget = PickerTarget.NONE;
			refreshPicker();
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildWalletContent() {
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.wallet.assets"), null));
		contentColumn.addView(spacer(dp(10)));

		ScrollView assetScroll = scrollShell();
		LinearLayout assetList = vertical();
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			long balance = balances[currency.ordinal()];
			long usdtMinor = CurrencyConversionService.convert(currency, CryptoCurrency.USDT, balance, exchangeSnapshot, RoundingMode.DOWN);
			assetList.addView(currencyRow(currency, currency.displayName().getString(),
					formatTickerAmount(currency, balance),
					"~" + CryptoCurrency.USDT.formatCompact(usdtMinor) + " USDT",
					currency == selectedWalletCurrency,
					() -> {
						selectedWalletCurrency = currency;
						requestRefresh(false, FocusTarget.NONE);
					}));
			assetList.addView(spacer(dp(6)));
		}
		assetScroll.addView(assetList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(assetScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		contentColumn.addView(spacer(dp(10)));

		LinearLayout activityCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		activityCard.setPadding(dp(12), dp(12), dp(12), dp(12));

		// Header row: title + toggle between the compact recent list and the
		// full pageable / filterable ledger browser.
		LinearLayout headerRow = new LinearLayout(requireContext());
		headerRow.setOrientation(LinearLayout.HORIZONTAL);
		headerRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView headerLabel = bodyText(t(walletLedgerBrowserOpen
				? "screen.virtual_craft.holographic_shop.recent_activity.full"
				: "screen.virtual_craft.holographic_shop.recent_activity"));
		headerRow.addView(headerLabel, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		Button toggle = compactActionButton(
				walletLedgerBrowserOpen
						? t("screen.virtual_craft.holographic_shop.recent_activity.collapse")
						: t("screen.virtual_craft.holographic_shop.recent_activity.browse"),
				() -> {
					walletLedgerBrowserOpen = !walletLedgerBrowserOpen;
					if (walletLedgerBrowserOpen) {
						ledgerPageOffset = 0;
						requestLedgerPage();
					}
					requestRefresh(false, captureFocusTarget());
				});
		// P1: 原 dp(80)×dp(26)  ∙2 dp(96)×dp(28)。
		// v19 用户反馈：dp(28) 高在 textSize=12f 中文下还是会裁字下半，提到 dp(32) 趋于完整。
		headerRow.addView(toggle, new LinearLayout.LayoutParams(dp(96), dp(32)));
		activityCard.addView(headerRow);
		activityCard.addView(spacer(dp(8)));

		if (!walletLedgerBrowserOpen) {
			if (recentEntries.isEmpty()) {
				activityCard.addView(hintText(t("screen.virtual_craft.holographic_shop.activity.empty")));
			} else {
				for (int index = 0; index < Math.min(MAX_ACTIVITY_ROWS, recentEntries.size()); index++) {
					activityCard.addView(ledgerRow(recentEntries.get(index)));
					if (index + 1 < Math.min(MAX_ACTIVITY_ROWS, recentEntries.size())) {
						activityCard.addView(spacer(dp(6)));
					}
				}
			}
		} else {
			activityCard.addView(buildLedgerFilterRow());
			activityCard.addView(spacer(dp(8)));
			if (ledgerPageEntries.isEmpty()) {
				activityCard.addView(hintText(t("screen.virtual_craft.holographic_shop.activity.empty")));
			} else {
				for (int index = 0; index < ledgerPageEntries.size(); index++) {
					activityCard.addView(ledgerRow(ledgerPageEntries.get(index)));
					if (index + 1 < ledgerPageEntries.size()) {
						activityCard.addView(spacer(dp(6)));
					}
				}
			}
			activityCard.addView(spacer(dp(8)));
			activityCard.addView(buildLedgerPageNav());
		}
		contentColumn.addView(activityCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/** Filter selector row (type + currency cyclers) for the ledger browser. */
	private View buildLedgerFilterRow() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		Button typeButton = compactActionButton(
				t("screen.virtual_craft.holographic_shop.activity.filter.type", ledgerTypeFilterLabel()),
				this::cycleLedgerTypeFilter);
		// v19 高度从 dp(26) 提到 dp(32)：compactActionButton 文本 12sp 中文 + padding (6, 6) 需 ≈30 px，
		// dp(26) 下“类型、币种”下半被裁，提到 dp(32) 后上下释出 2 px 富余。
		row.addView(typeButton, weighted(0, dp(32), 1f));
		var currencyParams = weighted(0, dp(32), 1f);
		currencyParams.setMargins(dp(6), 0, 0, 0);
		Button currencyButton = compactActionButton(
				t("screen.virtual_craft.holographic_shop.activity.filter.currency", ledgerCurrencyFilterLabel()),
				this::cycleLedgerCurrencyFilter);
		row.addView(currencyButton, currencyParams);
		return row;
	}

	/** Pagination row: prev / "X of Y" / next. */
	private View buildLedgerPageNav() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		int totalPages = Math.max(1, (ledgerPageTotal + LEDGER_PAGE_SIZE - 1) / LEDGER_PAGE_SIZE);
		int currentPage = (ledgerPageOffset / LEDGER_PAGE_SIZE) + 1;
		Button prev = compactActionButton(t("screen.virtual_craft.holographic_shop.activity.page.prev"), () -> {
			if (ledgerPageOffset >= LEDGER_PAGE_SIZE) {
				ledgerPageOffset -= LEDGER_PAGE_SIZE;
				requestLedgerPage();
			}
		});
		prev.setEnabled(ledgerPageOffset > 0);
		// P1: dp(64)×dp(24) 太窄，提到 dp(72)×dp(28)。
		// v19 用户反馈：dp(28) 下中文仍裁字下半，为与同区 domain“收起/类型/币种”高度一致，提到 dp(32)。
		row.addView(prev, new LinearLayout.LayoutParams(dp(72), dp(32)));

		TextView label = hintText(t("screen.virtual_craft.holographic_shop.activity.page.label", currentPage, totalPages));
		label.setGravity(Gravity.CENTER);
		var labelParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		labelParams.setMargins(dp(6), 0, dp(6), 0);
		row.addView(label, labelParams);

		Button next = compactActionButton(t("screen.virtual_craft.holographic_shop.activity.page.next"), () -> {
			if (ledgerPageOffset + LEDGER_PAGE_SIZE < ledgerPageTotal) {
				ledgerPageOffset += LEDGER_PAGE_SIZE;
				requestLedgerPage();
			}
		});
		next.setEnabled(ledgerPageOffset + LEDGER_PAGE_SIZE < ledgerPageTotal);
		// v19 高度同 prev，dp(28)→dp(32) 保证中文不裁字。
		row.addView(next, new LinearLayout.LayoutParams(dp(72), dp(32)));
		return row;
	}

	private void cycleLedgerTypeFilter() {
		String[] order = {"", "deposit", "withdraw", "swap", "transfer", "premium_buy", "premium_sell", "mining_settlement", "power_link"};
		int idx = 0;
		for (int i = 0; i < order.length; i++) {
			if (order[i].equals(ledgerTypeFilterId)) {
				idx = i;
				break;
			}
		}
		ledgerTypeFilterId = order[(idx + 1) % order.length];
		ledgerPageOffset = 0;
		requestLedgerPage();
	}

	private void cycleLedgerCurrencyFilter() {
		String[] order = {"", "btc", "eth", "usdt", "sol", "trx", "doge"};
		int idx = 0;
		for (int i = 0; i < order.length; i++) {
			if (order[i].equals(ledgerCurrencyFilterId)) {
				idx = i;
				break;
			}
		}
		ledgerCurrencyFilterId = order[(idx + 1) % order.length];
		ledgerPageOffset = 0;
		requestLedgerPage();
	}

	private String ledgerTypeFilterLabel() {
		if (ledgerTypeFilterId == null || ledgerTypeFilterId.isBlank()) {
			return t("screen.virtual_craft.holographic_shop.activity.filter.all");
		}
		return t("screen.virtual_craft.holographic_shop.ledger." + ledgerTypeFilterId);
	}

	private String ledgerCurrencyFilterLabel() {
		if (ledgerCurrencyFilterId == null || ledgerCurrencyFilterId.isBlank()) {
			return t("screen.virtual_craft.holographic_shop.activity.filter.all");
		}
		return ledgerCurrencyFilterId.toUpperCase(java.util.Locale.ROOT);
	}

	private void requestLedgerPage() {
		List<String> typeFilters = ledgerTypeFilterId == null || ledgerTypeFilterId.isBlank()
				? List.of()
				: List.of(ledgerTypeFilterId);
		List<String> currencyFilters = ledgerCurrencyFilterId == null || ledgerCurrencyFilterId.isBlank()
				? List.of()
				: List.of(ledgerCurrencyFilterId);
		PacketDistributor.sendToServer(new dev.tatami.virtualcraft.network.LedgerQueryPayload(
				sessionId,
				ledgerPageOffset,
				LEDGER_PAGE_SIZE,
				typeFilters,
				currencyFilters));
	}

	private void buildWalletActions() {
		long selectedBalance = balances[selectedWalletCurrency.ordinal()];
		actionColumn.addView(infoCard(
				t("screen.virtual_craft.holographic_shop.wallet.selected"),
				selectedWalletCurrency.displayName().getString(),
				formatTickerAmount(selectedWalletCurrency, selectedBalance),
				GREEN));
		actionColumn.addView(spacer(dp(10)));

		actionColumn.addView(modeSwitch(
				t("screen.virtual_craft.holographic_shop.wallet.mode.deposit"),
				walletActionMode == WalletActionMode.DEPOSIT,
				() -> {
					walletActionMode = WalletActionMode.DEPOSIT;
					requestRefresh(false, FocusTarget.NONE);
				},
				t("screen.virtual_craft.holographic_shop.wallet.mode.withdraw"),
				walletActionMode == WalletActionMode.WITHDRAW,
				() -> {
					walletActionMode = WalletActionMode.WITHDRAW;
					requestRefresh(false, FocusTarget.WALLET_AMOUNT);
				}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(10)));

		if (walletActionMode == WalletActionMode.DEPOSIT) {
			long depositable = inventoryWholeItemCount(selectedWalletCurrency);
			long requested = parseWhole(walletAmountText);
			String depositProblem = walletDepositProblem(requested, depositable);
			actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.amount_whole")));
			walletAmountInput = input(walletAmountText, Long.toString(Math.max(0L, depositable)));
			actionColumn.addView(walletAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
			actionColumn.addView(spacer(dp(6)));
			actionColumn.addView(wholeAmountSlider(
					t("screen.virtual_craft.holographic_shop.amount_whole"),
					depositable,
					requested,
					value -> walletAmountText = value,
					walletAmountInput,
					FocusTarget.WALLET_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			actionColumn.addView(spacer(dp(6)));
			actionColumn.addView(compactActionButton(t("screen.virtual_craft.holographic_shop.wallet.max_whole"), () -> {
				walletAmountText = Long.toString(inventoryWholeItemCount(selectedWalletCurrency));
				pendingInputOverrides.put(FocusTarget.WALLET_AMOUNT, walletAmountText);
				requestRefresh(false, FocusTarget.WALLET_AMOUNT);
			}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(QUICK_BUTTON_HEIGHT_DP)));
			actionColumn.addView(spacer(dp(10)));
			actionColumn.addView(statusCard(walletDepositSummary(requested), depositProblem, depositProblem.isBlank() ? GREEN : AMBER));
			actionColumn.addView(spacer(dp(10)));
			actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.deposit_current", selectedWalletCurrency.ticker()), GREEN, this::sendWalletDeposit),
				new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			actionColumn.addView(spacer(dp(6)));
			actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.deposit_all"), CYAN, () ->
					PacketDistributor.sendToServer(new WalletActionPayload(sessionId, WalletActionPayload.Action.DEPOSIT_ALL, "", "", 0L, ""))),
				new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			return;
		}

		long withdrawable = wholeItemCount(selectedWalletCurrency);
		long requested = parseWhole(walletAmountText);
		String withdrawProblem = walletWithdrawProblem(requested, withdrawable);
		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.amount_whole")));
		walletAmountInput = input(walletAmountText, Long.toString(Math.max(0L, withdrawable)));
		actionColumn.addView(walletAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(wholeAmountSlider(
				t("screen.virtual_craft.holographic_shop.amount_whole"),
				withdrawable,
				requested,
				value -> walletAmountText = value,
				walletAmountInput,
				FocusTarget.WALLET_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(compactActionButton(t("screen.virtual_craft.holographic_shop.wallet.max_whole"), () -> {
			walletAmountText = Long.toString(wholeItemCount(selectedWalletCurrency));
			pendingInputOverrides.put(FocusTarget.WALLET_AMOUNT, walletAmountText);
			requestRefresh(false, FocusTarget.WALLET_AMOUNT);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(QUICK_BUTTON_HEIGHT_DP)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(statusCard(walletWithdrawSummary(requested), withdrawProblem, withdrawProblem.isBlank() ? GREEN : AMBER));
		actionColumn.addView(spacer(dp(10)));

		Button withdrawCoin = actionButton(t("screen.virtual_craft.holographic_shop.withdraw_item"), GREEN, () -> sendWalletWithdraw(WalletActionPayload.Action.WITHDRAW_ITEM));
		actionColumn.addView(withdrawCoin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

		if (selectedWalletCurrency == CryptoCurrency.USDT) {
			actionColumn.addView(spacer(dp(6)));
			Button withdrawEmeralds = actionButton(t("screen.virtual_craft.holographic_shop.withdraw_emeralds"), AMBER, () -> sendWalletWithdraw(WalletActionPayload.Action.WITHDRAW_EMERALDS));
			actionColumn.addView(withdrawEmeralds, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
		}
	}

	private void refreshPlayerPicker() {
		LinearLayout pickerCard = card(PANEL_PICKER, CYAN, 10f);
		pickerCard.setOrientation(LinearLayout.VERTICAL);
		pickerCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		FrameLayout.LayoutParams pickerParams = new FrameLayout.LayoutParams(dp(360), dp(420));
		pickerParams.gravity = Gravity.CENTER;
		pickerOverlay.addView(pickerCard, pickerParams);

		pickerCard.addView(sectionHeader(
				t("screen.virtual_craft.holographic_shop.transfer.player_picker"),
				t("screen.virtual_craft.holographic_shop.transfer.target")));
		pickerCard.addView(spacer(dp(10)));

		ScrollView scroll = new HoloScrollView(requireContext());
		wireScrollFocus(scroll);
		LinearLayout list = vertical();
		if (suggestedPlayers.isEmpty()) {
			list.addView(emptyState(
					t("screen.virtual_craft.holographic_shop.transfer.empty_online"),
					t("screen.virtual_craft.holographic_shop.transfer.player_picker_hint")));
		} else {
			for (String playerName : suggestedPlayers) {
				list.addView(playerPickerRow(playerName));
				list.addView(spacer(dp(6)));
			}
		}
		scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		pickerCard.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		pickerCard.addView(spacer(dp(10)));
		pickerCard.addView(actionButton(t("screen.virtual_craft.holographic_shop.currency_picker_close"), CYAN, () -> {
			pickerTarget = PickerTarget.NONE;
			refreshPicker();
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildConvertContent() {
		long sourceAmount = convertSourceAmount();
		long targetAmount = convertTargetAmount();
		LinearLayout tradeRow = new LinearLayout(requireContext());
		tradeRow.setOrientation(LinearLayout.HORIZONTAL);

		View sourceCard = assetSelectorCard(t("screen.virtual_craft.holographic_shop.convert.pick_source"), convertSourceCurrency,
				formatTickerAmount(convertSourceCurrency, balances[convertSourceCurrency.ordinal()]),
				() -> openPicker(PickerTarget.CONVERT_SOURCE));
		tradeRow.addView(sourceCard, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		Button swapButton = actionButton("\u21C5", CYAN, this::swapConvertCurrencies);
		swapButton.setGravity(Gravity.CENTER);
		var swapParams = new LinearLayout.LayoutParams(dp(44), dp(44));
		swapParams.setMargins(dp(8), dp(16), dp(8), 0);
		tradeRow.addView(swapButton, swapParams);

		View targetCard = assetSelectorCard(t("screen.virtual_craft.holographic_shop.convert.pick_target"), convertTargetCurrency,
				formatTickerAmount(convertTargetCurrency, balances[convertTargetCurrency.ordinal()]),
				() -> openPicker(PickerTarget.CONVERT_TARGET));
		tradeRow.addView(targetCard, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.convert.trade"), convertPairLabel()));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(tradeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));

		contentColumn.addView(metricCard(t("screen.virtual_craft.holographic_shop.convert.rate_label"), convertRateText(), CYAN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(8)));
		LinearLayout metrics = new LinearLayout(requireContext());
		metrics.setOrientation(LinearLayout.HORIZONTAL);
		metrics.addView(metricCard(t("screen.virtual_craft.holographic_shop.no_fee"), t("screen.virtual_craft.holographic_shop.convert.fee_free"), GREEN), weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.48f));
		var modeParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.52f);
		modeParams.setMargins(dp(8), 0, 0, 0);
		metrics.addView(metricCard(t("screen.virtual_craft.holographic_shop.convert.mode_label"), convertModeShortLabel(), TEXT_DIM), modeParams);
		contentColumn.addView(metrics, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));

		LinearLayout previewCard = card(PANEL_SOFT, CYAN, 10f);
		previewCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		previewCard.addView(hintText(t("screen.virtual_craft.holographic_shop.convert.preview")));
		TextView previewValue = titleText(formatTickerAmount(convertTargetCurrency, targetAmount));
		previewValue.setTextColor(GREEN);
		previewCard.addView(previewValue);
		previewCard.addView(hintText(convertSummaryLine()));
		// Min-receive guard: even though there's no AMM, rounding + stale rates can
		// cause the realized payout to drift slightly. We surface a 0.5% slippage
		// floor so the user can sanity-check the worst case.
		long minReceive = convertMinimumReceiveAmount(targetAmount);
		previewCard.addView(spacer(dp(4)));
		previewCard.addView(hintText(t("screen.virtual_craft.holographic_shop.convert.min_receive",
				formatTickerAmount(convertTargetCurrency, minReceive),
				formatSlippagePercent())));
		String problem = convertProblem();
		TextView balanceLine = hintText(problem.isBlank() ? t("screen.virtual_craft.holographic_shop.convert.balance_ok") : problem);
		balanceLine.setTextColor(problem.isBlank() ? GREEN : AMBER);
		previewCard.addView(balanceLine);

		// Surface a clear AMBER warning when the rates aren't fresh so the player
		// understands why the preview might not match the realized amount.
		if (exchangeSnapshot.status() == ExchangeRateSnapshot.SourceStatus.STALE
				|| exchangeSnapshot.status() == ExchangeRateSnapshot.SourceStatus.DEFAULT) {
			TextView staleLine = hintText(t("screen.virtual_craft.holographic_shop.convert.rates_stale"));
			staleLine.setTextColor(AMBER);
			previewCard.addView(spacer(dp(4)));
			previewCard.addView(staleLine);
		}
		contentColumn.addView(previewCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/** Default slippage tolerance applied to the min-receive preview, in basis points. */
	private static final int CONVERT_SLIPPAGE_BPS = 50;

	/** Apply {@link #CONVERT_SLIPPAGE_BPS} as a floor against the preview amount. */
	private long convertMinimumReceiveAmount(long targetAmountMinor) {
		if (targetAmountMinor <= 0) {
			return 0L;
		}
		long deduction = (targetAmountMinor * CONVERT_SLIPPAGE_BPS) / 10_000L;
		return Math.max(0L, targetAmountMinor - deduction);
	}

	private String formatSlippagePercent() {
		// 50 bps -> "0.5%"
		java.math.BigDecimal pct = java.math.BigDecimal.valueOf(CONVERT_SLIPPAGE_BPS, 2)
				.stripTrailingZeros();
		if (pct.scale() < 0) {
			pct = pct.setScale(0, RoundingMode.UNNECESSARY);
		}
		return pct.toPlainString() + "%";
	}

	private void buildConvertActions() {
		actionColumn.addView(modeSwitch(
				t("screen.virtual_craft.holographic_shop.convert.source_short", convertSourceCurrency.ticker()),
				!convertReverseQuote,
				() -> {
					convertReverseQuote = false;
					pendingInputOverrides.put(FocusTarget.CONVERT_AMOUNT, convertAmountText);
					requestRefresh(false, FocusTarget.CONVERT_AMOUNT);
				},
				t("screen.virtual_craft.holographic_shop.convert.target_short", convertTargetCurrency.ticker()),
				convertReverseQuote,
				() -> {
					convertReverseQuote = true;
					pendingInputOverrides.put(FocusTarget.CONVERT_AMOUNT, convertAmountText);
					requestRefresh(false, FocusTarget.CONVERT_AMOUNT);
				}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.transfer.amount")));
		convertAmountInput = input(convertAmountText, convertReverseQuote ? convertTargetCurrency.ticker() : convertSourceCurrency.ticker());
		actionColumn.addView(convertAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		CryptoCurrency convertInputCurrency = convertReverseQuote ? convertTargetCurrency : convertSourceCurrency;
		long convertSliderMax = convertReverseQuote
				? CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, balances[convertSourceCurrency.ordinal()], exchangeSnapshot, RoundingMode.DOWN)
				: balances[convertSourceCurrency.ordinal()];
		actionColumn.addView(currencyAmountSlider(
				t("screen.virtual_craft.holographic_shop.transfer.amount"),
				convertInputCurrency,
				convertSliderMax,
				parseAmount(convertInputCurrency, convertAmountText),
				value -> convertAmountText = value,
				convertAmountInput,
				FocusTarget.CONVERT_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quickAmountRow(convertInputCurrency, convertSliderMax, value -> convertAmountText = value, FocusTarget.CONVERT_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(10)));
		String problem = convertProblem();
		actionColumn.addView(statusCard(convertSummaryLine(), problem, problem.isBlank() ? GREEN : AMBER));
		actionColumn.addView(spacer(dp(10)));

		Button submit = actionButton(t("screen.virtual_craft.holographic_shop.convert.submit"), GREEN, this::sendConvertAction);
		actionColumn.addView(submit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildShopContent() {
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.shop.catalog"), null));
		contentColumn.addView(spacer(dp(10)));

		// Search + sort + favorites toolbar.
		LinearLayout toolbar = new LinearLayout(requireContext());
		toolbar.setOrientation(LinearLayout.HORIZONTAL);
		shopSearchInput = input(shopSearchText, t("screen.virtual_craft.holographic_shop.shop.search_hint"));
		toolbar.addView(shopSearchInput, weighted(0, dp(30), 1f));
		Button sortButton = compactActionButton(t("screen.virtual_craft.holographic_shop.shop.sort." + shopSortMode.name().toLowerCase(java.util.Locale.ROOT)), () -> {
			ShopSortMode[] modes = ShopSortMode.values();
			shopSortMode = modes[(shopSortMode.ordinal() + 1) % modes.length];
			requestRefresh(false, FocusTarget.SHOP_SEARCH);
		});
		// 防御 wrap 被裁：compactActionButton 不调 setSingleLine，长文本会换行。固定 dp(30) 高度
		// 时 wrap 出来的第 2 行就会被父 LinearLayout 切掉（用户报告的"按钮被切开"根因）。
		// 现在 lang 已缩短到最多 5 字符（"名称 A→Z"），dp(132) 宽下单行可装；万一字体异常 wrap，
		// LayoutParams 高度用 WRAP_CONTENT + minHeight 兜底，让按钮整体长高而不是裁文字。
		sortButton.setMinimumHeight(dp(28));
		var sortParams = new LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT);
		sortParams.setMargins(dp(6), 0, 0, 0);
		toolbar.addView(sortButton, sortParams);
		Button favButton = compactActionButton(
				shopFavoritesOnly
						? t("screen.virtual_craft.holographic_shop.shop.favorites_on")
						: t("screen.virtual_craft.holographic_shop.shop.favorites_off"),
				() -> {
					shopFavoritesOnly = !shopFavoritesOnly;
					requestRefresh(false, FocusTarget.SHOP_SEARCH);
				});
		// favButton 同样套 WRAP_CONTENT + minHeight 防 wrap 被裁。dp(84) 宽对"★ 仅看"4 字符够用。
		favButton.setMinimumHeight(dp(28));
		var favParams = new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT);
		favParams.setMargins(dp(6), 0, 0, 0);
		toolbar.addView(favButton, favParams);
		contentColumn.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(8)));

		List<IndexedOffer> filteredOffers = filteredShopOffers();
		// Keep selectedOfferIndex pointing at a row that still appears, otherwise
		// snap to the first visible offer so the detail panel never goes empty.
		boolean selectionVisible = filteredOffers.stream().anyMatch(io -> io.originalIndex() == selectedOfferIndex);
		if (!selectionVisible && !filteredOffers.isEmpty()) {
			selectedOfferIndex = filteredOffers.get(0).originalIndex();
		}

		// 详情改为悬停弹出 ({@link #showShopTooltip})、不再占右侧面板，列表占满整宽。
		// 重建列表前先隐藏上一次的 tooltip：row view 被重新创建后，原本指向它的
		// HOVER_EXIT 不会再到达，必须主动 hide 避免 tooltip “慌子在上面”。
		hideShopTooltip();

		ScrollView listScroll = scrollShell();
		LinearLayout list = vertical();
		if (offers.isEmpty()) {
			list.addView(emptyState(t("screen.virtual_craft.holographic_shop.shop.empty"), t("screen.virtual_craft.holographic_shop.shop.waiting")));
		} else if (filteredOffers.isEmpty()) {
			list.addView(emptyState(t("screen.virtual_craft.holographic_shop.shop.no_match"), t("screen.virtual_craft.holographic_shop.shop.no_match_hint")));
		} else {
			for (IndexedOffer indexed : filteredOffers) {
				int originalIndex = indexed.originalIndex();
				ShopOffer offer = indexed.offer();
				list.addView(offerRow(offer, originalIndex == selectedOfferIndex, () -> {
					selectedOfferIndex = originalIndex;
					requestRefresh(false, FocusTarget.NONE);
				}));
				list.addView(spacer(dp(6)));
			}
		}
		listScroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(listScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
	}

	private void buildShopActions() {
		ShopOffer offer = selectedOffer();
		int buyMax = currentShopBuyMax(offer);
		int sellMax = currentShopSellMax(offer);
		actionColumn.addView(currencyActionButton(t("screen.virtual_craft.holographic_shop.shop_currency_short", shopSettlementCurrency.ticker()), shopSettlementCurrency, PickerTarget.SHOP_SETTLEMENT));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(modeSwitch(
				t("screen.virtual_craft.holographic_shop.buy"),
				shopTradeMode == ShopTradeMode.BUY,
				() -> {
					shopTradeMode = ShopTradeMode.BUY;
					requestRefresh(false, FocusTarget.NONE);
				},
				t("screen.virtual_craft.holographic_shop.sell"),
				shopTradeMode == ShopTradeMode.SELL,
				() -> {
					shopTradeMode = ShopTradeMode.SELL;
					requestRefresh(false, FocusTarget.NONE);
				}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(10)));

		int maxQuantity = Math.max(1, maxShopQuantity(buyMax, sellMax));
		int quantity = clampInt(parseInt(shopQuantityText, 1), 1, maxQuantity);
		shopQuantityText = Integer.toString(quantity);

		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.shop_quantity", quantity)));
		actionColumn.addView(quantitySlider(t("screen.virtual_craft.holographic_shop.transfer.amount"), 1, maxQuantity, quantity, value -> {
			shopQuantityText = Integer.toString(value);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(8)));
		actionColumn.addView(quantityControlRow(quantity, maxQuantity, value -> {
			shopQuantityText = Integer.toString(value);
			// shopQuantityText has no EditText counterpart, so no input override is needed.
			requestRefresh(false, FocusTarget.NONE);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(QUICK_BUTTON_HEIGHT_DP)));
		actionColumn.addView(spacer(dp(10)));

		String buyProblem = shopBuyProblem(quantity, offer, buyMax);
		String sellProblem = shopSellProblem(quantity, offer, sellMax);
		boolean buying = shopTradeMode == ShopTradeMode.BUY;
		long buyTotal = shopBuyTotal(quantity, offer);
		long sellTotal = shopSellTotal(quantity, offer);
		String summary = buying
				? t("screen.virtual_craft.holographic_shop.shop.buy_total", quantity, formatTickerAmount(shopSettlementCurrency, buyTotal))
				: t("screen.virtual_craft.holographic_shop.shop.sell_total", quantity, formatTickerAmount(shopSettlementCurrency, sellTotal));
		String issue = buying
				? (buyProblem.isBlank() ? t("screen.virtual_craft.holographic_shop.shop.auto_swap_detail") : buyProblem)
				: sellProblem;
		actionColumn.addView(statusCard(summary, issue, (buying ? buyProblem : sellProblem).isBlank() ? (buying ? CYAN : GREEN) : AMBER));
		actionColumn.addView(spacer(dp(10)));

		Button primary = actionButton(t(buying ? "screen.virtual_craft.holographic_shop.buy" : "screen.virtual_craft.holographic_shop.sell"),
				buying ? GREEN : AMBER,
				() -> {
					int currentQuantity = parseInt(shopQuantityText, 1);
					ShopOffer currentOffer = selectedOffer();
					String problem = shopTradeMode == ShopTradeMode.BUY
							? shopBuyProblem(currentQuantity, currentOffer, currentShopBuyMax(currentOffer))
							: shopSellProblem(currentQuantity, currentOffer, currentShopSellMax(currentOffer));
					if (problem.isBlank()) {
						sendShopAction(shopTradeMode == ShopTradeMode.BUY ? ShopActionPayload.Action.BUY : ShopActionPayload.Action.SELL);
					} else {
						requestRefresh(false, FocusTarget.NONE);
					}
				});
		actionColumn.addView(primary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildTransferContent() {
		// sectionHeader 子标题 lang 是 "余额：%s" 模板，必须传入实际转账币种余额，否则会显示字面 "%s"（用户反馈"文字错误"）。
		contentColumn.addView(sectionHeader(
				t("screen.virtual_craft.holographic_shop.transfer.recipients"),
				t("screen.virtual_craft.holographic_shop.transfer.balance",
						formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()]))));
		contentColumn.addView(spacer(dp(10)));

		LinearLayout targetCard = card(PANEL_SOFT, WINDOW_STROKE, 10f);
		targetCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		targetCard.addView(hintText(t("screen.virtual_craft.holographic_shop.transfer.target")));
		LinearLayout targetRow = new LinearLayout(requireContext());
		targetRow.setOrientation(LinearLayout.HORIZONTAL);
		transferTargetInput = input(transferTargetText, t("screen.virtual_craft.holographic_shop.transfer.target"));
		targetRow.addView(transferTargetInput, weighted(0, dp(34), 1f));
		var chooseParams = new LinearLayout.LayoutParams(dp(64), dp(34));
		chooseParams.setMargins(dp(8), 0, 0, 0);
		targetRow.addView(actionButton(t("screen.virtual_craft.holographic_shop.transfer.choose"), CYAN, () -> {
			if (transferTargetInput != null) {
				transferTargetText = transferTargetInput.getText().toString();
			}
			refreshPlayerSuggestions();
			openPicker(PickerTarget.TRANSFER_PLAYER);
		}), chooseParams);
		var pasteParams = new LinearLayout.LayoutParams(dp(64), dp(34));
		pasteParams.setMargins(dp(6), 0, 0, 0);
		targetRow.addView(actionButton(t("screen.virtual_craft.holographic_shop.transfer.paste"), CYAN, () -> {
			transferTargetText = readClipboard();
			pendingInputOverrides.put(FocusTarget.TRANSFER_TARGET, transferTargetText);
			refreshPlayerSuggestions();
			requestRefresh(false, FocusTarget.TRANSFER_TARGET);
		}), pasteParams);
		targetCard.addView(spacer(dp(6)));
		targetCard.addView(targetRow);
		targetCard.addView(spacer(dp(10)));
		if (!recentContacts.isEmpty()) {
			targetCard.addView(spacer(dp(10)));
			targetCard.addView(hintText(t("screen.virtual_craft.holographic_shop.transfer.recent_contacts")));
			targetCard.addView(spacer(dp(6)));
			targetCard.addView(recentContactsRow());
		}

		contentColumn.addView(targetCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));

		LinearLayout balanceCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		balanceCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		// gas 货币现在通过 WalletEconomyRules.TRANSFER_GAS_CURRENCY 统一拿（用户要求改为 TRX/波场币）。
		CryptoCurrency gasCurrency = WalletEconomyRules.TRANSFER_GAS_CURRENCY;
		balanceCard.addView(assetHeaderCard(transferCurrency, transferCurrency.displayName().getString(),
				formatTickerAmount(transferCurrency, balances[transferCurrency.ordinal()]),
				t("screen.virtual_craft.holographic_shop.transfer.gas_balance", formatTickerAmount(gasCurrency, balances[gasCurrency.ordinal()]))));
		balanceCard.addView(spacer(dp(8)));
		balanceCard.addView(metricCard(t("screen.virtual_craft.holographic_shop.transfer.gas"), gasCurrency.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR) + " " + gasCurrency.ticker(), AMBER));
		contentColumn.addView(balanceCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private void buildTransferActions() {
		actionColumn.addView(currencyActionButton(t("screen.virtual_craft.holographic_shop.transfer.currency_short", transferCurrency.ticker()), transferCurrency, PickerTarget.TRANSFER_CURRENCY));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.transfer.amount")));
		transferAmountInput = input(transferAmountText, transferCurrency.ticker());
		actionColumn.addView(transferAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(currencyAmountSlider(
				t("screen.virtual_craft.holographic_shop.transfer.amount"),
				transferCurrency,
				spendableBalance(transferCurrency, true),
				parseAmount(transferCurrency, transferAmountText),
				value -> transferAmountText = value,
				transferAmountInput,
				FocusTarget.TRANSFER_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quickAmountRow(transferCurrency, value -> transferAmountText = value, true, FocusTarget.TRANSFER_AMOUNT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(10)));
		String problem = transferProblem();
		actionColumn.addView(statusCard(transferSummary(), problem, problem.isBlank() ? GREEN : AMBER));
		actionColumn.addView(spacer(dp(10)));

		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.transfer.memo")));
		transferMemoInput = input(transferMemoText, t("screen.virtual_craft.holographic_shop.transfer.memo_hint"));
		actionColumn.addView(transferMemoInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
		actionColumn.addView(spacer(dp(10)));

		Button send = actionButton(t("screen.virtual_craft.holographic_shop.transfer.send"), GREEN, this::sendTransferAction);
		actionColumn.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildMiningContent() {
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.mining.vouchers"), t("screen.virtual_craft.holographic_shop.mining.preview")));
		contentColumn.addView(spacer(dp(10)));

		int vouchers = countHashVouchers();
		long payoutMinor = miningPayoutMinor();

		LinearLayout top = new LinearLayout(requireContext());
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.addView(infoCard(t("item.virtual_craft.hash_voucher"), t("screen.virtual_craft.holographic_shop.mining.available", vouchers), t("screen.virtual_craft.holographic_shop.mining.base", CryptoCurrency.BTC.formatCompact(HashVoucherItem.BTC_PAYOUT_MINOR), CryptoCurrency.BTC.ticker()), CYAN), weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.48f));
		var previewParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.52f);
		previewParams.setMargins(dp(10), 0, 0, 0);
		top.addView(infoCard(t("screen.virtual_craft.holographic_shop.mining.preview"), miningSettlementCurrency.displayName().getString(), formatTickerAmount(miningSettlementCurrency, payoutMinor), GREEN), previewParams);
		contentColumn.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(miningStatsDashboard(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/**
	 * Lifetime mining stats card with three headline metrics and a 7-day BTC
	 * production bar chart. Pulled entirely from the persisted {@link
	 * dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.MiningStatsEntry}.
	 */
	private View miningStatsDashboard() {
		LinearLayout card = card(PANEL_ROW, WINDOW_STROKE, 10f);
		card.setPadding(dp(14), dp(14), dp(14), dp(14));
		card.addView(hintText(t("screen.virtual_craft.holographic_shop.mining.stats")));
		card.addView(spacer(dp(8)));

		LinearLayout metrics = new LinearLayout(requireContext());
		metrics.setOrientation(LinearLayout.HORIZONTAL);
		// 容器固定高 dp(72)，每张 card 用 MATCH_PARENT 高度，
		// 确保 3 张 card 视觉上等高（不会因为某张 wrap 多一行而拉高整行）。
		metrics.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.total_settlements"),
				Long.toString(miningStatsCache.totalSettlements()),
				CYAN), weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		var voucherParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		voucherParams.setMargins(dp(8), 0, 0, 0);
		metrics.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.total_vouchers"),
				Long.toString(miningStatsCache.totalVouchers()),
				CYAN), voucherParams);
		var btcParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		btcParams.setMargins(dp(8), 0, 0, 0);
		metrics.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.total_btc"),
				CryptoCurrency.BTC.formatCompact(miningStatsCache.totalBtcMinor()) + " BTC",
				GREEN), btcParams);
		card.addView(metrics, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

		card.addView(spacer(dp(10)));
		card.addView(hintText(t("screen.virtual_craft.holographic_shop.mining.stats.last_7_days")));
		card.addView(spacer(dp(6)));
		card.addView(miningSevenDayChart(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
		return card;
	}

	/** Render the 7-day BTC production buckets as a simple bar chart. */
	private View miningSevenDayChart() {
		LinearLayout chart = new LinearLayout(requireContext());
		chart.setOrientation(LinearLayout.HORIZONTAL);
		chart.setGravity(Gravity.BOTTOM);

		java.util.List<dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.DailyBucket> buckets = miningStatsCache.last7DayBuckets();
		// Build a 7-element array indexed by today, today-1, ..., today-6 so the
		// chart always shows seven slots even when some days are zero.
		long todayStart = (java.time.Instant.now().getEpochSecond() / 86400L) * 86400L;
		long[] btcByDay = new long[7];
		for (var bucket : buckets) {
			long delta = (todayStart - bucket.dayStartEpochSeconds()) / 86400L;
			if (delta >= 0 && delta < btcByDay.length) {
				btcByDay[(int) delta] = bucket.btcMinor();
			}
		}
		long maxBtc = 1L;
		for (long value : btcByDay) {
			if (value > maxBtc) {
				maxBtc = value;
			}
		}
		// Iterate oldest -> newest so the rightmost bar is "today".
		for (int dayIndex = btcByDay.length - 1; dayIndex >= 0; dayIndex--) {
			long value = btcByDay[dayIndex];
			LinearLayout column = vertical();
			column.setGravity(Gravity.BOTTOM);
			View bar = new View(requireContext());
			int heightDp = (int) Math.max(2, Math.round(40.0 * value / maxBtc));
			bar.setBackground(rounded(value > 0 ? CYAN : ACCENT_LINE, 0, 4f, 0));
			LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
			barParams.setMargins(dp(2), 0, dp(2), 0);
			column.addView(bar, barParams);
			LinearLayout.LayoutParams columnParams = weighted(0, dp(40), 1f);
			chart.addView(column, columnParams);
		}
		return chart;
	}

	private void buildMiningActions() {
		int vouchers = countHashVouchers();
		int sliderMax = Math.max(1, vouchers);
		long miningCount = parseWhole(miningAmountText);
		int requested = clampInt((int) miningCount, 0, sliderMax);
		String problem = miningProblem(miningCount, vouchers);
		actionColumn.addView(currencyActionButton(t("screen.virtual_craft.holographic_shop.shop_currency_short", miningSettlementCurrency.ticker()), miningSettlementCurrency, PickerTarget.MINING_SETTLEMENT));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.mining.quantity", miningCount)));
		miningAmountInput = input(miningAmountText, Integer.toString(vouchers));
		actionColumn.addView(miningAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.mining.all"), CYAN, () -> {
			miningAmountText = Integer.toString(vouchers);
			pendingInputOverrides.put(FocusTarget.MINING_AMOUNT, miningAmountText);
			requestRefresh(false, FocusTarget.MINING_AMOUNT);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quantitySlider(t("item.virtual_craft.hash_voucher"), 0, sliderMax, requested, value -> {
			miningAmountText = Integer.toString(value);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(statusCard(miningSummaryLine(miningCount), problem, problem.isBlank() ? GREEN : AMBER));
		actionColumn.addView(spacer(dp(10)));

		Button settle = actionButton(t("screen.virtual_craft.holographic_shop.mining.settle"), GREEN, this::sendMiningAction);
		actionColumn.addView(settle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	/** Horizontal chip row of the player's most recent transfer recipients. */
	private View recentContactsRow() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		int max = Math.min(recentContacts.size(), 4);
		for (int index = 0; index < max; index++) {
			dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.Contact contact = recentContacts.get(index);
			Button chip = navButton(contact.name(), null);
			chip.setGravity(Gravity.CENTER);
			chip.setTextSize(12f);
			chip.setOnClickListener(v -> {
				transferTargetText = contact.name();
				if (!contact.lastMemo().isBlank()) {
					transferMemoText = contact.lastMemo();
					pendingInputOverrides.put(FocusTarget.TRANSFER_MEMO, transferMemoText);
				}
				pendingInputOverrides.put(FocusTarget.TRANSFER_TARGET, transferTargetText);
				refreshPlayerSuggestions();
				requestRefresh(false, FocusTarget.TRANSFER_TARGET);
			});
			LinearLayout.LayoutParams params = weighted(0, dp(26), 1f);
			if (index > 0) {
				params.setMargins(dp(6), 0, 0, 0);
			}
			row.addView(chip, params);
		}
		return row;
	}

	private View playerSuggestionGrid() {
		LinearLayout grid = vertical();
		for (int row = 0; row < 2; row++) {
			LinearLayout line = new LinearLayout(requireContext());
			line.setOrientation(LinearLayout.HORIZONTAL);
			boolean hasAny = false;
			for (int column = 0; column < 2; column++) {
				int index = row * 2 + column;
				if (index >= suggestedPlayers.size()) {
					continue;
				}
				String playerName = suggestedPlayers.get(index);
				Button button = navButton(playerName, null);
				button.setGravity(Gravity.CENTER);
				button.setOnClickListener(v -> {
					transferTargetText = playerName;
					pendingInputOverrides.put(FocusTarget.TRANSFER_TARGET, transferTargetText);
					refreshPlayerSuggestions();
					requestRefresh(false, FocusTarget.TRANSFER_TARGET);
				});
				if (column == 0) {
					line.addView(button, weighted(0, dp(28), 1f));
				} else {
					var buttonParams = weighted(0, dp(28), 1f);
					buttonParams.setMargins(dp(6), 0, 0, 0);
					line.addView(button, buttonParams);
				}
				hasAny = true;
			}
			if (hasAny) {
				grid.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				grid.addView(spacer(dp(6)));
			}
		}
		return grid;
	}

	private View playerPickerRow(String playerName) {
		LinearLayout row = card(PANEL_ROW, WINDOW_STROKE, 10f);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(8), dp(10), dp(8));
		row.setOnClickListener(v -> {
			transferTargetText = playerName;
			pendingInputOverrides.put(FocusTarget.TRANSFER_TARGET, transferTargetText);
			pickerTarget = PickerTarget.NONE;
			refreshPlayerSuggestions();
			requestRefresh(false, FocusTarget.TRANSFER_TARGET);
		});

		row.addView(playerAvatarView(playerName, ICON_SMALL_DP));
		LinearLayout text = vertical();
		var textParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, 0, 0);
		TextView name = bodyText(playerName);
		name.setTextSize(14f);
		name.setTextColor(TEXT);
		name.setMaxLines(1);
		name.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(name);
		TextView state = hintText(t("screen.virtual_craft.holographic_shop.transfer.online"));
		state.setMaxLines(1);
		text.addView(state);
		row.addView(text, textParams);
		return row;
	}

	private TextView playerAvatarView(String playerName, int sizeDp) {
		TextView badge = new TextView(requireContext());
		String initial = playerName == null || playerName.isBlank() ? "?" : playerName.substring(0, 1).toUpperCase(Locale.ROOT);
		badge.setText(initial);
		badge.setTextSize(12f);
		badge.setTextColor(0xFFFFFFFF);
		badge.setGravity(Gravity.CENTER);
		int color = switch (Math.floorMod(playerName == null ? 0 : playerName.hashCode(), 5)) {
			case 0 -> CYAN;
			case 1 -> GREEN;
			case 2 -> AMBER;
			case 3 -> 0xFF7C8CFF;
			default -> 0xFF38BDF8;
		};
		badge.setBackground(rounded(color, 0, 7f, 0));
		badge.setLayoutParams(squareLayout(sizeDp));
		return badge;
	}

	// shopDetailPanel() 已废除——详情改为悬停弹出 ({@link #showShopTooltip})，
	// 不再需要“列表 + 面板”双列布局。原面板里的详情卡内容生成逻辑仍由
	// {@link #shopDetailCard(ShopOffer)} 提供，tooltip 直接复用。

	/**
	 * 商品悬停详情弹出的入口点。vanilla item tooltip 风格：鼠标右下方 12/8dp
	 * 偏移出现，但严格约束在 {@link #contentFrame} 边界内部——<b>绝不可以溢出到
	 * actionShell（右侧“交易操作”栏）盖住买入/卖出按钮</b>。不够时依次退化：
	 * 右不够 → 左侧；左也不够 → 贴 contentFrame 左边。Y 不够 → 鼠标上方。
	 *
	 * <p>{@code anchor} 是被悬停的 row；{@code relMouseX/Y} 是鼠标相对 row 的坐标
	 * （{@code MotionEvent.getX/Y}）。同一 tooltip overlay 复用、只替换子 view 以避
	 * 免从 A row 到 B row 时的 hide→show 闪烁。</p>
	 */
	private void showShopTooltip(ShopOffer offer, View anchor, float relMouseX, float relMouseY) {
		if (shopTooltipOverlay == null || root == null || contentFrame == null || offer == null) {
			return;
		}
		String offerKey = offer.id();
		if (!offerKey.equals(shopTooltipOverlay.getTag())) {
			shopTooltipOverlay.removeAllViews();
			View card = shopDetailCard(offer);
			FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
					dp(230), ViewGroup.LayoutParams.WRAP_CONTENT);
			cp.gravity = Gravity.LEFT | Gravity.TOP;
			shopTooltipOverlay.addView(card, cp);
			shopTooltipOverlay.setTag(offerKey);
		}
		int tooltipW = dp(230);
		int tooltipH = dp(220); // shopDetailCard 的保守估计高度，仅用于 Y 边界退化判断

		// 鼠标位置 → root 内相对坐标；anchor 在 window 中的坐标 + relMouse 偏移 − root 在 window 中的坐标
		int[] anchorLoc = new int[2];
		int[] rootLoc = new int[2];
		int[] contentLoc = new int[2];
		anchor.getLocationInWindow(anchorLoc);
		root.getLocationInWindow(rootLoc);
		contentFrame.getLocationInWindow(contentLoc);
		int mouseRootX = anchorLoc[0] + (int) relMouseX - rootLoc[0];
		int mouseRootY = anchorLoc[1] + (int) relMouseY - rootLoc[1];

		// contentFrame 在 root 内的边界——tooltip 严格约束在其内，避免溢出到 actionShell。
		int contentLeft = contentLoc[0] - rootLoc[0];
		int contentTop = contentLoc[1] - rootLoc[1];
		int contentRight = contentLeft + contentFrame.getWidth();
		int contentBottom = contentTop + contentFrame.getHeight();

		// 默认：鼠标右下方 (vanilla tooltip 风格)
		int tooltipX = mouseRootX + dp(12);
		int tooltipY = mouseRootY + dp(8);

		// X 退化：右侧装不下 → 鼠标左侧；再不够 → 贴 contentFrame 左边
		if (tooltipX + tooltipW > contentRight) {
			tooltipX = mouseRootX - tooltipW - dp(12);
		}
		if (tooltipX < contentLeft) {
			tooltipX = contentLeft + dp(4);
		}

		// Y 退化：下方装不下 → 鼠标上方；再不够 → 贴 contentFrame 上边
		if (tooltipY + tooltipH > contentBottom) {
			tooltipY = mouseRootY - tooltipH - dp(8);
		}
		if (tooltipY < contentTop) {
			tooltipY = contentTop + dp(4);
		}

		View card = shopTooltipOverlay.getChildAt(0);
		if (card != null) {
			FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) card.getLayoutParams();
			cp.leftMargin = tooltipX;
			cp.topMargin = tooltipY;
			card.setLayoutParams(cp);
		}
		if (shopTooltipOverlay.getVisibility() != View.VISIBLE) {
			shopTooltipOverlay.setVisibility(View.VISIBLE);
		}
		// 不需要 bringToFront()：shopTooltipOverlay 在 onCreateView 末尾才 addView 到 root，
		// 是 root 的最后一个子 view，自然处于最顶层 z-order，已盖过 window 和 actionShell。
	}

	/** 隐藏商品悬停详情 tooltip。重建列表前 / HOVER_EXIT 时调。 */
	private void hideShopTooltip() {
		if (shopTooltipOverlay == null) {
			return;
		}
		shopTooltipOverlay.setVisibility(View.GONE);
		shopTooltipOverlay.setTag(null);
	}

	private View shopDetailCard(ShopOffer offer) {
		// v23 mica 风：整卡用 Win11 云母（极白 98% + 浅灰薄边 + 投影晕 + 玻璃边高光）。
		// 与外层 acrylic 主窗不同 — tooltip 更"轻盈悬浮"，视觉上"漂"在内容之上。
		LinearLayout detail = new LinearLayout(requireContext());
		detail.setOrientation(LinearLayout.VERTICAL);
		detail.setBackground(HolographicTheme.mica(dp(12)));
		// 上下左右各加 dp(4) 让 mica 投影晕有空间显出来（layerInset 已让内层缩 2，padding 12+2 兜底）
		detail.setPadding(dp(14), dp(14), dp(14), dp(14));

		// ── 头部：图标 + 名称 + "x1"（horizontal，紧凑）────────────────
		// 之前 vertical 排列让 "x1" 独占一行，垂直空间浪费。改成左图标右文字区。
		LinearLayout header = new LinearLayout(requireContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);

		header.addView(itemIconView(offer.itemId(), offer.displayName().getString(), ICON_LARGE_DP));

		LinearLayout headerText = vertical();
		var headerTextLp = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		headerTextLp.setMargins(dp(10), 0, 0, 0);

		TextView name = titleText(offer.displayName().getString());
		name.setTextSize(15f);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		headerText.addView(name);

		TextView count = hintText("x" + offer.count());
		count.setTextSize(11f);
		headerText.addView(count);

		header.addView(headerText, headerTextLp);
		detail.addView(header);
		detail.addView(spacer(dp(10)));

		// ── 价格组（买入/卖出）────────────────────────────────
		// 用浅色 sub-panel 包起来，让买入卖出价格作为单一信息单元突出。
		LinearLayout priceGroup = card(PANEL_SOFT, WINDOW_STROKE, 8f);
		priceGroup.setPadding(dp(10), dp(8), dp(10), dp(8));
		priceGroup.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.buy"),
				formatTickerAmount(shopSettlementCurrency, shopUnitBuyMinor(offer)), GREEN));
		priceGroup.addView(spacer(dp(4)));
		priceGroup.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.sell"),
				formatTickerAmount(shopSettlementCurrency, shopUnitSellMinor(offer)), AMBER));
		detail.addView(priceGroup, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		detail.addView(spacer(dp(8)));

		// ── 库存信息组 ──────────────────────────────────────
		LinearLayout stockGroup = card(PANEL_SOFT, WINDOW_STROKE, 8f);
		stockGroup.setPadding(dp(10), dp(8), dp(10), dp(8));
		stockGroup.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.shop.inventory"),
				Integer.toString(currentShopSellMax()), TEXT));
		stockGroup.addView(spacer(dp(4)));
		stockGroup.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.shop.buy_limit"),
				Integer.toString(currentShopBuyMax()), TEXT));
		detail.addView(stockGroup, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		detail.addView(spacer(dp(8)));

		// ── 提示文字：用 TEXT_DIM 取代抢眼的 CYAN，更像 footnote ─────────
		TextView autoSwap = hintText(t("screen.virtual_craft.holographic_shop.shop.auto_swap_detail"));
		autoSwap.setTextSize(11f);
		autoSwap.setMaxLines(3);
		autoSwap.setEllipsize(TextUtils.TruncateAt.END);
		detail.addView(autoSwap);
		return detail;
	}

	private View offerRow(ShopOffer offer, boolean selected, Runnable action) {
		LinearLayout row = card(selected ? PANEL_ACTIVE : PANEL_ROW, selected ? CYAN : WINDOW_STROKE, 10f);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(9), dp(10), dp(9));
		row.setOnClickListener(v -> action.run());

		// v23 hover tooltip 触发器从整行收紧到图标（用户原话："鼠标停留到图标"）。
		// 行为：鼠标进入图标 → mica 风详情卡弹出；移出图标 → 立即隐藏。
		// 这样移动鼠标到价格/星标按钮就不会一直挂着 tooltip 挡视线。
		View iconView = itemIconView(offer.itemId(), offer.displayName().getString(), ICON_SMALL_DP);
		iconView.setOnHoverListener((v, event) -> {
			int a = event.getAction();
			if (a == MotionEvent.ACTION_HOVER_ENTER) {
				showShopTooltip(offer, v, event.getX(), event.getY());
			} else if (a == MotionEvent.ACTION_HOVER_EXIT) {
				hideShopTooltip();
			}
			return false;
		});
		row.addView(iconView);

		LinearLayout text = vertical();
		var textParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);
		TextView title = bodyText(offer.displayName().getString());
		title.setTextSize(13f);
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(title);
		// 价格拆为两个独立 TextView上下堆叠，彻底避免 "买 90 USDT  卖 45 USDT" 被 wrap 切成
		// "买 90 USDT  卖" / "45 USDT"这种词与数字被拆烂的狼狈状。
		// 东亚语言中中文 "买"/"卖" 字很宽，原本的单行 row_price="买 %s  卖 %s" 在 catalog 区宽量下
		// 常规装不下；拆行后 row 高度稳定为 2 行、语义完整、不掳字。
		TextView buyPrice = hintText(t("screen.virtual_craft.holographic_shop.shop.row_buy",
				formatTickerAmount(shopSettlementCurrency, shopUnitBuyMinor(offer))));
		buyPrice.setTextSize(11f);
		buyPrice.setMaxLines(1);
		buyPrice.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(buyPrice);
		TextView sellPrice = hintText(t("screen.virtual_craft.holographic_shop.shop.row_sell",
				formatTickerAmount(shopSettlementCurrency, shopUnitSellMinor(offer))));
		sellPrice.setTextSize(11f);
		sellPrice.setMaxLines(1);
		sellPrice.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(sellPrice);
		row.addView(text, textParams);

		TextView count = hintText("x" + offer.count());
		count.setTextColor(selected ? CYAN : TEXT_SOFT);
		row.addView(count);

		boolean favorite = favoriteOfferIds.contains(offer.id());
		Button favBtn = compactIconButton(favorite ? "★" : "☆", () -> toggleFavoriteOffer(offer));
		favBtn.setTextColor(favorite ? AMBER : TEXT_SOFT);
		// 单字符 ★/☆ 用 compactIconButton（无 ellipsize），宽度 dp(34) 证字符稳稳塞下。
		var favParams = new LinearLayout.LayoutParams(dp(34), dp(28));
		favParams.setMargins(dp(8), 0, 0, 0);
		row.addView(favBtn, favParams);
		return row;
	}

	/** Tuple of (originalOfferIndex, ShopOffer) used by the filtered shop list. */
	private record IndexedOffer(int originalIndex, ShopOffer offer) {
	}

	/**
	 * Build the list of offers visible after applying the current search /
	 * favorites / sort selection. The original indices into {@link #offers}
	 * are preserved so that {@link #selectedOfferIndex} stays meaningful.
	 */
	private List<IndexedOffer> filteredShopOffers() {
		String query = shopSearchText == null ? "" : shopSearchText.trim().toLowerCase(java.util.Locale.ROOT);
		java.util.ArrayList<IndexedOffer> result = new java.util.ArrayList<>(offers.size());
		for (int index = 0; index < offers.size(); index++) {
			ShopOffer offer = offers.get(index);
			if (shopFavoritesOnly && !favoriteOfferIds.contains(offer.id())) {
				continue;
			}
			if (!query.isEmpty()) {
				String name = offer.displayName().getString().toLowerCase(java.util.Locale.ROOT);
				String id = offer.id().toLowerCase(java.util.Locale.ROOT);
				String itemPath = offer.itemId().getPath().toLowerCase(java.util.Locale.ROOT);
				if (!name.contains(query) && !id.contains(query) && !itemPath.contains(query)) {
					continue;
				}
			}
			result.add(new IndexedOffer(index, offer));
		}
		java.util.Comparator<IndexedOffer> comparator = switch (shopSortMode) {
			case BUY_PRICE_ASC -> java.util.Comparator.comparingLong(io -> io.offer().priceUsdtMinor());
			case BUY_PRICE_DESC -> java.util.Comparator.<IndexedOffer>comparingLong(io -> io.offer().priceUsdtMinor()).reversed();
			case SELL_PRICE_ASC -> java.util.Comparator.comparingLong(io -> io.offer().sellUsdtMinor());
			case SELL_PRICE_DESC -> java.util.Comparator.<IndexedOffer>comparingLong(io -> io.offer().sellUsdtMinor()).reversed();
			case NAME_ASC -> java.util.Comparator.comparing(io -> io.offer().displayName().getString().toLowerCase(java.util.Locale.ROOT));
			case DEFAULT -> null;
		};
		if (comparator != null) {
			result.sort(comparator);
		}
		return result;
	}

	/**
	 * Optimistically toggle the favorite state on the client and inform the
	 * server so it can persist the change. The authoritative state is sent back
	 * via {@link dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload}.
	 */
	private void toggleFavoriteOffer(ShopOffer offer) {
		if (offer == null || offer.id() == null || offer.id().isBlank()) {
			return;
		}
		boolean nowFavorite = !favoriteOfferIds.contains(offer.id());
		if (nowFavorite) {
			favoriteOfferIds.add(offer.id());
		} else {
			favoriteOfferIds.remove(offer.id());
		}
		PacketDistributor.sendToServer(new dev.tatami.virtualcraft.network.WalletPreferencesActionPayload(
				sessionId,
				nowFavorite
						? dev.tatami.virtualcraft.network.WalletPreferencesActionPayload.Action.ADD_FAVORITE
						: dev.tatami.virtualcraft.network.WalletPreferencesActionPayload.Action.REMOVE_FAVORITE,
				offer.id()));
		requestRefresh(false, captureFocusTarget());
	}

	/**
	 * Convert 页上「选择源币种 / 选择目标币种」卡片。
	 *
	 * <p>旧版复用 {@link #assetHeaderCard}，结果在狭卡宽下：
	 * <ul>
	 *   <li>title="Ethereum" 被截断为 "Eth..."；</li>
	 *   <li>valueText="0.00144 BTC" 被截断为 "0.00144 B*"。</li>
	 * </ul>
	 * 根因是 assetHeaderCard 把 icon/title+subtitle/value 三块水平平铺，
	 * weight=1f 的 text 区与 value 全 maxLines=1+ellipsize ，水平互抢后都被截。
	 *
	 * <p>重设为「上 label 中头部行 下金额独占」三行纵向布局：
	 * <pre>
	 *   选择源币种
	 *   [icon] Ethereum / ETH
	 *                   0.00144 BTC
	 * </pre>
	 * 金额独占一行后拥有整张卡宽，不会被截断；name/ticker 也不再与金额争抢水平空间。
	 */
	private View assetSelectorCard(String label, CryptoCurrency currency, String value, Runnable action) {
		LinearLayout card = card(PANEL_ROW, CYAN, 10f);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(12), dp(12), dp(12), dp(12));
		card.setOnClickListener(v -> action.run());

		card.addView(hintText(label));
		card.addView(spacer(dp(6)));

		// 头部行：icon + (币名/ticker)。不再携带 value，水平空间宽裕。
		LinearLayout head = new LinearLayout(requireContext());
		head.setOrientation(LinearLayout.HORIZONTAL);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.addView(currencyIconView(currency, ICON_LARGE_DP));

		LinearLayout names = vertical();
		var namesParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		namesParams.setMargins(dp(10), 0, 0, 0);
		TextView nameText = bodyText(currency.displayName().getString());
		nameText.setMaxLines(1);
		nameText.setEllipsize(TextUtils.TruncateAt.END);
		names.addView(nameText);
		TextView tickerText = hintText(currency.ticker());
		tickerText.setMaxLines(1);
		tickerText.setEllipsize(TextUtils.TruncateAt.END);
		names.addView(tickerText);
		head.addView(names, namesParams);
		card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		card.addView(spacer(dp(6)));

		// 金额独占第二行、右对齐、不截断。
		TextView valueText = bodyText(value);
		valueText.setTextSize(15f);
		valueText.setTextColor(GREEN);
		valueText.setGravity(Gravity.END);
		valueText.setMaxLines(1);
		valueText.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(valueText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return card;
	}

	private View assetHeaderCard(CryptoCurrency currency, String title, String subtitle, String value) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(currencyIconView(currency, ICON_LARGE_DP));

		LinearLayout text = vertical();
		var textParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);
		TextView titleText = bodyText(title);
		titleText.setMaxLines(1);
		titleText.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(titleText);
		TextView subtitleText = hintText(subtitle);
		subtitleText.setMaxLines(1);
		subtitleText.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(subtitleText);
		row.addView(text, textParams);

		TextView valueText = bodyText(value);
		valueText.setTextSize(15f);
		valueText.setTextColor(GREEN);
		valueText.setGravity(Gravity.END);
		valueText.setMaxLines(1);
		valueText.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(valueText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return row;
	}

	private View currencyRow(CryptoCurrency currency, String title, String primary, String secondary, boolean selected, Runnable action) {
		LinearLayout row = card(selected ? PANEL_ACTIVE : PANEL_ROW, selected ? CYAN : WINDOW_STROKE, 10f);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(9), dp(10), dp(9));
		row.setOnClickListener(v -> action.run());
		row.addView(currencyIconView(currency, ICON_SMALL_DP));

		LinearLayout text = vertical();
		var textParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);
		TextView titleView = bodyText(title);
		titleView.setMaxLines(1);
		titleView.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(titleView);
		TextView secondaryView = hintText(secondary);
		secondaryView.setMaxLines(1);
		secondaryView.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(secondaryView);
		row.addView(text, textParams);

		TextView primaryView = bodyText(primary);
		primaryView.setTextColor(selected ? CYAN : TEXT);
		primaryView.setGravity(Gravity.END);
		primaryView.setMaxLines(2);
		primaryView.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(primaryView);
		return row;
	}

	private View ledgerRow(SyncWalletPayload.Entry entry) {
		LinearLayout row = card(PANEL_ROW, WINDOW_STROKE, 10f);
		row.setPadding(dp(10), dp(8), dp(10), dp(8));
		// Title row: amount/currency on the left, relative time on the right.
		LinearLayout titleRow = new LinearLayout(requireContext());
		titleRow.setOrientation(LinearLayout.HORIZONTAL);
		titleRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = bodyText(ledgerTitle(entry));
		// 用户反馈"显示不全"：原 setMaxLines(1) + ellipsize.END 让长中文标题（"挖矿 +1.5 DOGE - 矿机 #...")
		// 直接尾部截成"挖矿..."。允许 2 行换行让标题完整显示；超过 2 行（极端情况）仍用 ellipsize.END 兜底，
		// 避免 row 高度无限增长把外层 ScrollView 撑爆。
		title.setMaxLines(2);
		title.setEllipsize(TextUtils.TruncateAt.END);
		titleRow.addView(title, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView when = hintText(HolographicTheme.relativeTime(entry.timestampSeconds()));
		when.setGravity(Gravity.END);
		when.setTextColor(TEXT_SOFT);
		titleRow.addView(when);
		row.addView(titleRow);
		TextView detail = hintText(ledgerDetail(entry));
		// detail 同样允许 2 行：transfer entries 的 detail 通常是"向 玩家名 - 备注"这种长字符串，
		// 单行截断后玩家完全不知道转给了谁。
		detail.setMaxLines(2);
		detail.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(detail);
		// If the entry has a memo (transfer note) show it as a third line.
		if (entry.memo() != null && !entry.memo().isBlank()) {
			TextView memo = hintText("\u201C" + entry.memo() + "\u201D");
			// memo 允许 2 行换行；玩家自填的备注没人愿意被截成"..."。
			memo.setMaxLines(2);
			memo.setEllipsize(TextUtils.TruncateAt.END);
			memo.setTextColor(TEXT_DIM);
			row.addView(memo);
		}
		return row;
	}

	private View quantityControlRow(int current, int max, java.util.function.IntConsumer onChanged) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(stepButton("-", Math.max(1, current - 1), onChanged), weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.72f));
		var valueParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.92f);
		valueParams.setMargins(dp(6), 0, dp(6), 0);
		row.addView(valueChip(Integer.toString(current)), valueParams);
		row.addView(stepButton("+", Math.min(max, current + 1), onChanged), weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.72f));
		var maxParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.92f);
		maxParams.setMargins(dp(6), 0, 0, 0);
		row.addView(stepButton(t("screen.virtual_craft.holographic_shop.max"), Math.max(1, max), onChanged), maxParams);
		return row;
	}

	private View valueChip(String text) {
		TextView chip = new TextView(requireContext());
		chip.setText(text);
		chip.setTextSize(13f);
		chip.setTextColor(TEXT);
		chip.setGravity(Gravity.CENTER);
		chip.setBackground(rounded(PANEL_ROW, WINDOW_STROKE, 9f, 1));
		return chip;
	}

	private Button stepButton(String label, int value, java.util.function.IntConsumer onChanged) {
		return compactActionButton(label, () -> onChanged.accept(value));
	}

	private View quantitySlider(String label, int min, int max, int current, java.util.function.IntConsumer onChanged) {
		LinearLayout block = vertical();
		TextView value = hintText(label + ": " + current);
		block.addView(value);
		// HoloSeekBar 内部直接 @Override onGenericMotionEvent 处理 ACTION_SCROLL，
		// 比 setOnGenericMotionListener 更稳：listener 受 ENABLED_MASK 校验，
		// 嵌套 ScrollView 时偶发吃事件；onGenericMotionEvent 直接走 dispatch chain。
		SeekBar seekBar = new HoloSeekBar(requireContext());
		seekBar.setMin(min);
		seekBar.setMax(max);
		seekBar.setProgress(clampInt(current, min, max));
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int resolved = clampInt(progress, min, max);
				value.setText(label + ": " + resolved);
				onChanged.accept(resolved);
				if (!fromUser) {
					requestRefresh(false, FocusTarget.NONE);
				}
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				requestRefresh(false, FocusTarget.NONE);
			}
		});
		block.addView(seekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return block;
	}

	private View currencyAmountSlider(String label,
			CryptoCurrency currency,
			long maxMinor,
			long currentMinor,
			java.util.function.Consumer<String> onChanged,
			EditText mirrorInput,
			FocusTarget focusTarget) {
		long safeMax = Math.max(0L, maxMinor);
		long safeCurrent = clampLong(currentMinor, 0L, safeMax);
		LinearLayout block = vertical();
		TextView value = hintText(label + ": " + formatTickerAmount(currency, safeCurrent));
		block.addView(value);
		SeekBar seekBar = new HoloSeekBar(requireContext());
		seekBar.setMin(0);
		seekBar.setMax(AMOUNT_SLIDER_STEPS);
		seekBar.setProgress(progressForAmount(safeCurrent, safeMax));
		seekBar.setEnabled(safeMax > 0L);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				long amount = amountForProgress(safeMax, progress);
				String text = currency.formatFixed(amount);
				value.setText(label + ": " + formatTickerAmount(currency, amount));
				onChanged.accept(text);
				if (mirrorInput != null && !mirrorInput.getText().toString().equals(text)) {
					mirrorInput.setText(text);
				}
				pendingInputOverrides.put(focusTarget, text);
				if (!fromUser) {
					requestRefresh(false, focusTarget);
				}
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				requestRefresh(false, focusTarget);
			}
		});
		block.addView(seekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return block;
	}

	private View wholeAmountSlider(String label,
			long maxWhole,
			long currentWhole,
			java.util.function.Consumer<String> onChanged,
			EditText mirrorInput,
			FocusTarget focusTarget) {
		long safeMax = Math.max(0L, maxWhole);
		long safeCurrent = clampLong(currentWhole, 0L, safeMax);
		LinearLayout block = vertical();
		TextView value = hintText(label + ": " + safeCurrent + " / " + safeMax);
		block.addView(value);
		SeekBar seekBar = new HoloSeekBar(requireContext());
		seekBar.setMin(0);
		seekBar.setMax(AMOUNT_SLIDER_STEPS);
		seekBar.setProgress(progressForAmount(safeCurrent, safeMax));
		seekBar.setEnabled(safeMax > 0L);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				long amount = amountForProgress(safeMax, progress);
				String text = Long.toString(amount);
				value.setText(label + ": " + amount + " / " + safeMax);
				onChanged.accept(text);
				if (mirrorInput != null && !mirrorInput.getText().toString().equals(text)) {
					mirrorInput.setText(text);
				}
				pendingInputOverrides.put(focusTarget, text);
				if (!fromUser) {
					requestRefresh(false, focusTarget);
				}
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				requestRefresh(false, focusTarget);
			}
		});
		block.addView(seekBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return block;
	}

	private View quickAmountRow(CryptoCurrency currency, java.util.function.Consumer<String> onPicked, boolean reserveTransferGas, FocusTarget focusTarget) {
		return quickAmountRow(currency, spendableBalance(currency, reserveTransferGas), onPicked, focusTarget);
	}

	private View quickAmountRow(CryptoCurrency currency, long maxMinor, java.util.function.Consumer<String> onPicked, FocusTarget focusTarget) {
		LinearLayout block = vertical();
		LinearLayout topRow = new LinearLayout(requireContext());
		topRow.setOrientation(LinearLayout.HORIZONTAL);
		// 致命 bug 修复：weighted() 第二参 raw int 会直接作为 px 传给
		// LinearLayout.LayoutParams。原代码 weighted(0, QUICK_BUTTON_HEIGHT_DP=32, 1f)
		// 将按钮高设成 32 像素 (约当 dp(16))，12sp 字体需要 >= dp(22)
		// 才能完整画出 → 文字被垂直裁成空白。所有调用点都改 dp()。
		topRow.addView(quickAmountButton("25%", currency, maxMinor, 1, 4, onPicked, focusTarget), weighted(0, dp(QUICK_BUTTON_HEIGHT_DP), 1f));
		var halfParams = weighted(0, dp(QUICK_BUTTON_HEIGHT_DP), 1f);
		halfParams.setMargins(dp(6), 0, 0, 0);
		topRow.addView(quickAmountButton("50%", currency, maxMinor, 1, 2, onPicked, focusTarget), halfParams);
		block.addView(topRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		block.addView(spacer(dp(6)));

		LinearLayout bottomRow = new LinearLayout(requireContext());
		bottomRow.setOrientation(LinearLayout.HORIZONTAL);
		bottomRow.addView(quickAmountButton(t("screen.virtual_craft.holographic_shop.quick_all"), currency, maxMinor, 1, 1, onPicked, focusTarget), weighted(0, dp(QUICK_BUTTON_HEIGHT_DP), 1f));
		var clearParams = weighted(0, dp(QUICK_BUTTON_HEIGHT_DP), 1f);
		clearParams.setMargins(dp(6), 0, 0, 0);
		bottomRow.addView(clearAmountButton(onPicked, focusTarget), clearParams);
		block.addView(bottomRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return block;
	}

	private Button quickAmountButton(String label, CryptoCurrency currency, long maxMinor, int numerator, int denominator, java.util.function.Consumer<String> onPicked, FocusTarget focusTarget) {
		return compactActionButton(label, () -> {
			long balance = Math.max(0L, maxMinor);
			long picked = denominator <= 1 ? balance : Math.max(0L, (balance / denominator) * numerator + ((balance % denominator) * numerator) / denominator);
			String value = currency.formatFixed(picked);
			onPicked.accept(value);
			pendingInputOverrides.put(focusTarget, value);
			requestRefresh(false, focusTarget);
		});
	}

	private Button clearAmountButton(java.util.function.Consumer<String> onPicked, FocusTarget focusTarget) {
		return compactActionButton(t("screen.virtual_craft.holographic_shop.clear"), () -> {
			onPicked.accept("");
			pendingInputOverrides.put(focusTarget, "");
			requestRefresh(false, focusTarget);
		});
	}

	private View statusCard(String summary, String issue, int accentColor) {
		LinearLayout card = card(PANEL_ROW, accentColor, 10f);
		card.setPadding(dp(12), dp(12), dp(12), dp(12));
		TextView summaryView = bodyText(summary);
		summaryView.setMaxLines(2);
		summaryView.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(summaryView);
		if (issue != null && !issue.isBlank()) {
			card.addView(spacer(dp(4)));
			TextView issueView = hintText(issue);
			issueView.setTextColor(accentColor == GREEN ? TEXT_DIM : accentColor);
			issueView.setMaxLines(2);
			issueView.setEllipsize(TextUtils.TruncateAt.END);
			card.addView(issueView);
		}
		return card;
	}

	private View infoCard(String label, String title, String value, int accentColor) {
		LinearLayout card = card(PANEL_SOFT, WINDOW_STROKE, 10f);
		card.setPadding(dp(14), dp(14), dp(14), dp(14));
		card.addView(hintText(label));
		card.addView(spacer(dp(4)));
		// title / value 都 inline 创建；显式 setSingleLine(false) + setEllipsize(null)
		// 以绕过 ModernUI TextView 可能的默认 singleLine=true 行为。
		TextView titleView = new TextView(requireContext());
		titleView.setText(title);
		titleView.setTextSize(13f);
		titleView.setTextColor(TEXT);
		titleView.setSingleLine(false);
		titleView.setEllipsize(null);
		titleView.setMaxLines(2);
		card.addView(titleView);
		card.addView(spacer(dp(4)));
		TextView valueView = new TextView(requireContext());
		valueView.setText(value);
		valueView.setTextSize(17f);
		valueView.setTextColor(accentColor);
		valueView.setSingleLine(false);
		valueView.setEllipsize(null);
		valueView.setMaxLines(2);
		card.addView(valueView);
		return card;
	}

	private View metricCard(String label, String value, int accentColor) {
		LinearLayout card = card(PANEL_SOFT, WINDOW_STROKE, 10f);
		card.setPadding(dp(10), dp(10), dp(10), dp(10));
		card.setMinimumWidth(0);
		card.addView(hintText(label));
		// value 改用多行 wrap、明确 setEllipsize(null) + setMaxLines 大值（效果同不限）。
		// 11sp 小字号 + 多行 wrap 结合，确保 “0.0006 BTC” 不会有 “…”。
		TextView valueView = new TextView(requireContext());
		valueView.setText(value);
		valueView.setTextSize(11f);
		valueView.setTextColor(accentColor);
		valueView.setSingleLine(false);
		valueView.setEllipsize(null);
		valueView.setMaxLines(Integer.MAX_VALUE);
		card.addView(valueView);
		return card;
	}

	private View keyValueRow(String key, String value, int valueColor) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		TextView keyView = hintText(key);
		row.addView(keyView, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView valueView = bodyText(value);
		valueView.setTextColor(valueColor);
		valueView.setGravity(Gravity.END);
		valueView.setMaxLines(1);
		valueView.setEllipsize(TextUtils.TruncateAt.END);
		row.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return row;
	}

	private View sectionHeader(String title, String subtitle) {
		LinearLayout header = vertical();
		TextView titleView = bodyText(title);
		titleView.setTextSize(16f);
		header.addView(titleView);
		if (subtitle != null && !subtitle.isBlank()) {
			TextView subtitleView = hintText(subtitle);
			subtitleView.setMaxLines(2);
			subtitleView.setEllipsize(TextUtils.TruncateAt.END);
			header.addView(subtitleView);
		}
		return header;
	}

	private View emptyState(String title, String subtitle) {
		LinearLayout state = card(PANEL_ROW, WINDOW_STROKE, 10f);
		state.setPadding(dp(14), dp(14), dp(14), dp(14));
		state.addView(bodyText(title));
		TextView subtitleView = hintText(subtitle);
		subtitleView.setMaxLines(2);
		subtitleView.setEllipsize(TextUtils.TruncateAt.END);
		state.addView(spacer(dp(4)));
		state.addView(subtitleView);
		return state;
	}

	private Button navButton(String text, Page page) {
		Button button = new Button(requireContext());
		button.setText(text);
		button.setTextSize(13f);
		button.setSingleLine();
		button.setEllipsize(TextUtils.TruncateAt.END);
		boolean selected = page != null && page == currentPage;
		button.setTextColor(selected ? TEXT : TEXT_DIM);
		button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
		button.setPadding(dp(11), dp(6), dp(11), dp(6));
		button.setBackground(rounded(selected ? PANEL_ACTIVE : PANEL_BUTTON, selected ? CYAN : WINDOW_STROKE, 9f, 1));
		if (page != null) {
			button.setOnClickListener(v -> navigateTo(page));
		}
		return button;
	}

	private void navigateTo(Page page) {
		currentPage = page;
		pickerTarget = PickerTarget.NONE;
		// Populate derived state that each page depends on BEFORE we rebuild so that the
		// render path never has to query the network layer or iterate global state.
		if (page == Page.TRANSFER) {
			refreshPlayerSuggestions();
		}
		requestRefresh(false, FocusTarget.NONE);
	}

	private Button actionButton(String text, int accentColor, Runnable action) {
		Button button = navButton(text, null);
		button.setGravity(Gravity.CENTER);
		button.setBackground(rounded(PANEL_BUTTON, accentColor, 9f, 1));
		button.setOnClickListener(v -> action.run());
		return button;
	}

	/**
	 * 完全独立的紧凑按钮实现：不再继承 navButton 以避开 ModernUI 中
	 * setSingleLine() + 短文本 + 紧凑 padding 组合下偶发的“宽度被压成 0
	 * 整串文本不画”的问题。这就是 25%/50%/全部/清空 仍然空白的根因。
	 */
	private Button compactActionButton(String text, Runnable action) {
		Button button = new Button(requireContext());
		button.setText(text);
		button.setTextSize(12f);
		button.setTextColor(TEXT_DIM);
		button.setGravity(Gravity.CENTER);
		// 关键：不调 setSingleLine、不 setEllipsize，让 TextView 走默认多行 wrap，
		// 短文本 “25%” / “清空” 一行就能放下，长文本可换行不被裁。
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		button.setPadding(dp(8), dp(6), dp(8), dp(6));
		button.setBackground(rounded(PANEL_BUTTON, WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
		return button;
	}

	private View modeSwitch(String leftText,
			boolean leftSelected,
			Runnable leftAction,
			String rightText,
			boolean rightSelected,
			Runnable rightAction) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(modeButton(leftText, leftSelected, leftAction), weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		var rightParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		rightParams.setMargins(dp(6), 0, 0, 0);
		row.addView(modeButton(rightText, rightSelected, rightAction), rightParams);
		return row;
	}

	private Button modeButton(String text, boolean selected, Runnable action) {
		Button button = new Button(requireContext());
		button.setText(text);
		button.setTextSize(12f);
		button.setSingleLine();
		button.setTextColor(selected ? TEXT : TEXT_DIM);
		button.setGravity(Gravity.CENTER);
		button.setPadding(dp(8), dp(6), dp(8), dp(6));
		button.setBackground(rounded(selected ? PANEL_ACTIVE : PANEL_BUTTON, selected ? CYAN : WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
		return button;
	}

	/**
	 * 单字符图标按钮（如 ★/☆）专用：不继承 navButton 以避免
	 * 被 setEllipsize(END) 把单字符判为超宽而整串换成空。
	 */
	private Button compactIconButton(String glyph, Runnable action) {
		Button button = new Button(requireContext());
		button.setText(glyph);
		button.setTextSize(14f);
		button.setSingleLine();
		button.setGravity(Gravity.CENTER);
		button.setTextColor(TEXT);
		button.setPadding(dp(4), dp(4), dp(4), dp(4));
		button.setBackground(rounded(PANEL_BUTTON, WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
		return button;
	}

	private Button currencyActionButton(String label, CryptoCurrency currency, PickerTarget target) {
		Button button = actionButton(label, CYAN, () -> openPicker(target));
		button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
		button.setCompoundDrawablesRelative(currencyIcon(currency, ICON_SMALL_DP), null, null, null);
		button.setCompoundDrawablePadding(dp(8));
		return button;
	}

	private EditText input(String value, String hint) {
		EditText input = new EditText(requireContext());
		input.setText(value);
		input.setHint(hint);
		input.setSingleLine();
		input.setTextSize(13f);
		input.setTextColor(TEXT);
		input.setHintTextColor(TEXT_SOFT);
		input.setPadding(dp(12), dp(6), dp(12), dp(6));
		input.setBackground(rounded(PANEL_ROW, WINDOW_STROKE, 9f, 1));
		return input;
	}

	private TextView chip() {
		TextView view = new TextView(requireContext());
		view.setTextSize(13f);
		view.setTextColor(TEXT);
		view.setGravity(Gravity.CENTER);
		view.setBackground(rounded(PANEL_ACTIVE, CYAN, 999f, 1));
		return view;
	}

	private TextView titleText(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(18f);
		view.setTextColor(TEXT);
		view.setMaxLines(1);
		view.setEllipsize(TextUtils.TruncateAt.END);
		return view;
	}

	private TextView bodyText(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(13f);
		view.setTextColor(TEXT);
		applyTextSafe(view);
		return view;
	}

	private TextView hintText(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(12f);
		view.setTextColor(TEXT_DIM);
		// hintText 是辅助说明性文本，允许自然换行。少数需要单行的调用点
		// 已在局部手动 setMaxLines(1) + setEllipsize（如钱包地址、empty-state
		// 副标题），不受影响。
		return view;
	}

	/**
	 * P1: 默认单行 + 尾部省略号，避免长翻译在固定高度容器里 wrap
	 * 成两行被 clip 掉。需要多行的地方调用者自己 setMaxLines/关 ellipsize。
	 */
	private static void applyTextSafe(TextView view) {
		view.setSingleLine(true);
		view.setEllipsize(TextUtils.TruncateAt.END);
	}

	private TextView fieldLabel(String text) {
		TextView view = hintText(text);
		view.setTextColor(TEXT_DIM);
		return view;
	}

	private LinearLayout card(int fill, int stroke, float radiusDp) {
		LinearLayout card = new LinearLayout(requireContext());
		card.setOrientation(LinearLayout.VERTICAL);
		// P3: 所有卡组从 simple rounded 升级为亚克力，获得玻璃高光线。
		// 在子项（button/chip/input）仍使用 rounded（不需高光，避免叠加描边遮住子元素）。
		card.setBackground(HolographicTheme.acrylic(fill, stroke, radiusDp));
		return card;
	}

	private ScrollView scrollShell() {
		ScrollView scroll = new HoloScrollView(requireContext());
		scroll.setBackground(HolographicTheme.acrylic(PANEL_ROW, WINDOW_STROKE, 10f));
		wireScrollFocus(scroll);
		return scroll;
	}

	/**
	 * 经过研读 ModernUI 源码，确认：ScrollView 构造函数已默认
	 * setFocusable(true) + setVerticalScrollBarEnabled(true)，并且重载
	 * onGenericMotionEvent 处理 ACTION_SCROLL → fling/smoothScrollBy。
	 * 鼠标滚轮无需任何 focus 切换，只要事件命中 ScrollView 矩形即可生效。
	 *
	 * 所以这个方法保留为 no-op，纯做语义占位，避免散弹式删除调用点。
	 * 真正的滚轮诊断 + 兜底已经下沉到 HoloScrollView.dispatchGenericMotionEvent。
	 */
	private static void wireScrollFocus(ScrollView scroll) {
		// no-op：交给 HoloScrollView 默认行为 + 内置诊断日志。
	}

	private LinearLayout vertical() {
		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		return layout;
	}

	private View spacer(int height) {
		View view = new View(requireContext());
		view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
		return view;
	}

	private LinearLayout.LayoutParams weighted(int width, int height, float weight) {
		return new LinearLayout.LayoutParams(width, height, weight);
	}

	/**
	 * 货币图标——通过 {@link HolographicIconCache} 加载 PNG 走 ModernUI
	 * {@link ImageDrawable} + nearest filter，与 vanilla 物品栏的像素艺术风格一致。
	 *
	 * <p><b>为什么不再用 {@code MinecraftSurfaceView / vanilla GuiGraphics.renderItem}</b>：
	 * 那条路径走 raw draw 不被 ModernUI 父容器（如 {@link ScrollView}）的 clip / scroll
	 * 影响——放在列表里时图标会"飞出"列表区域。{@link ImageDrawable} 走 Canvas 路径，
	 * 完全受父容器管控。</p>
	 */
	private View currencyIconView(CryptoCurrency currency, int sizeDp) {
		ImageView iv = new ImageView(requireContext());
		ImageDrawable d = HolographicIconCache.currencyIcon(currency, dp(sizeDp));
		if (d != null) {
			iv.setImageDrawable(d);
		}
		iv.setLayoutParams(squareLayout(sizeDp));
		return iv;
	}

	/**
	 * 物品图标——同 {@link #currencyIconView}，走 PNG + nearest filter。
	 * PNG 加载失败时回退到原文字徽章。
	 */
	private View itemIconView(ResourceLocation itemId, String fallbackText, int sizeDp) {
		ImageDrawable d = HolographicIconCache.itemIcon(itemId, dp(sizeDp));
		if (d != null) {
			ImageView iv = new ImageView(requireContext());
			iv.setImageDrawable(d);
			iv.setLayoutParams(squareLayout(sizeDp));
			return iv;
		}
		TextView badge = new TextView(requireContext());
		badge.setText(fallbackText.isBlank() ? "?" : fallbackText.substring(0, 1).toUpperCase(Locale.ROOT));
		badge.setTextSize(11f);
		badge.setTextColor(0xFFFFFFFF);
		badge.setGravity(Gravity.CENTER);
		badge.setBackground(rounded(CYAN, 0, 999f, 0));
		badge.setLayoutParams(squareLayout(sizeDp));
		return badge;
	}

	private LinearLayout.LayoutParams squareLayout(int sizeDp) {
		return new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
	}

	/**
	 * 供 Drawable 上下文（如 {@code setCompoundDrawablesRelative}）使用的 helper——
	 * 与 {@link #currencyIconView} 共用 {@link HolographicIconCache} + nearest filter。
	 */
	private ImageDrawable currencyIcon(CryptoCurrency currency, int sizeDp) {
		return HolographicIconCache.currencyIcon(currency, dp(sizeDp));
	}

	private ImageDrawable itemIcon(ResourceLocation itemId, int sizeDp) {
		return HolographicIconCache.itemIcon(itemId, dp(sizeDp));
	}

	private void openPicker(PickerTarget target) {
		pickerTarget = pickerTarget == target ? PickerTarget.NONE : target;
		refreshPicker();
	}

	private void applyCurrencySelection(CryptoCurrency currency) {
		switch (pickerTarget) {
			case CONVERT_SOURCE -> {
				convertSourceCurrency = currency;
				if (convertSourceCurrency == convertTargetCurrency) {
					convertTargetCurrency = nextDifferentCurrency(currency);
				}
				convertReverseQuote = false;
			}
			case CONVERT_TARGET -> {
				convertTargetCurrency = currency;
				if (convertTargetCurrency == convertSourceCurrency) {
					convertSourceCurrency = nextDifferentCurrency(currency);
				}
			}
			case SHOP_SETTLEMENT -> shopSettlementCurrency = currency;
			case TRANSFER_CURRENCY -> transferCurrency = currency;
			case MINING_SETTLEMENT -> miningSettlementCurrency = currency;
			case NONE -> {
				return;
			}
		}
		pickerTarget = PickerTarget.NONE;
		requestRefresh(false, FocusTarget.NONE);
	}

	private CryptoCurrency pickerCurrentCurrency() {
		return switch (pickerTarget) {
			case CONVERT_SOURCE -> convertSourceCurrency;
			case CONVERT_TARGET -> convertTargetCurrency;
			case SHOP_SETTLEMENT -> shopSettlementCurrency;
			case TRANSFER_CURRENCY -> transferCurrency;
			case MINING_SETTLEMENT -> miningSettlementCurrency;
			case TRANSFER_PLAYER, NONE -> summaryCurrency();
		};
	}

	private void swapConvertCurrencies() {
		CryptoCurrency oldSource = convertSourceCurrency;
		convertSourceCurrency = convertTargetCurrency;
		convertTargetCurrency = oldSource;
		requestRefresh(false, FocusTarget.CONVERT_AMOUNT);
	}

	private void stashInputs() {
		// Pull the latest value out of each EditText BEFORE we rebuild the view tree, but
		// honour any explicit override that was scheduled since the last refresh so that
		// programmatic updates ("Max whole", paste target, mode toggle, …) are not clobbered
		// by the old widget state.
		if (walletAmountInput != null && !pendingInputOverrides.containsKey(FocusTarget.WALLET_AMOUNT)) {
			walletAmountText = walletAmountInput.getText().toString();
		}
		if (convertAmountInput != null && !pendingInputOverrides.containsKey(FocusTarget.CONVERT_AMOUNT)) {
			convertAmountText = convertAmountInput.getText().toString();
		}
		if (transferTargetInput != null && !pendingInputOverrides.containsKey(FocusTarget.TRANSFER_TARGET)) {
			transferTargetText = transferTargetInput.getText().toString();
		}
		if (transferAmountInput != null && !pendingInputOverrides.containsKey(FocusTarget.TRANSFER_AMOUNT)) {
			transferAmountText = transferAmountInput.getText().toString();
		}
		if (transferMemoInput != null && !pendingInputOverrides.containsKey(FocusTarget.TRANSFER_MEMO)) {
			transferMemoText = transferMemoInput.getText().toString();
		}
		if (shopSearchInput != null && !pendingInputOverrides.containsKey(FocusTarget.SHOP_SEARCH)) {
			shopSearchText = shopSearchInput.getText().toString();
		}
		if (miningAmountInput != null && !pendingInputOverrides.containsKey(FocusTarget.MINING_AMOUNT)) {
			miningAmountText = miningAmountInput.getText().toString();
		}
		pendingInputOverrides.clear();
	}

	private FocusTarget captureFocusTarget() {
		if (walletAmountInput != null && walletAmountInput.isFocused()) {
			return FocusTarget.WALLET_AMOUNT;
		}
		if (convertAmountInput != null && convertAmountInput.isFocused()) {
			return FocusTarget.CONVERT_AMOUNT;
		}
		if (transferTargetInput != null && transferTargetInput.isFocused()) {
			return FocusTarget.TRANSFER_TARGET;
		}
		if (transferAmountInput != null && transferAmountInput.isFocused()) {
			return FocusTarget.TRANSFER_AMOUNT;
		}
		if (transferMemoInput != null && transferMemoInput.isFocused()) {
			return FocusTarget.TRANSFER_MEMO;
		}
		if (shopSearchInput != null && shopSearchInput.isFocused()) {
			return FocusTarget.SHOP_SEARCH;
		}
		if (miningAmountInput != null && miningAmountInput.isFocused()) {
			return FocusTarget.MINING_AMOUNT;
		}
		return FocusTarget.NONE;
	}

	private void restoreFocus(FocusTarget target) {
		if (target == FocusTarget.NONE || root == null) {
			return;
		}
		root.post(() -> {
			switch (target) {
				case WALLET_AMOUNT -> {
					if (walletAmountInput != null) {
						walletAmountInput.requestFocus();
					}
				}
				case CONVERT_AMOUNT -> {
					if (convertAmountInput != null) {
						convertAmountInput.requestFocus();
					}
				}
				case TRANSFER_TARGET -> {
					if (transferTargetInput != null) {
						transferTargetInput.requestFocus();
					}
				}
				case TRANSFER_AMOUNT -> {
					if (transferAmountInput != null) {
						transferAmountInput.requestFocus();
					}
				}
				case TRANSFER_MEMO -> {
					if (transferMemoInput != null) {
						transferMemoInput.requestFocus();
					}
				}
				case SHOP_SEARCH -> {
					if (shopSearchInput != null) {
						shopSearchInput.requestFocus();
					}
				}
				case MINING_AMOUNT -> {
					if (miningAmountInput != null) {
						miningAmountInput.requestFocus();
					}
				}
				case NONE -> {
				}
			}
		});
	}

	private CryptoCurrency summaryCurrency() {
		return switch (currentPage) {
			case CONVERT -> convertSourceCurrency;
			case SHOP -> shopSettlementCurrency;
			case TRANSFER -> transferCurrency;
			case MINING -> miningSettlementCurrency;
			case WALLET -> selectedWalletCurrency;
		};
	}

	private String actionTitle() {
		return switch (currentPage) {
			case WALLET -> t("screen.virtual_craft.holographic_shop.wallet.actions");
			case CONVERT -> t("screen.virtual_craft.holographic_shop.convert.actions");
			case SHOP -> t("screen.virtual_craft.holographic_shop.shop.actions");
			case TRANSFER -> t("screen.virtual_craft.holographic_shop.transfer.actions");
			case MINING -> t("screen.virtual_craft.holographic_shop.mining.actions");
		};
	}

	private String actionSubtitle() {
		return switch (currentPage) {
			case WALLET -> selectedWalletCurrency.displayName().getString();
			case CONVERT -> "";
			case SHOP -> {
				ShopOffer offer = selectedOffer();
				yield offer == null ? t("screen.virtual_craft.holographic_shop.shop.empty") : offer.displayName().getString();
			}
			case TRANSFER -> transferCurrency.ticker();
			case MINING -> miningSettlementCurrency.ticker();
		};
	}

	private String convertPairLabel() {
		return convertSourceCurrency.ticker() + " -> " + convertTargetCurrency.ticker();
	}

	private String rateStatusLabel() {
		return switch (exchangeSnapshot.status()) {
			case LIVE -> t("screen.virtual_craft.holographic_shop.status.live");
			case CACHED -> t("screen.virtual_craft.holographic_shop.status.cached");
			case STALE -> t("screen.virtual_craft.holographic_shop.status.stale");
			case DEFAULT -> t("screen.virtual_craft.holographic_shop.status.default");
		};
	}

	private int rateStatusColor() {
		return switch (exchangeSnapshot.status()) {
			case LIVE -> GREEN;
			case CACHED -> CYAN;
			case STALE, DEFAULT -> AMBER;
		};
	}

	private String convertRateText() {
		long oneSourceMinor = convertSourceCurrency.wholeUnitMinor();
		long rateTargetMinor = CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, oneSourceMinor, exchangeSnapshot, RoundingMode.HALF_UP);
		return t("screen.virtual_craft.holographic_shop.convert.rate", convertSourceCurrency.ticker(), convertTargetCurrency.formatCompact(rateTargetMinor), convertTargetCurrency.ticker());
	}

	private String convertModeLabel() {
		return t(convertReverseQuote ? "screen.virtual_craft.holographic_shop.convert.mode.target" : "screen.virtual_craft.holographic_shop.convert.mode.source");
	}

	private String convertModeShortLabel() {
		return convertReverseQuote
				? t("screen.virtual_craft.holographic_shop.convert.target_short", convertTargetCurrency.ticker())
				: t("screen.virtual_craft.holographic_shop.convert.source_short", convertSourceCurrency.ticker());
	}

	private String convertSummaryLine() {
		return t("screen.virtual_craft.holographic_shop.convert.summary", formatTickerAmount(convertSourceCurrency, convertSourceAmount()), formatTickerAmount(convertTargetCurrency, convertTargetAmount()));
	}

	private String convertProblem() {
		long sourceAmount = convertSourceAmount();
		if (convertSourceCurrency == convertTargetCurrency) {
			return t("screen.virtual_craft.holographic_shop.convert.same");
		}
		if (sourceAmount <= 0) {
			return t("screen.virtual_craft.holographic_shop.invalid_amount");
		}
		if (sourceAmount > balances[convertSourceCurrency.ordinal()]) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_balance");
		}
		return "";
	}

	private long convertSourceAmount() {
		if (!convertReverseQuote) {
			return parseAmount(convertSourceCurrency, convertAmountText);
		}
		long targetDesired = parseAmount(convertTargetCurrency, convertAmountText);
		return CurrencyConversionService.convert(convertTargetCurrency, convertSourceCurrency, targetDesired, exchangeSnapshot, RoundingMode.UP);
	}

	private long convertTargetAmount() {
		if (convertReverseQuote) {
			return parseAmount(convertTargetCurrency, convertAmountText);
		}
		return CurrencyConversionService.convert(convertSourceCurrency, convertTargetCurrency, parseAmount(convertSourceCurrency, convertAmountText), exchangeSnapshot, RoundingMode.HALF_UP);
	}

	private long walletWithdrawMinor() {
		return safeMultiply(parseWhole(walletAmountText), selectedWalletCurrency.wholeUnitMinor());
	}

	private long walletDepositMinor() {
		return safeMultiply(parseWhole(walletAmountText), selectedWalletCurrency.wholeUnitMinor());
	}

	private String walletDepositProblem() {
		return walletDepositProblem(parseWhole(walletAmountText), inventoryWholeItemCount(selectedWalletCurrency));
	}

	private String walletDepositProblem(long wholeCount, long depositable) {
		if (wholeCount <= 0) {
			return t("screen.virtual_craft.holographic_shop.invalid_amount");
		}
		if (wholeCount > depositable) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_balance");
		}
		return "";
	}

	private String walletDepositSummary() {
		return walletDepositSummary(parseWhole(walletAmountText));
	}

	private String walletDepositSummary(long wholeCount) {
		wholeCount = Math.max(0L, wholeCount);
		return t("screen.virtual_craft.holographic_shop.wallet.deposit_amount_summary", wholeCount, selectedWalletCurrency.ticker());
	}

	private String walletWithdrawProblem() {
		return walletWithdrawProblem(parseWhole(walletAmountText), wholeItemCount(selectedWalletCurrency));
	}

	private String walletWithdrawProblem(long wholeCount, long withdrawable) {
		if (wholeCount <= 0) {
			return t("screen.virtual_craft.holographic_shop.invalid_amount");
		}
		if (wholeCount > withdrawable) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_balance");
		}
		return "";
	}

	private String walletWithdrawSummary() {
		return walletWithdrawSummary(parseWhole(walletAmountText));
	}

	private String walletWithdrawSummary(long wholeCount) {
		wholeCount = Math.max(0L, wholeCount);
		return t("screen.virtual_craft.holographic_shop.wallet.withdraw_summary", wholeCount, selectedWalletCurrency.ticker());
	}

	private long shopUnitBuyMinor(ShopOffer offer) {
		return CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.priceUsdtMinor(), exchangeSnapshot, RoundingMode.UP);
	}

	private long shopUnitSellMinor(ShopOffer offer) {
		return CurrencyConversionService.convertUsdtMinorTo(shopSettlementCurrency, offer.sellUsdtMinor(), exchangeSnapshot, RoundingMode.DOWN);
	}

	private long shopBuyTotal(int quantity) {
		return shopBuyTotal(quantity, selectedOffer());
	}

	private long shopBuyTotal(int quantity, ShopOffer offer) {
		return offer == null ? 0L : safeMultiply(shopUnitBuyMinor(offer), Math.max(1, quantity));
	}

	private long shopSellTotal(int quantity) {
		return shopSellTotal(quantity, selectedOffer());
	}

	private long shopSellTotal(int quantity, ShopOffer offer) {
		return offer == null ? 0L : safeMultiply(shopUnitSellMinor(offer), Math.max(1, quantity));
	}

	private String shopBuyProblem(int quantity) {
		ShopOffer offer = selectedOffer();
		return shopBuyProblem(quantity, offer, currentShopBuyMax(offer));
	}

	private String shopBuyProblem(int quantity, ShopOffer offer, int buyMax) {
		if (offer == null || quantity <= 0 || quantity > buyMax) {
			return t("screen.virtual_craft.holographic_shop.shop.insufficient_total");
		}
		return "";
	}

	private String shopSellProblem(int quantity) {
		ShopOffer offer = selectedOffer();
		return shopSellProblem(quantity, offer, currentShopSellMax(offer));
	}

	private String shopSellProblem(int quantity, ShopOffer offer, int sellMax) {
		if (offer == null || quantity <= 0 || quantity > sellMax) {
			return t("screen.virtual_craft.holographic_shop.shop.no_inventory");
		}
		return "";
	}

	private String transferProblem() {
		long amount = parseAmount(transferCurrency, transferAmountText);
		String target = transferTargetText.trim();
		if (target.isBlank()) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_target");
		}
		if (!isOnlineTransferTarget(target)) {
			return t("screen.virtual_craft.holographic_shop.transfer.invalid_target_ui");
		}
		if (amount <= 0) {
			return t("screen.virtual_craft.holographic_shop.invalid_amount");
		}
		CryptoCurrency gasCurrency = WalletEconomyRules.TRANSFER_GAS_CURRENCY;
		if (balances[gasCurrency.ordinal()] < WalletEconomyRules.TRANSFER_GAS_MINOR) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_gas");
		}
		// 转账币种本身就是 gas 货币时，金额 + gas 必须同时在余额内。
		long required = transferCurrency == gasCurrency ? amount + WalletEconomyRules.TRANSFER_GAS_MINOR : amount;
		if (balances[transferCurrency.ordinal()] < required) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_balance");
		}
		return "";
	}

	private String transferSummary() {
		long amount = parseAmount(transferCurrency, transferAmountText);
		// gas 金额以 TRANSFER_GAS_CURRENCY 格式化（现在是 TRX），lang summary 模板拼 "TRX" 纯string 结尾。
		CryptoCurrency gasCurrency = WalletEconomyRules.TRANSFER_GAS_CURRENCY;
		String target = transferTargetText.trim();
		if (target.isBlank()) {
			return t("screen.virtual_craft.holographic_shop.transfer.summary_pending",
					formatTickerAmount(transferCurrency, amount),
					gasCurrency.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR));
		}
		return t("screen.virtual_craft.holographic_shop.transfer.summary", target, formatTickerAmount(transferCurrency, amount), gasCurrency.formatCompact(WalletEconomyRules.TRANSFER_GAS_MINOR));
	}

	private long miningPayoutMinor() {
		return miningPayoutMinor(parseWhole(miningAmountText));
	}

	private long miningPayoutMinor(long count) {
		long btcMinor = safeMultiply(HashVoucherItem.BTC_PAYOUT_MINOR, Math.max(0L, count));
		return CurrencyConversionService.convert(CryptoCurrency.BTC, miningSettlementCurrency, btcMinor, exchangeSnapshot, RoundingMode.DOWN);
	}

	private String miningSummaryLine() {
		return miningSummaryLine(parseWhole(miningAmountText));
	}

	private String miningSummaryLine(long count) {
		return t("screen.virtual_craft.holographic_shop.mining.summary", Math.max(0L, count), formatTickerAmount(miningSettlementCurrency, miningPayoutMinor(count)));
	}

	private String miningProblem() {
		return miningProblem(parseWhole(miningAmountText), countHashVouchers());
	}

	private String miningProblem(long count, int vouchers) {
		if (count <= 0) {
			return t("screen.virtual_craft.holographic_shop.mining.none");
		}
		if (count > vouchers) {
			return t("screen.virtual_craft.holographic_shop.transfer.need_balance");
		}
		return "";
	}

	private String ledgerTitle(SyncWalletPayload.Entry entry) {
		return HolographicText.ledgerTitle(entry, key -> t(key));
	}

	private String ledgerDetail(SyncWalletPayload.Entry entry) {
		return HolographicText.ledgerDetail(entry, offers, key -> t(key));
	}

	private String pickerPrompt() {
		return switch (pickerTarget) {
			case CONVERT_SOURCE -> t("screen.virtual_craft.holographic_shop.convert.pick_source");
			case CONVERT_TARGET -> t("screen.virtual_craft.holographic_shop.convert.pick_target");
			case SHOP_SETTLEMENT, MINING_SETTLEMENT -> t("screen.virtual_craft.holographic_shop.shop_currency");
			case TRANSFER_CURRENCY -> t("screen.virtual_craft.holographic_shop.transfer.currency");
			case TRANSFER_PLAYER -> t("screen.virtual_craft.holographic_shop.transfer.player_picker");
			case NONE -> "";
		};
	}

	private boolean isOnlineTransferTarget(String target) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && target.equalsIgnoreCase(minecraft.player.getGameProfile().getName())) {
			return false;
		}
		if (minecraft.getConnection() == null) {
			return false;
		}
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			if (target.equalsIgnoreCase(info.getProfile().getName())) {
				return true;
			}
		}
		return false;
	}

	private String readClipboard() {
		try {
			return Minecraft.getInstance().keyboardHandler.getClipboard().trim();
		} catch (Exception ignored) {
			return transferTargetText;
		}
	}

	private void refreshPlayerSuggestions() {
		suggestedPlayers.clear();
		String filter = transferTargetText.trim().toLowerCase(Locale.ROOT);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() == null) {
			return;
		}
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().getName();
			if (minecraft.player != null && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName())) {
				continue;
			}
			if (filter.isBlank() || name.toLowerCase(Locale.ROOT).contains(filter)) {
				suggestedPlayers.add(name);
				if (suggestedPlayers.size() >= 4) {
					return;
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

	private int currentShopBuyMax() {
		return currentShopBuyMax(selectedOffer());
	}

	private int currentShopBuyMax(ShopOffer offer) {
		if (offer == null || offer.priceUsdtMinor() <= 0) {
			return 0;
		}
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		return (int) Math.max(0L, totalUsdtMinor / offer.priceUsdtMinor());
	}

	private int currentShopSellMax() {
		return currentShopSellMax(selectedOffer());
	}

	private int currentShopSellMax(ShopOffer offer) {
		if (offer == null || offer.count() <= 0) {
			return 0;
		}
		return countInventoryItems(offer) / offer.count();
	}

	private int maxShopQuantity() {
		return maxShopQuantity(currentShopBuyMax(), currentShopSellMax());
	}

	private int maxShopQuantity(int buyMax, int sellMax) {
		return Math.max(buyMax, sellMax);
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

	private void sendShopAction(ShopActionPayload.Action action) {
		ShopOffer offer = selectedOffer();
		if (offer == null) {
			return;
		}
		PacketDistributor.sendToServer(new ShopActionPayload(sessionId, action, selectedOfferIndex, Math.max(1, parseInt(shopQuantityText, 1)), shopSettlementCurrency.id()));
	}

	private void sendWalletDeposit() {
		stashInputs();
		if (!walletDepositProblem().isBlank()) {
			requestRefresh(false, FocusTarget.WALLET_AMOUNT);
			return;
		}
		PacketDistributor.sendToServer(new WalletActionPayload(
				sessionId,
				WalletActionPayload.Action.DEPOSIT_SELECTED,
				selectedWalletCurrency.id(),
				"",
				walletDepositMinor(),
				""));
	}

	private void sendWalletWithdraw(WalletActionPayload.Action action) {
		stashInputs();
		if (!walletWithdrawProblem().isBlank()) {
			requestRefresh(false, FocusTarget.WALLET_AMOUNT);
			return;
		}
		CryptoCurrency currency = action == WalletActionPayload.Action.WITHDRAW_EMERALDS
				? CryptoCurrency.USDT
				: selectedWalletCurrency;
		PacketDistributor.sendToServer(new WalletActionPayload(sessionId, action, currency.id(), "", walletWithdrawMinor(), ""));
	}

	private void sendConvertAction() {
		stashInputs();
		if (!convertProblem().isBlank()) {
			requestRefresh(false, FocusTarget.CONVERT_AMOUNT);
			return;
		}
		PacketDistributor.sendToServer(new WalletActionPayload(
				sessionId,
				WalletActionPayload.Action.CONVERT,
				convertSourceCurrency.id(),
				convertTargetCurrency.id(),
				convertSourceAmount(),
				""));
	}

	private void sendTransferAction() {
		stashInputs();
		refreshPlayerSuggestions();
		if (!transferProblem().isBlank()) {
			requestRefresh(false, transferTargetText.trim().isBlank() ? FocusTarget.TRANSFER_TARGET : FocusTarget.TRANSFER_AMOUNT);
			return;
		}
		PacketDistributor.sendToServer(new WalletActionPayload(
				sessionId,
				WalletActionPayload.Action.TRANSFER,
				transferCurrency.id(),
				"",
				parseAmount(transferCurrency, transferAmountText),
				transferTargetText.trim(),
				transferMemoText.trim()));
	}

	private void sendMiningAction() {
		stashInputs();
		if (!miningProblem().isBlank()) {
			requestRefresh(false, FocusTarget.MINING_AMOUNT);
			return;
		}
		PacketDistributor.sendToServer(new WalletActionPayload(
				sessionId,
				WalletActionPayload.Action.SETTLE_MINING,
				miningSettlementCurrency.id(),
				"",
				parseWhole(miningAmountText),
				""));
	}

	private long inventoryWholeItemCount(CryptoCurrency currency) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 0L;
		}
		long total = 0L;
		for (var stack : player.getInventory().items) {
			if (currency.matches(stack) || (currency == CryptoCurrency.USDT && stack.is(net.minecraft.world.item.Items.EMERALD))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private long wholeItemCount(CryptoCurrency currency) {
		return Math.max(0L, balances[currency.ordinal()] / currency.wholeUnitMinor());
	}

	private long spendableBalance(CryptoCurrency currency, boolean reserveTransferGas) {
		long balance = balances[currency.ordinal()];
		// gas 货币部分余额要预留，避免“点全部转账”后没钱付 gas 被服务端拒接。
		if (reserveTransferGas && currency == WalletEconomyRules.TRANSFER_GAS_CURRENCY) {
			return Math.max(0L, balance - WalletEconomyRules.TRANSFER_GAS_MINOR);
		}
		return balance;
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
			return Integer.parseInt(text.trim());
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private int progressForAmount(long amount, long maxAmount) {
		if (maxAmount <= 0L || amount <= 0L) {
			return 0;
		}
		if (amount >= maxAmount) {
			return AMOUNT_SLIDER_STEPS;
		}
		return clampInt((int) Math.round((double) amount * AMOUNT_SLIDER_STEPS / (double) maxAmount), 0, AMOUNT_SLIDER_STEPS);
	}

	private long amountForProgress(long maxAmount, int progress) {
		int safeProgress = clampInt(progress, 0, AMOUNT_SLIDER_STEPS);
		if (maxAmount <= 0L || safeProgress <= 0) {
			return 0L;
		}
		if (safeProgress >= AMOUNT_SLIDER_STEPS) {
			return maxAmount;
		}
		long base = maxAmount / AMOUNT_SLIDER_STEPS;
		long remainder = maxAmount % AMOUNT_SLIDER_STEPS;
		return base * safeProgress + (remainder * safeProgress) / AMOUNT_SLIDER_STEPS;
	}

	private long safeMultiply(long a, long b) {
		try {
			return Math.multiplyExact(a, b);
		} catch (ArithmeticException ignored) {
			return Long.MAX_VALUE;
		}
	}

	private CryptoCurrency nextDifferentCurrency(CryptoCurrency currency) {
		CryptoCurrency[] currencies = CryptoCurrency.values();
		return currencies[(currency.ordinal() + 1) % currencies.length];
	}

	private int currencyColor(CryptoCurrency currency) {
		return HolographicTheme.currencyColor(currency);
	}

	private ShapeDrawable rounded(int fill, int stroke, float radiusDp, int strokeWidthDp) {
		return HolographicTheme.rounded(fill, stroke, dp(radiusDp), dp(strokeWidthDp));
	}

	/**
	 * 返回完整钱包地址。地址是 {@code "VC-" + 10 字符 hex}，总长 13。
	 * sidebar metaCard 区域宽裕、能装下完整地址，不再用 {@link #shortWalletAddress()} 的 9 字符缩写。
	 */
	private String displayWalletAddress() {
		return walletAddress == null || walletAddress.isBlank() ? "VC-UNKNOWN" : walletAddress;
	}

	private String shortWalletAddress() {
		if (walletAddress == null || walletAddress.isBlank()) {
			return "VC-UNKNOWN";
		}
		// 5+…+3 = 9 字符，能塞进 nav 侧栏 dp(126) 的可视宽度；
		// 点击请复制钱包地址的 header handler 仍拿完整 walletAddress。
		return walletAddress.length() <= 9 ? walletAddress
				: walletAddress.substring(0, 5) + "\u2026" + walletAddress.substring(walletAddress.length() - 3);
	}

	private String formatTickerAmount(CryptoCurrency currency, long minorAmount) {
		return HolographicText.tickerAmount(currency, minorAmount);
	}

	private String formatAmount(String currencyId, long amountMinor) {
		return HolographicText.amount(currencyId, amountMinor);
	}

	private String t(String key, Object... args) {
		return net.minecraft.network.chat.Component.translatable(key, args).getString();
	}

	private int dp(float value) {
		return root == null ? Math.round(value) : root.dp(value);
	}
}
