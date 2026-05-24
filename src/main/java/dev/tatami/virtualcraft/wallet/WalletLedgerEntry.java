package dev.tatami.virtualcraft.wallet;

import net.minecraft.nbt.CompoundTag;

/**
 * A single entry in the player's wallet ledger.
 *
 * <p>The historical {@code detail} field is intentionally kept for backwards
 * compatibility (it already encodes varied information such as
 * {@code "shares:7"}, an offer id, or an opposite-party player name).  When we
 * need to attach a free-form user note to a transaction (e.g. a transfer memo)
 * the new {@link #memo} field is used so we do not conflict with existing
 * semantics. Old save data without the {@code memo} NBT key deserializes to an
 * empty string.</p>
 */
public record WalletLedgerEntry(
		WalletLedgerType type,
		String primaryCurrencyId,
		long primaryAmountMinor,
		String secondaryCurrencyId,
		long secondaryAmountMinor,
		String detail,
		long timestampSeconds,
		String memo) {

	public WalletLedgerEntry {
		if (detail == null) {
			detail = "";
		}
		if (memo == null) {
			memo = "";
		}
	}

	/** Convenience overload that defaults {@link #memo} to empty. */
	public WalletLedgerEntry(
			WalletLedgerType type,
			String primaryCurrencyId,
			long primaryAmountMinor,
			String secondaryCurrencyId,
			long secondaryAmountMinor,
			String detail,
			long timestampSeconds) {
		this(type, primaryCurrencyId, primaryAmountMinor, secondaryCurrencyId, secondaryAmountMinor, detail, timestampSeconds, "");
	}

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", type.id());
		tag.putString("primary_currency", primaryCurrencyId);
		tag.putLong("primary_amount", primaryAmountMinor);
		tag.putString("secondary_currency", secondaryCurrencyId);
		tag.putLong("secondary_amount", secondaryAmountMinor);
		tag.putString("detail", detail);
		tag.putLong("timestamp", timestampSeconds);
		// Only persist the memo when the user supplied one; this keeps existing
		// save files byte-identical for unrelated entries.
		if (memo != null && !memo.isEmpty()) {
			tag.putString("memo", memo);
		}
		return tag;
	}

	public static WalletLedgerEntry deserialize(CompoundTag tag) {
		return new WalletLedgerEntry(
				WalletLedgerType.byId(tag.getString("type")),
				tag.getString("primary_currency"),
				tag.getLong("primary_amount"),
				tag.getString("secondary_currency"),
				tag.getLong("secondary_amount"),
				tag.getString("detail"),
				tag.getLong("timestamp"),
				tag.contains("memo") ? tag.getString("memo") : "");
	}
}
