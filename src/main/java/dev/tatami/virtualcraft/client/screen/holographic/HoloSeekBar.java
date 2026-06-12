package dev.tatami.virtualcraft.client.screen.holographic;

import dev.tatami.virtualcraft.VirtualCraft;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.SeekBar;

/**
 * Holographic terminal SeekBar，对齐 ModernUI 自家
 * {@code PreferencesFragment#addSliderToLayout} 的实现配方。
 *
 * <h3>关键配方（必须三件套，缺一不可）</h3>
 * <ol>
 *   <li><b>构造时套 {@code R.style.Widget_Material3_SeekBar_Slider}</b>：
 *       此 style 在 {@code SystemTheme} 中预设了
 *       {@code focusable=true / clickable=true} 以及关键的
 *       {@code SliderThumbDrawable}——后者带 hover/press 状态切换，
 *       且 {@code paddingLeft/Right=5dp} 让 view 矩形比可见 track 更宽，
 *       hover 命中区域覆盖整个 track 而不只是细线本身。
 *       这正是用户反馈的"指针对着滑条 thumb 才有用，移开就消失"的根因——
 *       默认构造的 SeekBar 没有这一套，hit-target 仅 thumb 大小。</li>
 *   <li><b>{@code setUserAnimationEnabled(true)}</b>：开启 thumb 缩放/track 平滑动画，
 *       与 PreferencesFragment 一致。</li>
 *   <li><b>{@code setRevealOnFocusHint(false)}</b>：禁止 ScrollView 在 SeekBar
 *       getFocus 时自动 {@code scrollToDescendant(focused)}。否则 fragment 首次
 *       layout 时 ScrollView 会把页面滚到滑条位置卡住，造成"y 完全不动"。</li>
 * </ol>
 *
 * <h3>SCROLL 拦截上提到 dispatch 入口</h3>
 * 直接 override {@code dispatchGenericMotionEvent}，在 view 收到事件的第一行
 * 接管 SCROLL，绕过 {@code dispatchGenericPointerEvent → ENABLED_MASK 检查 →
 * listener → onGenericMotionEvent} 链路上任何可能让事件被丢弃的中间环节。
 * 这样不管 SeekBar 内部状态如何，hover 在 view 矩形内滚轮就一定生效。
 */
@SuppressWarnings("this-escape") // ModernUI SeekBar constructor invokes overridable methods; expected by the framework.
public class HoloSeekBar extends SeekBar {
	/** 默认关，按需 {@link #setDebug(boolean)} 开启诊断日志。 */
	private static volatile boolean DEBUG_SCROLL = false;
	private long lastDispatchLogNanos;
	private boolean syntheticUserChange;

	public HoloSeekBar(Context context) {
		// 与 ModernUI PreferencesFragment#addSliderToLayout 完全一致的构造签名。
		// style 内置 focusable + clickable + SliderThumbDrawable + 5dp padding。
		super(context, null, null, R.style.Widget_Material3_SeekBar_Slider);
		setUserAnimationEnabled(true);
		// 阻止 ScrollView 在 fragment 首次 layout 时把页面自动滚到滑条位置。
		// 之前一旦启用 Material3 style 就出现"contentScroll 也滚不动"，
		// 是 ScrollView.requestChildFocus → scrollToDescendant 把整页定位卡死。
		setRevealOnFocusHint(false);
	}

	public static void setDebug(boolean enabled) {
		DEBUG_SCROLL = enabled;
	}

	public boolean isSyntheticUserChange() {
		return syntheticUserChange;
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		final int action = event.getAction();

		// 入口诊断：节流 250ms，记录到 SeekBar 的所有 generic motion，含 SCROLL/HOVER。
		// 用户之前抱怨"滚轮无效"，需要先确认 SCROLL 事件到底有没有到 SeekBar。
		if (DEBUG_SCROLL && (action == MotionEvent.ACTION_SCROLL
				|| action == MotionEvent.ACTION_HOVER_ENTER
				|| action == MotionEvent.ACTION_HOVER_EXIT)) {
			long now = System.nanoTime();
			if (now - lastDispatchLogNanos > 250_000_000L) {
				lastDispatchLogNanos = now;
				VirtualCraft.LOGGER.info(
						"[HoloSeekBar] dispatch action={} x={} y={} VSCROLL={} HSCROLL={}",
						action, event.getX(), event.getY(),
						event.getAxisValue(MotionEvent.AXIS_VSCROLL),
						event.getAxisValue(MotionEvent.AXIS_HSCROLL));
			}
		}

		// 直接在 dispatch 入口接管 SCROLL，绕过 ENABLED_MASK / listener / hover 等所有
		// 可能让 onGenericMotionEvent 被跳过的中间环节。
		if (action == MotionEvent.ACTION_SCROLL) {
			float vDelta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
			float hDelta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
			float delta = vDelta != 0f ? vDelta : hDelta;
			if (delta != 0f) {
				int range = Math.max(1, getMax() - getMin());
				int step = Math.max(1, range / 20);
				int next = clamp(getProgress() + (delta > 0 ? step : -step), getMin(), getMax());
				if (DEBUG_SCROLL) {
					VirtualCraft.LOGGER.info(
							"[HoloSeekBar] SCROLL handled delta={} {} -> {}",
							delta, getProgress(), next);
				}
				syntheticUserChange = true;
				try {
					setProgress(next);
				} finally {
					syntheticUserChange = false;
				}
				return true;
			}
		}
		return super.dispatchGenericMotionEvent(event);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
