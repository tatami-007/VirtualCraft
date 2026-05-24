package dev.tatami.virtualcraft.client.screen.widget;

import dev.tatami.virtualcraft.client.screen.MachineAssets;
import dev.tatami.virtualcraft.client.screen.MachineDraw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * AE2-CS 18×20 图标按钮，使用 icons.png 中的 16×16 icon sprite。
 * 视觉：{@link MachineDraw#drawIconButton(GuiGraphics, int, int, int, boolean, boolean)}
 *
 * <h3>用法</h3>
 * <pre>{@code
 * IconButton gear = new IconButton(x, y, MachineAssets.ICON_GEAR_U,
 *         Component.translatable("tooltip.virtual_craft.side_config"),
 *         button -> openSideConfig());
 * gear.setHighlighted(currentMode == OPEN);
 * }</pre>
 *
 * @see MachineAssets#ICON_GEAR_U
 * @see MachineAssets#ICON_BACK_U
 */
@SuppressWarnings("this-escape") // setTooltip() in the constructor is the standard vanilla widget pattern.
public class IconButton extends Button {
	public static final int WIDTH = MachineAssets.ICON_BUTTON_W;
	public static final int HEIGHT = MachineAssets.ICON_BUTTON_H;
	public static final int SIZE = WIDTH;

	private final int iconU;
	private boolean highlighted;
	private int highlightedFrameU = MachineAssets.ICON_BUTTON_BOTH_U;
	private int highlightedFrameV = MachineAssets.ICON_BUTTON_DARK_V;

	public IconButton(int x, int y, int iconU, Component tooltipText, OnPress onPress) {
		super(x, y, WIDTH, HEIGHT, Component.empty(), onPress, DEFAULT_NARRATION);
		this.iconU = iconU;
		setTooltip(net.minecraft.client.gui.components.Tooltip.create(tooltipText));
	}

	/** 设置是否高亮（如当前选中项），高亮加上紫蓝 accent 边框。 */
	public void setHighlighted(boolean highlighted) {
		this.highlighted = highlighted;
	}

	public IconButton setHighlightedFrameU(int highlightedFrameU) {
		this.highlightedFrameU = highlightedFrameU;
		return this;
	}

	public IconButton setHighlightedFrame(int highlightedFrameU, int highlightedFrameV) {
		this.highlightedFrameU = highlightedFrameU;
		this.highlightedFrameV = highlightedFrameV;
		return this;
	}

	public boolean isHighlighted() {
		return highlighted;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int frameU = highlighted ? highlightedFrameU : MachineAssets.ICON_BUTTON_NORMAL_U;
		int frameV = highlighted ? highlightedFrameV : MachineAssets.ICON_BUTTON_DARK_V;
		MachineDraw.drawIconButton(guiGraphics, getX(), getY(), iconU, isHoveredOrFocused(),
				frameU, frameV);
	}
}
