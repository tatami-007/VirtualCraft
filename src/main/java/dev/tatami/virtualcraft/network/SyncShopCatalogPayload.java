package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.List;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment;
import dev.tatami.virtualcraft.shop.ShopOffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncShopCatalogPayload(int menuId, List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String id, String itemId, int count, long priceUsdtMinor, long sellUsdtMinor, boolean enabled) {
		private static Entry read(FriendlyByteBuf buffer) {
			return new Entry(
					buffer.readUtf(64),
					buffer.readUtf(128),
					buffer.readVarInt(),
					buffer.readVarLong(),
					buffer.readVarLong(),
					buffer.readBoolean());
		}

		private static void write(FriendlyByteBuf buffer, Entry entry) {
			buffer.writeUtf(entry.id, 64);
			buffer.writeUtf(entry.itemId, 128);
			buffer.writeVarInt(entry.count);
			buffer.writeVarLong(entry.priceUsdtMinor);
			buffer.writeVarLong(entry.sellUsdtMinor);
			buffer.writeBoolean(entry.enabled);
		}

		public static Entry fromOffer(ShopOffer offer) {
			return new Entry(
					offer.id(),
					offer.itemId().toString(),
					offer.count(),
					offer.priceUsdtMinor(),
					offer.sellUsdtMinor(),
					offer.enabled());
		}

		public ShopOffer toOffer() {
			return ShopOffer.create(id, itemId, count, priceUsdtMinor, sellUsdtMinor, enabled).orElse(null);
		}
	}

	public static final Type<SyncShopCatalogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_shop_catalog"));
	public static final StreamCodec<FriendlyByteBuf, SyncShopCatalogPayload> STREAM_CODEC = StreamCodec.of(SyncShopCatalogPayload::write, SyncShopCatalogPayload::read);

	@Override
	public Type<SyncShopCatalogPayload> type() {
		return TYPE;
	}

	private static SyncShopCatalogPayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		int size = buffer.readVarInt();
		List<Entry> entries = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			entries.add(Entry.read(buffer));
		}
		return new SyncShopCatalogPayload(menuId, entries);
	}

	private static void write(FriendlyByteBuf buffer, SyncShopCatalogPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeVarInt(payload.entries.size());
		for (Entry entry : payload.entries) {
			Entry.write(buffer, entry);
		}
	}

	public static void handle(SyncShopCatalogPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncShopCatalogPayload payload) {
			HolographicShopFragment fragment = HolographicShopFragment.fromCurrentScreen(payload.menuId());
			if (fragment != null) {
				fragment.applyCatalogSync(payload.entries());
			} else {
				dev.tatami.virtualcraft.client.screen.holographic.HolographicShopClientCache.storeCatalog(payload.entries());
			}
		}
	}
}
