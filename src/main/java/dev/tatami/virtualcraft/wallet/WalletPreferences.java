package dev.tatami.virtualcraft.wallet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Per-player Holographic Terminal preferences that must persist across
 * sessions and devices.  Stored as a separate attachment from
 * {@link WalletBalances} so the balance serialization stays tight and the
 * preference blob can evolve independently (via the {@link #version} field).
 */
public final class WalletPreferences implements INBTSerializable<CompoundTag> {
	public static final int CURRENT_VERSION = 1;
	public static final int MAX_RECENT_CONTACTS = 16;
	public static final int MAX_FAVORITE_OFFERS = 48;
	public static final int MAX_MEMO_LENGTH = 48;

	private int version = CURRENT_VERSION;
	private final List<TransferContact> recentContacts = new ArrayList<>();
	private final LinkedHashSet<String> favoriteOfferIds = new LinkedHashSet<>();
	private final MiningStats miningStats = new MiningStats();

	public int version() {
		return version;
	}

	public List<TransferContact> recentContacts() {
		return Collections.unmodifiableList(recentContacts);
	}

	public Set<String> favoriteOfferIds() {
		return Collections.unmodifiableSet(favoriteOfferIds);
	}

	public MiningStats miningStats() {
		return miningStats;
	}

	public boolean isFavorite(String offerId) {
		return offerId != null && !offerId.isBlank() && favoriteOfferIds.contains(offerId);
	}

	public boolean setFavorite(String offerId, boolean favorite) {
		if (offerId == null || offerId.isBlank()) {
			return false;
		}
		if (favorite) {
			if (favoriteOfferIds.contains(offerId)) {
				return false;
			}
			if (favoriteOfferIds.size() >= MAX_FAVORITE_OFFERS) {
				return false;
			}
			favoriteOfferIds.add(offerId);
			return true;
		}
		return favoriteOfferIds.remove(offerId);
	}

	public void clearFavorites() {
		favoriteOfferIds.clear();
	}

	/**
	 * Record that the owner sent a transfer to {@code contactName} so that the
	 * UI can surface recent recipients. The most recently used contact always
	 * bubbles to the front of the list; the list is bounded by
	 * {@link #MAX_RECENT_CONTACTS}.
	 */
	public void touchContact(String contactName, String memo, long sentUsdtMinor) {
		if (contactName == null || contactName.isBlank()) {
			return;
		}
		String trimmed = contactName.trim();
		long now = Instant.now().getEpochSecond();
		TransferContact existing = null;
		Iterator<TransferContact> it = recentContacts.iterator();
		while (it.hasNext()) {
			TransferContact contact = it.next();
			if (contact.name().equalsIgnoreCase(trimmed)) {
				existing = contact;
				it.remove();
				break;
			}
		}
		long totalSent = Math.max(0L, (existing == null ? 0L : existing.totalSentUsdtMinor()) + Math.max(0L, sentUsdtMinor));
		String resolvedMemo = memo == null ? "" : memo.trim();
		if (resolvedMemo.length() > MAX_MEMO_LENGTH) {
			resolvedMemo = resolvedMemo.substring(0, MAX_MEMO_LENGTH);
		}
		recentContacts.add(0, new TransferContact(trimmed, now, totalSent, resolvedMemo));
		while (recentContacts.size() > MAX_RECENT_CONTACTS) {
			recentContacts.remove(recentContacts.size() - 1);
		}
	}

	public void clearContacts() {
		recentContacts.clear();
	}

	public void recordMiningSettlement(CryptoCurrency payoutCurrency, long payoutMinor, long totalBtcMinor, int voucherCount) {
		miningStats.record(payoutCurrency, payoutMinor, totalBtcMinor, voucherCount);
	}

	public void copyFrom(WalletPreferences other) {
		version = other.version;
		recentContacts.clear();
		recentContacts.addAll(other.recentContacts);
		favoriteOfferIds.clear();
		favoriteOfferIds.addAll(other.favoriteOfferIds);
		miningStats.copyFrom(other.miningStats);
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("version", version);
		ListTag contactsList = new ListTag();
		for (TransferContact contact : recentContacts) {
			contactsList.add(contact.serialize());
		}
		tag.put("recent_contacts", contactsList);
		ListTag favoritesList = new ListTag();
		for (String offerId : favoriteOfferIds) {
			favoritesList.add(StringTag.valueOf(offerId));
		}
		tag.put("favorite_offers", favoritesList);
		tag.put("mining_stats", miningStats.serialize());
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		version = nbt.contains("version") ? Math.max(1, nbt.getInt("version")) : 1;
		recentContacts.clear();
		ListTag contactsList = nbt.getList("recent_contacts", Tag.TAG_COMPOUND);
		for (Tag entry : contactsList) {
			if (entry instanceof CompoundTag compound) {
				TransferContact contact = TransferContact.deserialize(compound);
				if (contact != null) {
					recentContacts.add(contact);
				}
			}
		}
		favoriteOfferIds.clear();
		ListTag favoritesList = nbt.getList("favorite_offers", Tag.TAG_STRING);
		for (Tag entry : favoritesList) {
			String id = entry.getAsString();
			if (id != null && !id.isBlank()) {
				favoriteOfferIds.add(id);
			}
			if (favoriteOfferIds.size() >= MAX_FAVORITE_OFFERS) {
				break;
			}
		}
		if (nbt.contains("mining_stats", Tag.TAG_COMPOUND)) {
			miningStats.deserialize(nbt.getCompound("mining_stats"));
		}
	}

	/** A player the owner has recently transferred funds to. */
	public record TransferContact(String name, long lastUsedEpochSeconds, long totalSentUsdtMinor, String lastMemo) {
		public TransferContact {
			if (name == null) {
				name = "";
			}
			if (lastMemo == null) {
				lastMemo = "";
			}
			if (totalSentUsdtMinor < 0L) {
				totalSentUsdtMinor = 0L;
			}
		}

		CompoundTag serialize() {
			CompoundTag tag = new CompoundTag();
			tag.putString("name", name);
			tag.putLong("last_used", lastUsedEpochSeconds);
			tag.putLong("total_sent_usdt", totalSentUsdtMinor);
			if (!lastMemo.isEmpty()) {
				tag.putString("memo", lastMemo);
			}
			return tag;
		}

		static TransferContact deserialize(CompoundTag tag) {
			String name = tag.getString("name");
			if (name == null || name.isBlank()) {
				return null;
			}
			return new TransferContact(
					name,
					tag.getLong("last_used"),
					tag.getLong("total_sent_usdt"),
					tag.contains("memo") ? tag.getString("memo") : "");
		}
	}

	/** Aggregate mining settlement statistics. */
	public static final class MiningStats {
		private long totalSettlements;
		private long totalVouchers;
		private long totalBtcMinor;
		private long firstSettleEpochSeconds;
		private long lastSettleEpochSeconds;
		private final EnumMap<CryptoCurrency, Long> payoutsByCurrencyMinor = new EnumMap<>(CryptoCurrency.class);
		private final List<DailyBucket> last7DayBuckets = new ArrayList<>();

		public long totalSettlements() {
			return totalSettlements;
		}

		public long totalVouchers() {
			return totalVouchers;
		}

		public long totalBtcMinor() {
			return totalBtcMinor;
		}

		public long firstSettleEpochSeconds() {
			return firstSettleEpochSeconds;
		}

		public long lastSettleEpochSeconds() {
			return lastSettleEpochSeconds;
		}

		public Map<CryptoCurrency, Long> payoutsByCurrencyMinor() {
			return Collections.unmodifiableMap(payoutsByCurrencyMinor);
		}

		public List<DailyBucket> last7DayBuckets() {
			return Collections.unmodifiableList(last7DayBuckets);
		}

		void record(CryptoCurrency payoutCurrency, long payoutMinor, long btcAmountMinor, int voucherCount) {
			long now = Instant.now().getEpochSecond();
			totalSettlements++;
			totalVouchers += Math.max(0, voucherCount);
			totalBtcMinor = Math.addExact(totalBtcMinor, Math.max(0L, btcAmountMinor));
			if (firstSettleEpochSeconds == 0L) {
				firstSettleEpochSeconds = now;
			}
			lastSettleEpochSeconds = now;
			if (payoutCurrency != null && payoutMinor > 0L) {
				payoutsByCurrencyMinor.merge(payoutCurrency, payoutMinor, Math::addExact);
			}
			rollupToday(now, btcAmountMinor, voucherCount);
		}

		private void rollupToday(long nowEpochSeconds, long btcAmountMinor, int voucherCount) {
			long dayStart = nowEpochSeconds - (nowEpochSeconds % 86400L);
			if (!last7DayBuckets.isEmpty() && last7DayBuckets.get(0).dayStartEpochSeconds() == dayStart) {
				DailyBucket head = last7DayBuckets.remove(0);
				last7DayBuckets.add(0, new DailyBucket(dayStart, head.btcMinor() + Math.max(0L, btcAmountMinor), head.voucherCount() + Math.max(0, voucherCount)));
			} else {
				last7DayBuckets.add(0, new DailyBucket(dayStart, Math.max(0L, btcAmountMinor), Math.max(0, voucherCount)));
			}
			while (last7DayBuckets.size() > 7) {
				last7DayBuckets.remove(last7DayBuckets.size() - 1);
			}
		}

		void copyFrom(MiningStats other) {
			totalSettlements = other.totalSettlements;
			totalVouchers = other.totalVouchers;
			totalBtcMinor = other.totalBtcMinor;
			firstSettleEpochSeconds = other.firstSettleEpochSeconds;
			lastSettleEpochSeconds = other.lastSettleEpochSeconds;
			payoutsByCurrencyMinor.clear();
			payoutsByCurrencyMinor.putAll(other.payoutsByCurrencyMinor);
			last7DayBuckets.clear();
			last7DayBuckets.addAll(other.last7DayBuckets);
		}

		CompoundTag serialize() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("total_settlements", totalSettlements);
			tag.putLong("total_vouchers", totalVouchers);
			tag.putLong("total_btc_minor", totalBtcMinor);
			tag.putLong("first_settle", firstSettleEpochSeconds);
			tag.putLong("last_settle", lastSettleEpochSeconds);
			CompoundTag payoutsTag = new CompoundTag();
			for (Map.Entry<CryptoCurrency, Long> entry : payoutsByCurrencyMinor.entrySet()) {
				if (entry.getValue() != null && entry.getValue() > 0L) {
					payoutsTag.putLong(entry.getKey().id(), entry.getValue());
				}
			}
			tag.put("payouts", payoutsTag);
			ListTag bucketsList = new ListTag();
			for (DailyBucket bucket : last7DayBuckets) {
				CompoundTag bucketTag = new CompoundTag();
				bucketTag.putLong("day", bucket.dayStartEpochSeconds());
				bucketTag.putLong("btc_minor", bucket.btcMinor());
				bucketTag.putInt("vouchers", bucket.voucherCount());
				bucketsList.add(bucketTag);
			}
			tag.put("daily_buckets", bucketsList);
			return tag;
		}

		void deserialize(CompoundTag tag) {
			totalSettlements = tag.getLong("total_settlements");
			totalVouchers = tag.getLong("total_vouchers");
			totalBtcMinor = tag.getLong("total_btc_minor");
			firstSettleEpochSeconds = tag.getLong("first_settle");
			lastSettleEpochSeconds = tag.getLong("last_settle");
			payoutsByCurrencyMinor.clear();
			if (tag.contains("payouts", Tag.TAG_COMPOUND)) {
				CompoundTag payoutsTag = tag.getCompound("payouts");
				for (String key : payoutsTag.getAllKeys()) {
					CryptoCurrency.byId(key.toLowerCase(Locale.ROOT)).ifPresent(currency -> {
						long value = payoutsTag.getLong(key);
						if (value > 0L) {
							payoutsByCurrencyMinor.put(currency, value);
						}
					});
				}
			}
			last7DayBuckets.clear();
			if (tag.contains("daily_buckets", Tag.TAG_LIST)) {
				ListTag bucketsList = tag.getList("daily_buckets", Tag.TAG_COMPOUND);
				for (Tag entry : bucketsList) {
					if (entry instanceof CompoundTag compound) {
						last7DayBuckets.add(new DailyBucket(
								compound.getLong("day"),
								compound.getLong("btc_minor"),
								compound.getInt("vouchers")));
					}
					if (last7DayBuckets.size() >= 7) {
						break;
					}
				}
			}
		}

		public record DailyBucket(long dayStartEpochSeconds, long btcMinor, int voucherCount) {
		}
	}
}
