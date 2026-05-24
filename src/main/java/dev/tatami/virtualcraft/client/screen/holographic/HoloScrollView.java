package dev.tatami.virtualcraft.client.screen.holographic;

import dev.tatami.virtualcraft.VirtualCraft;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.SeekBar;

/**
 * Holographic terminal ScrollView，专门解决 ModernUI 默认事件链
 * "鼠标 hover 在文字/控件上时滚轮失灵，只有空白背景能滚" 的故障。
 *
 * <h3>故障根因</h3>
 * ModernUI 默认的 {@code View.dispatchGenericMotionEvent} 把 ACTION_SCROLL
 * 走 {@code dispatchGenericPointerEvent → 找命中子 → child.dispatchGenericMotionEvent → ...}。
 * 整链路理论上若所有子都 {@code return false}，会冒泡回到 ScrollView 自己的
 * {@code onGenericMotionEvent} 走滚动逻辑。但实测：只要鼠标命中任何非空白
 * 子 view（TextView / LinearLayout / Button / 卡片）SCROLL 就被默认链路某处吞掉。
 *
 * <h3>修复策略：按命中子类型路由 SCROLL 事件</h3>
 * <ol>
 *   <li><b>命中是 SeekBar 子类（含 HoloSeekBar）</b>：让默认链路走，子优先消费。
 *       SeekBar 内置/自实现的 SCROLL 处理会调节 progress，符合用户预期。</li>
 *   <li><b>命中是其它任何 view</b>：跳过子分发，直接调
 *       {@code ScrollView.onGenericMotionEvent(event)} 让 ScrollView 自己滚。
 *       这样无论文字/控件/卡片，hover 滚轮都能滚 ScrollView。</li>
 * </ol>
 *
 * <h3>不破坏其它行为</h3>
 * 仅 ACTION_SCROLL 走特殊路径；HOVER / TOUCH / KEY 等所有其它事件原封交给
 * super，nested-scroll / fling / overscroll-glow / 拖动等保持 ModernUI 默认。
 */
public class HoloScrollView extends ScrollView {
	/** 默认关，按需 {@link #setDebug(boolean)} 开启诊断日志。 */
	private static volatile boolean DEBUG_SCROLL = false;
	private long lastLogNanos;

	public HoloScrollView(Context context) {
		super(context);
	}

	public static void setDebug(boolean enabled) {
		DEBUG_SCROLL = enabled;
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		if (event.getAction() != MotionEvent.ACTION_SCROLL) {
			return super.dispatchGenericMotionEvent(event);
		}

		// 命中测试：找鼠标位置下的最深叶子 view
		float x = event.getX();
		float y = event.getY();
		View hit = findDeepestHitChild(this, x, y);

		boolean handled;
		String route;
		if (hit instanceof SeekBar) {
			// SeekBar 优先消费 SCROLL（调节 progress）
			handled = super.dispatchGenericMotionEvent(event);
			route = "SeekBar->" + hit.getClass().getSimpleName();
		} else {
			// 其它任何子 view：跳过子分发链路，ScrollView 自己直接消费 SCROLL
			handled = onGenericMotionEvent(event);
			route = "self<-" + (hit == null ? "null" : hit.getClass().getSimpleName());
		}

		if (DEBUG_SCROLL) {
			long now = System.nanoTime();
			if (now - lastLogNanos > 200_000_000L) {
				lastLogNanos = now;
				VirtualCraft.LOGGER.info(
						"[HoloScrollView] SCROLL route={} VSCROLL={} handled={} scrollY={}",
						route, event.getAxisValue(MotionEvent.AXIS_VSCROLL), handled,
						getScrollY());
			}
		}
		return handled;
	}

	/**
	 * 命中测试：递归找最深的可见命中叶子 view。
	 *
	 * <h4>坐标系约定</h4>
	 * <ul>
	 *   <li>{@code event.getX()/getY()} 在 {@code dispatchGenericMotionEvent} 里
	 *       已经被 {@code dispatchTransformedGenericPointerEvent} offsetLocation 过，
	 *       即<b>已经是 HoloScrollView 自己的本地坐标系</b>（左上角 = (0,0)），
	 *       不需要再减 {@code root.getLeft()}。</li>
	 *   <li>判断 child 命中要加上 root 自己的 {@code scrollX/Y}，因为 {@code child.mLeft/mTop}
	 *       是 root 的"内容坐标系"，不含 scroll；而 {@code event} 是"屏幕坐标系"，
	 *       两者差 {@code root.getScrollX()/Y()}。</li>
	 * </ul>
	 */
	private static View findDeepestHitChild(ViewGroup root, float px, float py) {
		// px, py 已是 root 本地坐标。加 scroll 转到 root "内容坐标系"，与 child.mLeft 同尺度。
		return findInGroup(root, px + root.getScrollX(), py + root.getScrollY());
	}

	/**
	 * 在 {@code group} 的内容坐标系下递归找命中叶子。
	 *
	 * @param group 当前 ViewGroup
	 * @param lx    group 内容坐标系下的 x（与 child.mLeft 同尺度）
	 * @param ly    group 内容坐标系下的 y
	 */
	private static View findInGroup(ViewGroup group, float lx, float ly) {
		// 倒序遍历：上层子优先（与 ViewGroup.dispatchGenericPointerEvent 一致）
		for (int i = group.getChildCount() - 1; i >= 0; i--) {
			View child = group.getChildAt(i);
			if (child.getVisibility() != VISIBLE) {
				continue;
			}
			if (lx < child.getLeft() || lx >= child.getRight()
					|| ly < child.getTop() || ly >= child.getBottom()) {
				continue;
			}
			if (child instanceof ViewGroup childGroup) {
				// 转到 child 的内容坐标系：减去 child 的位置 + 加上 child 自己的 scroll
				float ccx = lx - child.getLeft() + childGroup.getScrollX();
				float ccy = ly - child.getTop() + childGroup.getScrollY();
				View deeper = findInGroup(childGroup, ccx, ccy);
				if (deeper != null) {
					return deeper;
				}
				// 子 ViewGroup 自身命中但其孙没命中（命中空白）→ 返回这个 ViewGroup
				return child;
			}
			return child;
		}
		return null;
	}
}
