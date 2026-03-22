package dev.tatami.virtualcraft;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import net.minecraft.util.Tuple;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Collection;
import java.util.ArrayList;

import dev.tatami.virtualcraft.client.AbsorberOreHighlighter;
import dev.tatami.virtualcraft.client.MiningSurveyorHighlighter;
import dev.tatami.virtualcraft.client.VirtualCraftClient;
import dev.tatami.virtualcraft.exchange.ExchangeRateService;
import dev.tatami.virtualcraft.network.GeneratorActionPayload;
import dev.tatami.virtualcraft.network.ShopActionPayload;
import dev.tatami.virtualcraft.network.SyncExchangePayload;
import dev.tatami.virtualcraft.network.SyncGeneratorPayload;
import dev.tatami.virtualcraft.network.SyncShopCatalogPayload;
import dev.tatami.virtualcraft.network.SyncWalletPayload;
import dev.tatami.virtualcraft.registry.ModCreativeTabs;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModCapabilities;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import dev.tatami.virtualcraft.network.TriggerShockOverlayPayload;
import dev.tatami.virtualcraft.network.WalletActionPayload;

@Mod("virtual_craft")
public class VirtualCraft {
	public static final Logger LOGGER = LogManager.getLogger(VirtualCraft.class);
	public static final String MODID = "virtual_craft";
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public VirtualCraft(IEventBus modEventBus) {
		NeoForge.EVENT_BUS.register(this);
		modEventBus.addListener(this::registerNetworking);
		modEventBus.addListener(ModCapabilities::register);
		if (FMLEnvironment.dist.isClient()) {
			AbsorberOreHighlighter highlighter = new AbsorberOreHighlighter();
			MiningSurveyorHighlighter surveyorHighlighter = new MiningSurveyorHighlighter();
			NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
				if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
					highlighter.renderInGame(event.getPoseStack());
					surveyorHighlighter.renderInGame(event.getPoseStack());
				}
			});
			modEventBus.addListener(VirtualCraftClient::registerScreens);
			modEventBus.addListener(VirtualCraftClient::registerShaders);
			NeoForge.EVENT_BUS.addListener(VirtualCraftClient::renderShockOverlay);
		}

		addNetworkMessage(TriggerShockOverlayPayload.TYPE, TriggerShockOverlayPayload.STREAM_CODEC, TriggerShockOverlayPayload::handle);
		addNetworkMessage(SyncWalletPayload.TYPE, SyncWalletPayload.STREAM_CODEC, SyncWalletPayload::handle);
		addNetworkMessage(SyncExchangePayload.TYPE, SyncExchangePayload.STREAM_CODEC, SyncExchangePayload::handle);
		addNetworkMessage(WalletActionPayload.TYPE, WalletActionPayload.STREAM_CODEC, WalletActionPayload::handle);
		addNetworkMessage(SyncShopCatalogPayload.TYPE, SyncShopCatalogPayload.STREAM_CODEC, SyncShopCatalogPayload::handle);
		addNetworkMessage(ShopActionPayload.TYPE, ShopActionPayload.STREAM_CODEC, ShopActionPayload::handle);
		addNetworkMessage(SyncGeneratorPayload.TYPE, SyncGeneratorPayload.STREAM_CODEC, SyncGeneratorPayload::handle);
		addNetworkMessage(GeneratorActionPayload.TYPE, GeneratorActionPayload.STREAM_CODEC, GeneratorActionPayload::handle);

		ModBlocks.REGISTRY.register(modEventBus);
		ModBlockEntities.REGISTRY.register(modEventBus);
		ModAttachments.REGISTRY.register(modEventBus);

		ModItems.REGISTRY.register(modEventBus);
		ModMenuTypes.REGISTRY.register(modEventBus);

		ModCreativeTabs.REGISTRY.register(modEventBus);
	}
	private static boolean networkingRegistered = false;
	private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

	private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
	}

	public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
		if (networkingRegistered)
			throw new IllegalStateException("Cannot register new network messages after networking has been registered");
		MESSAGES.put(id, new NetworkMessage<>(reader, handler));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void registerNetworking(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar(MODID);
		MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
		networkingRegistered = true;
	}

	private static final Collection<Tuple<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workQueue.add(new Tuple<>(action, tick));
	}

	@SubscribeEvent
	public void tick(ServerTickEvent.Post event) {
		if (event.getServer() != null) {
			ExchangeRateService.tick(event.getServer());
		}
		List<Tuple<Runnable, Integer>> actions = new ArrayList<>();
		workQueue.forEach(work -> {
			work.setB(work.getB() - 1);
			if (work.getB() == 0)
				actions.add(work);
		});
		actions.forEach(e -> e.getA().run());
		workQueue.removeAll(actions);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ExchangeRateService.initialize(event.getServer());
	}
}


