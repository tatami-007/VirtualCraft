package dev.tatami.virtualcraft.client.screen;

import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.CYAN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ACTIVE;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ALT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_BUTTON;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ROW;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_SOFT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT_DIM;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.TEXT_SOFT;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.WINDOW;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.WINDOW_STROKE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dev.tatami.virtualcraft.client.screen.holographic.HoloScrollView;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicIconCache;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.network.MiningSurveyorActionPayload;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.TextWatcher;
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
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

public class MiningSurveyorFragment extends Fragment implements ScreenCallback {
	private final ItemStack stack;
	private final List<MiningSurveyTarget> targets = new ArrayList<>();

	private FrameLayout root;
	private LinearLayout targetCard;
	private LinearLayout resultList;
	private ScrollView resultScroll;
	private LinearLayout filterRowContainer;
	private Button searchClearButton;
	private Button scanButton;
	private TextView headerCount;
	private EditText searchInput;
	private MiningSurveyTarget selectedTarget;
	private String lastClickedKey;
	private long lastClickTime;
	private SourceFilter sourceFilter = SourceFilter.ALL;
	private SortMode sortMode = SortMode.RELEVANCE;
	private String searchText = "";

	public MiningSurveyorFragment(ItemStack stack) {
		this.stack = stack.copy();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, icyllis.modernui.util.DataSet savedInstanceState) {
		targets.clear();
		targets.addAll(MiningSurveyTarget.available().stream()
				.filter(target -> !"all".equals(target.key()))
				.toList());
		selectedTarget = resolveInitialTarget();

		root = new FrameLayout(requireContext());
		root.setFocusable(true);
		root.setFocusableInTouchMode(true);
		root.setOnKeyListener((v, keyCode, event) -> handleListKey(keyCode, event));
		root.setBackground(HolographicTheme.rounded(0x40081422, 0, 0f, 0));
		root.setPadding(dp(18), dp(18), dp(18), dp(18));

		LinearLayout window = vertical();
		window.setBackground(HolographicTheme.acrylic(WINDOW, WINDOW_STROKE, 12f));
		window.setPadding(dp(18), dp(18), dp(18), dp(18));
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(760), dp(500));
		params.gravity = Gravity.CENTER;
		root.addView(window, params);

		window.addView(buildHeader());
		window.addView(spacer(dp(12)));

		View divider = new View(requireContext());
		divider.setBackground(HolographicTheme.rounded(0x2A0EA5E9, 0, 0f, 0));
		window.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
		window.addView(spacer(dp(12)));

		LinearLayout body = new LinearLayout(requireContext());
		body.setOrientation(LinearLayout.HORIZONTAL);
		window.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		LinearLayout main = vertical();
		main.setPadding(dp(14), dp(14), dp(14), dp(14));
		main.setBackground(HolographicTheme.acrylic(PANEL, WINDOW_STROKE, 10f));
		LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		body.addView(main, mainParams);

		// Search → grouped filters → result list. Keep permanent controls compact;
		// advanced syntax lives in the tooltip instead of occupying layout width.
		main.addView(buildSearchBar());
		main.addView(spacer(dp(8)));
		main.addView(buildFilterRow());
		main.addView(spacer(dp(8)));

		resultScroll = new HoloScrollView(requireContext());
		resultList = vertical();
		resultList.setPadding(dp(2), dp(2), dp(8), dp(18));
		resultScroll.addView(resultList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		main.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		LinearLayout action = vertical();
		action.setPadding(dp(14), dp(14), dp(14), dp(14));
		action.setBackground(HolographicTheme.acrylic(PANEL_ALT, WINDOW_STROKE, 10f));
		LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(224), ViewGroup.LayoutParams.MATCH_PARENT);
		actionParams.setMargins(dp(12), 0, 0, 0);
		body.addView(action, actionParams);

