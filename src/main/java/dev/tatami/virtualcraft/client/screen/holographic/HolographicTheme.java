package dev.tatami.virtualcraft.client.screen.holographic;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.LayerDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Toast;

/**
 * Static design tokens & drawable helpers shared by every page of the
 * Holographic Terminal UI. Keeping them in one place guarantees a consistent
 * look and shrinks the main fragment.
 */
public final class HolographicTheme {
	private HolographicTheme() {
	}

	// -- Backdrop layers (Win11 Light Acrylic, no real blur) ------------
	// 放弃真模糊后，面板 alpha 提到 90-96%，利用顶部高光线 + 深青描边
	// 来保持“玻璃边”视觉，不再带全屏 blur shader。
	public static final int OVERLAY = 0x66081422;          // 轻遮罩，注意力聚焦到 GUI
	public static final int WINDOW = 0xB0F7FBFE;           // 94% 主窗体（原 72%）
	public static final int WINDOW_STROKE = 0x4A7E94B0;    // 30% 浅青描边
	public static final int WINDOW_HIGHLIGHT = 0x66FFFFFF; // 40% 白亮光泽线，亚克力“玻璃边”关键
	// Liquid-glass(液态玻璃)调参：体渐变上亮下沉的幅度 + 左上斜向扫光强度。
	// 纯靠 ModernUI 的 GradientDrawable 合成，不依赖任何 backdrop blur/折射 shader。
	private static final int LIQUID_DEEP = 0x0A1626;       // 渐变下沉混入的深岩蓝（仅取 RGB）
	private static final float LIQUID_TOP_LIGHTEN = 0.10f; // 顶部向白提亮比例
	private static final float LIQUID_BOTTOM_DEEPEN = 0.06f; // 底部向深岩蓝下沉比例
	private static final int LIQUID_SHEEN_HI = 0x2EFFFFFF;  // 左上角扫光起点（26% 白）
	private static final int LIQUID_SHEEN_LO = 0x00FFFFFF;  // 扫光淡出到全透明

	// -- Win11 Mica 云母风（专给 tooltip/弹出卡用，比 Acrylic 更白更微妙）------
	// Mica vs Acrylic：Mica 几乎不透明（98%）、描边极淡、双层高光制造"悬浮"分层感。
	public static final int MICA_FILL      = 0xFAFCFCFD;   // 98% 极淡冷白（cool white，比纯白稍偏蓝灰）
	public static final int MICA_STROKE    = 0x40C8CED6;   // 25% 浅灰蓝薄描边（柔和不刺眼）
	public static final int MICA_INNER     = 0xCCFFFFFF;   // 80% 白色顶部高光线（"玻璃边"反光）
	public static final int MICA_SHADOW    = 0x35000000;   // 21% 黑色外阴影（悬浮投影感）
	public static final int PANEL = 0xC2FFFFFF;            // 96%（原 76%）
	public static final int PANEL_ALT = 0xB8E8F1F8;        // 93%（原 69%）
	public static final int PANEL_SOFT = 0xB0F0F8FE;       // 91%（原 63%）
	public static final int PANEL_ACTIVE = 0xCEF2F9FF;     // 95%（原 88%）
	public static final int PANEL_BUTTON = 0xCCFFFFFF;     // 95%（原 80%）
	public static final int PANEL_ROW = 0xC2FFFFFF;        // 96%（原 80%）
	public static final int PANEL_PICKER = 0xCEF8FBFF;     // 94%（原 84%）
	public static final int ACCENT_LINE = 0x2A0EA5E9;      // 青色点缀（不动）
	public static final int PICKER_SCRIM = 0x60081422;     // picker 下面全屏遮罩

	// -- Accent & state colors --------------------------------------------
	public static final int CYAN = 0xFF0EA5E9;
	public static final int GREEN = 0xFF10B981;
	public static final int AMBER = 0xFFD97706;
	public static final int RED = 0xFFDC2626;

