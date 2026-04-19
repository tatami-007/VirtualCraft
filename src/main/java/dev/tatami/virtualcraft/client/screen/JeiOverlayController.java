package dev.tatami.virtualcraft.client.screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

final class JeiOverlayController {
	private static Boolean previousOverlayEnabled;
	private static Boolean previousBookmarkEnabled;

	private JeiOverlayController() {
	}

	static void hideOverlay() {
		try {
			Object toggleState = resolveToggleState();
			if (toggleState == null) {
				return;
			}
			Method isOverlayEnabled = toggleState.getClass().getMethod("isOverlayEnabled");
			boolean enabled = (boolean) isOverlayEnabled.invoke(toggleState);
			if (previousOverlayEnabled == null) {
				previousOverlayEnabled = enabled;
			}
			if (enabled) {
				Method toggleOverlayEnabled = toggleState.getClass().getMethod("toggleOverlayEnabled");
				toggleOverlayEnabled.invoke(toggleState);
			}
			Method isBookmarkOverlayEnabled = toggleState.getClass().getMethod("isBookmarkOverlayEnabled");
			boolean bookmarksEnabled = (boolean) isBookmarkOverlayEnabled.invoke(toggleState);
			if (previousBookmarkEnabled == null) {
				previousBookmarkEnabled = bookmarksEnabled;
			}
			if (bookmarksEnabled) {
				Method setBookmarkEnabled = toggleState.getClass().getMethod("setBookmarkEnabled", boolean.class);
				setBookmarkEnabled.invoke(toggleState, false);
			}
		} catch (Exception ignored) {
		}
	}

	static void restoreOverlay() {
		try {
			if (previousOverlayEnabled == null) {
				return;
			}
			Object toggleState = resolveToggleState();
			if (toggleState == null) {
				return;
			}
			Method isOverlayEnabled = toggleState.getClass().getMethod("isOverlayEnabled");
			boolean enabled = (boolean) isOverlayEnabled.invoke(toggleState);
			if (previousOverlayEnabled && !enabled) {
				Method toggleOverlayEnabled = toggleState.getClass().getMethod("toggleOverlayEnabled");
				toggleOverlayEnabled.invoke(toggleState);
			}
			if (previousBookmarkEnabled != null) {
				Method isBookmarkOverlayEnabled = toggleState.getClass().getMethod("isBookmarkOverlayEnabled");
				boolean bookmarksEnabled = (boolean) isBookmarkOverlayEnabled.invoke(toggleState);
				if (previousBookmarkEnabled && !bookmarksEnabled) {
					Method setBookmarkEnabled = toggleState.getClass().getMethod("setBookmarkEnabled", boolean.class);
					setBookmarkEnabled.invoke(toggleState, true);
				}
			}
			previousOverlayEnabled = null;
			previousBookmarkEnabled = null;
		} catch (Exception ignored) {
		}
	}

	private static Object resolveToggleState() throws Exception {
		Class<?> pluginClass = Class.forName("mezz.jei.neoforge.plugins.neoforge.NeoForgeGuiPlugin");
		Method getResourceReloadHandler = pluginClass.getMethod("getResourceReloadHandler");
		Object optional = getResourceReloadHandler.invoke(null);
		if (!(optional instanceof Optional<?> resourceOptional) || resourceOptional.isEmpty()) {
			return null;
		}
		Object resourceReloadHandler = resourceOptional.get();
		Field ingredientListOverlayField = resourceReloadHandler.getClass().getDeclaredField("ingredientListOverlay");
		ingredientListOverlayField.setAccessible(true);
		Object ingredientListOverlay = ingredientListOverlayField.get(resourceReloadHandler);
		Field toggleStateField = ingredientListOverlay.getClass().getDeclaredField("toggleState");
		toggleStateField.setAccessible(true);
		return toggleStateField.get(ingredientListOverlay);
	}
}
