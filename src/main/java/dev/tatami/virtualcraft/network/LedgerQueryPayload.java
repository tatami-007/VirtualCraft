package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.wallet.WalletLedgerType;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server request for a filtered / paged slice of the ledger.  The
 * server responds with a {@link SyncWalletLedgerPagePayload}.
 */
public record LedgerQueryPayload(int menuId, int offset, int limit, List<String> typeFilters, List<String> currencyFilters) implements CustomPacketPayload {
	public static final int MAX_FILTERS = 12;
	public static final int MAX_LIMIT = 64;

	public LedgerQueryPayload {
		typeFilters = typeFilters == null ? List.of() : List.copyOf(typeFilters);
		currencyFilters = currencyFilters == null ? List.of() : List.copyOf(currencyFilters);
	}

	public static final Type<LedgerQueryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "ledger_query"));
	public static final StreamCodec<FriendlyByteBuf, LedgerQueryPayload> STREAM_CODEC = StreamCodec.of(LedgerQueryPayload::write, LedgerQueryPayload::read);

	@Override
	public Type<LedgerQueryPayload> type() {
		return TYPE;
	}

	private static LedgerQueryPayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		int offset = buffer.readVarInt();
		int limit = buffer.readVarInt();
		int typeCount = Math.min(MAX_FILTERS, buffer.readVarInt());
		List<String> types = new ArrayList<>(typeCount);
		for (int index = 0; index < typeCount; index++) {
			types.add(buffer.readUtf(32));
		}
		int currencyCount = Math.min(MAX_FILTERS, buffer.readVarInt());
		List<String> currencies = new ArrayList<>(currencyCount);
		for (int index = 0; index < currencyCount; index++) {
			currencies.add(buffer.readUtf(16));
		}
		return new LedgerQueryPayload(menuId, offset, limit, types, currencies);
	}

	private static void write(FriendlyByteBuf buffer, LedgerQueryPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeVarInt(Math.max(0, payload.offset));
		buffer.writeVarInt(Math.min(MAX_LIMIT, Math.max(1, payload.limit)));
		int typeCount = Math.min(MAX_FILTERS, payload.typeFilters.size());
		buffer.writeVarInt(typeCount);
		for (int index = 0; index < typeCount; index++) {
			buffer.writeUtf(payload.typeFilters.get(index), 32);
		}
		int currencyCount = Math.min(MAX_FILTERS, payload.currencyFilters.size());
		buffer.writeVarInt(currencyCount);
		for (int index = 0; index < currencyCount; index++) {
			buffer.writeUtf(payload.currencyFilters.get(index), 16);
		}
	}

	public static void handle(LedgerQueryPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !HolographicTerminalSession.isOpen(player, payload.menuId())) {
				return;
			}
			int offset = Math.max(0, payload.offset());
			int limit = Math.min(MAX_LIMIT, Math.max(1, payload.limit()));
			Set<WalletLedgerType> typeFilter = new HashSet<>();
			for (String id : payload.typeFilters()) {
				WalletLedgerType type = resolveType(id);
				if (type != null) {
					typeFilter.add(type);
				}
			}
			Set<String> currencyFilter = payload.currencyFilters().stream()
					.filter(s -> s != null && !s.isBlank())
					.map(String::toLowerCase)
					.collect(Collectors.toCollection(HashSet::new));
			int totalMatching = WalletService.ledgerMatchCount(player, typeFilter, currencyFilter);
			List<SyncWalletPayload.Entry> entries = WalletService.pageEntries(player, offset, limit, typeFilter, currencyFilter).stream()
					.map(SyncWalletPayload.Entry::fromLedgerEntry)
					.toList();
			context.reply(new SyncWalletLedgerPagePayload(payload.menuId(), offset, totalMatching, entries,
					Collections.unmodifiableList(new ArrayList<>(payload.typeFilters())),
					Collections.unmodifiableList(new ArrayList<>(payload.currencyFilters()))));
		});
	}

	private static WalletLedgerType resolveType(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		for (WalletLedgerType type : WalletLedgerType.values()) {
			if (type.id().equals(id)) {
				return type;
			}
		}
		return null;
	}
}
