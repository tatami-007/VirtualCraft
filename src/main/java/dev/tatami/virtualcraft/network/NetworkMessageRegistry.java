package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

public final class NetworkMessageRegistry {
	private static boolean networkingRegistered = false;
	private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

	private NetworkMessageRegistry() {
	}

	private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
	}

	public static void registerDefaults() {
		add(TriggerShockOverlayPayload.TYPE, TriggerShockOverlayPayload.STREAM_CODEC, TriggerShockOverlayPayload::handle);
		add(SyncWalletPayload.TYPE, SyncWalletPayload.STREAM_CODEC, SyncWalletPayload::handle);
		add(SyncWalletLedgerPagePayload.TYPE, SyncWalletLedgerPagePayload.STREAM_CODEC, SyncWalletLedgerPagePayload::handle);
		add(SyncWalletPreferencesPayload.TYPE, SyncWalletPreferencesPayload.STREAM_CODEC, SyncWalletPreferencesPayload::handle);
		add(SyncExchangePayload.TYPE, SyncExchangePayload.STREAM_CODEC, SyncExchangePayload::handle);
		add(WalletActionPayload.TYPE, WalletActionPayload.STREAM_CODEC, WalletActionPayload::handle);
		add(WalletPreferencesActionPayload.TYPE, WalletPreferencesActionPayload.STREAM_CODEC, WalletPreferencesActionPayload::handle);
		add(LedgerQueryPayload.TYPE, LedgerQueryPayload.STREAM_CODEC, LedgerQueryPayload::handle);
		add(SyncShopCatalogPayload.TYPE, SyncShopCatalogPayload.STREAM_CODEC, SyncShopCatalogPayload::handle);
		add(ShopActionPayload.TYPE, ShopActionPayload.STREAM_CODEC, ShopActionPayload::handle);
		add(OpenHolographicTerminalPayload.TYPE, OpenHolographicTerminalPayload.STREAM_CODEC, OpenHolographicTerminalPayload::handle);
		add(CloseHolographicTerminalPayload.TYPE, CloseHolographicTerminalPayload.STREAM_CODEC, CloseHolographicTerminalPayload::handle);
		add(LaserBatchBindPayload.TYPE, LaserBatchBindPayload.STREAM_CODEC, LaserBatchBindPayload::handle);
		add(MiningSurveyorActionPayload.TYPE, MiningSurveyorActionPayload.STREAM_CODEC, MiningSurveyorActionPayload::handle);
		add(SyncMiningSurveyorPayload.TYPE, SyncMiningSurveyorPayload.STREAM_CODEC, SyncMiningSurveyorPayload::handle);
	}

	public static <T extends CustomPacketPayload> void add(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
		if (networkingRegistered) {
			throw new IllegalStateException("Cannot register new network messages after networking has been registered");
		}
		MESSAGES.put(id, new NetworkMessage<>(reader, handler));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(VirtualCraft.MODID);
		MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
		networkingRegistered = true;
	}
}
