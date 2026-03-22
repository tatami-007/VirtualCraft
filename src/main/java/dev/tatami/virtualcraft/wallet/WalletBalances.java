package dev.tatami.virtualcraft.wallet;

import java.util.EnumMap;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class WalletBalances implements INBTSerializable<CompoundTag> {
	private final EnumMap<CryptoCurrency, Long> balances = new EnumMap<>(CryptoCurrency.class);
	private final WalletLedger ledger = new WalletLedger();

	public WalletBalances() {
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			balances.put(currency, 0L);
		}
	}

	public long getMinorUnits(CryptoCurrency currency) {
		return balances.getOrDefault(currency, 0L);
	}

	public String getDisplayAmount(CryptoCurrency currency) {
		return currency.formatCompact(getMinorUnits(currency));
	}

	public WalletLedger ledger() {
		return ledger;
	}

	public void setMinorUnits(CryptoCurrency currency, long amount) {
		balances.put(currency, Math.max(0L, amount));
	}

	public long depositMinorUnits(CryptoCurrency currency, long amount) {
		if (amount <= 0) {
			return getMinorUnits(currency);
		}
		long updated = Math.addExact(getMinorUnits(currency), amount);
		setMinorUnits(currency, updated);
		return updated;
	}

	public long depositWholeItem(CryptoCurrency currency, long itemCount) {
		if (itemCount <= 0) {
			return getMinorUnits(currency);
		}
		return depositMinorUnits(currency, Math.multiplyExact(currency.wholeUnitMinor(), itemCount));
	}

	public boolean canAfford(CryptoCurrency currency, long amount) {
		return amount >= 0 && getMinorUnits(currency) >= amount;
	}

	public boolean withdrawMinorUnits(CryptoCurrency currency, long amount) {
		if (amount <= 0 || !canAfford(currency, amount)) {
			return false;
		}
		setMinorUnits(currency, getMinorUnits(currency) - amount);
		return true;
	}

	public boolean withdrawWholeItem(CryptoCurrency currency, long itemCount) {
		if (itemCount <= 0) {
			return false;
		}
		return withdrawMinorUnits(currency, Math.multiplyExact(currency.wholeUnitMinor(), itemCount));
	}

	public long[] snapshot() {
		CryptoCurrency[] currencies = CryptoCurrency.values();
		long[] snapshot = new long[currencies.length];
		for (int index = 0; index < currencies.length; index++) {
			snapshot[index] = getMinorUnits(currencies[index]);
		}
		return snapshot;
	}

	public void copyFrom(WalletBalances other) {
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			setMinorUnits(currency, other.getMinorUnits(currency));
		}
		ledger.copyFrom(other.ledger());
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			long amount = getMinorUnits(currency);
			if (amount > 0) {
				tag.putLong(currency.id(), amount);
			}
		}
		tag.put("ledger", ledger.serializeNBT(provider));
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			setMinorUnits(currency, nbt.getLong(currency.id()));
		}
		if (nbt.contains("ledger")) {
			ledger.deserializeNBT(provider, nbt.getCompound("ledger"));
		}
	}
}
