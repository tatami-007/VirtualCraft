package dev.tatami.virtualcraft.client.screen.widget;

import java.util.function.LongFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class AmountSliderWidget extends AbstractSliderButton {
	private static final int TRACK_PADDING = 8;
	private static final int KNOB_WIDTH = 12;
	private long minValue;
	private long maxValue;
	private long step;
	private long currentValue;
	private final LongFunction<Component> formatter;

	public AmountSliderWidget(int x, int y, int width, int height, long minValue, long maxValue, long step, long currentValue, LongFunction<Component> formatter) {
		super(x, y, width, height, Component.empty(), 0.0D);
		this.formatter = formatter;
		configure(minValue, maxValue, step, currentValue);
	}

	public void configure(long minValue, long maxValue, long step, long currentValue) {
		this.minValue = Math.max(0L, minValue);
		this.maxValue = Math.max(this.minValue, maxValue);
		this.step = Math.max(1L, step);
		if (this.maxValue <= this.minValue) {
			this.currentValue = this.maxValue;
			this.value = 0.0D;
			active = false;
		} else {
			this.currentValue = Mth.clamp(currentValue, this.minValue, this.maxValue);
			this.currentValue = snap(this.currentValue);
			this.value = normalize(this.currentValue);
			active = true;
		}
		updateMessage();
	}

	public long currentValue() {
		return currentValue;
	}

	public void setFraction(double fraction) {
		if (maxValue <= minValue) {
			return;
		}
		value = Mth.clamp(fraction, 0.0D, 1.0D);
		applyValue();
	}

	public void setCurrentValue(long currentValue) {
		configure(minValue, maxValue, step, currentValue);
	}

	@Override
	protected void updateMessage() {
		setMessage(formatter.apply(currentValue));
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		boolean hovered = isHoveredOrFocused();
		int outer = !active ? 0xFFC7D4E0 : hovered ? 0xFFB2D1DE : 0xFFC7D7E5;
		int inner = !active ? 0xFFE8EEF4 : 0xFFF6FAFD;
		int track = !active ? 0xFFD9E2EB : 0xFFC3D5E3;
		int fill = hovered ? 0xFF67C6DE : 0xFF58B7D1;
		int knob = hovered ? 0xFFFFFFFF : 0xFFF8FBFF;
		guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, outer);
		guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, inner);
		int trackY = getY() + height - 7;
		int trackLeft = getX() + TRACK_PADDING;
		int trackRight = getX() + width - TRACK_PADDING;
		int trackWidth = Math.max(1, trackRight - trackLeft);
		guiGraphics.fill(trackLeft, trackY, trackRight, trackY + 3, track);
		int fillWidth = maxValue <= minValue ? 0 : (int) Math.round(trackWidth * value);
		guiGraphics.fill(trackLeft, trackY, trackLeft + fillWidth, trackY + 3, fill);
		int knobX = trackLeft + Math.max(0, fillWidth - KNOB_WIDTH / 2);
		knobX = Mth.clamp(knobX, trackLeft, trackRight - KNOB_WIDTH);
		guiGraphics.fill(knobX, trackY - 4, knobX + KNOB_WIDTH, trackY + 7, knob);

		Component label = getMessage();
		int textColor = !active ? 0xFF7F94A8 : 0xFF31485F;
		int textX = getX() + width / 2 - Minecraft.getInstance().font.width(label) / 2;
		guiGraphics.drawString(Minecraft.getInstance().font, label, textX, getY() + 6, textColor, false);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setValueFromMouse(mouseX);
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		setValueFromMouse(mouseX);
	}

	@Override
	protected void applyValue() {
		if (maxValue <= minValue) {
			currentValue = maxValue;
			value = 0.0D;
			updateMessage();
			return;
		}
		currentValue = snap(denormalize(value));
		value = normalize(currentValue);
		updateMessage();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!active || !visible || maxValue <= minValue) {
			return false;
		}
		long mouseStep = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? Math.max(step * 10L, 1L) : step;
		long nextValue = currentValue + (scrollY > 0 ? mouseStep : -mouseStep);
		currentValue = Mth.clamp(nextValue, minValue, maxValue);
		currentValue = snap(currentValue);
		value = normalize(currentValue);
		updateMessage();
		return true;
	}

	private long snap(long rawValue) {
		if (maxValue <= minValue) {
			return maxValue;
		}
		long offset = Math.max(0L, rawValue - minValue);
		long snapped = minValue + Math.round((double) offset / (double) step) * step;
		if (snapped > maxValue) {
			snapped = maxValue;
		}
		if (snapped < minValue) {
			snapped = minValue;
		}
		return snapped;
	}

	private double normalize(long amount) {
		if (maxValue <= minValue) {
			return 0.0D;
		}
		return (double) (amount - minValue) / (double) (maxValue - minValue);
	}

	private long denormalize(double normalized) {
		if (maxValue <= minValue) {
			return maxValue;
		}
		double clamped = Mth.clamp(normalized, 0.0D, 1.0D);
		return minValue + Math.round(clamped * (maxValue - minValue));
	}

	private void setValueFromMouse(double mouseX) {
		if (!active || maxValue <= minValue) {
			return;
		}
		double usableWidth = Math.max(1.0D, width - TRACK_PADDING * 2.0D);
		double normalized = (mouseX - (getX() + TRACK_PADDING)) / usableWidth;
		value = Mth.clamp(normalized, 0.0D, 1.0D);
		applyValue();
	}
}
