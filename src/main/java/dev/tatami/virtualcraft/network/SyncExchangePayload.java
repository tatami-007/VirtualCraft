package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.math.BigDecimal;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.exchange.ExchangeRateSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncExchangePayload(int menuId, String status, long updatedAtEpochSeconds, List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String currencyId, String quoteToUsdt) {
		private static Entry read(FriendlyByteBuf buffer) {
			return new Entry(buffer.readUtf(16), buffer.readUtf(64));
		}

		private static void write(FriendlyByteBuf buffer, Entry entry) {
			buffer.writeUtf(entry.currencyId, 16);
			buffer.writeUtf(entry.quoteToUsdt, 64);
		}
	}

	public static final Type<SyncExchangePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_exchange"));
	public static final StreamCodec<FriendlyByteBuf, SyncExchangePayload> STREAM_CODEC = StreamCodec.of(SyncExchangePayload::write, SyncExchangePayload::read);

	@Override
	public Type<SyncExchangePayload> type() {
		return TYPE;
	}

	public static SyncExchangePayload fromSnapshot(int menuId, ExchangeRateSnapshot snapshot) {
		List<Entry> entries = new ArrayList<>(CryptoCurrency.values().length);
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			entries.add(new Entry(currency.id(), snapshot.quoteToUsdt(currency).toPlainString()));
		}
		return new SyncExchangePayload(menuId, snapshot.status().name(), snapshot.updatedAtEpochSeconds(), entries);
	}

	private static SyncExchangePayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		String status = buffer.readUtf(32);
		long updatedAtEpochSeconds = buffer.readVarLong();
		int size = buffer.readVarInt();
		List<Entry> entries = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			entries.add(Entry.read(buffer));
		}
		return new SyncExchangePayload(menuId, status, updatedAtEpochSeconds, entries);
	}

	private static void write(FriendlyByteBuf buffer, SyncExchangePayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeUtf(payload.status, 32);
		buffer.writeVarLong(payload.updatedAtEpochSeconds);
		buffer.writeVarInt(payload.entries.size());
		for (Entry entry : payload.entries) {
			Entry.write(buffer, entry);
		}
	}

	public static void handle(SyncExchangePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncExchangePayload payload) {
			EnumMap<CryptoCurrency, BigDecimal> quotes = new EnumMap<>(CryptoCurrency.class);
			for (Entry entry : payload.entries()) {
				CryptoCurrency.byId(entry.currencyId()).ifPresent(currency -> quotes.put(currency, new BigDecimal(entry.quoteToUsdt())));
			}
			ExchangeRateSnapshot.SourceStatus sourceStatus;
			try {
				sourceStatus = ExchangeRateSnapshot.SourceStatus.valueOf(payload.status());
			} catch (IllegalArgumentException exception) {
				sourceStatus = ExchangeRateSnapshot.SourceStatus.DEFAULT;
			}
			ExchangeRateSnapshot snapshot = new ExchangeRateSnapshot(quotes, payload.updatedAtEpochSeconds(), sourceStatus);
			HolographicShopFragment fragment = HolographicShopFragment.fromCurrentScreen(payload.menuId());
			if (fragment != null) {
				fragment.applyExchangeSync(snapshot);
			}
		}
	}
}
