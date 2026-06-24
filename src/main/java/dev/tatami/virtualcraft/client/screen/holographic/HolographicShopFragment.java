package dev.tatami.virtualcraft.client.screen.holographic;

import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.ACCENT_LINE;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.AMBER;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.CYAN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.GREEN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.RED;
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
import dev.tatami.virtualcraft.contract.DailyContractSnapshot;
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
import icyllis.modernui.view.KeyEvent;
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
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicShopFragment extends Fragment implements ScreenCallback {
	private static final int AMOUNT_SLIDER_STEPS = 1000;

	/**
	 * Distinct, rarity-flavoured palette for the voucher mix bar — indexed by
	 * tier position so each tier keeps a stable colour: cyan -> indigo ->
	 * violet -> gold (common to legendary).
	 */
	private static final int[] VOUCHER_TIER_COLORS = {0xFF0EA5E9, 0xFF6366F1, 0xFF8B5CF6, 0xFFF59E0B};

	private record VoucherTierCount(Item item, int count, long btcMinor) {
	}

	private record VoucherSummary(List<VoucherTierCount> tiers, int totalCount, long totalBtcMinor) {
	}

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
	private final List<DailyContractSnapshot> contractCache = new ArrayList<>();

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

	private String walletAddress = "VC-UNKNOWN";
	private ExchangeRateSnapshot exchangeSnapshot = ExchangeRateSnapshot.defaultSnapshot();
	// Remembers the last tab across terminal re-opens within a session, so the
	// player returns to where they were (e.g. Mining/Contracts) instead of being
	// bounced back to Wallet every time they reopen the terminal.
	private static Page lastOpenedPage = Page.WALLET;
	private Page currentPage = lastOpenedPage;
	// When true, the next content rebuild plays a fade/slide-in transition. Set on
	// real page switches (and first open) only, so in-page refreshes don't flicker.
	private boolean pageTransitionPending;
	// Visual order of the nav column — indexes the 1..6 keyboard shortcuts.
	private static final Page[] NAV_ORDER = {
			Page.MARKET, Page.CONVERT, Page.CONTRACTS, Page.WALLET, Page.TRANSFER, Page.SHOP, Page.MINING };
	// Trading pairs shown in the MARKET page selector + markets list.
	private static final CryptoCurrency[] MARKET_PAIRS = {
			CryptoCurrency.BTC, CryptoCurrency.ETH, CryptoCurrency.SOL,
			CryptoCurrency.DOGE, CryptoCurrency.TRX };
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

	// --- v26 holographic terminal interaction polish ---
	// #1 Preserve inner ScrollView offsets across full view-tree rebuilds. Keyed by a
	//    stable per-list id so an in-page refresh (selecting a row, dragging a slider)
	//    no longer snaps long lists back to the top. Cleared on explicit page switches.
	private final java.util.Map<String, Integer> savedScrollY = new java.util.HashMap<>();
	private final java.util.Map<String, ScrollView> liveScrollShells = new java.util.HashMap<>();
	// #2 Live-update the action-panel summary card while a slider is dragged. The retained
	//    views are updated in place instead of rebuilding the tree, because a rebuild would
	//    destroy the SeekBar mid-drag and abort the gesture.
	private java.util.function.Supplier<StatusModel> liveStatusModel;
	private LinearLayout liveStatusCardView;
	private TextView liveStatusSummaryView;
	private TextView liveStatusIssueView;
	// #3 Enter-to-submit: the active page's primary action (withdraw/convert/transfer/...).
	private Runnable primaryAction;

	/** Immutable view-model for the action-panel summary card (see {@link #liveStatusCard}). */
	private record StatusModel(String summary, String issue, int accent) {
	}

	// --- Refresh debounce ---
	// Multiple sync packets (wallet, exchange, preferences, catalog) routinely
	// arrive in the same client tick after openMenu, and each used to drive a
	// full view-tree rebuild via root.post(). Collapsing them into a single
	// pass removes the "flash of old layout" players saw immediately after
	// opening the terminal, and roughly halves the worst-case rebuild cost.
	private boolean refreshScheduled;
	private boolean refreshIncludeHeader;
	private FocusTarget refreshFocusTarget = FocusTarget.NONE;
	private boolean exchangeRefreshPending;
	private long lastExchangeSyncRebuildMs;
	private long exchangeRefreshStartedAtMs;
	private Button headerRefreshButton;
	private int exchangeRefreshRequestId;
	// v24: success-flash window. applyExchangeSync sets this to (now + 1200 ms),
	// and the header rebuild path reads it to flash the refresh button green with
	// “同步成功” before reverting to “刷新”. Expired timestamps are
	// cleared in-place during the rebuild check so no extra cleanup hook is needed.
	private long exchangeRefreshSuccessUntilMs;
	// v24 tick-driven: timestamp when a snapshot arrived while pending=true.
	// Held here (not promoted directly) so the tick handler can flip pending
	// → success only AFTER the 300 ms minimum AMBER window has elapsed.
	// 0 = no deferred flip queued. Tick reads + clears this.
	private long exchangeRefreshSnapshotArrivedMs;
	// v24 tick-driven: dedup flag so the 20 Hz client tick doesn't enqueue a
	// fresh root.post(applyRefreshButtonState) every frame while a transition
	// is in flight. Set when we post, cleared by the posted Runnable.
	private boolean refreshStateUpdateScheduled;

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
			this.contractCache.addAll(cachedPrefs.contracts());
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

	/**
	 * v24 tick-driven refresh button: called from VirtualCraftClient’s
	 * ClientTickEvent.Post listener at 20 Hz. Walks the currently displayed
	 * screen and, if it’s a HolographicShopFragment with active refresh state,
	 * advances the state machine and repaints the button.
	 *
	 * <p>Replaces an earlier postDelayed-chained design that silently failed
	 * because ModernUI’s View.postDelayed does not reliably fire on the
	 * Minecraft render thread.</p>
	 */
	public static void tickActiveRefreshButton() {
		if (!(Minecraft.getInstance().screen instanceof MuiScreen mui)) {
			return;
		}
		if (!(mui.getFragment() instanceof HolographicShopFragment fragment)) {
			return;
		}
		// Cheap early-out: if no refresh state is active, nothing to do.
		// (Pure state reads, safe on render thread.)
		if (!fragment.exchangeRefreshPending
				&& fragment.exchangeRefreshSuccessUntilMs == 0L
				&& fragment.exchangeRefreshSnapshotArrivedMs == 0L) {
			return;
		}
		// CRITICAL: ClientTickEvent.Post fires on the Render thread, but
		// applyRefreshButtonState calls View.setText which mutates ModernUI
		// views and MUST run on ModernUI's UI thread (otherwise the engine
		// asserts and the client crashes via Core.checkUiThread). Dispatch
		// via root.post(Runnable) — that posts to the UI thread queue and
		// executes on the next UI cycle.
		//
		// The refreshStateUpdateScheduled guard prevents the 20 Hz tick from
		// enqueuing 20 redundant Runnables per second while a 1.2 s success
		// flash is in flight. One queued update at a time is enough; the
		// state machine is idempotent and converges on next tick if needed.
		if (fragment.root == null || fragment.refreshStateUpdateScheduled) {
			return;
		}
		fragment.refreshStateUpdateScheduled = true;
		fragment.root.post(() -> {
			fragment.refreshStateUpdateScheduled = false;
			fragment.applyRefreshButtonState();
		});
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
		if (currentPage == Page.TRANSFER) {
			refreshPlayerSuggestions();
		}
		pageTransitionPending = true;
		refreshPage(FocusTarget.NONE);
		// Number-key tab switching (1..6). The root holds focus by default so the
		// shortcuts work immediately on open; once a text field is focused it
		// naturally consumes the digit (typing an amount), so we never fight input.
		root.setFocusable(true);
		root.setFocusableInTouchMode(true);
		root.setOnKeyListener((v, keyCode, event) -> handleNavShortcut(keyCode, event));
		root.post(root::requestFocus);
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
		for (dev.tatami.virtualcraft.currency.CryptoCurrency c : dev.tatami.virtualcraft.currency.CryptoCurrency.values()) {
			java.math.BigDecimal price = snapshot.quoteToUsdt(c);
			HolographicShopClientCache.recordPrice(c, price);
			HolographicMarketData.updateLivePrice(c, price);
		}
		if (exchangeRefreshPending && exchangeRefreshSnapshotArrivedMs == 0L) {
			exchangeRefreshSnapshotArrivedMs = System.currentTimeMillis();
		}
		long now = System.currentTimeMillis();
		// On the MARKET (K-line) page we repaint just the live chart / price widgets in
		// place on every tick — no full-screen rebuild — so the chart updates in real time
		// without the 5s throttle that made it feel sluggish. A pending manual refresh
		// still falls through to the normal rebuild path below.
		if (currentPage == Page.MARKET && !exchangeRefreshPending) {
			if (root != null && !marketUpdateScheduled) {
				marketUpdateScheduled = true;
				root.post(() -> {
					marketUpdateScheduled = false;
					populateMarketDynamic();
				});
			}
			return;
		}
		if (exchangeRefreshPending || now - lastExchangeSyncRebuildMs >= 5000L) {
			lastExchangeSyncRebuildMs = now;
			requestRefresh(true, captureFocusTarget());
		}
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
		contractCache.clear();
		contractCache.addAll(payload.contracts());
		HolographicShopClientCache.storePreferences(payload);
		if (currentPage == Page.SHOP || currentPage == Page.TRANSFER || currentPage == Page.MINING || currentPage == Page.CONTRACTS) {
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

		long nowMs = System.currentTimeMillis();
		if (exchangeRefreshPending && nowMs - exchangeRefreshStartedAtMs > 6000L) {
			exchangeRefreshPending = false;
		}
		if (exchangeRefreshSuccessUntilMs > 0L && nowMs >= exchangeRefreshSuccessUntilMs) {
			exchangeRefreshSuccessUntilMs = 0L;
		}
		boolean refreshShowSuccess = exchangeRefreshSuccessUntilMs > nowMs && !exchangeRefreshPending;
		String refreshKey;
		int refreshAccent;
		if (exchangeRefreshPending) {
			refreshKey = "screen.virtual_craft.holographic_shop.refreshing";
			refreshAccent = AMBER;
		} else if (refreshShowSuccess) {
			refreshKey = "screen.virtual_craft.holographic_shop.refresh_success";
			refreshAccent = GREEN;
		} else {
			refreshKey = "screen.virtual_craft.holographic_shop.refresh";
			refreshAccent = CYAN;
		}
		Button refreshButton = headerIconButton(t(refreshKey), refreshAccent, this::requestExchangeRefresh);
		refreshButton.setTooltipText(t("screen.virtual_craft.holographic_shop.refresh_tooltip"));
		headerRefreshButton = refreshButton;
		var refreshParams = new LinearLayout.LayoutParams(dp(HEADER_REFRESH_WIDTH_DP), dp(30));
		refreshParams.setMargins(0, 0, dp(10), 0);
		header.addView(refreshButton, refreshParams);

		headerValue = chip();
		header.addView(headerValue, new LinearLayout.LayoutParams(dp(170), dp(30)));

		headerStatus = chip();
		var statusParams = new LinearLayout.LayoutParams(dp(96), dp(30));
		statusParams.setMargins(dp(8), 0, 0, 0);
		header.addView(headerStatus, statusParams);

return header;
	}

	private void requestExchangeRefresh() {
		if (exchangeRefreshPending) {
			return;
		}
		exchangeRefreshPending = true;
		exchangeRefreshStartedAtMs = System.currentTimeMillis();
		// Any in-flight success flash or queued promotion from a previous sync is stale.
		exchangeRefreshSuccessUntilMs = 0L;
		exchangeRefreshSnapshotArrivedMs = 0L;
		exchangeRefreshRequestId++;
		// v24 tick-driven: paint “同步中” immediately on click so the user gets
		// instant feedback. All time-based transitions (900 ms timeout, 300 ms
		// min AMBER → GREEN flip, 1200 ms GREEN → CYAN revert) are owned by the
		// ClientTickEvent.Post handler calling applyRefreshButtonState() at 20 Hz.
		// View.postDelayed is NOT used because it doesn’t reliably fire on the
		// Minecraft render thread under ModernUI — callbacks silently drop.
		applyRefreshButtonState();
		PacketDistributor.sendToServer(new WalletActionPayload(sessionId, WalletActionPayload.Action.REFRESH_RATES, "", "", 0L, ""));
		requestRefresh(true, captureFocusTarget());
	}

	private void refreshHeader() {
		long totalUsdtMinor = CurrencyConversionService.totalWalletValueUsdtMinor(balances, exchangeSnapshot);
		headerValue.setText(CryptoCurrency.USDT.formatCompact(totalUsdtMinor) + " USDT");
		// v24: status chip stays on the real rate-status label even during a
		// refresh — progress is now owned by the refresh button alone.
		headerStatus.setText(rateStatusLabel());
		headerStatus.setTextColor(rateStatusColor());
		// v24 fix: header is built once in onCreateView; refreshHeader is the
		// only update path the rebuild scheduler invokes. Re-apply the refresh
		// button state here so click → “同步中” → “同步成功” → “刷新”
		// transitions actually become visible on screen.
		applyRefreshButtonState();
		// 原用 shortWalletAddress() 主动取中间裁出 "VC-38...1F6" 省略号。用户明确要求不要省略号，
		// 改走完整 displayWalletAddress（上文 onCreateView 里 headerAddress 已 setMaxLines(2)+不裁断）。

	}

	/**
	 * v24: single source of truth for the refresh button's three visual states
	 * (idle “刷新” CYAN / pending “同步中” AMBER / success “同步成功”
	 * GREEN). Called from refreshHeader (so the scheduled rebuild path picks
	 * up state changes) and from requestExchangeRefresh (so click feels
	 * instant instead of waiting one frame for root.post to fire).
	 */
	private void applyRefreshButtonState() {
		if (headerRefreshButton == null) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		// v24 tick-driven state machine. Idempotent; safe to call every tick.
		// (1) Promote a queued snapshot to success once the 300 ms min AMBER
		//     window has elapsed. This guarantees the user sees “同步中”.
		if (exchangeRefreshSnapshotArrivedMs > 0L && exchangeRefreshPending
				&& nowMs - exchangeRefreshStartedAtMs >= 300L) {
			exchangeRefreshPending = false;
			exchangeRefreshSuccessUntilMs = nowMs + 1200L;
			exchangeRefreshSnapshotArrivedMs = 0L;
		}
		// (2) Timeout fallback: if no snapshot ever arrives, clear pending
		//     after 900 ms so the button doesn’t stay stuck on AMBER.
		if (exchangeRefreshPending && nowMs - exchangeRefreshStartedAtMs > 6000L) {
			exchangeRefreshPending = false;
			exchangeRefreshSnapshotArrivedMs = 0L;
		}
		// (3) Expire success window so the button reverts to CYAN “刷新”.
		if (exchangeRefreshSuccessUntilMs > 0L && nowMs >= exchangeRefreshSuccessUntilMs) {
			exchangeRefreshSuccessUntilMs = 0L;
		}
		String key;
		int accent;
		if (exchangeRefreshPending) {
			key = "screen.virtual_craft.holographic_shop.refreshing";
			accent = AMBER;
		} else if (exchangeRefreshSuccessUntilMs > nowMs) {
			key = "screen.virtual_craft.holographic_shop.refresh_success";
			accent = GREEN;
		} else {
			key = "screen.virtual_craft.holographic_shop.refresh";
			accent = CYAN;
		}
		headerRefreshButton.setText(t(key));
		headerRefreshButton.setBackground(rounded(PANEL_BUTTON, accent, 9f, 1));
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
		stashScrollPositions();
		refreshNav();
		buildContentPage();
		buildActionPage();
		refreshPicker();
		restoreFocus(focusTarget);
		if (pageTransitionPending) {
			pageTransitionPending = false;
			HolographicTheme.enterTransition(contentColumn);
		}
	}

	private void refreshNav() {
		navColumn.removeAllViews();
		// v27 trading-first order (对标币安/欧易) with a 16dp static MC item icon per tab:
		//   market   -> spyglass           (观察行情；clock 是动画物品取不到静态贴图)
		//   convert  -> ender pearl        (warp / swap motion)
		//   contracts-> writable book      (合约 / 签约)
		//   wallet   -> emerald            (gem = stored value)
		//   transfer -> paper              (signed note / cheque)
		//   shop     -> gold ingot         (universal commerce token)
		//   mining   -> encrypted hash     (mod's own settlement voucher)
		ResourceLocation marketIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "spyglass");
		ResourceLocation convertIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "ender_pearl");
		ResourceLocation contractsIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "writable_book");
		ResourceLocation walletIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "emerald");
		ResourceLocation transferIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "paper");
		ResourceLocation shopIcon = ResourceLocation.fromNamespaceAndPath("minecraft", "gold_ingot");
		ResourceLocation miningIcon = ResourceLocation.fromNamespaceAndPath("virtual_craft", "encrypted_hash_voucher");
		LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
		navColumn.addView(navIconButton(marketIcon, "1  " + t("screen.virtual_craft.holographic_shop.page.market"), Page.MARKET), navParams);
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(convertIcon, "2  " + t("screen.virtual_craft.holographic_shop.page.convert"), Page.CONVERT), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(contractsIcon, "3  " + t("screen.virtual_craft.holographic_shop.page.contracts"), Page.CONTRACTS), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(walletIcon, "4  " + t("screen.virtual_craft.holographic_shop.page.wallet"), Page.WALLET), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(transferIcon, "5  " + t("screen.virtual_craft.holographic_shop.page.transfer"), Page.TRANSFER), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(shopIcon, "6  " + t("screen.virtual_craft.holographic_shop.page.shop"), Page.SHOP), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(6)));
		navColumn.addView(navIconButton(miningIcon, "7  " + t("screen.virtual_craft.holographic_shop.page.mining"), Page.MINING), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
		navColumn.addView(spacer(dp(14)));
		// 资产总额只在顶部 header 的 USDT 药丸里展示一次（headerValue），侧栏不再重复一张
		// “资产总览”卡片——既消除重复，也腾出垂直空间，避免底部“钱包地址/汇率状态”卡被裁。
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
			case CONTRACTS -> buildContractsContent();
			case MARKET -> buildMarketContent();
		}
	}

	private void buildActionPage() {
		actionColumn.removeAllViews();
		// Drop references to the previous page's live-update views so a stale slider drag
		// can never write into a recycled card.
		liveStatusModel = null;
		liveStatusCardView = null;
		liveStatusSummaryView = null;
		liveStatusIssueView = null;
		primaryAction = null;
		actionColumn.addView(sectionHeader(actionTitle(), actionSubtitle()));
		actionColumn.addView(spacer(dp(10)));

		switch (currentPage) {
			case WALLET -> buildWalletActions();
			case CONVERT -> buildConvertActions();
			case SHOP -> buildShopActions();
			case TRANSFER -> buildTransferActions();
			case MINING -> buildMiningActions();
			case CONTRACTS -> buildContractsActions();
			case MARKET -> buildMarketActions();
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

		ScrollView assetScroll = scrollShell("wallet.assets");
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
		String[] order = {"", "deposit", "withdraw", "swap", "transfer", "premium_buy", "premium_sell", "mining_settlement", "contract_reward", "power_link"};
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
			primaryAction = this::sendWalletDeposit;
			submitOnEnter(walletAmountInput);
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
			actionColumn.addView(liveStatusCard(() -> {
				long req = parseWhole(walletAmountText);
				long max = inventoryWholeItemCount(selectedWalletCurrency);
				String p = walletDepositProblem(req, max);
				return new StatusModel(walletDepositSummary(req), p, p.isBlank() ? GREEN : AMBER);
			}));
			actionColumn.addView(spacer(dp(10)));
			actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.deposit_current", selectedWalletCurrency.ticker()), GREEN, this::sendWalletDeposit),
				new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			actionColumn.addView(spacer(dp(6)));
			actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.deposit_all"), CYAN, () -> {
					PacketDistributor.sendToServer(new WalletActionPayload(sessionId, WalletActionPayload.Action.DEPOSIT_ALL, "", "", 0L, ""));
					toast("screen.virtual_craft.holographic_shop.toast.deposit");
				}),
				new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
			return;
		}

		long withdrawable = wholeItemCount(selectedWalletCurrency);
		long requested = parseWhole(walletAmountText);
		String withdrawProblem = walletWithdrawProblem(requested, withdrawable);
		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.amount_whole")));
		walletAmountInput = input(walletAmountText, Long.toString(Math.max(0L, withdrawable)));
		primaryAction = () -> sendWalletWithdraw(WalletActionPayload.Action.WITHDRAW_ITEM);
		submitOnEnter(walletAmountInput);
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
		actionColumn.addView(liveStatusCard(() -> {
			long req = parseWhole(walletAmountText);
			long max = wholeItemCount(selectedWalletCurrency);
			String p = walletWithdrawProblem(req, max);
			return new StatusModel(walletWithdrawSummary(req), p, p.isBlank() ? GREEN : AMBER);
		}));
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
		primaryAction = this::sendConvertAction;
		submitOnEnter(convertAmountInput);
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
		actionColumn.addView(liveStatusCard(() -> {
			String p = convertProblem();
			return new StatusModel(convertSummaryLine(), p, p.isBlank() ? GREEN : AMBER);
		}));
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
		// v24: parent must be taller than children (dp(34)) by at least 2 dp on each
		// side, otherwise the rounded background's bottom 1-px stroke / corner arc
		// is sub-pixel-clipped by the parent's bottom edge (visible as "被遮挡" on
		// the "默认" / "全部" / "★" buttons). dp(40) parent + CENTER_VERTICAL gives
		// the buttons (dp(34)) 3 dp slack top + 3 dp slack bottom.
		toolbar.setGravity(Gravity.CENTER_VERTICAL);
		shopSearchInput = input(shopSearchText, t("screen.virtual_craft.holographic_shop.shop.search_hint"));
		toolbar.addView(shopSearchInput, weighted(0, dp(34), 1f));
		Button sortButton = compactActionButton(t("screen.virtual_craft.holographic_shop.shop.sort." + shopSortMode.name().toLowerCase(java.util.Locale.ROOT)), () -> {
			ShopSortMode[] modes = ShopSortMode.values();
			shopSortMode = modes[(shopSortMode.ordinal() + 1) % modes.length];
			requestRefresh(false, FocusTarget.SHOP_SEARCH);
		});
		// v25 ultra-compact sort cycler: labels are now pure-symbol (↕ / 买↑ / 卖↓ /
		// A→Z), at most 3 chars wide, so dp(132) was 2.4x too wide. Shrink to dp(56)
		// to free toolbar space for a wider search input. Hover tooltip lists the
		// cyclable modes so the symbol meaning stays discoverable.
		sortButton.setTextSize(13f);
		sortButton.setMinimumHeight(dp(34));
		sortButton.setTooltipText(t("screen.virtual_craft.holographic_shop.shop.sort_tooltip"));
		var sortParams = new LinearLayout.LayoutParams(dp(56), dp(34));
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
		// v25 favorites toggle is now a pure star glyph (★ filled = only favorites,
		// ☆ outline = all). dp(40) is just enough for a single 14sp glyph centered
		// in the toggle background. Hover tooltip carries the full description.
		favButton.setTextSize(14f);
		favButton.setTextColor(shopFavoritesOnly ? AMBER : TEXT_DIM);
		favButton.setMinimumHeight(dp(34));
		favButton.setTooltipText(t("screen.virtual_craft.holographic_shop.shop.favorites_tooltip"));
		var favParams = new LinearLayout.LayoutParams(dp(40), dp(34));
		favParams.setMargins(dp(6), 0, 0, 0);
		toolbar.addView(favButton, favParams);
		contentColumn.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
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

		ScrollView listScroll = scrollShell("shop.catalog");
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
		actionColumn.addView(liveStatusCard(() -> {
			ShopOffer liveOffer = selectedOffer();
			int liveBuyMax = currentShopBuyMax(liveOffer);
			int liveSellMax = currentShopSellMax(liveOffer);
			int liveMaxQuantity = Math.max(1, maxShopQuantity(liveBuyMax, liveSellMax));
			int liveQuantity = clampInt(parseInt(shopQuantityText, 1), 1, liveMaxQuantity);
			String liveBuyProblem = shopBuyProblem(liveQuantity, liveOffer, liveBuyMax);
			String liveSellProblem = shopSellProblem(liveQuantity, liveOffer, liveSellMax);
			boolean liveBuying = shopTradeMode == ShopTradeMode.BUY;
			String liveSummary = liveBuying
					? t("screen.virtual_craft.holographic_shop.shop.buy_total", liveQuantity, formatTickerAmount(shopSettlementCurrency, shopBuyTotal(liveQuantity, liveOffer)))
					: t("screen.virtual_craft.holographic_shop.shop.sell_total", liveQuantity, formatTickerAmount(shopSettlementCurrency, shopSellTotal(liveQuantity, liveOffer)));
			String liveIssue = liveBuying
					? (liveBuyProblem.isBlank() ? t("screen.virtual_craft.holographic_shop.shop.auto_swap_detail") : liveBuyProblem)
					: liveSellProblem;
			int liveAccent = (liveBuying ? liveBuyProblem : liveSellProblem).isBlank() ? (liveBuying ? CYAN : GREEN) : AMBER;
			return new StatusModel(liveSummary, liveIssue, liveAccent);
		}));
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
		// v24: same fix as shopContent toolbar — WRAP_CONTENT row + dp(34) children
		// leaves 0 dp slack and clips the rounded button's bottom stroke ("选择" /
		// "粘贴" 被遮挡). Force dp(40) + CENTER_VERTICAL so the stroke has room.
		targetRow.setGravity(Gravity.CENTER_VERTICAL);
		transferTargetInput = input(transferTargetText, t("screen.virtual_craft.holographic_shop.transfer.target"));
		submitOnEnter(transferTargetInput);
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
		targetCard.addView(targetRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
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
		primaryAction = this::sendTransferAction;
		submitOnEnter(transferAmountInput);
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
		actionColumn.addView(liveStatusCard(() -> {
			String p = transferProblem();
			return new StatusModel(transferSummary(), p, p.isBlank() ? GREEN : AMBER);
		}));
		actionColumn.addView(spacer(dp(10)));

		actionColumn.addView(fieldLabel(t("screen.virtual_craft.holographic_shop.transfer.memo")));
		transferMemoInput = input(transferMemoText, t("screen.virtual_craft.holographic_shop.transfer.memo_hint"));
		submitOnEnter(transferMemoInput);
		actionColumn.addView(transferMemoInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
		actionColumn.addView(spacer(dp(10)));

		Button send = actionButton(t("screen.virtual_craft.holographic_shop.transfer.send"), GREEN, this::sendTransferAction);
		actionColumn.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildMiningContent() {
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.mining.vouchers"), t("screen.virtual_craft.holographic_shop.mining.preview")));
		contentColumn.addView(spacer(dp(10)));

		VoucherSummary vouchers = hashVoucherSummary();
		long payoutMinor = miningPayoutMinor();

		LinearLayout top = new LinearLayout(requireContext());
		top.setOrientation(LinearLayout.HORIZONTAL);
		// v24: equal weights + measureWithLargestChildEnabled so both info cards
		// share the same width AND height (“哈希凭证” wrap was making the left
		// card visibly taller than “结算预览”). MATCH_PARENT on each child’s height
		// then lets the shorter card stretch to the taller one’s measured height.
		top.setMeasureWithLargestChildEnabled(true);
		var voucherParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		top.addView(infoCard(
				t("screen.virtual_craft.holographic_shop.mining.vouchers"),
				t("screen.virtual_craft.holographic_shop.mining.available", vouchers.totalCount()),
				t("screen.virtual_craft.holographic_shop.mining.total_value", CryptoCurrency.BTC.formatCompact(vouchers.totalBtcMinor()), CryptoCurrency.BTC.ticker()),
				CYAN), voucherParams);
		var previewParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		previewParams.setMargins(dp(10), 0, 0, 0);
		top.addView(infoCard(t("screen.virtual_craft.holographic_shop.mining.preview"), miningSettlementCurrency.displayName().getString(), formatTickerAmount(miningSettlementCurrency, payoutMinor), GREEN), previewParams);
		contentColumn.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(virtualRelicHintCard(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(miningVoucherBreakdown(vouchers), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(spacer(dp(10)));
		contentColumn.addView(miningStatsDashboard(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private View virtualRelicHintCard() {
		LinearLayout card = card(PANEL_SOFT, CYAN, 10f);
		card.setPadding(dp(14), dp(10), dp(14), dp(10));
		card.addView(hintText(t("screen.virtual_craft.holographic_shop.mining.relics")));
		card.addView(bodyText(t("screen.virtual_craft.holographic_shop.mining.relics_hint")));
		card.addView(spacer(dp(6)));
		card.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.mining.fragments"),
				Integer.toString(countDataFragments()),
				CYAN));
		return card;
	}

	private View miningVoucherBreakdown(VoucherSummary summary) {
		LinearLayout card = card(PANEL_ROW, WINDOW_STROKE, 10f);
		card.setPadding(dp(14), dp(12), dp(14), dp(12));
		card.addView(hintText(t("screen.virtual_craft.holographic_shop.mining.breakdown")));
		if (summary.totalCount() <= 0) {
			card.addView(bodyText(t("screen.virtual_craft.holographic_shop.mining.none")));
			return card;
		}
		card.addView(spacer(dp(8)));
		card.addView(voucherRarityBar(summary), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
		card.addView(spacer(dp(10)));
		for (VoucherTierCount tier : summary.tiers()) {
			if (tier.count() <= 0) {
				continue;
			}
			card.addView(keyValueRow(
					new net.minecraft.world.item.ItemStack(tier.item()).getHoverName().getString(),
					t("screen.virtual_craft.holographic_shop.mining.tier_count", tier.count(), CryptoCurrency.BTC.formatCompact(safeMultiply(tier.btcMinor(), tier.count())), CryptoCurrency.BTC.ticker()),
					TEXT));
		}
		long usdtMinor = CurrencyConversionService.convert(CryptoCurrency.BTC, CryptoCurrency.USDT, summary.totalBtcMinor(), exchangeSnapshot, RoundingMode.DOWN);
		card.addView(spacer(dp(6)));
		card.addView(keyValueRow(
				CryptoCurrency.BTC.formatCompact(summary.totalBtcMinor()) + " " + CryptoCurrency.BTC.ticker(),
				t("screen.virtual_craft.holographic_shop.mining.value_usdt", CryptoCurrency.USDT.formatCompact(usdtMinor)),
				GREEN));
		return card;
	}

	/**
	 * Horizontal stacked bar visualising the proportion of each voucher tier by
	 * count. Each tier keeps a stable colour (indexed by tier position) so the
	 * segments do not reshuffle colours as counts change.
	 */
	private View voucherRarityBar(VoucherSummary summary) {
		LinearLayout bar = new LinearLayout(requireContext());
		bar.setOrientation(LinearLayout.HORIZONTAL);
		List<VoucherTierCount> tiers = summary.tiers();
		boolean first = true;
		for (int index = 0; index < tiers.size(); index++) {
			VoucherTierCount tier = tiers.get(index);
			if (tier.count() <= 0) {
				continue;
			}
			View segment = new View(requireContext());
			segment.setBackground(rounded(VOUCHER_TIER_COLORS[index % VOUCHER_TIER_COLORS.length], 0, 3f, 0));
			LinearLayout.LayoutParams params = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, tier.count());
			if (!first) {
				params.setMargins(dp(2), 0, 0, 0);
			}
			first = false;
			bar.addView(segment, params);
		}
		return bar;
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
		card.addView(spacer(dp(10)));
		card.addView(miningWeekSummary(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
		long avgMinor = miningStatsCache.totalSettlements() > 0
				? miningStatsCache.totalBtcMinor() / miningStatsCache.totalSettlements()
				: 0L;
		card.addView(spacer(dp(8)));
		card.addView(keyValueRow(
				t("screen.virtual_craft.holographic_shop.mining.stats.avg_payout"),
				CryptoCurrency.BTC.formatCompact(avgMinor) + " " + CryptoCurrency.BTC.ticker(),
				CYAN));
		return card;
	}

	/** Three-up summary of the last 7 days of BTC production: total / daily avg / best day. */
	private View miningWeekSummary() {
		long[] btcByDay = sevenDayBtc();
		long total = 0L;
		long best = 0L;
		for (long value : btcByDay) {
			total += value;
			if (value > best) {
				best = value;
			}
		}
		long avg = total / btcByDay.length;
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.week_total"),
				CryptoCurrency.BTC.formatCompact(total),
				CYAN), weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		var avgParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		avgParams.setMargins(dp(8), 0, 0, 0);
		row.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.week_avg"),
				CryptoCurrency.BTC.formatCompact(avg),
				CYAN), avgParams);
		var bestParams = weighted(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		bestParams.setMargins(dp(8), 0, 0, 0);
		row.addView(metricCard(
				t("screen.virtual_craft.holographic_shop.mining.stats.week_best"),
				CryptoCurrency.BTC.formatCompact(best),
				GREEN), bestParams);
		return row;
	}

	/** Render the 7-day BTC production buckets as a simple bar chart. */
	private View miningSevenDayChart() {
		LinearLayout chart = new LinearLayout(requireContext());
		chart.setOrientation(LinearLayout.HORIZONTAL);
		chart.setGravity(Gravity.BOTTOM);

		long[] btcByDay = sevenDayBtc();
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

	/**
	 * Last 7 days of BTC production as a 7-element array indexed by today,
	 * today-1, ..., today-6 (so it always has seven slots even when some days
	 * are zero). Shared by the bar chart and the week summary.
	 */
	private long[] sevenDayBtc() {
		java.util.List<dev.tatami.virtualcraft.network.SyncWalletPreferencesPayload.DailyBucket> buckets = miningStatsCache.last7DayBuckets();
		long todayStart = (java.time.Instant.now().getEpochSecond() / 86400L) * 86400L;
		long[] btcByDay = new long[7];
		for (var bucket : buckets) {
			long delta = (todayStart - bucket.dayStartEpochSeconds()) / 86400L;
			if (delta >= 0 && delta < btcByDay.length) {
				btcByDay[(int) delta] = bucket.btcMinor();
			}
		}
		return btcByDay;
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
		primaryAction = this::sendMiningAction;
		submitOnEnter(miningAmountInput);
		actionColumn.addView(miningAmountInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(quantitySlider(t("item.virtual_craft.hash_voucher"), 0, sliderMax, requested, value -> {
			String text = Integer.toString(value);
			miningAmountText = text;
			pendingInputOverrides.put(FocusTarget.MINING_AMOUNT, text);
			if (miningAmountInput != null && !miningAmountInput.getText().toString().equals(text)) {
				miningAmountInput.setText(text);
			}
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(6)));
		actionColumn.addView(actionButton(t("screen.virtual_craft.holographic_shop.mining.all"), CYAN, () -> {
			miningAmountText = Integer.toString(vouchers);
			pendingInputOverrides.put(FocusTarget.MINING_AMOUNT, miningAmountText);
			requestRefresh(false, FocusTarget.MINING_AMOUNT);
		}), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
		actionColumn.addView(spacer(dp(10)));
		actionColumn.addView(liveStatusCard(() -> {
			long liveCount = parseWhole(miningAmountText);
			String p = miningProblem(liveCount, countHashVouchers());
			return new StatusModel(miningSummaryLine(liveCount), p, p.isBlank() ? GREEN : AMBER);
		}));
		actionColumn.addView(spacer(dp(10)));

		Button settle = actionButton(t("screen.virtual_craft.holographic_shop.mining.settle"), GREEN, this::sendMiningAction);
		actionColumn.addView(settle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
	}

	private void buildContractsContent() {
		contentColumn.addView(sectionHeader(
				t("screen.virtual_craft.holographic_shop.contracts.title"),
				t("screen.virtual_craft.holographic_shop.contracts.reset_in", contractResetCountdown())));
		contentColumn.addView(spacer(dp(10)));

		if (contractCache.isEmpty()) {
			contentColumn.addView(emptyState(
					t("screen.virtual_craft.holographic_shop.contracts.empty"),
					t("screen.virtual_craft.holographic_shop.contracts.empty_hint")));
			return;
		}

		java.util.List<DailyContractSnapshot> ordered = sortedContracts();
		ScrollView scroll = scrollShell("contracts");
		LinearLayout list = vertical();
		list.setPadding(dp(10), dp(10), dp(10), dp(10));
		for (int index = 0; index < ordered.size(); index++) {
			list.addView(contractCard(ordered.get(index)));
			if (index + 1 < ordered.size()) {
				list.addView(spacer(dp(8)));
			}
		}
		scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		contentColumn.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
	}

	private View contractCard(DailyContractSnapshot contract) {
		boolean complete = contract.progress() >= contract.target();
		int accent = contract.claimed() ? WINDOW_STROKE : complete ? GREEN : CYAN;
		LinearLayout card = card(complete && !contract.claimed() ? PANEL_ACTIVE : PANEL_ROW, accent, 10f);
		card.setPadding(dp(12), dp(10), dp(12), dp(10));

		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		ResourceLocation iconId = ResourceLocation.tryParse(contract.iconItemId());
		row.addView(itemIconView(iconId == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "paper") : iconId, "*", ICON_SMALL_DP));

		LinearLayout text = vertical();
		var textParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);
		text.addView(bodyText(t(contract.titleKey())));
		LinearLayout rewardRow = new LinearLayout(requireContext());
		rewardRow.setOrientation(LinearLayout.HORIZONTAL);
		rewardRow.setGravity(Gravity.CENTER_VERTICAL);
		int rewardColor = rewardAccent(contract.rewardKind());
		View rewardPill = new View(requireContext());
		rewardPill.setBackground(rounded(rewardColor, 0, 999f, 0));
		LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(dp(6), dp(6));
		pillParams.setMargins(0, 0, dp(5), 0);
		rewardRow.addView(rewardPill, pillParams);
		TextView reward = hintText(t("screen.virtual_craft.holographic_shop.contracts.reward", contract.rewardText()));
		reward.setMaxLines(1);
		reward.setEllipsize(TextUtils.TruncateAt.END);
		reward.setTextColor(rewardColor);
		rewardRow.addView(reward, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		text.addView(rewardRow);
		row.addView(text, textParams);

		TextView progress = bodyText(contractProgressText(contract));
		progress.setTextColor(complete ? GREEN : CYAN);
		progress.setGravity(Gravity.END);
		row.addView(progress);
		card.addView(row);
		card.addView(spacer(dp(8)));
		card.addView(contractProgressBar(contract, accent), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
		card.addView(spacer(dp(6)));
		LinearLayout actionRow = new LinearLayout(requireContext());
		actionRow.setOrientation(LinearLayout.HORIZONTAL);
		actionRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView goal = hintText(t(contract.goalKey(), contract.targetText()));
		goal.setMaxLines(1);
		goal.setEllipsize(TextUtils.TruncateAt.END);
		actionRow.addView(goal, weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		Button claim = compactActionButton(contract.claimed()
				? t("screen.virtual_craft.holographic_shop.contracts.claimed")
				: t("screen.virtual_craft.holographic_shop.contracts.claim"), () -> {
					sendContractClaim(contract.id());
					toast("screen.virtual_craft.holographic_shop.toast.claim");
				});
		claim.setEnabled(complete && !contract.claimed());
		claim.setTextColor(complete && !contract.claimed() ? GREEN : TEXT_DIM);
		actionRow.addView(claim, new LinearLayout.LayoutParams(dp(76), dp(30)));
		card.addView(actionRow);
		return card;
	}

	private View contractProgressBar(DailyContractSnapshot contract, int accent) {
		FrameLayout frame = new FrameLayout(requireContext());
		frame.setBackground(rounded(PANEL_SOFT, WINDOW_STROKE, 999f, 1));
		View fill = new View(requireContext());
		fill.setBackground(rounded(accent, 0, 999f, 0));
		float ratio = contract.target() <= 0L ? 0f : Math.min(1f, (float) contract.progress() / (float) contract.target());
		frame.post(() -> {
			int width = Math.max(1, Math.round(frame.getWidth() * ratio));
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
			fill.setLayoutParams(params);
		});
		frame.addView(fill, new FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
		return frame;
	}

	private void buildContractsActions() {
		actionColumn.addView(infoCard(
				t("screen.virtual_craft.holographic_shop.contracts.today"),
				t("screen.virtual_craft.holographic_shop.contracts.count", completedContracts(), contractCache.size()),
				t("screen.virtual_craft.holographic_shop.contracts.claimable", claimableContracts()),
				CYAN));
		if (!contractCache.isEmpty()) {
			int total = contractCache.size();
			float ratio = total <= 0 ? 0f : Math.min(1f, (float) completedContracts() / (float) total);
			actionColumn.addView(spacer(dp(8)));
			actionColumn.addView(progressBar(ratio, GREEN), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
			actionColumn.addView(spacer(dp(10)));
			boolean any = claimableContracts() > 0;
			Button claimAll = actionButton(t("screen.virtual_craft.holographic_shop.contracts.claim_all"), GREEN, this::claimAllContracts);
			claimAll.setEnabled(any);
			claimAll.setTextColor(any ? GREEN : TEXT_DIM);
			actionColumn.addView(claimAll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
		}
	}

	/** Claim every contract that is complete but not yet claimed (one packet each). */
	private void claimAllContracts() {
		int claimed = 0;
		for (DailyContractSnapshot contract : contractCache) {
			if (!contract.claimed() && contract.progress() >= contract.target()) {
				sendContractClaim(contract.id());
				claimed++;
			}
		}
		if (claimed > 0) {
			toast("screen.virtual_craft.holographic_shop.toast.claim");
		}
	}

	/** Generic [0,1] progress bar (post-measured fill), reused by the contracts overview. */
	private View progressBar(float ratio, int accent) {
		FrameLayout frame = new FrameLayout(requireContext());
		frame.setBackground(rounded(PANEL_SOFT, WINDOW_STROKE, 999f, 1));
		View fill = new View(requireContext());
		fill.setBackground(rounded(accent, 0, 999f, 0));
		float clamped = Math.max(0f, Math.min(1f, ratio));
		frame.post(() -> {
			int width = Math.max(1, Math.round(frame.getWidth() * clamped));
			fill.setLayoutParams(new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
		});
		frame.addView(fill, new FrameLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
		return frame;
	}

	/** Ordered for display: claimable first, then in-progress (by ratio desc), then claimed. */
	private java.util.List<DailyContractSnapshot> sortedContracts() {
		java.util.List<DailyContractSnapshot> ordered = new ArrayList<>(contractCache);
		ordered.sort(java.util.Comparator
				.comparingInt((DailyContractSnapshot c) -> contractRank(c))
				.thenComparing(java.util.Comparator.comparingDouble((DailyContractSnapshot c) -> contractProgressRatio(c)).reversed()));
		return ordered;
	}

	private int contractRank(DailyContractSnapshot contract) {
		if (contract.claimed()) {
			return 2;
		}
		return contract.progress() >= contract.target() ? 0 : 1;
	}

	private double contractProgressRatio(DailyContractSnapshot contract) {
		return contract.target() <= 0L ? 1.0 : Math.min(1.0, (double) contract.progress() / (double) contract.target());
	}

	/** Colour-codes a contract reward by its kind so the list reads at a glance. */
	private int rewardAccent(String kind) {
		return switch (kind == null ? "" : kind) {
			case "quantum_hash_voucher" -> 0xFF8B5CF6;
			case "encrypted_hash_voucher" -> 0xFF6366F1;
			case "explorer_hash_voucher" -> CYAN;
			case "btc" -> 0xFFF59E0B;
			case "usdt" -> GREEN;
			case "data_fragment" -> 0xFF38BDF8;
			default -> TEXT_DIM;
		};
	}

	/** Seconds until the next UTC-midnight contract refresh, formatted as e.g. "7h 12m". */
	private String contractResetCountdown() {
		long now = System.currentTimeMillis() / 1000L;
		long dayStart = now - Math.floorMod(now, 86400L);
		long resetIn = Math.max(0L, dayStart + 86400L - now);
		return formatDuration(resetIn);
	}

	private String formatDuration(long seconds) {
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0) {
			return minutes + "m";
		}
		return Math.max(0L, seconds) + "s";
	}

	private int completedContracts() {
		int count = 0;
		for (DailyContractSnapshot contract : contractCache) {
			if (contract.progress() >= contract.target()) {
				count++;
			}
		}
		return count;
	}

	private int claimableContracts() {
		int count = 0;
		for (DailyContractSnapshot contract : contractCache) {
			if (!contract.claimed() && contract.progress() >= contract.target()) {
				count++;
			}
		}
		return count;
	}

	private String contractProgressText(DailyContractSnapshot contract) {
		long progress = Math.min(contract.progress(), contract.target());
		return progress + "/" + contract.target();
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
			HolographicTheme.interactive(chip);
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
				HolographicTheme.interactive(button);
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
		HolographicTheme.interactive(row);

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
		row.setPadding(dp(10), dp(9), dp(16), dp(9));
		row.setOnClickListener(v -> action.run());
		HolographicTheme.interactive(row);

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
		// 单字符 ★/☆ 仍需要给圆角背景留安全边，否则 ModernUI 父行偶发裁掉描边。
		var favParams = new LinearLayout.LayoutParams(dp(40), dp(34));
		favParams.setMargins(dp(10), 0, 0, 0);
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
		HolographicTheme.interactive(card);

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
		HolographicTheme.interactive(row);
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
				} else {
					updateLiveStatus();
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
				} else {
					updateLiveStatus();
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
				} else {
					updateLiveStatus();
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

	/**
	 * Like {@link #statusCard} but retains references to the card and its text views so the
	 * summary can be refreshed in place while a slider is dragged (#2). The supplier is
	 * re-evaluated from current model state by {@link #updateLiveStatus()}.
	 */
	private View liveStatusCard(java.util.function.Supplier<StatusModel> model) {
		liveStatusModel = model;
		StatusModel m = model.get();
		LinearLayout card = card(PANEL_ROW, m.accent(), 10f);
		card.setPadding(dp(12), dp(12), dp(12), dp(12));
		TextView summaryView = bodyText(m.summary());
		summaryView.setMaxLines(2);
		summaryView.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(summaryView);
		card.addView(spacer(dp(4)));
		// Always create the issue row (even when blank) so live updates can toggle it via
		// visibility instead of adding/removing children mid-drag.
		TextView issueView = hintText("");
		issueView.setMaxLines(2);
		issueView.setEllipsize(TextUtils.TruncateAt.END);
		card.addView(issueView);
		liveStatusCardView = card;
		liveStatusSummaryView = summaryView;
		liveStatusIssueView = issueView;
		applyLiveStatus(m);
		return card;
	}

	private void applyLiveStatus(StatusModel m) {
		if (liveStatusSummaryView != null) {
			liveStatusSummaryView.setText(m.summary());
		}
		if (liveStatusIssueView != null) {
			if (m.issue() == null || m.issue().isBlank()) {
				liveStatusIssueView.setText("");
				liveStatusIssueView.setVisibility(View.GONE);
			} else {
				liveStatusIssueView.setVisibility(View.VISIBLE);
				liveStatusIssueView.setText(m.issue());
				liveStatusIssueView.setTextColor(m.accent() == GREEN ? TEXT_DIM : m.accent());
			}
		}
		if (liveStatusCardView != null) {
			liveStatusCardView.setBackground(HolographicTheme.acrylic(PANEL_ROW, m.accent(), 10f));
		}
	}

	/** Recompute and re-render the live status card from the current model, if one is active. */
	private void updateLiveStatus() {
		if (liveStatusModel != null) {
			applyLiveStatus(liveStatusModel.get());
		}
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
		HolographicTheme.interactive(button);
		return button;
	}

	/**
	 * v25 nav tab with an MC item icon prefix. Each tab now reads as a tactile
	 * "page badge" because the 16dp item sprite (emerald / ender pearl / gold
	 * ingot / paper / hash voucher) is instantly recognizable — it drops the
	 * cognitive load of parsing 2 CJK characters at every glance. The textual
	 * label stays alongside so locale fallbacks and players unfamiliar with
	 * the iconography don't lose orientation.
	 *
	 * <p>Implementation note: piggybacks on {@link #navButton} so the selected
	 * vs. unselected styling (background, stroke color, text dim) flows through
	 * unchanged. We only add the compound drawable + padding on top — the same
	 * pattern proven by {@link #currencyActionButton}.</p>
	 */
	private Button navIconButton(ResourceLocation iconId, String text, Page page) {
		Button button = navButton(text, page);
		ImageDrawable icon = itemIcon(iconId, 16);
		if (icon != null) {
			button.setCompoundDrawablesRelative(icon, null, null, null);
			button.setCompoundDrawablePadding(dp(8));
		}
		return button;
	}

	private boolean handleNavShortcut(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		if (keyCode < KeyEvent.KEY_1 || keyCode > KeyEvent.KEY_9) {
			return false;
		}
		int index = keyCode - KeyEvent.KEY_1;
		if (index >= NAV_ORDER.length) {
			return false;
		}
		Page page = NAV_ORDER[index];
		if (page != currentPage) {
			navigateTo(page);
		}
		if (root != null) {
			root.requestFocus();
		}
		return true;
	}

	private void navigateTo(Page page) {
		currentPage = page;
		lastOpenedPage = page;
		pageTransitionPending = true;
		pickerTarget = PickerTarget.NONE;
		// Explicit page switches start at the top; only in-page rebuilds preserve scroll.
		savedScrollY.clear();
		liveScrollShells.clear();
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

	private Button headerIconButton(String glyph, int accentColor, Runnable action) {
		Button button = new Button(requireContext());
		button.setText(glyph);
		button.setTextSize(19f);
		button.setSingleLine();
		button.setGravity(Gravity.CENTER);
		button.setTextColor(TEXT);
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		// Symmetric padding so the status glyph sits dead-centre in the button.
		// The old asymmetric (right/bottom) padding nudged it up-left.
		button.setPadding(0, 0, 0, 0);
		button.setBackground(rounded(PANEL_BUTTON, accentColor, 9f, 1));
		button.setOnClickListener(v -> action.run());
		HolographicTheme.interactive(button);
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
		HolographicTheme.interactive(button);
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
		HolographicTheme.interactive(button);
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
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		button.setPadding(0, 0, 0, 0);
		button.setBackground(rounded(PANEL_BUTTON, WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
		HolographicTheme.interactive(button);
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

	/**
	 * Wire an EditText so pressing Enter (or numpad Enter) runs the current page's primary
	 * action (#3). The freshly typed value is flushed into model state via {@link
	 * #stashInputs()} first, mirroring what a full rebuild would do, so the action sees the
	 * latest input. The listener is checked before the editor's own key handling.
	 */
	private void submitOnEnter(EditText input) {
		if (input == null) {
			return;
		}
		input.setOnKeyListener((v, keyCode, event) -> {
			if (event.getAction() == KeyEvent.ACTION_DOWN
					&& (keyCode == KeyEvent.KEY_ENTER || keyCode == KeyEvent.KEY_KP_ENTER)) {
				Runnable action = primaryAction;
				if (action != null) {
					stashInputs();
					action.run();
					return true;
				}
			}
			return false;
		});
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
		return scrollShell(null);
	}

	/**
	 * Keyed variant of {@link #scrollShell()}. When {@code key} is non-null the new shell is
	 * registered so its scroll offset is captured before the next rebuild ({@link
	 * #stashScrollPositions()}) and re-applied here, keeping long lists from snapping back to
	 * the top whenever an in-page interaction triggers a full view-tree rebuild.
	 */
	private ScrollView scrollShell(String key) {
		ScrollView scroll = new HoloScrollView(requireContext());
		scroll.setBackground(HolographicTheme.acrylic(PANEL_ROW, WINDOW_STROKE, 10f));
		wireScrollFocus(scroll);
		if (key != null) {
			liveScrollShells.put(key, scroll);
			Integer saved = savedScrollY.get(key);
			if (saved != null && saved > 0) {
				int target = saved;
				// Defer until after measure/layout so the content height exists and the
				// offset is not clamped to 0.
				scroll.post(() -> scroll.scrollTo(0, target));
			}
		}
		return scroll;
	}

	/**
	 * Snapshot the live scroll offsets just before the view tree is rebuilt. Mirrors the
	 * input/focus stashing done in {@link #stashInputs()}.
	 */
	private void stashScrollPositions() {
		if (liveScrollShells.isEmpty()) {
			return;
		}
		for (java.util.Map.Entry<String, ScrollView> entry : liveScrollShells.entrySet()) {
			ScrollView scroll = entry.getValue();
			if (scroll != null) {
				savedScrollY.put(entry.getKey(), scroll.getScrollY());
			}
		}
		liveScrollShells.clear();
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
			case CONTRACTS -> CryptoCurrency.USDT;
			case WALLET -> selectedWalletCurrency;
			case MARKET -> marketSelectedCurrency;
		};
	}


	// ---- MARKET page: K-line price chart ----

	private CryptoCurrency marketSelectedCurrency = CryptoCurrency.BTC;
	private HolographicMarketData.Interval marketInterval = HolographicMarketData.Interval.M1;

	// Live-updatable MARKET hosts. The K-line page repaints ONLY these in place on
	// every price tick (see applyExchangeSync) instead of tearing down and rebuilding
	// the whole screen, which is what made the chart feel laggy / "refresh too slow".
	private LinearLayout marketPriceHost;
	private LinearLayout marketChartHost;
	private LinearLayout marketStatsHost;
	private int marketChartHeightPx;
	// Coalesces live MARKET repaints: applyExchangeSync runs on the Render thread, but
	// ModernUI view mutations MUST happen on the UI thread, so we hop via root.post and
	// keep at most one repaint queued at a time (the repaint is idempotent).
	private boolean marketUpdateScheduled;
	// Binance-style markets list under the chart (one tappable row per pair), repainted live.
	private LinearLayout marketListHost;
	// Volume histogram beneath the candles, and the order-book + recent-trades panel.
	private LinearLayout marketVolumeHost;
	private LinearLayout marketBookHost;
	// Rolling synthetic trade tape for the active pair (the sim has no real trade feed,
	// so a print is appended whenever the live price moves; cleared on pair switch).
	private final java.util.ArrayDeque<MarketTrade> marketTrades = new java.util.ArrayDeque<>();
	private CryptoCurrency marketTradesCurrency;
	private java.math.BigDecimal lastTradePrice;

	private record MarketTrade(java.math.BigDecimal price, double amount, boolean buy, long time) {
	}

	private void buildMarketContent() {
		contentColumn.addView(sectionHeader(
				t("screen.virtual_craft.holographic_shop.market.title"),
				t("screen.virtual_craft.holographic_shop.market.subtitle")));
		contentColumn.addView(spacer(dp(6)));

		// Interval (timeframe) selector — Binance / OKX style tab strip.
		LinearLayout intervalRow = new LinearLayout(requireContext());
		intervalRow.setOrientation(LinearLayout.HORIZONTAL);
		intervalRow.setGravity(Gravity.CENTER_VERTICAL);
		for (HolographicMarketData.Interval interval : HolographicMarketData.Interval.values()) {
			boolean selected = interval == marketInterval;
			Button btn = navButton(interval.code(), null);
			btn.setTextSize(11f);
			btn.setGravity(Gravity.CENTER);
			btn.setTextColor(selected ? CYAN : TEXT_SOFT);
			if (selected) {
				ShapeDrawable bg = new ShapeDrawable();
				bg.setCornerRadius(dp(6));
				bg.setColor(0x30_22D3EE);
				btn.setBackground(bg);
			}
			HolographicMarketData.Interval captured = interval;
			btn.setOnClickListener(v -> {
				marketInterval = captured;
				HolographicMarketData.ensureHistory(marketSelectedCurrency, captured);
				requestRefresh(false, FocusTarget.NONE);
			});
			HolographicTheme.interactive(btn);
			var ivParams = new LinearLayout.LayoutParams(0, dp(26), 1f);
			ivParams.setMargins(dp(1), 0, dp(1), 0);
			intervalRow.addView(btn, ivParams);
		}
		contentColumn.addView(intervalRow);
		contentColumn.addView(spacer(dp(6)));

		// Currency selector row
		LinearLayout selectorRow = new LinearLayout(requireContext());
		selectorRow.setOrientation(LinearLayout.HORIZONTAL);
		selectorRow.setGravity(Gravity.CENTER_VERTICAL);
		for (CryptoCurrency currency : new CryptoCurrency[]{
				CryptoCurrency.BTC, CryptoCurrency.ETH, CryptoCurrency.SOL,
				CryptoCurrency.DOGE, CryptoCurrency.TRX}) {
			Button btn = navButton(currency.ticker(), null);
			boolean selected = currency == marketSelectedCurrency;
			btn.setTextColor(selected ? GREEN : TEXT_SOFT);
			if (selected) {
				ShapeDrawable bg = new ShapeDrawable();
				bg.setCornerRadius(dp(6));
				bg.setColor(0x30_00FF88);
				btn.setBackground(bg);
			}
			CryptoCurrency captured = currency;
			btn.setOnClickListener(v -> {
				marketSelectedCurrency = captured;
				HolographicMarketData.ensureHistory(captured, marketInterval);
				requestRefresh(false, FocusTarget.NONE);
			});
			HolographicTheme.interactive(btn);
			var btnParams = new LinearLayout.LayoutParams(0, dp(28), 1f);
			btnParams.setMargins(dp(2), 0, dp(2), 0);
			selectorRow.addView(btn, btnParams);
		}
		contentColumn.addView(selectorRow);
		contentColumn.addView(spacer(dp(8)));

		// Kick off the historical kline pull for the current pair / interval so the
		// chart paints real candles immediately instead of slowly accreting them one
		// per period from live ticks.
		HolographicMarketData.ensureHistory(marketSelectedCurrency, marketInterval);

		// Live price header (price + period change), repainted in place each tick.
		marketPriceHost = vertical();
		contentColumn.addView(marketPriceHost);
		contentColumn.addView(spacer(dp(6)));

		// Candlestick chart host.
		marketChartHeightPx = dp(220);
		marketChartHost = new LinearLayout(requireContext());
		marketChartHost.setOrientation(LinearLayout.VERTICAL);
		contentColumn.addView(marketChartHost, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, marketChartHeightPx));
		contentColumn.addView(spacer(dp(4)));

		// Volume sub-chart (Binance-style histogram aligned under the candles).
		marketVolumeHost = new LinearLayout(requireContext());
		marketVolumeHost.setOrientation(LinearLayout.VERTICAL);
		contentColumn.addView(marketVolumeHost, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
		contentColumn.addView(spacer(dp(8)));

		// Stats host (24h high / low / amplitude / volume).
		marketStatsHost = new LinearLayout(requireContext());
		marketStatsHost.setOrientation(LinearLayout.HORIZONTAL);
		marketStatsHost.setGravity(Gravity.CENTER);
		contentColumn.addView(marketStatsHost, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		// Order book + recent trades (Binance-style two-column depth panel).
		contentColumn.addView(spacer(dp(12)));
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.market.depth"), null));
		contentColumn.addView(spacer(dp(6)));
		marketBookHost = new LinearLayout(requireContext());
		marketBookHost.setOrientation(LinearLayout.HORIZONTAL);
		contentColumn.addView(marketBookHost, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		// Markets list (Binance-style): one tappable row per pair, repainted live.
		contentColumn.addView(spacer(dp(14)));
		contentColumn.addView(sectionHeader(t("screen.virtual_craft.holographic_shop.market.markets"), null));
		contentColumn.addView(spacer(dp(6)));
		marketListHost = vertical();
		contentColumn.addView(marketListHost);

		populateMarketDynamic();

		// REST history arrives asynchronously; repaint a couple of times shortly after
		// open so it shows up even if no fresh live tick happens to land first.
		if (root != null) {
			root.postDelayed(this::populateMarketDynamic, 600L);
			root.postDelayed(this::populateMarketDynamic, 1600L);
		}
	}

	/**
	 * Repaints ONLY the live MARKET widgets (price header, candlestick chart, stats)
	 * from the latest {@link HolographicMarketData} candles. Cheap enough to run on
	 * every price tick, which is what gives the page its real-time, Binance-like feel
	 * without the full-screen rebuild that previously throttled updates to every 5s.
	 */
	private void populateMarketDynamic() {
		if (currentPage != Page.MARKET || marketChartHost == null || marketPriceHost == null) {
			return;
		}
		List<HolographicMarketData.Candle> candles =
				HolographicMarketData.getCandles(marketSelectedCurrency, marketInterval);

		java.math.BigDecimal price = candles.isEmpty()
				? exchangeSnapshot.quoteToUsdt(marketSelectedCurrency)
				: candles.get(candles.size() - 1).close();
		java.math.BigDecimal windowOpen = candles.isEmpty() ? price : candles.get(0).open();
		java.math.BigDecimal change = price.subtract(windowOpen);
		double pct = windowOpen.signum() == 0 ? 0.0
				: change.doubleValue() / windowOpen.doubleValue() * 100.0;
		boolean up = change.signum() >= 0;
		int trendColor = up ? GREEN : RED;

		// ---- price header ----
		marketPriceHost.removeAllViews();
		TextView pairLabel = new TextView(requireContext());
		pairLabel.setText(marketSelectedCurrency.ticker() + " / USDT");
		pairLabel.setTextColor(TEXT_SOFT);
		pairLabel.setTextSize(12f);
		marketPriceHost.addView(pairLabel);

		TextView priceValue = new TextView(requireContext());
		priceValue.setText("$" + fmtPrice(price));
		priceValue.setTextColor(trendColor);
		priceValue.setTextSize(24f);
		marketPriceHost.addView(priceValue);

		TextView changeValue = new TextView(requireContext());
		String sign = up ? "+" : "";
		changeValue.setText((up ? "\u25B2 " : "\u25BC ") + sign + fmtPrice(change)
				+ "  (" + sign + String.format(java.util.Locale.US, "%.2f", pct) + "%)");
		changeValue.setTextColor(trendColor);
		changeValue.setTextSize(12f);
		marketPriceHost.addView(changeValue);

		// ---- chart ----
		marketChartHost.removeAllViews();
		if (candles.isEmpty()) {
			TextView waiting = bodyText(t("screen.virtual_craft.holographic_shop.market.waiting"));
			waiting.setTextColor(TEXT_SOFT);
			waiting.setGravity(Gravity.CENTER);
			marketChartHost.addView(waiting, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, marketChartHeightPx));
		} else {
			marketChartHost.addView(buildCandleChart(candles, marketChartHeightPx),
					new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, marketChartHeightPx));
		}

		// ---- volume sub-chart ----
		if (marketVolumeHost != null) {
			marketVolumeHost.removeAllViews();
			if (!candles.isEmpty()) {
				marketVolumeHost.addView(buildVolumeChart(candles, dp(46)),
						new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
			}
		}

		// ---- stats (24h high / low / amplitude / volume) ----
		marketStatsHost.removeAllViews();
		if (!candles.isEmpty()) {
			java.math.BigDecimal high = candles.stream()
					.map(HolographicMarketData.Candle::high)
					.max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO);
			java.math.BigDecimal low = candles.stream()
					.map(HolographicMarketData.Candle::low)
					.min(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO);
			double amp = low.signum() == 0 ? 0.0
					: high.subtract(low).doubleValue() / low.doubleValue() * 100.0;
			java.math.BigDecimal vol = candles.stream()
					.map(HolographicMarketData.Candle::volume)
					.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

			// Even 4-up ticker bar. Each chip carries its own weight so the row fills
			// the width; a plain spacer() here would be MATCH_PARENT-wide and shove the
			// later chips off-screen (only HIGH would show).
			marketStatsHost.addView(statChip(t("screen.virtual_craft.holographic_shop.market.high"), "$" + fmtPrice(high), GREEN),
					weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			marketStatsHost.addView(statChip(t("screen.virtual_craft.holographic_shop.market.low"), "$" + fmtPrice(low), RED),
					weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			marketStatsHost.addView(statChip(t("screen.virtual_craft.holographic_shop.market.amplitude"),
					String.format(java.util.Locale.US, "%.2f", amp) + "%", CYAN),
					weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			marketStatsHost.addView(statChip(t("screen.virtual_craft.holographic_shop.market.vol24"),
					fmtCompact(vol) + " " + marketSelectedCurrency.ticker(), TEXT),
					weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		}

		// ---- order book + recent trades ----
		if (marketBookHost != null) {
			marketBookHost.removeAllViews();
			recordTrade(price, up);
			View book = buildOrderBook(price, up);
			var bookParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
			bookParams.setMargins(0, 0, dp(6), 0);
			marketBookHost.addView(book, bookParams);
			marketBookHost.addView(buildRecentTrades(),
					new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		}

		// ---- markets list (tap a row to switch pair) ----
		if (marketListHost != null) {
			marketListHost.removeAllViews();
			for (CryptoCurrency coin : MARKET_PAIRS) {
				List<HolographicMarketData.Candle> cc =
						HolographicMarketData.getCandles(coin, marketInterval);
				java.math.BigDecimal cp = cc.isEmpty()
						? exchangeSnapshot.quoteToUsdt(coin)
						: cc.get(cc.size() - 1).close();
				java.math.BigDecimal co = cc.isEmpty() ? cp : cc.get(0).open();
				double cpct = co.signum() == 0 ? 0.0
						: cp.subtract(co).doubleValue() / co.doubleValue() * 100.0;
				marketListHost.addView(marketTickerRow(coin, cp, cpct));
				marketListHost.addView(spacer(dp(6)));
			}
		}
	}

	/** A Binance-style markets-list row: icon · pair · last price · period change %. */
	private View marketTickerRow(CryptoCurrency coin, java.math.BigDecimal price, double pct) {
		boolean selected = coin == marketSelectedCurrency;
		boolean up = pct >= 0;
		LinearLayout row = card(selected ? PANEL_ACTIVE : PANEL_ROW, selected ? CYAN : WINDOW_STROKE, 10f);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(8), dp(10), dp(8));
		row.setOnClickListener(v -> {
			marketSelectedCurrency = coin;
			HolographicMarketData.ensureHistory(coin, marketInterval);
			requestRefresh(false, FocusTarget.NONE);
		});
		HolographicTheme.interactive(row);

		row.addView(currencyIconView(coin, ICON_SMALL_DP));

		TextView pair = bodyText(coin.ticker() + " / USDT");
		pair.setTextColor(selected ? CYAN : TEXT);
		pair.setMaxLines(1);
		var pairParams = weighted(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		pairParams.setMargins(dp(10), 0, dp(8), 0);
		row.addView(pair, pairParams);

		TextView priceView = bodyText("$" + fmtPrice(price));
		priceView.setTextColor(TEXT);
		priceView.setGravity(Gravity.END);
		row.addView(priceView);

		TextView pctView = new TextView(requireContext());
		pctView.setText((up ? "+" : "") + String.format(java.util.Locale.US, "%.2f", pct) + "%");
		pctView.setTextColor(up ? GREEN : RED);
		pctView.setTextSize(12f);
		pctView.setGravity(Gravity.END);
		var pctParams = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
		pctParams.setMargins(dp(10), 0, 0, 0);
		row.addView(pctView, pctParams);
		return row;
	}

	/** Adaptive price formatting with thousands separators, like a spot exchange ticker. */
	private static String fmtPrice(java.math.BigDecimal value) {
		java.math.BigDecimal abs = value.abs();
		int digits;
		if (abs.compareTo(java.math.BigDecimal.ONE) >= 0) {
			digits = 2;
		} else if (abs.compareTo(new java.math.BigDecimal("0.01")) >= 0) {
			digits = 4;
		} else {
			digits = 6;
		}
		java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
		nf.setMinimumFractionDigits(digits);
		nf.setMaximumFractionDigits(digits);
		return nf.format(value);
	}

	private View buildCandleChart(List<HolographicMarketData.Candle> candles, int chartHeightPx) {
		// Find price range
		java.math.BigDecimal minPrice = candles.stream()
				.map(HolographicMarketData.Candle::low)
				.min(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO);
		java.math.BigDecimal maxPrice = candles.stream()
				.map(HolographicMarketData.Candle::high)
				.max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ONE);
		java.math.BigDecimal range = maxPrice.subtract(minPrice);
		if (range.signum() == 0) range = java.math.BigDecimal.ONE;

		FrameLayout chartFrame = new FrameLayout(requireContext());
		ShapeDrawable chartBg = new ShapeDrawable();
		chartBg.setCornerRadius(dp(6));
		chartBg.setColor(0x20_FFFFFF);
		chartBg.setStroke(1, WINDOW_STROKE);
		chartFrame.setBackground(chartBg);
		int padV = dp(8);
		chartFrame.setPadding(dp(4), padV, dp(4), padV);

		int chartHeight = chartHeightPx - padV * 2;

		// Faint horizontal grid lines for readability (drawn behind the candles).
		LinearLayout grid = new LinearLayout(requireContext());
		grid.setOrientation(LinearLayout.VERTICAL);
		for (int g = 0; g < 4; g++) {
			View line = new View(requireContext());
			ShapeDrawable ld = new ShapeDrawable();
			ld.setColor(0x14_FFFFFF);
			line.setBackground(ld);
			grid.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
			if (g < 3) {
				grid.addView(spacer(0), new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
			}
		}
		chartFrame.addView(grid, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, chartHeight));

		LinearLayout candleRow = new LinearLayout(requireContext());
		candleRow.setOrientation(LinearLayout.HORIZONTAL);
		candleRow.setGravity(Gravity.BOTTOM);

		int candleWidth = dp(5);
		int gap = dp(1);
		int maxCandles = 60;
		int start = Math.max(0, candles.size() - maxCandles);

		for (int i = start; i < candles.size(); i++) {
			HolographicMarketData.Candle candle = candles.get(i);
			boolean up = candle.isUp();
			int color = up ? GREEN : RED;
			java.math.BigDecimal bodyTop = up ? candle.close() : candle.open();
			java.math.BigDecimal bodyBot = up ? candle.open() : candle.close();

			// Normalize to chart height
			double wickTopFrac = candle.high().subtract(minPrice).doubleValue() / range.doubleValue();
			double wickBotFrac = candle.low().subtract(minPrice).doubleValue() / range.doubleValue();
			double bodyTopFrac = bodyTop.subtract(minPrice).doubleValue() / range.doubleValue();
			double bodyBotFrac = bodyBot.subtract(minPrice).doubleValue() / range.doubleValue();

			int wickTopPx = (int)(wickTopFrac * chartHeight);
			int wickBotPx = (int)(wickBotFrac * chartHeight);
			int bodyTopPx = (int)(bodyTopFrac * chartHeight);
			int bodyBotPx = (int)(bodyBotFrac * chartHeight);
			int bodyH = Math.max(dp(1), bodyTopPx - bodyBotPx);

			// Candle container (vertical from bottom)
			FrameLayout candleFrame = new FrameLayout(requireContext());

			// Wick (thin line from low to high)
			View wick = new View(requireContext());
			ShapeDrawable wickBg = new ShapeDrawable();
			wickBg.setColor(color);
			wick.setBackground(wickBg);
			int wickH = Math.max(dp(1), wickTopPx - wickBotPx);
			var wickParams = new FrameLayout.LayoutParams(dp(1), wickH);
			wickParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			wickParams.bottomMargin = wickBotPx;
			candleFrame.addView(wick, wickParams);

			// Body (wider rect)
			View body = new View(requireContext());
			ShapeDrawable bodyBg = new ShapeDrawable();
			bodyBg.setColor(color);
			bodyBg.setCornerRadius(dp(1));
			body.setBackground(bodyBg);
			var bodyParams = new FrameLayout.LayoutParams(candleWidth, bodyH);
			bodyParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			bodyParams.bottomMargin = bodyBotPx;
			candleFrame.addView(body, bodyParams);

			var frameParams = new LinearLayout.LayoutParams(candleWidth + gap, chartHeight);
			candleRow.addView(candleFrame, frameParams);
		}

		chartFrame.addView(candleRow);

		// Price labels overlay (left side)
		LinearLayout priceLabels = new LinearLayout(requireContext());
		priceLabels.setOrientation(LinearLayout.VERTICAL);
		priceLabels.setGravity(Gravity.START);
		var priceLabelsParams = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
		priceLabels.setPadding(dp(2), dp(4), 0, dp(4));

		TextView highLabel = hintText("$" + fmtPrice(maxPrice));
		highLabel.setTextColor(0x80_FFFFFF);
		highLabel.setTextSize(8f);
		priceLabels.addView(highLabel, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f));

		TextView lowLabel = hintText("$" + fmtPrice(minPrice));
		lowLabel.setTextColor(0x80_FFFFFF);
		lowLabel.setTextSize(8f);
		lowLabel.setGravity(Gravity.BOTTOM);
		priceLabels.addView(lowLabel, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f));

		chartFrame.addView(priceLabels, priceLabelsParams);

		return chartFrame;
	}

	/** Volume histogram aligned 1:1 with the candlestick chart's last 60 bars. */
	private View buildVolumeChart(List<HolographicMarketData.Candle> candles, int heightPx) {
		FrameLayout frame = new FrameLayout(requireContext());
		ShapeDrawable bg = new ShapeDrawable();
		bg.setCornerRadius(dp(6));
		bg.setColor(0x20_FFFFFF);
		bg.setStroke(1, WINDOW_STROKE);
		frame.setBackground(bg);
		int padV = dp(4);
		frame.setPadding(dp(4), padV, dp(4), padV);
		int h = heightPx - padV * 2;

		int candleWidth = dp(5);
		int gap = dp(1);
		int maxCandles = 60;
		int start = Math.max(0, candles.size() - maxCandles);
		double maxVol = 0;
		for (int i = start; i < candles.size(); i++) {
			maxVol = Math.max(maxVol, candles.get(i).volume().doubleValue());
		}
		if (maxVol <= 0) {
			maxVol = 1;
		}

		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.BOTTOM);
		for (int i = start; i < candles.size(); i++) {
			HolographicMarketData.Candle c = candles.get(i);
			int color = c.isUp() ? 0x80_22C55E : 0x80_FF4D4F;
			int barH = Math.max(dp(1), (int) (c.volume().doubleValue() / maxVol * h));
			FrameLayout cell = new FrameLayout(requireContext());
			View bar = new View(requireContext());
			ShapeDrawable bd = new ShapeDrawable();
			bd.setColor(color);
			bar.setBackground(bd);
			var bp = new FrameLayout.LayoutParams(candleWidth, barH);
			bp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
			cell.addView(bar, bp);
			row.addView(cell, new LinearLayout.LayoutParams(candleWidth + gap, h));
		}
		frame.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
		return frame;
	}

	/**
	 * Appends a synthetic trade print to the rolling tape whenever the live price
	 * moves. The sim has no real per-trade feed, so amounts are deterministic
	 * pseudo-noise scaled to the pair's price magnitude. Tape resets on pair switch.
	 */
	private void recordTrade(java.math.BigDecimal price, boolean up) {
		if (marketTradesCurrency != marketSelectedCurrency) {
			marketTrades.clear();
			marketTradesCurrency = marketSelectedCurrency;
			lastTradePrice = null;
		}
		if (price == null || price.signum() <= 0) {
			return;
		}
		if (lastTradePrice != null && price.compareTo(lastTradePrice) == 0) {
			return;
		}
		double m = price.doubleValue();
		double scale = m > 5000 ? 0.6 : m > 100 ? 12 : m > 1 ? 200 : 20000;
		double amt = scale * (0.03 + pseudo(System.nanoTime()));
		marketTrades.addFirst(new MarketTrade(price, amt, up, System.currentTimeMillis()));
		while (marketTrades.size() > 30) {
			marketTrades.removeLast();
		}
		lastTradePrice = price;
	}

	/** Binance-style order book: 8 ask levels, the spread/last row, then 8 bid levels. */
	private View buildOrderBook(java.math.BigDecimal mid, boolean up) {
		LinearLayout col = card(PANEL_ROW, WINDOW_STROKE, 10f);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(8), dp(8), dp(8), dp(8));

		TextView title = hintText(t("screen.virtual_craft.holographic_shop.market.order_book"));
		title.setTextColor(TEXT_SOFT);
		title.setTextSize(11f);
		col.addView(title);
		col.addView(spacer(dp(4)));
		col.addView(bookLabels());
		col.addView(spacer(dp(2)));

		double m = mid == null ? 0.0 : mid.doubleValue();
		if (m <= 0) {
			return col;
		}
		int levels = 8;
		long seedBase = (long) Math.floor(m * 100.0);
		double tick = Math.max(m * 0.0005, m > 1 ? 0.01 : 0.000001);
		double scale = m > 5000 ? 2.2 : m > 100 ? 35 : m > 1 ? 600 : 50000;
		double[] askSizes = new double[levels + 1];
		double[] bidSizes = new double[levels + 1];
		double[] askCum = new double[levels + 1];
		double[] bidCum = new double[levels + 1];
		double ac = 0;
		double bc = 0;
		for (int i = 1; i <= levels; i++) {
			askSizes[i] = scale * (0.12 + pseudo(seedBase + i * 7L));
			ac += askSizes[i];
			askCum[i] = ac;
			bidSizes[i] = scale * (0.12 + pseudo(seedBase - i * 7L));
			bc += bidSizes[i];
			bidCum[i] = bc;
		}
		double maxCum = Math.max(Math.max(ac, bc), 1e-9);

		for (int i = levels; i >= 1; i--) {
			col.addView(depthRow(java.math.BigDecimal.valueOf(m + tick * i), askSizes[i], askCum[i] / maxCum, true));
		}
		TextView spread = new TextView(requireContext());
		spread.setSingleLine(true);
		spread.setText("$" + fmtPrice(mid));
		spread.setTextColor(up ? GREEN : RED);
		spread.setTextSize(13f);
		spread.setPadding(0, dp(3), 0, dp(3));
		col.addView(spread);
		for (int i = 1; i <= levels; i++) {
			col.addView(depthRow(java.math.BigDecimal.valueOf(m - tick * i), bidSizes[i], bidCum[i] / maxCum, false));
		}
		return col;
	}

	/** One order-book level: a depth-proportional tint bar with price + size overlaid. */
	private View depthRow(java.math.BigDecimal price, double size, double frac, boolean ask) {
		int rowH = dp(15);
		FrameLayout row = new FrameLayout(requireContext());

		LinearLayout bar = new LinearLayout(requireContext());
		bar.setOrientation(LinearLayout.HORIZONTAL);
		double f = Math.max(0.0, Math.min(1.0, frac));
		if (f < 1.0) {
			bar.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, rowH, (float) (1.0 - f)));
		}
		View fill = new View(requireContext());
		ShapeDrawable fd = new ShapeDrawable();
		fd.setColor(ask ? 0x22_FF4D4F : 0x22_22C55E);
		fill.setBackground(fd);
		bar.addView(fill, new LinearLayout.LayoutParams(0, rowH, (float) Math.max(0.0001, f)));
		row.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));

		LinearLayout text = new LinearLayout(requireContext());
		text.setOrientation(LinearLayout.HORIZONTAL);
		text.setGravity(Gravity.CENTER_VERTICAL);
		TextView p = new TextView(requireContext());
		p.setSingleLine(true);
		p.setText(fmtPrice(price));
		p.setTextSize(9.5f);
		p.setTextColor(ask ? RED : GREEN);
		text.addView(p, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView s = new TextView(requireContext());
		s.setSingleLine(true);
		s.setText(fmtAmount(size));
		s.setTextSize(9.5f);
		s.setTextColor(TEXT_SOFT);
		s.setGravity(Gravity.END);
		text.addView(s, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		row.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
		return row;
	}

	private View bookLabels() {
		LinearLayout labels = new LinearLayout(requireContext());
		labels.setOrientation(LinearLayout.HORIZONTAL);
		TextView lp = hintText(t("screen.virtual_craft.holographic_shop.market.col_price"));
		lp.setTextSize(9f);
		labels.addView(lp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView ls = hintText(t("screen.virtual_craft.holographic_shop.market.col_amount"));
		ls.setTextSize(9f);
		ls.setGravity(Gravity.END);
		labels.addView(ls, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		return labels;
	}

	/** Binance-style recent-trades tape (newest first) for the active pair. */
	private View buildRecentTrades() {
		LinearLayout col = card(PANEL_ROW, WINDOW_STROKE, 10f);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(8), dp(8), dp(8), dp(8));

		TextView title = hintText(t("screen.virtual_craft.holographic_shop.market.recent_trades"));
		title.setTextColor(TEXT_SOFT);
		title.setTextSize(11f);
		col.addView(title);
		col.addView(spacer(dp(4)));

		LinearLayout labels = new LinearLayout(requireContext());
		labels.setOrientation(LinearLayout.HORIZONTAL);
		TextView lp = hintText(t("screen.virtual_craft.holographic_shop.market.col_price"));
		lp.setTextSize(9f);
		labels.addView(lp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView ls = hintText(t("screen.virtual_craft.holographic_shop.market.col_amount"));
		ls.setTextSize(9f);
		ls.setGravity(Gravity.END);
		labels.addView(ls, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView lt = hintText(t("screen.virtual_craft.holographic_shop.market.col_time"));
		lt.setTextSize(9f);
		lt.setGravity(Gravity.END);
		labels.addView(lt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		col.addView(labels);
		col.addView(spacer(dp(2)));

		if (marketTrades.isEmpty()) {
			TextView empty = hintText("\u2014");
			empty.setTextSize(9.5f);
			col.addView(empty);
			return col;
		}
		java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm:ss");
		int shown = 0;
		for (MarketTrade tr : marketTrades) {
			if (shown++ >= 16) {
				break;
			}
			LinearLayout row = new LinearLayout(requireContext());
			row.setOrientation(LinearLayout.HORIZONTAL);
			TextView p = new TextView(requireContext());
			p.setSingleLine(true);
			p.setText(fmtPrice(tr.price()));
			p.setTextSize(9.5f);
			p.setTextColor(tr.buy() ? GREEN : RED);
			row.addView(p, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			TextView a = new TextView(requireContext());
			a.setSingleLine(true);
			a.setText(fmtAmount(tr.amount()));
			a.setTextSize(9.5f);
			a.setTextColor(TEXT_SOFT);
			a.setGravity(Gravity.END);
			row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			TextView ti = new TextView(requireContext());
			ti.setSingleLine(true);
			ti.setText(tf.format(new java.util.Date(tr.time())));
			ti.setTextSize(9.5f);
			ti.setTextColor(TEXT_DIM);
			ti.setGravity(Gravity.END);
			row.addView(ti, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			col.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(15)));
		}
		return col;
	}

	/** Compact magnitude formatting (K / M / B), like an exchange's 24h volume ticker. */
	private static String fmtCompact(java.math.BigDecimal value) {
		double v = value.doubleValue();
		double a = Math.abs(v);
		if (a >= 1_000_000_000.0) {
			return String.format(java.util.Locale.US, "%.2fB", v / 1_000_000_000.0);
		}
		if (a >= 1_000_000.0) {
			return String.format(java.util.Locale.US, "%.2fM", v / 1_000_000.0);
		}
		if (a >= 1_000.0) {
			return String.format(java.util.Locale.US, "%.2fK", v / 1_000.0);
		}
		return String.format(java.util.Locale.US, "%.2f", v);
	}

	private static String fmtAmount(double v) {
		double a = Math.abs(v);
		if (a >= 1000.0) {
			return String.format(java.util.Locale.US, "%.1f", v);
		}
		if (a >= 1.0) {
			return String.format(java.util.Locale.US, "%.3f", v);
		}
		return String.format(java.util.Locale.US, "%.5f", v);
	}

	/** Deterministic [0,1) pseudo-noise (SplitMix64) for synthesizing book/trade sizes. */
	private static double pseudo(long seed) {
		long x = seed * 6364136223846793005L + 1442695040888963407L;
		x ^= (x >>> 29);
		x *= 0xBF58476D1CE4E5B9L;
		x ^= (x >>> 32);
		return (x >>> 11) / (double) (1L << 53);
	}

	private View statChip(String label, String value, int valueColor) {
		LinearLayout chip = vertical();
		chip.setGravity(Gravity.CENTER);
		TextView labelView = hintText(label);
		labelView.setTextColor(TEXT_SOFT);
		labelView.setTextSize(10f);
		labelView.setGravity(Gravity.CENTER);
		chip.addView(labelView);
		TextView valueView = new TextView(requireContext());
		valueView.setText(value);
		valueView.setTextColor(valueColor);
		valueView.setTextSize(12f);
		valueView.setGravity(Gravity.CENTER);
		chip.addView(valueView);
		return chip;
	}

	private void buildMarketActions() {
		actionColumn.addView(bodyText(t("screen.virtual_craft.holographic_shop.market.info")));
		actionColumn.addView(spacer(dp(10)));

		// Rate status
		LinearLayout statusCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		statusCard.setPadding(dp(10), dp(10), dp(10), dp(10));
		statusCard.setOrientation(LinearLayout.VERTICAL);
		TextView statusLabel = hintText(t("screen.virtual_craft.holographic_shop.rate_status_short"));
		statusCard.addView(statusLabel);
		statusCard.addView(spacer(dp(3)));
		TextView statusValue = bodyText(rateStatusLabel());
		statusValue.setTextColor(rateStatusColor());
		statusCard.addView(statusValue);
		actionColumn.addView(statusCard, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		actionColumn.addView(spacer(dp(8)));

		// WebSocket indicator
		boolean wsConnected = dev.tatami.virtualcraft.exchange.ExchangeRateService.isWebSocketConnected();
		LinearLayout wsCard = card(PANEL_ROW, WINDOW_STROKE, 10f);
		wsCard.setPadding(dp(10), dp(10), dp(10), dp(10));
		wsCard.setOrientation(LinearLayout.VERTICAL);
		TextView wsLabel = hintText("WebSocket");
		wsCard.addView(wsLabel);
		wsCard.addView(spacer(dp(3)));
		TextView wsStatus = bodyText(wsConnected
				? t("screen.virtual_craft.holographic_shop.market.ws_connected")
				: t("screen.virtual_craft.holographic_shop.market.ws_disconnected"));
		wsStatus.setTextColor(wsConnected ? GREEN : RED);
		wsCard.addView(wsStatus);
		actionColumn.addView(wsCard, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private String actionTitle() {
		return switch (currentPage) {
			case WALLET -> t("screen.virtual_craft.holographic_shop.wallet.actions");
			case CONVERT -> t("screen.virtual_craft.holographic_shop.convert.actions");
			case SHOP -> t("screen.virtual_craft.holographic_shop.shop.actions");
			case TRANSFER -> t("screen.virtual_craft.holographic_shop.transfer.actions");
			case MINING -> t("screen.virtual_craft.holographic_shop.mining.actions");
			case CONTRACTS -> t("screen.virtual_craft.holographic_shop.contracts.actions");
			case MARKET -> t("screen.virtual_craft.holographic_shop.market.actions");
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
			case CONTRACTS -> t("screen.virtual_craft.holographic_shop.contracts.subtitle_short");
					case MARKET -> "";
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
		long btcMinor = miningBtcMinor(count);
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
		return hashVoucherSummary().totalCount();
	}

	private int countDataFragments() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		int total = 0;
		for (var stack : player.getInventory().items) {
			if (stack.is(ModItems.DATA_FRAGMENT.get())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private long miningBtcMinor(long count) {
		long remaining = Math.max(0L, count);
		long totalBtcMinor = 0L;
		for (VoucherTierCount tier : hashVoucherSummary().tiers()) {
			if (remaining <= 0L) {
				break;
			}
			long used = Math.min(remaining, tier.count());
			totalBtcMinor = safeAdd(totalBtcMinor, safeMultiply(tier.btcMinor(), used));
			remaining -= used;
		}
		return totalBtcMinor;
	}

	private VoucherSummary hashVoucherSummary() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return new VoucherSummary(List.of(), 0, 0L);
		}
		List<Item> voucherItems = ModItems.hashVoucherItems();
		int[] counts = new int[voucherItems.size()];
		for (var stack : player.getInventory().items) {
			if (!HashVoucherItem.isHashVoucher(stack)) {
				continue;
			}
			for (int index = 0; index < voucherItems.size(); index++) {
				if (stack.is(voucherItems.get(index))) {
					counts[index] += stack.getCount();
					break;
				}
			}
		}
		List<VoucherTierCount> tiers = new ArrayList<>();
		int totalCount = 0;
		long totalBtcMinor = 0L;
		for (int index = 0; index < voucherItems.size(); index++) {
			Item item = voucherItems.get(index);
			int count = counts[index];
			long value = item instanceof HashVoucherItem voucher ? voucher.btcPayoutMinor() : 0L;
			tiers.add(new VoucherTierCount(item, count, value));
			totalCount += count;
			totalBtcMinor = safeAdd(totalBtcMinor, safeMultiply(value, count));
		}
		return new VoucherSummary(tiers, totalCount, totalBtcMinor);
	}

	/** Show a transient app-style confirmation toast keyed by a lang string. */
	private void toast(String key) {
		HolographicTheme.toast(requireContext(), t(key));
	}

	private void sendShopAction(ShopActionPayload.Action action) {
		ShopOffer offer = selectedOffer();
		if (offer == null) {
			return;
		}
		PacketDistributor.sendToServer(new ShopActionPayload(sessionId, action, selectedOfferIndex, Math.max(1, parseInt(shopQuantityText, 1)), shopSettlementCurrency.id()));
		toast("screen.virtual_craft.holographic_shop.toast.order");
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
		toast("screen.virtual_craft.holographic_shop.toast.deposit");
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
		toast("screen.virtual_craft.holographic_shop.toast.withdraw");
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
		toast("screen.virtual_craft.holographic_shop.toast.convert");
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
		toast("screen.virtual_craft.holographic_shop.toast.transfer");
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
		toast("screen.virtual_craft.holographic_shop.toast.mining");
	}

	private void sendContractClaim(String contractId) {
		PacketDistributor.sendToServer(new dev.tatami.virtualcraft.network.WalletPreferencesActionPayload(
				sessionId,
				dev.tatami.virtualcraft.network.WalletPreferencesActionPayload.Action.CLAIM_CONTRACT,
				contractId));
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

	private long safeAdd(long a, long b) {
		try {
			return Math.addExact(a, b);
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