	// -- Typography -------------------------------------------------------
	public static final int TEXT = 0xFF163047;
	public static final int TEXT_DIM = 0xFF5F7C96;
	public static final int TEXT_SOFT = 0xFF8AA0B4;

	// -- Spacing & sizing in dp ------------------------------------------
	// 图标尺寸目标对齐 16px texture 的整数倍缩放，减少非整数倍产生的锯齿感：
	// SMALL=20 (接近 2x)，LARGE=32 (精准 2x，但视觉比原 28 更舒服)。
	public static final int ICON_SMALL_DP = 20;
	public static final int ICON_LARGE_DP = 32;
	// v25 icon-only refresh: ↻ (idle) / ⟳ (syncing) / ✓ (success). The 92dp
	// width that used to hold "同步成功" is now wasted whitespace, so shrink to
	// 40dp — the glyph itself reads the state, the full description lives in
	// the hover tooltip via setTooltipText.
	public static final int HEADER_REFRESH_WIDTH_DP = 72;
	public static final int MAX_ACTIVITY_ROWS = 4;
	// 12sp 字体 + dp(6)*2 padding 至少需要 dp(30) 才能完整显示；
	// 原值 26 低于行高导致 quickAmount 按钮文字被裁成空白椭圆。
	public static final int QUICK_BUTTON_HEIGHT_DP = 32;

	public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
			.ofPattern("MM-dd HH:mm")
			.withZone(ZoneId.systemDefault());

	/** Build a rectangular {@link ShapeDrawable} with optional stroke. */
	public static ShapeDrawable rounded(int fill, int stroke, float radiusPx, int strokeWidthPx) {
		ShapeDrawable drawable = new ShapeDrawable();
		drawable.setCornerRadius(radiusPx);
		drawable.setColor(fill);
		if (stroke != 0) {
			drawable.setStroke(Math.max(1, strokeWidthPx), stroke);
		}
		return drawable;
	}

	/**
	 * Win11 亚克力风格的圆角背景：底层 fill + 外描边 stroke + 叠加在顶部的
	 * 1px 白色 highlight 描边，模拟玻璃边反光。双层描边 alpha 错开让
	 * 身体看起来莽薄不动跟脸。需要位置精准“只顶部”高光可后续进化；
	 * 当前以双层全描边代替。
	 */
	public static Drawable acrylic(int fill, int stroke, float radiusPx) {
		// 1) 玻璃体——垂直渐变(顶亮→中色→底沉)，制造“透光的厚玻璃”体积感，
		//    保留原 1px 描边作为玻璃硬边。
		int top = blendKeepAlpha(fill, 0xFFFFFF, LIQUID_TOP_LIGHTEN);
		int bottom = blendKeepAlpha(fill, LIQUID_DEEP, LIQUID_BOTTOM_DEEPEN);
		GradientDrawable body = new GradientDrawable(
				GradientDrawable.Orientation.TOP_BOTTOM, new int[] { top, fill, bottom });
		body.setCornerRadius(radiusPx);
		if (stroke != 0) {
			body.setStroke(1, stroke);
		}
		// 2) 斜向扫光——从左上向中心淡出的白色高光，模拟玻璃面反射的那道光，
		//    内缩 1px 落在描边内侧。
		GradientDrawable sheen = new GradientDrawable(
				GradientDrawable.Orientation.TL_BR,
				new int[] { LIQUID_SHEEN_HI, LIQUID_SHEEN_LO, LIQUID_SHEEN_LO });
		sheen.setCornerRadius(radiusPx);
		// 3) 顶部亮边——保留原“玻璃边”反光线。
		ShapeDrawable edge = new ShapeDrawable();
		edge.setCornerRadius(radiusPx);
		edge.setColor(0);
		edge.setStroke(1, WINDOW_HIGHLIGHT);
		LayerDrawable layers = new LayerDrawable(new Drawable[] { body, sheen, edge });
		layers.setLayerInset(1, 1, 1, 1, 1);
		return layers;
	}

