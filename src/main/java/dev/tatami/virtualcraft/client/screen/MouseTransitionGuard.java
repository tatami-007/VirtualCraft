package dev.tatami.virtualcraft.client.screen;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps the OS cursor from briefly snapping to the window center while the
 * server swaps one container menu screen for another.
 */
public final class MouseTransitionGuard {
	private static final long VALID_FOR_MILLIS = 1_000L;

	private static double pendingX;
	private static double pendingY;
	private static long pendingWindow;
	private static long capturedAtMillis;

	private MouseTransitionGuard() {
	}

	public static void capture() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.mouseHandler == null || minecraft.getWindow() == null) {
			return;
		}
		pendingWindow = minecraft.getWindow().getWindow();
		pendingX = minecraft.mouseHandler.xpos();
		pendingY = minecraft.mouseHandler.ypos();
		capturedAtMillis = System.currentTimeMillis();
	}

	public static void restoreIfRecent() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null || capturedAtMillis == 0L) {
			return;
		}
		long window = minecraft.getWindow().getWindow();
		if (window != pendingWindow || System.currentTimeMillis() - capturedAtMillis > VALID_FOR_MILLIS) {
			clear();
			return;
		}
		GLFW.glfwSetCursorPos(window, pendingX, pendingY);
		clear();
	}

	private static void clear() {
		capturedAtMillis = 0L;
		pendingWindow = 0L;
	}
}
