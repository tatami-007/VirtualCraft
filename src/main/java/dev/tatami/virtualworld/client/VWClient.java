package dev.tatami.virtualworld.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
}
