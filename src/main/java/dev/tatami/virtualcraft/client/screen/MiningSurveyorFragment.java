package dev.tatami.virtualcraft.client.screen;

import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.CYAN;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL;
import static dev.tatami.virtualcraft.client.screen.holographic.HolographicTheme.PANEL_ACTIVE;
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

/**
 * Redesigned mining surveyor GUI: single-column, click-to-scan, search-only.
 * <p>No right panel, no filter tabs, no sort buttons. Type to search,
 * click to lock target, done. Keyboard: arrow keys to navigate, Enter to confirm.</p>
 */
public class MiningSurveyorFragment extends Fragment implements ScreenCallback {
	private final ItemStack stack;
	private final List<MiningSurveyTarget> targets = new ArrayList<>();

	private FrameLayout root;
	private LinearLayout resultList;
	private ScrollView resultScroll;
	private EditText searchInput;
	private TextView headerCount;
	private String searchText = "";
	private int highlightIndex = -1;

	public MiningSurveyorFragment(ItemStack stack) {
		this.stack = stack.copy();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, icyllis.modernui.util.DataSet savedInstanceState) {
		targets.clear();
		targets.addAll(MiningSurveyTarget.available().stream()
				.filter(target -> !"all".equals(target.key()))
				.toList());

		root = new FrameLayout(requireContext());
		root.setFocusable(true);
		root.setFocusableInTouchMode(true);
		root.setOnKeyListener((v, keyCode, event) -> handleKey(keyCode, event));
		root.setBackground(HolographicTheme.rounded(0x40081422, 0, 0f, 0));
		root.setPadding(dp(18), dp(18), dp(18), dp(18));

		LinearLayout window = vertical();
		window.setBackground(HolographicTheme.acrylic(WINDOW, WINDOW_STROKE, 12f));
		window.setPadding(dp(18), dp(16), dp(18), dp(16));
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(500), dp(460));
		params.gravity = Gravity.CENTER;
		root.addView(window, params);

		window.addView(buildHeader());
		window.addView(spacer(dp(10)));

		MiningSurveyTarget current = resolveCurrentTarget();
		if (current != null) {
			window.addView(buildTrackingBar(current));
			window.addView(spacer(dp(8)));
		}

		window.addView(buildSearchBar());
		window.addView(spacer(dp(10)));

		resultScroll = new HoloScrollView(requireContext());
		resultList = vertical();
		resultList.setPadding(dp(2), dp(2), dp(6), dp(14));
		resultScroll.addView(resultList, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		window.addView(resultScroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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

		TextView title = new TextView(requireContext());
		title.setText(t("screen.virtual_craft.mining_surveyor.title"));
		title.setTextSize(17f);
		title.setTextColor(TEXT);
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

		headerCount = new TextView(requireContext());
		headerCount.setTextSize(13f);
		headerCount.setTextColor(TEXT_DIM);
		header.addView(headerCount);
		return header;
	}

	private View buildTrackingBar(MiningSurveyTarget current) {
		LinearLayout bar = new LinearLayout(requireContext());
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setBackground(HolographicTheme.acrylic(PANEL_ACTIVE, CYAN, 8f));
		bar.setPadding(dp(12), dp(8), dp(8), dp(8));

		bar.addView(itemIconView(current.icon(), 20), new LinearLayout.LayoutParams(dp(20), dp(20)));

		TextView label = new TextView(requireContext());
		label.setText(t("screen.virtual_craft.mining_surveyor.tracking") + "  " + current.name().getString());
		label.setTextSize(12f);
		label.setTextColor(CYAN);
		label.setSingleLine(true);
		label.setEllipsize(TextUtils.TruncateAt.END);
		LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		labelParams.setMargins(dp(8), 0, dp(8), 0);
		bar.addView(label, labelParams);

		Button clearBtn = new Button(requireContext());
		clearBtn.setText("\u2715");
		clearBtn.setTextSize(12f);
		clearBtn.setTextColor(TEXT_DIM);
		clearBtn.setGravity(Gravity.CENTER);
		clearBtn.setMinimumWidth(0);
		clearBtn.setMinimumHeight(0);
		clearBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
		clearBtn.setBackground(HolographicTheme.rounded(PANEL_BUTTON, WINDOW_STROKE, 6f, 1));
		clearBtn.setOnClickListener(v -> {
			PacketDistributor.sendToServer(new MiningSurveyorActionPayload("", true));
			toast("screen.virtual_craft.mining_surveyor.toast.clear");
			close();
		});
		HolographicTheme.interactive(clearBtn);
		bar.addView(clearBtn, new LinearLayout.LayoutParams(dp(28), dp(24)));

		HolographicTheme.interactive(bar);
		return bar;
	}

	private View buildSearchBar() {
		LinearLayout bar = new LinearLayout(requireContext());
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);

		searchInput = new EditText(requireContext());
		searchInput.setText(searchText);
		searchInput.setHint(t("screen.virtual_craft.mining_surveyor.search_hint"));
		searchInput.setSingleLine();
		searchInput.setTextSize(13f);
		searchInput.setTextColor(TEXT);
		searchInput.setHintTextColor(TEXT_SOFT);
		searchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
		searchInput.setBackground(HolographicTheme.rounded(PANEL_ROW, WINDOW_STROKE, 8f, 1));
		searchInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				searchText = s.toString();
				highlightIndex = -1;
				refreshResults();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		searchInput.setOnKeyListener((v, keyCode, event) -> handleKey(keyCode, event));
		bar.addView(searchInput, new LinearLayout.LayoutParams(0, dp(34), 1f));

		return bar;
	}