		// -- Selected target card ---
		targetCard = vertical();
		targetCard.setPadding(dp(14), dp(12), dp(14), dp(12));
		action.addView(targetCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		action.addView(spacer(dp(10)));

		// -- Scan button ---
		scanButton = actionButton(t("screen.virtual_craft.mining_surveyor.scan"), CYAN, this::scan);
		action.addView(scanButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
		action.addView(spacer(dp(8)));

		// -- Utility row ---
		action.addView(buildUtilityActions(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
		action.addView(buildHintRow());
		refreshSelectedCard();
		refreshResults();
		HolographicTheme.enterTransition(window);
		root.post(() -> {
			searchInput.clearFocus();
			root.requestFocus();
		});
		return root;
	}

	private View buildHeader() {
		LinearLayout header = new LinearLayout(requireContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = titleText(t("screen.virtual_craft.mining_surveyor.title"));
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		headerCount = hintText("");
		headerCount.setTextSize(13f);
		header.addView(headerCount);
		return header;
	}

	private View buildSearchBar() {
		// Search bar layout: [input flex=1] [✕ clear]. Result counts were removed
		// from this row because they clipped the right edge at GUI scale 3/4.
		LinearLayout bar = new LinearLayout(requireContext());
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);

		searchInput = input(searchText, t("screen.virtual_craft.mining_surveyor.search_hint"));
		searchInput.setTextSize(14f);
		searchInput.setPadding(dp(14), dp(7), dp(14), dp(7));
		// Hover-only syntax hint: @mod / #tag / space=AND / right-click clear.
		// Keeps the UI free of permanent prose while remaining discoverable.
		searchInput.setTooltipText(t("screen.virtual_craft.mining_surveyor.search_tooltip"));
		searchInput.setOnTouchListener((view, event) -> {
			if (event.getActionButton() == MotionEvent.BUTTON_SECONDARY
					|| event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
				if (event.getAction() == MotionEvent.ACTION_UP) {
					clearSearch();
				}
				return true;
			}
			return false;
		});
		searchInput.setOnContextClickListener(view -> true);
		searchInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				searchText = s.toString();
				refreshResults();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		// v25 Enter-to-scan: if exactly one result remains visible, hitting Enter
		// selects+scans without forcing the user back to the mouse for the scan
		// button. Most common path is "type 'diamo' → Enter".
		// ↑/↓ move the selection through the live result list, Enter scans it —
		// the whole pick→scan loop becomes keyboard-only, no reach for the mouse.
		searchInput.setOnKeyListener((v, keyCode, event) -> handleListKey(keyCode, event));
		LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(32), 1f);
		bar.addView(searchInput, searchParams);

		searchClearButton = compactButton("\u2715", this::clearSearch);
		searchClearButton.setTextSize(13f);
		searchClearButton.setPadding(0, 0, 0, 0);
		LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(30), dp(32));
		clearParams.setMargins(dp(6), 0, 0, 0);
		bar.addView(searchClearButton, clearParams);

		return bar;
	}

	private void attemptQuickScan() {
		List<MiningSurveyTarget> visible = visibleTargets();
		if (selectedTarget != null && containsKey(visible, selectedTarget)) {
			// Honor the active selection first — this is what the ↑/↓ + Enter flow expects.
			scan();
		} else if (visible.size() == 1) {
			// Disambiguated by the current search/filter — fire the one match.
			selectedTarget = visible.get(0);
			scan();
		}
		// Else ambiguous; do nothing so the player picks one explicitly.
	}

	private boolean handleListKey(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		switch (keyCode) {
			case KeyEvent.KEY_DOWN -> {
				moveSelection(1);
				return true;
			}
			case KeyEvent.KEY_UP -> {
				moveSelection(-1);
				return true;
			}
			case KeyEvent.KEY_ENTER, KeyEvent.KEY_KP_ENTER -> {
				attemptQuickScan();
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void moveSelection(int delta) {
		List<MiningSurveyTarget> visible = visibleTargets();
		if (visible.isEmpty()) {
			return;
		}
		int index = -1;
		if (selectedTarget != null) {
			for (int i = 0; i < visible.size(); i++) {
				if (visible.get(i).key().equals(selectedTarget.key())) {
					index = i;
					break;
				}
			}
		}
		int next = index < 0
				? (delta > 0 ? 0 : visible.size() - 1)
				: Math.floorMod(index + delta, visible.size());
		selectedTarget = visible.get(next);
		refreshSelectedCard();
		refreshResults();
		scrollSelectedIntoView(next);
	}

	private void scrollSelectedIntoView(int index) {
		if (resultScroll == null) {
			return;
		}
		int rowHeight = dp(48) + dp(6);
		int top = index * rowHeight;
		int bottom = top + dp(48);
		resultScroll.post(() -> {
			int viewTop = resultScroll.getScrollY();
			int viewHeight = resultScroll.getHeight();
			if (top < viewTop) {
				HolographicTheme.smoothScrollY(resultScroll, top);
			} else if (bottom > viewTop + viewHeight) {
				HolographicTheme.smoothScrollY(resultScroll, Math.max(0, bottom - viewHeight));
			}
		});
	}

	private boolean containsKey(List<MiningSurveyTarget> list, MiningSurveyTarget target) {
		for (MiningSurveyTarget candidate : list) {
			if (candidate.key().equals(target.key())) {
				return true;
			}
		}
		return false;
	}

	private void updateScanButton() {
		if (scanButton == null) {
			return;
		}
		if (selectedTarget != null) {
			String name = selectedTarget.name().getString();
			if (name.length() > 8) {
				name = name.substring(0, 8) + "\u2026";
			}
			scanButton.setText(t("screen.virtual_craft.mining_surveyor.scan") + " \u00b7 " + name);
			scanButton.setEnabled(true);
			scanButton.setAlpha(1f);
		} else {
			scanButton.setText(t("screen.virtual_craft.mining_surveyor.scan"));
			scanButton.setEnabled(false);
			scanButton.setAlpha(0.4f);
		}
	}

	private void refreshSearchAffordances() {
		if (searchClearButton != null) {
			boolean hasText = !searchText.isEmpty();
			searchClearButton.setEnabled(hasText);
			searchClearButton.setAlpha(hasText ? 1f : 0.35f);
		}
	}

	private void clearSearch() {
		searchText = "";
		searchInput.setText("");
		searchInput.requestFocus();
	}

	/**
	 * v25 filter row: a single horizontal row, no segment labels.
	 *
	 * <pre>
	 * [全部] [原版] [模组]  │  [★] [A→Z] [@]
	 *  ──source──            ───sort───
	 * </pre>
	 *
	 * <p>Layout principle: chip text alone carries meaning. Source chips use
	 * minimal nouns ("全部" / "原版" / "模组") and sort chips use pure symbols
	 * (★ recommended, A→Z alphabetical, @ namespace) so the row reads as
	 * pure visual primitives — no leading "来源:" or "排序:" prose. A 1-px
	 * vertical divider separates the two semantic groups.</p>
	 */
	private View buildFilterRow() {
		filterRowContainer = new LinearLayout(requireContext());
		filterRowContainer.setOrientation(LinearLayout.HORIZONTAL);
		filterRowContainer.setGravity(Gravity.CENTER_VERTICAL);
		populateFilterRow();
		return filterRowContainer;
	}

	private void populateFilterRow() {
		if (filterRowContainer == null) {
			return;
		}
		filterRowContainer.removeAllViews();

		LinearLayout sourceGroup = new LinearLayout(requireContext());
		sourceGroup.setOrientation(LinearLayout.HORIZONTAL);
		sourceGroup.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams sourceGroupParams = new LinearLayout.LayoutParams(0, dp(26), 1.35f);
		filterRowContainer.addView(sourceGroup, sourceGroupParams);

		for (SourceFilter filter : SourceFilter.values()) {
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(26), 1f);
			if (filter.ordinal() > 0) {
				params.setMargins(dp(3), 0, 0, 0);
			}
			sourceGroup.addView(toggleButton(filter.label(), sourceFilter == filter, () -> {
				sourceFilter = filter;
				populateFilterRow();
				refreshResults();
			}), params);
		}

		View divider = new View(requireContext());
		divider.setBackground(HolographicTheme.rounded(WINDOW_STROKE, 0, 0f, 0));
		LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(16));
		dividerParams.setMargins(dp(8), 0, dp(8), 0);
		filterRowContainer.addView(divider, dividerParams);

		LinearLayout sortGroup = new LinearLayout(requireContext());
		sortGroup.setOrientation(LinearLayout.HORIZONTAL);
		sortGroup.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout.LayoutParams sortGroupParams = new LinearLayout.LayoutParams(0, dp(26), 1f);
		filterRowContainer.addView(sortGroup, sortGroupParams);

		for (SortMode mode : SortMode.values()) {
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(26), 1f);
			if (mode.ordinal() > 0) {
				params.setMargins(dp(3), 0, 0, 0);
			}
			sortGroup.addView(toggleButton(mode.label(), sortMode == mode, () -> {
				sortMode = mode;
				populateFilterRow();
				refreshResults();
			}), params);
		}
	}

	private void refreshResults() {
		if (resultList == null) {
			return;
		}
		resultList.removeAllViews();
		List<MiningSurveyTarget> visible = visibleTargets();
		for (MiningSurveyTarget target : visible) {
			resultList.addView(targetRow(target), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
			resultList.addView(spacer(dp(6)));
		}
		if (visible.isEmpty()) {
			resultList.addView(emptyState(t("screen.virtual_craft.mining_surveyor.empty"), t("screen.virtual_craft.mining_surveyor.empty_hint")));
		}
		if (headerCount != null) {
			headerCount.setText(t("screen.virtual_craft.mining_surveyor.count", visible.size()));
		}
		refreshSearchAffordances();
	}

	private View targetRow(MiningSurveyTarget target) {
		boolean selected = selectedTarget != null && selectedTarget.key().equals(target.key());
		// v25 selected-row visual: a 3dp cyan left stripe plus a tinted background
		// reads instantly across the list, even at a glance. Previously only the
		// border color changed — invisible against the dim panel background.
		LinearLayout container = new LinearLayout(requireContext());
		container.setOrientation(LinearLayout.HORIZONTAL);
		container.setGravity(Gravity.CENTER_VERTICAL);
		container.setBackground(HolographicTheme.acrylic(
				selected ? PANEL_ACTIVE : PANEL_ROW,
				selected ? CYAN : WINDOW_STROKE, 10f));

		if (selected) {
			View stripe = new View(requireContext());
			stripe.setBackground(HolographicTheme.rounded(CYAN, 0, 0f, 0));
			LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT);
			stripeParams.setMargins(dp(2), dp(6), dp(8), dp(6));
			container.addView(stripe, stripeParams);
		} else {
			// Transparent placeholder so selected/unselected rows align horizontally.
			container.addView(spacer(dp(13)), new LinearLayout.LayoutParams(dp(13), ViewGroup.LayoutParams.MATCH_PARENT));
		}

		container.addView(itemIconView(target.icon(), 26), new LinearLayout.LayoutParams(dp(26), dp(26)));
		LinearLayout text = vertical();
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(10), 0);
		TextView name = bodyText(target.name().getString());
		name.setTextColor(selected ? CYAN : TEXT);
		text.addView(name);
		text.addView(hintText(namespaceLabel(target) + "  " + tagSummary(target)));
		container.addView(text, textParams);
		// Single click selects (so the player can inspect the side card); a quick
		// second click on the same row commits the scan — one gesture, no detour to
		// the Scan button. The 350ms window mirrors a standard double-click.
		container.setOnClickListener(view -> {
			long now = System.currentTimeMillis();
			boolean doubleClick = target.key().equals(lastClickedKey) && (now - lastClickTime) < 350L;
			lastClickedKey = target.key();
			lastClickTime = now;
			selectedTarget = target;
			if (doubleClick) {
				scan();
			} else {
				refreshSelectedCard();
				refreshResults();
			}
		});
		HolographicTheme.interactive(container);
		return container;
	}

