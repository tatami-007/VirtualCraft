package dev.tatami.virtualcraft.machine;

import net.minecraft.network.chat.Component;

/**
 * 通用机器面配置模式（v15 从 CondenserSideMode 重命名 + 移到通用包，机器无关）。
 *
 * <p>所有实现 {@link IConfigurableSidedMachine} 接口的 BlockEntity 都用这个枚举管理 6 面 I/O 行为。</p>
 *
 * <h4>4 种模式</h4>
 * <ul>
 *   <li>{@link #NONE}   — 既不允许输入也不允许输出（完全屏蔽）</li>
 *   <li>{@link #INPUT}  — 仅允许外部 hopper/管道往里推物品（机器作为接收端）</li>
 *   <li>{@link #OUTPUT} — 仅允许外部 hopper/管道从里抽物品（机器作为产物输出端）</li>
 *   <li>{@link #BOTH}   — 进出都允许（默认）</li>
 * </ul>
 *
 * <p>{@link #next()} 返回下一个模式（NONE→INPUT→OUTPUT→BOTH→NONE 循环），供 SideConfig GUI 点击切换使用。</p>
 */
public enum SideMode {
	NONE(false, false, "X", "none"),
	INPUT(true, false, "I", "input"),
	OUTPUT(false, true, "O", "output"),
	BOTH(true, true, "IO", "both");

	private final boolean allowsInsert;
	private final boolean allowsExtract;
	private final String shortLabel;
	private final String translationKey;

	SideMode(boolean allowsInsert, boolean allowsExtract, String shortLabel, String translationKey) {
		this.allowsInsert = allowsInsert;
		this.allowsExtract = allowsExtract;
		this.shortLabel = shortLabel;
		this.translationKey = translationKey;
	}

	public boolean allowsInsert() {
		return allowsInsert;
	}

	public boolean allowsExtract() {
		return allowsExtract;
	}

	public String shortLabel() {
		return shortLabel;
	}

	public Component displayName() {
		return Component.translatable("tooltip.virtual_craft.side_mode." + translationKey);
	}

	public SideMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
