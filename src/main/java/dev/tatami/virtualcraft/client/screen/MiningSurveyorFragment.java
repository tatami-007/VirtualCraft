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
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.LayoutInflater;
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
	private TextView resultCount;
	private EditText searchInput;
	private MiningSurveyTarget selectedTarget;
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

		main.addView(buildSearchBar());
		main.addView(spacer(dp(10)));
		main.addView(buildFilterRow());
		main.addView(spacer(dp(10)));

		ScrollView scroll = new HoloScrollView(requireContext());
		resultList = vertical();
		resultList.setPadding(dp(2), dp(2), dp(8), dp(2));
		scroll.addView(resultList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		main.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
		main.addView(spacer(dp(8)));
		resultCount = hintText("");
		main.addView(resultCount);

		LinearLayout action = vertical();
		action.setPadding(dp(14), dp(14), dp(14), dp(14));
		action.setBackground(HolographicTheme.acrylic(PANEL_ALT, WINDOW_STROKE, 10f));
		LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(224), ViewGroup.LayoutParams.MATCH_PARENT);
		actionParams.setMargins(dp(12), 0, 0, 0);
		body.addView(action, actionParams);

		targetCard = vertical();
		targetCard.setPadding(dp(14), dp(14), dp(14), dp(14));
		action.addView(targetCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		action.addView(spacer(dp(12)));
		TextView flowHint = hintText(t("screen.virtual_craft.mining_surveyor.flow_hint"));
		flowHint.setMaxLines(2);
		action.addView(flowHint);
		action.addView(spacer(dp(12)));
		action.addView(actionButton(t("screen.virtual_craft.mining_surveyor.scan"), CYAN, this::scan), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
		action.addView(spacer(dp(8)));
		action.addView(actionButton(t("screen.virtual_craft.mining_surveyor.clear"), 0xFF8AA0B4, this::clearLock), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
		action.addView(spacer(dp(8)));
		action.addView(actionButton(t("gui.done"), 0xFF8AA0B4, this::close), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

		refreshSelectedCard();
		refreshResults();
		root.post(() -> searchInput.requestFocus());
		return root;
	}

	private View buildHeader() {
		LinearLayout header = new LinearLayout(requireContext());
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		TextView title = titleText(t("screen.virtual_craft.mining_surveyor.title"));
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView hotkey = chip("E / Esc");
		header.addView(hotkey, new LinearLayout.LayoutParams(dp(82), dp(28)));
		return header;
	}

	private View buildSearchBar() {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		searchInput = input(searchText, t("screen.virtual_craft.mining_surveyor.search_hint"));
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
		row.addView(searchInput, new LinearLayout.LayoutParams(0, dp(36), 1f));
		LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(70), dp(36));
		clearParams.setMargins(dp(8), 0, 0, 0);
		row.addView(compactButton(t("screen.virtual_craft.mining_surveyor.clear_search"), () -> {
			searchText = "";
			searchInput.setText("");
			searchInput.requestFocus();
		}), clearParams);
		return row;
	}

	private View buildFilterRow() {
		LinearLayout block = vertical();
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		for (SourceFilter filter : SourceFilter.values()) {
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(32));
			if (filter.ordinal() > 0) {
				params.setMargins(dp(6), 0, 0, 0);
			}
			row.addView(toggleButton(filter.label(), sourceFilter == filter, () -> {
				sourceFilter = filter;
				refreshFilterRow(block);
				refreshResults();
			}), params);
		}
		LinearLayout.LayoutParams modParams = new LinearLayout.LayoutParams(dp(76), dp(32));
		modParams.setMargins(dp(10), 0, 0, 0);
		row.addView(compactButton("@mod", () -> {
			searchInput.setText(appendToken(searchInput.getText().toString(), "@"));
			searchInput.requestFocus();
		}), modParams);
		LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(dp(76), dp(32));
		tagParams.setMargins(dp(6), 0, 0, 0);
		row.addView(compactButton("#标签", () -> {
			searchInput.setText(appendToken(searchInput.getText().toString(), "#ores/"));
			searchInput.requestFocus();
		}), tagParams);
		block.addView(row);

		LinearLayout sortRow = new LinearLayout(requireContext());
		sortRow.setOrientation(LinearLayout.HORIZONTAL);
		for (SortMode mode : SortMode.values()) {
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(30));
			if (mode.ordinal() > 0) {
				params.setMargins(dp(6), 0, 0, 0);
			}
			sortRow.addView(toggleButton(mode.label(), sortMode == mode, () -> {
				sortMode = mode;
				refreshFilterRow(block);
				refreshResults();
			}), params);
		}
		block.addView(spacer(dp(6)));
		block.addView(sortRow);
		TextView help = hintText(t("screen.virtual_craft.mining_surveyor.search_help"));
		help.setSingleLine(true);
		help.setEllipsize(TextUtils.TruncateAt.END);
		block.addView(spacer(dp(6)));
		block.addView(help);
		return block;
	}

	private void refreshFilterRow(LinearLayout oldBlock) {
		if (oldBlock == null || oldBlock.getParent() == null) {
			return;
		}
		ViewGroup parent = (ViewGroup) oldBlock.getParent();
		int index = parent.indexOfChild(oldBlock);
		parent.removeView(oldBlock);
		parent.addView(buildFilterRow(), index);
		searchInput.requestFocus();
	}

	private void refreshResults() {
		if (resultList == null) {
			return;
		}
		resultList.removeAllViews();
		List<MiningSurveyTarget> visible = visibleTargets();
		for (MiningSurveyTarget target : visible) {
			resultList.addView(targetRow(target), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
			resultList.addView(spacer(dp(8)));
		}
		if (visible.isEmpty()) {
			resultList.addView(emptyState(t("screen.virtual_craft.mining_surveyor.empty"), t("screen.virtual_craft.mining_surveyor.empty_hint")));
		}
		if (resultCount != null) {
			resultCount.setText(t("screen.virtual_craft.mining_surveyor.count", visible.size()));
		}
	}

	private View targetRow(MiningSurveyTarget target) {
		boolean selected = selectedTarget != null && selectedTarget.key().equals(target.key());
		LinearLayout row = card(selected ? PANEL_ACTIVE : PANEL_ROW, selected ? CYAN : WINDOW_STROKE, 10f);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(dp(10), dp(8), dp(10), dp(8));
		row.addView(itemIconView(target.icon(), 28), new LinearLayout.LayoutParams(dp(28), dp(28)));
		LinearLayout text = vertical();
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);
		TextView name = bodyText(target.name().getString());
		name.setTextColor(selected ? CYAN : TEXT);
		text.addView(name);
		text.addView(hintText(namespaceLabel(target) + "  " + tagSummary(target)));
		row.addView(text, textParams);
		TextView action = chip(selected ? t("screen.virtual_craft.mining_surveyor.selected") : t("screen.virtual_craft.mining_surveyor.pick"));
		row.addView(action, new LinearLayout.LayoutParams(dp(74), dp(28)));
		row.setOnClickListener(view -> {
			selectedTarget = target;
			refreshSelectedCard();
			refreshResults();
		});
		return row;
	}

	private void refreshSelectedCard() {
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
		close();
	}

	private void clearLock() {
		PacketDistributor.sendToServer(new MiningSurveyorActionPayload("", true));
		close();
	}

	private void close() {
		Minecraft.getInstance().setScreen(null);
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
				return image;
			}
		}
		TextView fallback = chip("?");
		fallback.setTextSize(14f);
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

	private String appendToken(String current, String token) {
		String trimmed = current == null ? "" : current.trim();
		return trimmed.isEmpty() ? token : trimmed + " " + token;
	}

	private LinearLayout card(int fill, int stroke, float radiusDp) {
		LinearLayout card = vertical();
		card.setBackground(HolographicTheme.acrylic(fill, stroke, radiusDp));
		return card;
	}

	private Button actionButton(String text, int accentColor, Runnable action) {
		Button button = compactButton(text, action);
		button.setTextColor(TEXT);
		button.setBackground(HolographicTheme.rounded(PANEL_BUTTON, accentColor, 9f, 1));
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
		button.setPadding(dp(8), dp(6), dp(8), dp(6));
		button.setBackground(HolographicTheme.rounded(PANEL_BUTTON, WINDOW_STROKE, 9f, 1));
		button.setOnClickListener(v -> action.run());
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
