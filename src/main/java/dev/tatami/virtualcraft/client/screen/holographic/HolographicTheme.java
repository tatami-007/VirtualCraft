package dev.tatami.virtualcraft.client.screen.holographic;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.LayerDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

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
	public static final int OVERLAY = 0x40081422;          // 轻遮罩，注意力聚焦到 GUI
	public static final int WINDOW = 0xF2F7FBFE;           // 94% 主窗体（原 72%）
	public static final int WINDOW_STROKE = 0x4A7E94B0;    // 30% 浅青描边
	public static final int WINDOW_HIGHLIGHT = 0x66FFFFFF; // 40% 白亮光泽线，亚克力“玻璃边”关键

	// -- Win11 Mica 云母风（专给 tooltip/弹出卡用，比 Acrylic 更白更微妙）------
	// Mica vs Acrylic：Mica 几乎不透明（98%）、描边极淡、双层高光制造"悬浮"分层感。
	public static final int MICA_FILL      = 0xFAFCFCFD;   // 98% 极淡冷白（cool white，比纯白稍偏蓝灰）
	public static final int MICA_STROKE    = 0x40C8CED6;   // 25% 浅灰蓝薄描边（柔和不刺眼）
	public static final int MICA_INNER     = 0xCCFFFFFF;   // 80% 白色顶部高光线（"玻璃边"反光）
	public static final int MICA_SHADOW    = 0x35000000;   // 21% 黑色外阴影（悬浮投影感）
	public static final int PANEL = 0xF5FFFFFF;            // 96%（原 76%）
	public static final int PANEL_ALT = 0xEEE8F1F8;        // 93%（原 69%）
	public static final int PANEL_SOFT = 0xE8F0F8FE;       // 91%（原 63%）
	public static final int PANEL_ACTIVE = 0xF2F2F9FF;     // 95%（原 88%）
	public static final int PANEL_BUTTON = 0xF2FFFFFF;     // 95%（原 80%）
	public static final int PANEL_ROW = 0xF5FFFFFF;        // 96%（原 80%）
	public static final int PANEL_PICKER = 0xF0F8FBFF;     // 94%（原 84%）
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
	public static final int HEADER_REFRESH_WIDTH_DP = 74;
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
		ShapeDrawable base = new ShapeDrawable();
		base.setCornerRadius(radiusPx);
		base.setColor(fill);
		if (stroke != 0) {
			base.setStroke(1, stroke);
		}
		ShapeDrawable highlight = new ShapeDrawable();
		highlight.setCornerRadius(radiusPx);
		highlight.setColor(0);
		highlight.setStroke(1, WINDOW_HIGHLIGHT);
		return new LayerDrawable(new Drawable[] { base, highlight });
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
}