	private boolean handleKey(int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) {
			return false;
		}
		List<MiningSurveyTarget> visible = visibleTargets();
		switch (keyCode) {
			case KeyEvent.KEY_DOWN -> {
				highlightIndex = Math.min(highlightIndex + 1, visible.size() - 1);
				refreshResults();
				scrollIntoView(highlightIndex);
				return true;
			}
			case KeyEvent.KEY_UP -> {
				highlightIndex = Math.max(highlightIndex - 1, 0);
				refreshResults();
				scrollIntoView(highlightIndex);
				return true;
			}
			case KeyEvent.KEY_ENTER, KeyEvent.KEY_KP_ENTER -> {
				if (highlightIndex >= 0 && highlightIndex < visible.size()) {
					lockTarget(visible.get(highlightIndex));
				} else if (visible.size() == 1) {
					lockTarget(visible.get(0));
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private void scrollIntoView(int index) {
		if (resultScroll == null || index < 0) {
			return;
		}
		int rowH = dp(46) + dp(4);
		int top = index * rowH;
		int bottom = top + dp(46);
		resultScroll.post(() -> {
			int viewTop = resultScroll.getScrollY();
			int viewH = resultScroll.getHeight();
			if (top < viewTop) {
				HolographicTheme.smoothScrollY(resultScroll, top);
			} else if (bottom > viewTop + viewH) {
				HolographicTheme.smoothScrollY(resultScroll, Math.max(0, bottom - viewH));
			}
		});
	}

	private void refreshResults() {
		if (resultList == null) {
			return;
		}
		resultList.removeAllViews();
		List<MiningSurveyTarget> visible = visibleTargets();
		for (int i = 0; i < visible.size(); i++) {
			MiningSurveyTarget target = visible.get(i);
			boolean highlighted = i == highlightIndex;
			resultList.addView(targetRow(target, highlighted),
					new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
			resultList.addView(spacer(dp(4)));
		}
		if (visible.isEmpty()) {
			LinearLayout empty = vertical();
			empty.setPadding(dp(14), dp(20), dp(14), dp(20));
			empty.setBackground(HolographicTheme.acrylic(PANEL_SOFT, WINDOW_STROKE, 8f));
			TextView msg = new TextView(requireContext());
			msg.setText(t("screen.virtual_craft.mining_surveyor.empty"));
			msg.setTextSize(13f);
			msg.setTextColor(TEXT_DIM);
			msg.setGravity(Gravity.CENTER);
			empty.addView(msg);
			resultList.addView(empty);
		}
		if (headerCount != null) {
			headerCount.setText(t("screen.virtual_craft.mining_surveyor.count", visible.size()));
		}
	}

	private View targetRow(MiningSurveyTarget target, boolean highlighted) {
		LinearLayout row = new LinearLayout(requireContext());
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackground(HolographicTheme.acrylic(
				highlighted ? PANEL_ACTIVE : PANEL_ROW,
				highlighted ? CYAN : WINDOW_STROKE, 8f));
		row.setPadding(dp(12), dp(6), dp(12), dp(6));

		if (highlighted) {
			View stripe = new View(requireContext());
			stripe.setBackground(HolographicTheme.rounded(CYAN, 0, 0f, 0));
			LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT);
			stripeParams.setMargins(0, dp(4), dp(10), dp(4));
			row.addView(stripe, stripeParams);
		}

		row.addView(itemIconView(target.icon(), 24),
				new LinearLayout.LayoutParams(dp(24), dp(24)));

		LinearLayout text = vertical();
		LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textParams.setMargins(dp(10), 0, dp(8), 0);

		TextView name = new TextView(requireContext());
		name.setText(target.name().getString());
		name.setTextSize(13f);
		name.setTextColor(highlighted ? CYAN : TEXT);
		name.setSingleLine(true);
		name.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(name);

		TextView meta = new TextView(requireContext());
		meta.setText(namespaceLabel(target) + "  " + tagSummary(target));
		meta.setTextSize(11f);
		meta.setTextColor(TEXT_DIM);
		meta.setSingleLine(true);
		meta.setEllipsize(TextUtils.TruncateAt.END);
		text.addView(meta);

		row.addView(text, textParams);

		row.setOnClickListener(v -> lockTarget(target));
		HolographicTheme.interactive(row);
		return row;
	}

	private void lockTarget(MiningSurveyTarget target) {
		PacketDistributor.sendToServer(new MiningSurveyorActionPayload(target.key(), false));
		toast("screen.virtual_craft.mining_surveyor.toast.scan");
		close();
	}

	private List<MiningSurveyTarget> visibleTargets() {
		List<SearchToken> tokens = SearchToken.parse(searchText);
		Comparator<MiningSurveyTarget> comparator = tokens.isEmpty()
				? Comparator.comparing(t -> t.name().getString(), String.CASE_INSENSITIVE_ORDER)
				: Comparator.<MiningSurveyTarget>comparingInt(t -> relevance(t, tokens)).reversed()
						.thenComparing(t -> t.name().getString(), String.CASE_INSENSITIVE_ORDER);
		return targets.stream()
				.filter(t -> matches(t, tokens))
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

	private MiningSurveyTarget resolveCurrentTarget() {
		MiningSurveyTarget saved = MiningSurveyTargetData.selectedTarget(stack);
		if (!"all".equals(saved.key()) && targets.stream().anyMatch(t -> t.key().equals(saved.key()))) {
			return saved;
		}
		return null;
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
		TextView fallback = new TextView(requireContext());
		fallback.setText("?");
		fallback.setTextSize(14f);
		fallback.setTextColor(TEXT);
		fallback.setGravity(Gravity.CENTER);
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

	String namespace(MiningSurveyTarget target) {
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
