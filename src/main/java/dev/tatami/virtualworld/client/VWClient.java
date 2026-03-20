package dev.tatami.virtualworld.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import dev.tatami.virtualworld.client.screen.ElectricCondenserScreen;
import dev.tatami.virtualworld.client.screen.ElectricCondenserSideConfigScreen;
import dev.tatami.virtualworld.core.definitions.VWMenuTypes;

public final class VWClient {
	private VWClient() {
	}

	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(VWMenuTypes.ELECTRIC_CONDENSER.get(), ElectricCondenserScreen::new);
		event.register(VWMenuTypes.ELECTRIC_CONDENSER_SIDE_CONFIG.get(), ElectricCondenserSideConfigScreen::new);
	}

	public static void registerShaders(RegisterShadersEvent event) {
		try {
			ShockOverlayShaders.register(event);
		} catch (Exception exception) {
			throw new RuntimeException("Failed to register electric overlay shader", exception);
		}
	}

	public static void renderShockOverlay(RenderGuiLayerEvent.Post event) {
		ShockTextOverlay.render(event);
	}
}

