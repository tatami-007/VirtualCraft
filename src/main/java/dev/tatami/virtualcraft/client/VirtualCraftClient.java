package dev.tatami.virtualcraft.client;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import dev.tatami.virtualcraft.client.screen.ElectricBallGeneratorScreen;
import dev.tatami.virtualcraft.client.screen.ElectricCondenserScreen;
import dev.tatami.virtualcraft.client.screen.SideConfigScreen;
import dev.tatami.virtualcraft.client.binding.BoundMachineHighlighter;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopClientCache;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment;
import dev.tatami.virtualcraft.client.screen.PowMinerScreen;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicIconCache;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class VirtualCraftClient {
	private VirtualCraftClient() {
	}

	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.ELECTRIC_CONDENSER.get(), ElectricCondenserScreen::new);
		event.register(ModMenuTypes.ELECTRIC_BALL_GENERATOR.get(), ElectricBallGeneratorScreen::new);
		// v15 通用面配置 Screen — 所有 IConfigurableSidedMachine 机器共用
		event.register(ModMenuTypes.SIDE_CONFIG.get(), SideConfigScreen::new);
		event.register(ModMenuTypes.POW_MINER.get(), PowMinerScreen::new);
		// HolographicShop 重构后不走 menu 系统：客户端收到 OpenHolographicTerminalPayload 后用
		// MuiModApi.openScreen(...) 直接拉起一个 SimpleScreen（非 AbstractContainerScreen）——
		// JEI 默认不识别这种 Screen 为容器 GUI，不会绘 Ingredient Overlay。
		// WirelessPowerTower 重做后无 GUI — 玩家用 LaserBindingTool 直接绑定，无需 Screen
	}

	/**
	 * 注册方块实体渲染器。
	 *
	 * <p>无线供电塔原本有个 {@code WirelessPowerTowerRenderer}（BER）负责画光束，已被客户端独立
	 * 渲染器 {@link BoundMachineHighlighter} 取代 —— 后者走 {@code RenderLevelStageEvent}，
	 * 不受 chunk section culling 影响，解决“光束在屏幕边缘消失”问题。</p>
	 *
	 * <p>目前本 mod 没有需要 BER 的方块实体，这个方法作为未来加新 BER 的预留入口存在。</p>
	 */
	public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		// 暂无需要注册的 BER
	}

	public static void registerShaders(RegisterShadersEvent event) {
		try {
			dev.tatami.virtualcraft.client.overlay.ShockOverlayShaders.register(event);
		} catch (Exception exception) {
			throw new RuntimeException("Failed to register electric overlay shader", exception);
		}
	}

	public static void renderShockOverlay(RenderGuiLayerEvent.Post event) {
		dev.tatami.virtualcraft.client.overlay.ShockTextOverlay.render(event);
	}

	/**
	 * Pre-warms the Holographic Terminal currency icon cache as soon as the
	 * client joins a world. This avoids a synchronous resource-pack read on
	 * the very first frame the terminal is opened.
	 */
	public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
		HolographicIconCache.prewarmCurrencies();
	}

	/**
	 * Drops the in-memory Holographic Terminal sync cache on disconnect so
	 * that the next session never paints stale balances or wallet addresses
	 * from a previous server. The icon byte cache is also cleared because
	 * resource packs may have changed between sessions.
	 */
	public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		HolographicShopClientCache.invalidate();
		HolographicIconCache.invalidate();
	}

	/**
	 * Drives the Holographic Shop refresh button’s time-based state machine
	 * (idle “刷新” / pending “同步中” / success “同步成功”).
	 * ModernUI’s View.postDelayed doesn’t reliably fire on the Minecraft
	 * render thread, so we tick at 20 Hz here instead. tickActiveRefreshButton
	 * cheaply early-outs when no refresh state is active.
	 */
	public static void onClientTick(ClientTickEvent.Post event) {
		HolographicShopFragment.tickActiveRefreshButton();
	}
}


