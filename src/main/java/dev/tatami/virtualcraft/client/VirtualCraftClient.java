package dev.tatami.virtualcraft.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import dev.tatami.virtualcraft.client.ShockOverlayShaders;
import dev.tatami.virtualcraft.client.ShockTextOverlay;
import dev.tatami.virtualcraft.client.screen.CurrencyGeneratorScreen;
import dev.tatami.virtualcraft.client.screen.ElectricBallGeneratorScreen;
import dev.tatami.virtualcraft.client.screen.ElectricCondenserScreen;
import dev.tatami.virtualcraft.client.screen.ElectricCondenserSideConfigScreen;
import dev.tatami.virtualcraft.client.screen.HolographicShopScreen;
import dev.tatami.virtualcraft.client.screen.PowMinerScreen;
import dev.tatami.virtualcraft.client.screen.WirelessPowerScreen;
import dev.tatami.virtualcraft.registry.ModMenuTypes;

public final class VirtualCraftClient {
	private VirtualCraftClient() {
	}

	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.ELECTRIC_CONDENSER.get(), ElectricCondenserScreen::new);
		event.register(ModMenuTypes.ELECTRIC_BALL_GENERATOR.get(), ElectricBallGeneratorScreen::new);
		event.register(ModMenuTypes.ELECTRIC_CONDENSER_SIDE_CONFIG.get(), ElectricCondenserSideConfigScreen::new);
		event.register(ModMenuTypes.HOLOGRAPHIC_SHOP.get(), HolographicShopScreen::new);
		event.register(ModMenuTypes.CURRENCY_GENERATOR.get(), CurrencyGeneratorScreen::new);
		event.register(ModMenuTypes.POW_MINER.get(), PowMinerScreen::new);
		event.register(ModMenuTypes.WIRELESS_POWER.get(), WirelessPowerScreen::new);
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


