package dev.tatami.virtualcraft;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import dev.tatami.virtualcraft.client.binding.BoundMachineHighlighter;
import dev.tatami.virtualcraft.client.binding.LaserBindingClientHandler;
import dev.tatami.virtualcraft.client.highlight.AbsorberOreHighlighter;
import dev.tatami.virtualcraft.client.highlight.MiningSurveyorHighlighter;
import dev.tatami.virtualcraft.client.VirtualCraftClient;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.network.CloseHolographicTerminalPayload;
import dev.tatami.virtualcraft.network.NetworkMessageRegistry;
import dev.tatami.virtualcraft.energy.WirelessEnergyNetwork;
import dev.tatami.virtualcraft.registry.ModCreativeTabs;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModCapabilities;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import dev.tatami.virtualcraft.server.ServerWorkQueue;

@Mod("virtual_craft")
@SuppressWarnings("this-escape") // NeoForge.EVENT_BUS.register(this) in the constructor is the standard mod pattern.
public class VirtualCraft {
	public static final Logger LOGGER = LogManager.getLogger(VirtualCraft.class);
	public static final String MODID = "virtual_craft";
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public VirtualCraft(IEventBus modEventBus) {
		NeoForge.EVENT_BUS.register(this);
		NetworkMessageRegistry.registerDefaults();
		modEventBus.addListener(NetworkMessageRegistry::registerPayloadHandlers);
		modEventBus.addListener(ModCapabilities::register);
		modEventBus.addListener(ModEntityTypes::registerAttributes);
		if (FMLEnvironment.dist.isClient()) {
			AbsorberOreHighlighter highlighter = new AbsorberOreHighlighter();
			MiningSurveyorHighlighter surveyorHighlighter = new MiningSurveyorHighlighter();
			BoundMachineHighlighter boundMachineHighlighter = new BoundMachineHighlighter();
			NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
				if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
					highlighter.renderInGame(event.getPoseStack());
					surveyorHighlighter.renderInGame(event.getPoseStack());
					// 手持光束绑定器时、在已绑定机器中心画半透明小红方块，让塔顶发出的红色光束看起来是“打在小方块上”的效果
					boundMachineHighlighter.renderInGame(event.getPoseStack());
				}
			});
			// Ctrl + Shift + 右键 机器 → 批量绑定周围机器到选中的源塔
			LaserBindingClientHandler.register();
			modEventBus.addListener(VirtualCraftClient::registerScreens);
			modEventBus.addListener(VirtualCraftClient::registerShaders);
			// 1:1 ME-Beam-Former 风格的塔光束渲染：光束从塔顶射向每个绑定目标
			modEventBus.addListener(VirtualCraftClient::registerBlockEntityRenderers);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::renderShockOverlay);
			NeoForge.EVENT_BUS.addListener(surveyorHighlighter::renderGui);
			// Pre-warm Holographic Terminal currency icons on world join (so the first
			// open of the terminal does not pay synchronous resource-pack IO),
			// and drop the per-session client cache on disconnect so we never paint
			// stale balances/addresses from a previous server.
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientLogin);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientLogout);
			// v24 tick-driven holographic shop refresh button state machine —
			// ModernUI View.postDelayed is unreliable on the render thread, so
			// we drive transitions from the 20 Hz client tick instead.
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientTick);
		}

		// 1:1 ME-Beam-Former 全局无线塔管理器 — 集中调度所有塔的能量转发，应听 ServerTickEvent.Post + ServerStoppedEvent
		WirelessEnergyNetwork.registerEvents(NeoForge.EVENT_BUS);

		ModBlocks.REGISTRY.register(modEventBus);
		ModDimensions.CHUNK_GENERATOR_CODECS.register(modEventBus);
		ModBlockEntities.REGISTRY.register(modEventBus);
		ModAttachments.REGISTRY.register(modEventBus);
		ModEntityTypes.REGISTRY.register(modEventBus);

		ModItems.REGISTRY.register(modEventBus);
		ModMenuTypes.REGISTRY.register(modEventBus);

		ModCreativeTabs.REGISTRY.register(modEventBus);
	}

	public static void queueServerWork(int tick, Runnable action) {
		ServerWorkQueue.queue(tick, action);
	}

	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		if (event.getServer() != null) {
			ExchangeRateService.tick(event.getServer());
		}
		ServerWorkQueue.tick(LOGGER);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ExchangeRateService.initialize(event.getServer());
	}

	/**
	 * 玩家断线时清理全息终端 session。防止 {@link HolographicTerminalSession}
	 * 的 {@code Map<UUID, Integer>} 随玩家进出不断增长。玩家主动关闭 GUI 会发
	 * {@link CloseHolographicTerminalPayload} 正常清理；但遇到崩溃/超时断连这种场景
	 * Close 包不一定能到达，靠 PlayerLoggedOut 做最后一道塔。
	 */
	@SubscribeEvent
	public void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		HolographicTerminalSession.cleanup(event.getEntity().getUUID());
	}
}