	/**
	 * Linearly blend an ARGB color's RGB channels toward a target RGB while
	 * keeping the original alpha — used to derive the liquid-glass gradient
	 * stops from a single fill color.
	 */
	private static int blendKeepAlpha(int argb, int rgbTarget, float t) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int tr = (rgbTarget >> 16) & 0xFF;
		int tg = (rgbTarget >> 8) & 0xFF;
		int tb = rgbTarget & 0xFF;
		r = Math.round(r + (tr - r) * t);
		g = Math.round(g + (tg - g) * t);
		b = Math.round(b + (tb - b) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 * Win11 Mica 云母风圆角背景（专给 tooltip/弹出卡用，比 acrylic 更白更微妙）：
	 * 三层堆叠（背景 → 顶层）：
	 * <ol>
	 *   <li>外阴影晕（{@link #MICA_SHADOW} 21% 黑，比主体大 2px 投影晕，悬浮感）</li>
	 *   <li>主体 98% 极淡冷白 ({@link #MICA_FILL}) + 25% 浅灰蓝薄描边 ({@link #MICA_STROKE})</li>
	 *   <li>顶部 80% 白色高光线 ({@link #MICA_INNER}, 1px 内描边，"玻璃边"反光)</li>
	 * </ol>
	 * 与 {@link #acrylic} 的区别：mica 更白(98% vs 94%)、更薄边(浅灰vs深青)、加投影晕模拟"悬浮卡"。
	 */
	public static Drawable mica(float radiusPx) {
		// 1) 外阴影晕 — LayerDrawable 让阴影层比主体大 2px 各方向，制造"投影外扩"视觉
		ShapeDrawable shadow = new ShapeDrawable();
		shadow.setCornerRadius(radiusPx + 2f);
		shadow.setColor(MICA_SHADOW);
		// 2) 主体白色 + 浅灰蓝薄描边
		ShapeDrawable base = new ShapeDrawable();
		base.setCornerRadius(radiusPx);
		base.setColor(MICA_FILL);
		base.setStroke(1, MICA_STROKE);
		// 3) 顶部 1px 白高光（玻璃边反光）
		ShapeDrawable highlight = new ShapeDrawable();
		highlight.setCornerRadius(radiusPx);
		highlight.setColor(0);
		highlight.setStroke(1, MICA_INNER);
		LayerDrawable layers = new LayerDrawable(new Drawable[] { shadow, base, highlight });
		// 主体和高光层各方向内缩 2px，让阴影层"渗出"边缘 2px（投影晕效果）
		layers.setLayerInset(1, 2, 2, 2, 2);
		layers.setLayerInset(2, 2, 2, 2, 2);
		return layers;
	}

	/** Brand color for each currency, used as a fallback badge tint. */
	public static int currencyColor(CryptoCurrency currency) {
		return switch (currency) {
			case BTC -> 0xFFF7931A;
			case ETH -> 0xFF627EEA;
			case USDT -> 0xFF26A17B;
			case SOL -> 0xFF9945FF;
			case TRX -> 0xFFEF0027; // Tron 官方品牌红
			case DOGE -> 0xFFC2A633;
		};
	}

	/**
	 * Format an epoch-seconds timestamp as a coarse relative duration string
	 * such as {@code "5m ago"}, {@code "2h ago"}, {@code "3d ago"} or
	 * {@code "just now"}. The output uses short ASCII suffixes that are
	 * understandable across English and Chinese locales without requiring a
	 * separate translation key per bucket.
	 */
	public static String relativeTime(long epochSeconds) {
		if (epochSeconds <= 0L) {
			return "";
		}
		long now = java.time.Instant.now().getEpochSecond();
		long delta = Math.max(0L, now - epochSeconds);
		if (delta < 45L) {
			return "just now";
		}
		if (delta < 3600L) {
			long minutes = Math.max(1L, delta / 60L);
			return minutes + "m ago";
		}
		if (delta < 86400L) {
			long hours = Math.max(1L, delta / 3600L);
			return hours + "h ago";
		}
		if (delta < 7L * 86400L) {
			long days = Math.max(1L, delta / 86400L);
			return days + "d ago";
		}
		// For older entries, falling back to the existing absolute timestamp
		// preserves precision without polluting the UI.
		return TIME_FORMAT.format(java.time.Instant.ofEpochSecond(epochSeconds));
	}

	// -- App-feel micro-interactions -------------------------------------
	// These rely on ModernUI's frame-driven ValueAnimator (NOT View.postDelayed,
	// which is unreliable on Minecraft's render thread) so they animate smoothly.

	private static final float PRESS_SCALE = 0.97f;
	private static final float HOVER_ALPHA = 0.78f;
	private static final float PRESS_ALPHA = 0.55f;

	/**
	 * Wires hover + press feedback onto any clickable view. Hover dims the
	 * view slightly; press both dims and shrinks it. Alpha-based hover avoids
	 * the scale-up clipping that occurred when the view grew past its layout
	 * bounds. Both listeners return {@code false} so click handling is untouched.
	 */
	public static void interactive(View view) {
		final boolean[] state = new boolean[2];
		view.setOnHoverListener((v, event) -> {
			int action = event.getAction();
			if (action == MotionEvent.ACTION_HOVER_ENTER) {
				state[0] = true;
				applyInteractiveState(v, state);
			} else if (action == MotionEvent.ACTION_HOVER_EXIT) {
				state[0] = false;
				applyInteractiveState(v, state);
			}
			return false;
		});
		view.setOnTouchListener((v, event) -> {
			int action = event.getAction();
			if (action == MotionEvent.ACTION_DOWN) {
				state[1] = true;
				applyInteractiveState(v, state);
			} else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				state[1] = false;
				applyInteractiveState(v, state);
			}
			return false;
		});
	}

	private static void applyInteractiveState(View view, boolean[] state) {
		float targetAlpha = 1f;
		float targetScale = 1f;
		if (state[1]) {
			targetAlpha = PRESS_ALPHA;
			targetScale = PRESS_SCALE;
		} else if (state[0]) {
			targetAlpha = HOVER_ALPHA;
		}
		animateInteractive(view, targetAlpha, targetScale);
	}

	private static void animateInteractive(View view, float targetAlpha, float targetScale) {
		final float fromAlpha = view.getAlpha();
		final float fromScale = view.getScaleX();
		if (Math.abs(fromAlpha - targetAlpha) < 0.001f && Math.abs(fromScale - targetScale) < 0.001f) {
			return;
		}
		view.setPivotX(view.getWidth() / 2f);
		view.setPivotY(view.getHeight() / 2f);
		ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
		anim.setDuration(90);
		anim.addUpdateListener(a -> {
			float f = (float) a.getAnimatedValue();
			view.setAlpha(fromAlpha + (targetAlpha - fromAlpha) * f);
			float scale = fromScale + (targetScale - fromScale) * f;
			view.setScaleX(scale);
			view.setScaleY(scale);
		});
		anim.start();
	}

	/** Fade + slide-in used when a page's content tree is (re)built on tab switch. */
	public static void enterTransition(View view) {
		view.setAlpha(0f);
		view.setTranslationX(14f);
		ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
		anim.setDuration(160);
		anim.addUpdateListener(a -> {
			float f = (float) a.getAnimatedValue();
			view.setAlpha(f);
			view.setTranslationX((1f - f) * 14f);
		});
		anim.start();
	}

	/** Animated vertical scroll, so keyboard navigation glides instead of jumping. */
	public static void smoothScrollY(View view, int targetY) {
		final int from = view.getScrollY();
		if (from == targetY) {
			return;
		}
		ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
		anim.setDuration(150);
		anim.addUpdateListener(a -> {
			float f = (float) a.getAnimatedValue();
			view.scrollTo(0, Math.round(from + (targetY - from) * f));
		});
		anim.start();
	}

	/** App-style transient toast notification anchored by the ModernUI window. */
	public static void toast(icyllis.modernui.core.Context context, String text) {
		Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
	}
}