	private void refreshSelectedCard() {
		updateScanButton();
		if (targetCard == null) {
			return;
		}
		targetCard.removeAllViews();
		targetCard.setBackground(HolographicTheme.acrylic(PANEL_ROW, WINDOW_STROKE, 10f));
		targetCard.addView(hintText(t("screen.virtual_craft.mining_surveyor.target")));
		targetCard.addView(spacer(dp(8)));
		if (selectedTarget == null) {
			targetCard.addView(bodyText(t("screen.virtual_craft.mining_surveyor.empty")));
			return;
		}
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(itemIconView(selectedTarget.icon(), 34), new LinearLayout.LayoutParams(dp(34), dp(34)));
		LinearLayout text = vertical();
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, 0, 0);
		text.addView(bodyText(selectedTarget.name().getString()));
		text.addView(hintText(namespaceLabel(selectedTarget)));
		row.addView(text, textParams);
		targetCard.addView(row);
		targetCard.addView(spacer(dp(10)));
		TextView tags = hintText(tagSummary(selectedTarget));
		tags.setMaxLines(2);
		targetCard.addView(tags);
	}

	private View buildUtilityActions() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.addView(secondaryActionButton(t("screen.virtual_craft.mining_surveyor.clear"), this::clearLock),
				new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
		LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
		doneParams.setMargins(dp(8), 0, 0, 0);
		row.addView(secondaryActionButton(t("gui.done"), this::close), doneParams);
		return row;
	}

	/** Hint row below the utility buttons. */
	private View buildHintRow() {
		TextView hint = hintText(t("screen.virtual_craft.mining_surveyor.dblclick_hint"));
		hint.setTextSize(10f);
		hint.setGravity(Gravity.CENTER);
		hint.setPadding(0, dp(6), 0, 0);
		return hint;
	}

	private Button secondaryActionButton(String text, Runnable action) {
		Button button = compactButton(text, action);
		button.setTextColor(TEXT_DIM);
		button.setBackground(HolographicTheme.rounded(PANEL_ROW, WINDOW_STROKE, 9f, 1));
		return button;
	}

	private List<MiningSurveyTarget> visibleTargets() {
		List<SearchToken> tokens = SearchToken.parse(searchText);
		Comparator<MiningSurveyTarget> comparator = switch (sortMode) {
			case RELEVANCE -> Comparator.<MiningSurveyTarget>comparingInt(target -> relevance(target, tokens)).reversed()
					.thenComparing(target -> target.name().getString(), String.CASE_INSENSITIVE_ORDER);
			case NAME -> Comparator.comparing(target -> target.name().getString(), String.CASE_INSENSITIVE_ORDER);
			case MOD -> Comparator.comparing(this::namespace).thenComparing(target -> target.name().getString(), String.CASE_INSENSITIVE_ORDER);
		};
		return targets.stream()
				.filter(sourceFilter::matches)
				.filter(target -> matches(target, tokens))
				.sorted(comparator)
				.toList();
	}

	private boolean matches(MiningSurveyTarget target, List<SearchToken> tokens) {
		for (SearchToken token : tokens) {
			if (!token.matches(target, this)) {
				return false;
			}
		}
		return true;
	}

	private int relevance(MiningSurveyTarget target, List<SearchToken> tokens) {
		if (tokens.isEmpty()) {
			return 0;
		}
		int score = 0;
		String name = target.name().getString().toLowerCase(Locale.ROOT);
		String key = target.key().toLowerCase(Locale.ROOT);
		for (SearchToken token : tokens) {
			if (name.equals(token.value) || key.equals(token.value)) score += 40;
			else if (name.startsWith(token.value) || key.startsWith(token.value)) score += 18;
			else if (name.contains(token.value) || key.contains(token.value)) score += 8;
			else if (namespace(target).contains(token.value)) score += 4;
		}
		return score;
	}

	private void scan() {
		if (selectedTarget == null) {
			return;
		}
		PacketDistributor.sendToServer(new MiningSurveyorActionPayload(selectedTarget.key(), false));
		toast("screen.virtual_craft.mining_surveyor.toast.scan");
		close();
	}

	private void clearLock() {
		PacketDistributor.sendToServer(new MiningSurveyorActionPayload("", true));
		toast("screen.virtual_craft.mining_surveyor.toast.clear");
		close();
	}

	private void close() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.screen instanceof MuiScreen mui && mui.getFragment() == this) {
				mui.self().onClose();
			} else {
				minecraft.setScreen(null);
			}
		});
	}

	@Override
	public boolean isBackKey(int keyCode, KeyEvent event) {
		return keyCode == KeyEvent.KEY_ESCAPE || keyCode == KeyEvent.KEY_E;
	}

	@Override
	public boolean shouldBlurBackground() {
		return false;
	}

	@Override
	public boolean hasDefaultBackground() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private MiningSurveyTarget resolveInitialTarget() {
		MiningSurveyTarget saved = MiningSurveyTargetData.selectedTarget(stack);
		if (!"all".equals(saved.key()) && targets.stream().anyMatch(target -> target.key().equals(saved.key()))) {
			return saved;
		}
		return targets.isEmpty() ? null : targets.getFirst();
	}

	private View itemIconView(ItemStack stack, int sizeDp) {
		ResourceLocation itemId = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId != null) {
			ImageDrawable drawable = HolographicIconCache.itemIcon(itemId, dp(sizeDp));
			if (drawable != null) {
				ImageView image = new ImageView(requireContext());
				image.setImageDrawable(drawable);
				image.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
				return image;
			}
		}
		TextView fallback = chip("?");
		fallback.setTextSize(14f);
		fallback.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
		return fallback;
	}

	private String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private String tagSummary(MiningSurveyTarget target) {
		return target.tags().stream()
				.findFirst()
				.map(TagKey::location)
				.map(ResourceLocation::toString)
				.orElse("#c:ores");
	}

	private String namespaceLabel(MiningSurveyTarget target) {
		return "@" + namespace(target);
	}

	private String namespace(MiningSurveyTarget target) {
		ItemStack icon = target.icon();
		if (!icon.isEmpty()) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(icon.getItem());
			if (id != null) {
				return id.getNamespace();
			}
		}
		for (Block block : target.fallbackBlocks()) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (id != null) {
				return id.getNamespace();
			}
		}
		return "unknown";
	}

	private LinearLayout card(int fill, int stroke, float radiusDp) {
		LinearLayout card = vertical();
		card.setBackground(HolographicTheme.acrylic(fill, stroke, radiusDp));
		return card;
	}

	private Button actionButton(String text, int accentColor, Runnable action) {
		Button button = compactButton(text, action);
		button.setTextColor(TEXT);
		button.setTextSize(13f);
		button.setBackground(HolographicTheme.rounded(PANEL_ACTIVE, accentColor, 9f, 1));
		return button;
	}

	private Button compactButton(String text, Runnable action) {
		Button button = new Button(requireContext());
		button.setText(text);
		button.setTextSize(12f);
		button.setTextColor(TEXT_DIM);
		button.setGravity(Gravity.CENTER);
		button.setMinimumWidth(0);
		button.setMinimumHeight(0);
		button.setPadding(dp(8), dp(5), dp(8), dp(5));
		button.setBackground(HolographicTheme.rounded(PANEL_BUTTON, WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
		HolographicTheme.interactive(button);
		return button;
	}

	private Button toggleButton(String text, boolean active, Runnable action) {
		Button button = compactButton(text, action);
		button.setTextColor(active ? TEXT : TEXT_DIM);
		button.setBackground(HolographicTheme.rounded(active ? PANEL_ACTIVE : PANEL_BUTTON, active ? CYAN : WINDOW_STROKE, 9f, 1));
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
		input.setBackground(HolographicTheme.rounded(PANEL_ROW, WINDOW_STROKE, 9f, 1));
		return input;
	}

	private TextView titleText(String text) {
		TextView view = bodyText(text);
		view.setTextSize(18f);
		return view;
	}

	private TextView bodyText(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(13f);
		view.setTextColor(TEXT);
		view.setSingleLine(true);
		view.setEllipsize(TextUtils.TruncateAt.END);
		return view;
	}

	private TextView hintText(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(12f);
		view.setTextColor(TEXT_DIM);
		return view;
	}

	private TextView chip(String text) {
		TextView view = new TextView(requireContext());
		view.setText(text);
		view.setTextSize(12f);
		view.setTextColor(TEXT);
		view.setGravity(Gravity.CENTER);
		view.setSingleLine(true);
		view.setEllipsize(TextUtils.TruncateAt.END);
		view.setBackground(HolographicTheme.rounded(PANEL_ACTIVE, CYAN, 999f, 1));
		return view;
	}

	private View emptyState(String title, String subtitle) {
		LinearLayout state = card(PANEL_SOFT, WINDOW_STROKE, 10f);
		state.setPadding(dp(14), dp(14), dp(14), dp(14));
		state.addView(bodyText(title));
		state.addView(spacer(dp(6)));
		TextView subtitleView = hintText(subtitle);
		subtitleView.setMaxLines(2);
		state.addView(subtitleView);
		return state;
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

	private int dp(float value) {
		return root == null ? Math.round(value) : root.dp(value);
	}

	private void toast(String key) {
		HolographicTheme.toast(requireContext(), t(key));
	}

	private enum SortMode {
		RELEVANCE,
		NAME,
		MOD;

		String label() {
			return Component.translatable("screen.virtual_craft.mining_surveyor.sort." + name().toLowerCase(Locale.ROOT)).getString();
		}

		SortMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private enum SourceFilter {
		ALL,
		VANILLA,
		MODDED;

		String label() {
			return Component.translatable("screen.virtual_craft.mining_surveyor.filter." + name().toLowerCase(Locale.ROOT)).getString();
		}

		boolean matches(MiningSurveyTarget target) {
			String ns = namespaceOf(target);
			return switch (this) {
				case ALL -> true;
				case VANILLA -> "minecraft".equals(ns);
				case MODDED -> !"minecraft".equals(ns);
			};
		}

		SourceFilter next() {
			return values()[(ordinal() + 1) % values().length];
		}

		private String namespaceOf(MiningSurveyTarget target) {
			ItemStack icon = target.icon();
			if (!icon.isEmpty()) {
				ResourceLocation id = BuiltInRegistries.ITEM.getKey(icon.getItem());
				if (id != null) {
					return id.getNamespace();
				}
			}
			return target.fallbackBlocks().stream()
					.map(BuiltInRegistries.BLOCK::getKey)
					.filter(id -> id != null)
					.findFirst()
					.map(ResourceLocation::getNamespace)
					.orElse("unknown");
		}
	}

	private record SearchToken(TokenKind kind, String value) {
		static List<SearchToken> parse(String query) {
			if (query.isBlank()) {
				return List.of();
			}
			List<SearchToken> tokens = new ArrayList<>();
			for (String raw : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
				if (raw.length() > 1 && raw.charAt(0) == '@') {
					tokens.add(new SearchToken(TokenKind.MOD, raw.substring(1)));
				} else if (raw.length() > 1 && raw.charAt(0) == '#') {
					tokens.add(new SearchToken(TokenKind.TAG, raw.substring(1)));
				} else if (!raw.isBlank()) {
					tokens.add(new SearchToken(TokenKind.TEXT, raw));
				}
			}
			return tokens;
		}

		boolean matches(MiningSurveyTarget target, MiningSurveyorFragment screen) {
			return switch (kind) {
				case TEXT -> target.name().getString().toLowerCase(Locale.ROOT).contains(value)
						|| target.key().toLowerCase(Locale.ROOT).contains(value)
						|| screen.namespace(target).contains(value)
						|| screen.tagSummary(target).toLowerCase(Locale.ROOT).contains(value);
				case MOD -> screen.namespace(target).contains(value);
				case TAG -> target.key().toLowerCase(Locale.ROOT).contains(value)
						|| target.tags().stream()
								.map(TagKey::location)
								.map(ResourceLocation::toString)
								.map(text -> text.toLowerCase(Locale.ROOT))
								.anyMatch(text -> text.contains(value));
			};
		}
	}

	private enum TokenKind {
		TEXT,
		MOD,
		TAG
	}
}
