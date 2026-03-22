package dev.tatami.virtualcraft.wallet;

import net.minecraft.nbt.CompoundTag;

public record WalletLedgerEntry(
		WalletLedgerType type,
		String primaryCurrencyId,
		long primaryAmountMinor,
		String secondaryCurrencyId,
		long secondaryAmountMinor,
		String detail,
		long timestampSeconds) {

	public CompoundTag serialize() {
		CompoundTag tag = new CompoundTag();
		tag.putString("type", type.id());
		tag.putString("primary_currency", primaryCurrencyId);
		tag.putLong("primary_amount", primaryAmountMinor);
		tag.putString("secondary_currency", secondaryCurrencyId);
		tag.putLong("secondary_amount", secondaryAmountMinor);
		tag.putString("detail", detail);
		tag.putLong("timestamp", timestampSeconds);
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
				tag.getLong("timestamp"));
	}
}
