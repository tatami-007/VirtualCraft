package dev.tatami.virtualworld.network;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.client.ShockTextOverlay;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TriggerShockOverlayPayload() implements CustomPacketPayload {
	public static final Type<TriggerShockOverlayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualWorld.MODID, "trigger_electrocution_overlay"));
	public static final TriggerShockOverlayPayload INSTANCE = new TriggerShockOverlayPayload();
	public static final StreamCodec<FriendlyByteBuf, TriggerShockOverlayPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<TriggerShockOverlayPayload> type() {
		return TYPE;
	}

	public static void handle(TriggerShockOverlayPayload payload, IPayloadContext context) {
		context.enqueueWork(ClientHooks::showElectrocutionOverlay);
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void showElectrocutionOverlay() {
			ShockTextOverlay.show();
		}
	}
}

