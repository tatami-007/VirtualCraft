package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.CurrencyGeneratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncGeneratorPayload(int menuId, String ownerName) implements CustomPacketPayload {
	public static final Type<SyncGeneratorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_generator"));
	public static final StreamCodec<FriendlyByteBuf, SyncGeneratorPayload> STREAM_CODEC = StreamCodec.of(SyncGeneratorPayload::write, SyncGeneratorPayload::read);

	@Override
	public Type<SyncGeneratorPayload> type() {
		return TYPE;
	}

	private static SyncGeneratorPayload read(FriendlyByteBuf buffer) {
		return new SyncGeneratorPayload(buffer.readVarInt(), buffer.readUtf(64));
	}

	private static void write(FriendlyByteBuf buffer, SyncGeneratorPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeUtf(payload.ownerName, 64);
	}

	public static void handle(SyncGeneratorPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncGeneratorPayload payload) {
			if (Minecraft.getInstance().screen instanceof CurrencyGeneratorScreen screen && screen.matchesMenu(payload.menuId())) {
				screen.applyGeneratorSync(payload.ownerName());
			}
		}
	}
}
