package dev.tatami.virtualcraft;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import dev.tatami.virtualcraft.client.binding.BoundMachineHighlighter;
import dev.tatami.virtualcraft.client.binding.LaserBindingClientHandler;
import dev.tatami.virtualcraft.client.highlight.AbsorberOreHighlighter;
import dev.tatami.virtualcraft.client.highlight.MiningSurveyorHighlighter;
import dev.tatami.virtualcraft.client.VirtualCraftClient;
import dev.tatami.virtualcraft.config.SnakeBossConfig;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.gameplay.SnakeKingBlessingHandler;
import dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler;
import dev.tatami.virtualcraft.network.CloseHolographicTerminalPayload;
import dev.tatami.virtualcraft.network.NetworkMessageRegistry;
import dev.tatami.virtualcraft.energy.WirelessEnergyNetwork;
import dev.tatami.virtualcraft.energy.EnergyPipeNetwork;
import dev.tatami.virtualcraft.registry.ModCreativeTabs;
import dev.tatami.virtualcraft.registry.ModArmorMaterials;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModCapabilities;
import dev.tatami.virtualcraft.registry.ModFluids;
import dev.tatami.virtualcraft.registry.ModFeatures;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import dev.tatami.virtualcraft.registry.ModMobEffects;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import dev.tatami.virtualcraft.registry.ModSounds;
import dev.tatami.virtualcraft.registry.ModStructures;
import dev.tatami.virtualcraft.registry.ModRecipeTypes;
import dev.tatami.virtualcraft.server.ServerWorkQueue;
import dev.tatami.virtualcraft.world.LightningForestEffects;
import dev.tatami.virtualcraft.world.VirtualWorldTerrainMigration;

