package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.List;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client response to {@link LedgerQueryPayload}. Carries the page
 * entries as well as the filter echo so the client can reconcile its local UI
 * state with the filter the server actually evaluated.
 */
public record SyncWalletLedgerPagePayload(
		int menuId,
		int offset,
		int totalMatching,
		List<SyncWalletPayload.Entry> entries,
		List<String> typeFilters,
		List<String> currencyFilters) implements CustomPacketPayload {

	public SyncWalletLedgerPagePayload {
		entries = entries == null ? List.of() : List.copyOf(entries);
		typeFilters = typeFilters == null ? List.of() : List.copyOf(typeFilters);
		currencyFilters = currencyFilters == null ? List.of() : List.copyOf(currencyFilters);
	}

	public static final Type<SyncWalletLedgerPagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_wallet_ledger_page"));
	public static final StreamCodec<FriendlyByteBuf, SyncWalletLedgerPagePayload> STREAM_CODEC = StreamCodec.of(SyncWalletLedgerPagePayload::write, SyncWalletLedgerPagePayload::read);

	@Override
	public Type<SyncWalletLedgerPagePayload> type() {
		return TYPE;
	}

	private static SyncWalletLedgerPagePayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		int offset = buffer.readVarInt();
		int total = buffer.readVarInt();
		int entryCount = buffer.readVarInt();
		List<SyncWalletPayload.Entry> entries = new ArrayList<>(entryCount);
		for (int i = 0; i < entryCount; i++) {
			entries.add(readEntry(buffer));
		}
		int typeFilterCount = buffer.readVarInt();
		List<String> typeFilters = new ArrayList<>(typeFilterCount);
		for (int i = 0; i < typeFilterCount; i++) {
			typeFilters.add(buffer.readUtf(32));
		}
		int currencyFilterCount = buffer.readVarInt();
		List<String> currencyFilters = new ArrayList<>(currencyFilterCount);
		for (int i = 0; i < currencyFilterCount; i++) {
			currencyFilters.add(buffer.readUtf(16));
		}
		return new SyncWalletLedgerPagePayload(menuId, offset, total, entries, typeFilters, currencyFilters);
	}

	private static void write(FriendlyByteBuf buffer, SyncWalletLedgerPagePayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeVarInt(Math.max(0, payload.offset));
		buffer.writeVarInt(Math.max(0, payload.totalMatching));
		buffer.writeVarInt(payload.entries.size());
		for (SyncWalletPayload.Entry entry : payload.entries) {
			writeEntry(buffer, entry);
		}
		buffer.writeVarInt(payload.typeFilters.size());
		for (String filter : payload.typeFilters) {
			buffer.writeUtf(filter, 32);
		}
		buffer.writeVarInt(payload.currencyFilters.size());
		for (String filter : payload.currencyFilters) {
			buffer.writeUtf(filter, 16);
		}
	}

	// The SyncWalletPayload.Entry read/write helpers are package-private statics; to
	// avoid coupling we inline the matching serialization here.
	private static SyncWalletPayload.Entry readEntry(FriendlyByteBuf buffer) {
		return new SyncWalletPayload.Entry(
				buffer.readUtf(32),
				buffer.readUtf(16),
				buffer.readVarLong(),
				buffer.readUtf(16),
				buffer.readVarLong(),
				buffer.readUtf(128),
				buffer.readVarLong(),
				buffer.readUtf(64));
	}

	private static void writeEntry(FriendlyByteBuf buffer, SyncWalletPayload.Entry entry) {
		buffer.writeUtf(entry.typeId(), 32);
		buffer.writeUtf(entry.primaryCurrencyId(), 16);
		buffer.writeVarLong(entry.primaryAmountMinor());
		buffer.writeUtf(entry.secondaryCurrencyId(), 16);
		buffer.writeVarLong(entry.secondaryAmountMinor());
		buffer.writeUtf(entry.detail(), 128);
		buffer.writeVarLong(entry.timestampSeconds());
		buffer.writeUtf(entry.memo(), 64);
	}

	public static void handle(SyncWalletLedgerPagePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncWalletLedgerPagePayload payload) {
			HolographicShopFragment fragment = HolographicShopFragment.fromCurrentScreen(payload.menuId());
			if (fragment != null) {
				fragment.applyLedgerPage(payload.offset(), payload.totalMatching(), payload.entries(), payload.typeFilters(), payload.currencyFilters());
			}
		}
	}
}
