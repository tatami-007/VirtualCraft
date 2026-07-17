package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractSnapshot;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.wallet.WalletPreferences;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client snapshot of the player's {@link WalletPreferences} so the
 * Holographic Terminal can display favorites, recent transfer contacts and
 * aggregated mining statistics without round-tripping on every frame.
 */
public record SyncWalletPreferencesPayload(
		int menuId,
		List<Contact> recentContacts,
		List<String> favoriteOfferIds,
		MiningStatsEntry miningStats,
		List<DailyContractSnapshot> contracts) implements CustomPacketPayload {

	public SyncWalletPreferencesPayload {
		recentContacts = recentContacts == null ? List.of() : List.copyOf(recentContacts);
		favoriteOfferIds = favoriteOfferIds == null ? List.of() : List.copyOf(favoriteOfferIds);
		if (miningStats == null) {
			miningStats = MiningStatsEntry.EMPTY;
		}
		contracts = contracts == null ? List.of() : List.copyOf(contracts);
	}

	public record Contact(String name, long lastUsedEpochSeconds, long totalSentUsdtMinor, String lastMemo) {
		public Contact {
			if (name == null) {
				name = "";
			}
			if (lastMemo == null) {
				lastMemo = "";
			}
		}
	}

	public record PayoutEntry(String currencyId, long amountMinor) {
	}

	public record DailyBucket(long dayStartEpochSeconds, long btcMinor, int voucherCount) {
	}

	public record MiningStatsEntry(
			long totalSettlements,
			long totalVouchers,
			long totalBtcMinor,
			long firstSettleEpochSeconds,
			long lastSettleEpochSeconds,
			List<PayoutEntry> payouts,
			List<DailyBucket> last7DayBuckets) {

		public static final MiningStatsEntry EMPTY = new MiningStatsEntry(0, 0, 0, 0, 0, List.of(), List.of());

		public MiningStatsEntry {
			payouts = payouts == null ? List.of() : List.copyOf(payouts);
			last7DayBuckets = last7DayBuckets == null ? List.of() : List.copyOf(last7DayBuckets);
		}

		public long totalFor(CryptoCurrency currency) {
			if (currency == null) {
				return 0L;
			}
			String id = currency.id();
			for (PayoutEntry entry : payouts) {
				if (id.equals(entry.currencyId())) {
					return entry.amountMinor();
				}
			}
			return 0L;
		}
	}

	public static final Type<SyncWalletPreferencesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_wallet_preferences"));
	public static final StreamCodec<FriendlyByteBuf, SyncWalletPreferencesPayload> STREAM_CODEC = StreamCodec.of(SyncWalletPreferencesPayload::write, SyncWalletPreferencesPayload::read);

	@Override
	public Type<SyncWalletPreferencesPayload> type() {
		return TYPE;
	}

	public static SyncWalletPreferencesPayload fromPreferences(int menuId, ServerPlayer player) {
		return fromPreferences(menuId, player.getUUID(), dev.tatami.virtualcraft.wallet.WalletService.getPreferences(player));
	}

	public static SyncWalletPreferencesPayload fromPreferences(int menuId, WalletPreferences preferences) {
		return fromPreferences(menuId, new java.util.UUID(0L, 0L), preferences);
	}

	public static SyncWalletPreferencesPayload fromPreferences(int menuId, java.util.UUID playerId, WalletPreferences preferences) {
		List<Contact> contacts = new ArrayList<>(preferences.recentContacts().size());
		for (WalletPreferences.TransferContact contact : preferences.recentContacts()) {
			contacts.add(new Contact(contact.name(), contact.lastUsedEpochSeconds(), contact.totalSentUsdtMinor(), contact.lastMemo()));
		}
		List<String> favorites = new ArrayList<>(preferences.favoriteOfferIds());
		WalletPreferences.MiningStats stats = preferences.miningStats();
		EnumMap<CryptoCurrency, Long> payoutMap = new EnumMap<>(CryptoCurrency.class);
		payoutMap.putAll(stats.payoutsByCurrencyMinor());
		List<PayoutEntry> payouts = new ArrayList<>(payoutMap.size());
		for (Map.Entry<CryptoCurrency, Long> entry : payoutMap.entrySet()) {
			payouts.add(new PayoutEntry(entry.getKey().id(), entry.getValue() == null ? 0L : entry.getValue()));
		}
		List<DailyBucket> buckets = new ArrayList<>(stats.last7DayBuckets().size());
		for (WalletPreferences.MiningStats.DailyBucket bucket : stats.last7DayBuckets()) {
			buckets.add(new DailyBucket(bucket.dayStartEpochSeconds(), bucket.btcMinor(), bucket.voucherCount()));
		}
		MiningStatsEntry statsEntry = new MiningStatsEntry(
				stats.totalSettlements(),
				stats.totalVouchers(),
				stats.totalBtcMinor(),
				stats.firstSettleEpochSeconds(),
				stats.lastSettleEpochSeconds(),
				payouts,
				buckets);
		return new SyncWalletPreferencesPayload(menuId, contacts, favorites, statsEntry, DailyContractService.snapshots(playerId, preferences));
	}

	private static SyncWalletPreferencesPayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		int contactCount = buffer.readVarInt();
		List<Contact> contacts = new ArrayList<>(contactCount);
		for (int i = 0; i < contactCount; i++) {
			contacts.add(new Contact(buffer.readUtf(32), buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(64)));
		}
		int favCount = buffer.readVarInt();
		List<String> favorites = new ArrayList<>(favCount);
		for (int i = 0; i < favCount; i++) {
			favorites.add(buffer.readUtf(64));
		}
		long totalSettlements = buffer.readVarLong();
		long totalVouchers = buffer.readVarLong();
		long totalBtcMinor = buffer.readVarLong();
		long firstSettle = buffer.readVarLong();
		long lastSettle = buffer.readVarLong();
		int payoutCount = buffer.readVarInt();
		List<PayoutEntry> payouts = new ArrayList<>(payoutCount);
		for (int i = 0; i < payoutCount; i++) {
			payouts.add(new PayoutEntry(buffer.readUtf(16), buffer.readVarLong()));
		}
		int bucketCount = buffer.readVarInt();
		List<DailyBucket> buckets = new ArrayList<>(bucketCount);
		for (int i = 0; i < bucketCount; i++) {
			buckets.add(new DailyBucket(buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt()));
		}
		MiningStatsEntry stats = new MiningStatsEntry(totalSettlements, totalVouchers, totalBtcMinor, firstSettle, lastSettle, payouts, buckets);
		int contractCount = buffer.readVarInt();
		List<DailyContractSnapshot> contracts = new ArrayList<>(contractCount);
		for (int i = 0; i < contractCount; i++) {
			contracts.add(new DailyContractSnapshot(
					buffer.readUtf(96),
					buffer.readUtf(32),
					buffer.readUtf(128),
					buffer.readUtf(128),
					buffer.readUtf(48),
					buffer.readVarLong(),
					buffer.readVarLong(),
					buffer.readBoolean(),
					buffer.readUtf(48),
					buffer.readUtf(48),
					buffer.readUtf(96)));
		}
		return new SyncWalletPreferencesPayload(menuId, contacts, favorites, stats, contracts);
	}

	private static void write(FriendlyByteBuf buffer, SyncWalletPreferencesPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeVarInt(payload.recentContacts.size());
		for (Contact contact : payload.recentContacts) {
			buffer.writeUtf(contact.name(), 32);
			buffer.writeVarLong(contact.lastUsedEpochSeconds());
			buffer.writeVarLong(contact.totalSentUsdtMinor());
			buffer.writeUtf(contact.lastMemo(), 64);
		}
		buffer.writeVarInt(payload.favoriteOfferIds.size());
		for (String id : payload.favoriteOfferIds) {
			buffer.writeUtf(id, 64);
		}
		MiningStatsEntry stats = payload.miningStats;
		buffer.writeVarLong(stats.totalSettlements());
		buffer.writeVarLong(stats.totalVouchers());
		buffer.writeVarLong(stats.totalBtcMinor());
		buffer.writeVarLong(stats.firstSettleEpochSeconds());
		buffer.writeVarLong(stats.lastSettleEpochSeconds());
		buffer.writeVarInt(stats.payouts().size());
		for (PayoutEntry entry : stats.payouts()) {
			buffer.writeUtf(entry.currencyId(), 16);
			buffer.writeVarLong(entry.amountMinor());
		}
		buffer.writeVarInt(stats.last7DayBuckets().size());
		for (DailyBucket bucket : stats.last7DayBuckets()) {
			buffer.writeVarLong(bucket.dayStartEpochSeconds());
			buffer.writeVarLong(bucket.btcMinor());
			buffer.writeVarInt(bucket.voucherCount());
		}
		buffer.writeVarInt(payload.contracts.size());
		for (DailyContractSnapshot contract : payload.contracts) {
			buffer.writeUtf(contract.id(), 96);
			buffer.writeUtf(contract.typeId(), 32);
			buffer.writeUtf(contract.titleKey(), 128);
			buffer.writeUtf(contract.goalKey(), 128);
			buffer.writeUtf(contract.targetText(), 48);
			buffer.writeVarLong(contract.target());
			buffer.writeVarLong(contract.progress());
			buffer.writeBoolean(contract.claimed());
			buffer.writeUtf(contract.rewardKind(), 48);
			buffer.writeUtf(contract.rewardText(), 48);
			buffer.writeUtf(contract.iconItemId(), 96);
		}
	}

	public static void handle(SyncWalletPreferencesPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncWalletPreferencesPayload payload) {
			HolographicShopFragment fragment = HolographicShopFragment.fromCurrentScreen(payload.menuId());
			if (fragment != null) {
				fragment.applyPreferencesSync(payload);
			} else {
				dev.tatami.virtualcraft.client.screen.holographic.HolographicShopClientCache.storePreferences(payload);
			}
		}
	}
}