@Mod("virtual_craft")
@SuppressWarnings("this-escape") // NeoForge.EVENT_BUS.register(this) in the constructor is the standard mod pattern.
public class VirtualCraft {
	public static final Logger LOGGER = LogManager.getLogger(VirtualCraft.class);
	public static final String MODID = "virtual_craft";
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public VirtualCraft(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, SnakeBossConfig.SPEC, "virtual_craft-boss.toml");
		NeoForge.EVENT_BUS.register(this);
		NetworkMessageRegistry.registerDefaults();
		modEventBus.addListener(NetworkMessageRegistry::registerPayloadHandlers);
		modEventBus.addListener(ModCapabilities::register);
		if (FMLEnvironment.dist.isClient()) {
			AbsorberOreHighlighter highlighter = new AbsorberOreHighlighter();
			MiningSurveyorHighlighter surveyorHighlighter = new MiningSurveyorHighlighter();
			dev.tatami.virtualcraft.client.highlight.QuantumScanOverlay quantumScanOverlay =
					new dev.tatami.virtualcraft.client.highlight.QuantumScanOverlay();
			BoundMachineHighlighter boundMachineHighlighter = new BoundMachineHighlighter();
			NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
				if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
					highlighter.renderInGame(event.getPoseStack());
					surveyorHighlighter.renderInGame(event.getPoseStack());
					// 手持光束绑定器时、在已绑定机器中心画半透明小红方块，让塔顶发出的红色光束看起来是“打在小方块上”的效果
					boundMachineHighlighter.renderInGame(event.getPoseStack());
					dev.tatami.virtualcraft.client.render.SnakeAttackTelegraphRenderer.render(event.getPoseStack());
					dev.tatami.virtualcraft.client.render.SnakeAttackEffectRenderer.render(event.getPoseStack());
				}
			});
			// Ctrl + Shift + 右键 机器 → 批量绑定周围机器到选中的源塔
			LaserBindingClientHandler.register();
			modEventBus.addListener(VirtualCraftClient::registerScreens);
			modEventBus.addListener(VirtualCraftClient::registerBlockColors);
			modEventBus.addListener(VirtualCraftClient::registerItemColors);
			modEventBus.addListener(VirtualCraftClient::registerShaders);
			modEventBus.addListener(VirtualCraftClient::registerEntityRenderers);
			modEventBus.addListener(VirtualCraftClient::registerParticles);
			modEventBus.addListener(VirtualCraftClient::registerClientExtensions);
			modEventBus.addListener(dev.tatami.virtualcraft.client.ThunderPoolClient::registerFluidExtensions);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::renderShockOverlay);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::renderSnakeBossBar);
			NeoForge.EVENT_BUS.addListener(dev.tatami.virtualcraft.client.SnakeBossCombatFeedback::onCameraAngles);
			NeoForge.EVENT_BUS.addListener(surveyorHighlighter::renderGui);
			NeoForge.EVENT_BUS.addListener(quantumScanOverlay::render);
			// Pre-warm Holographic Terminal currency icons on world join (so the first
			// open of the terminal does not pay synchronous resource-pack IO),
			// and drop the per-session client cache on disconnect so we never paint
			// stale balances/addresses from a previous server.
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientLogin);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientLogout);
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
					VirtualCraftClient::prioritizeSmithingRecipes);
			// v24 tick-driven holographic shop refresh button state machine —
			// ModernUI View.postDelayed is unreliable on the render thread, so
			// we drive transitions from the 20 Hz client tick instead.
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::onClientTick);
		}

		// 1:1 ME-Beam-Former 全局无线塔管理器 — 集中调度所有塔的能量转发，应听 ServerTickEvent.Post + ServerStoppedEvent
		WirelessEnergyNetwork.registerEvents(NeoForge.EVENT_BUS);
		EnergyPipeNetwork.registerEvents(NeoForge.EVENT_BUS);
		NeoForge.EVENT_BUS.addListener(dev.tatami.virtualcraft.world.LightningResonanceEvents::onEntityJoin);
		NeoForge.EVENT_BUS.addListener(SnakeKingBlessingHandler::onDamageDealt);
		NeoForge.EVENT_BUS.addListener(SnakeKingBlessingHandler::amplifyDamage);
		NeoForge.EVENT_BUS.addListener(SnakeKingBlessingHandler::onPlayerTick);
		NeoForge.EVENT_BUS.addListener(VirtualWeaponParryHandler::onIncomingDamage);

		ModFluids.TYPES.register(modEventBus);
		ModFluids.REGISTRY.register(modEventBus);
		ModBlocks.REGISTRY.register(modEventBus);
		ModParticleTypes.REGISTRY.register(modEventBus);
		ModSounds.REGISTRY.register(modEventBus);
		ModEntityTypes.REGISTRY.register(modEventBus);
		modEventBus.addListener(ModEntityTypes::registerAttributes);
		ModFeatures.REGISTRY.register(modEventBus);
		ModStructures.STRUCTURE_TYPES.register(modEventBus);
		ModStructures.PIECE_TYPES.register(modEventBus);
		ModDimensions.CHUNK_GENERATOR_CODECS.register(modEventBus);
		ModBlockEntities.REGISTRY.register(modEventBus);
		ModAttachments.REGISTRY.register(modEventBus);
		ModRecipeTypes.TYPES.register(modEventBus);
		ModRecipeTypes.SERIALIZERS.register(modEventBus);

		ModArmorMaterials.REGISTRY.register(modEventBus);
		ModMobEffects.REGISTRY.register(modEventBus);
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
			LightningForestEffects.tick(event.getServer());
		}
		ServerWorkQueue.tick(LOGGER);
	}

	@SubscribeEvent
	public void onServerAboutToStart(ServerAboutToStartEvent event) {
		VirtualWorldTerrainMigration.migrateIfNeeded(event.getServer());
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ExchangeRateService.initialize(event.getServer());
		var virtualLevel = event.getServer().getLevel(ModDimensions.VIRTUAL_WORLD_LEVEL);
		if (virtualLevel != null) {
			// A short-lived portal ticket lets the chunk system prepare the landing
			// area in the background instead of doing all work when the computer is used.
			virtualLevel.getChunkSource().addRegionTicket(
					TicketType.PORTAL, new ChunkPos(0, 0), 3, BlockPos.ZERO);
		}
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

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		ExchangeRateService.shutdown();
	}
}


