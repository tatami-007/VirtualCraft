package dev.tatami.virtualcraft.wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class WalletLedger implements INBTSerializable<CompoundTag> {
	/**
	 * Maximum ledger depth kept on disk.  Historically this was 32, but the
	 * terminal now exposes pageable / filterable history so we keep a richer
	 * window.  Older saves containing fewer entries load as-is; newer saves
	 * with more than this are trimmed to the cap on the next mutation.
	 */
	public static final int MAX_ENTRIES = 128;
	private final List<WalletLedgerEntry> entries = new ArrayList<>();

	public List<WalletLedgerEntry> entries() {
		return List.copyOf(entries);
	}

	public List<WalletLedgerEntry> recent(int limit) {
		return List.copyOf(entries.subList(0, Math.min(Math.max(0, limit), entries.size())));
	}

	public int size() {
		return entries.size();
	}

	/**
	 * Return a filtered page of ledger entries.  Pass {@code null} for either
	 * filter to disable it. {@code offset} is clamped to the filtered result
	 * count so callers cannot drive past the end by accident.
	 */
	public List<WalletLedgerEntry> page(int offset, int limit, Set<WalletLedgerType> typeFilter, Set<String> currencyFilter) {
		if (limit <= 0) {
			return List.of();
		}
		List<WalletLedgerEntry> filtered = filter(typeFilter, currencyFilter);
		int safeOffset = Math.min(Math.max(0, offset), filtered.size());
		int endExclusive = Math.min(filtered.size(), safeOffset + limit);
		return List.copyOf(filtered.subList(safeOffset, endExclusive));
	}

	public int countMatching(Set<WalletLedgerType> typeFilter, Set<String> currencyFilter) {
		return filter(typeFilter, currencyFilter).size();
	}

	private List<WalletLedgerEntry> filter(Set<WalletLedgerType> typeFilter, Set<String> currencyFilter) {
		if ((typeFilter == null || typeFilter.isEmpty()) && (currencyFilter == null || currencyFilter.isEmpty())) {
			return entries;
		}
		List<WalletLedgerEntry> filtered = new ArrayList<>(entries.size());
		for (WalletLedgerEntry entry : entries) {
			if (typeFilter != null && !typeFilter.isEmpty() && !typeFilter.contains(entry.type())) {
				continue;
			}
			if (currencyFilter != null && !currencyFilter.isEmpty() && !matchesCurrency(entry, currencyFilter)) {
				continue;
			}
			filtered.add(entry);
		}
		return filtered;
	}

	private static boolean matchesCurrency(WalletLedgerEntry entry, Set<String> currencyFilter) {
		String primary = entry.primaryCurrencyId() == null ? "" : entry.primaryCurrencyId().toLowerCase(Locale.ROOT);
		String secondary = entry.secondaryCurrencyId() == null ? "" : entry.secondaryCurrencyId().toLowerCase(Locale.ROOT);
		for (String allowed : currencyFilter) {
			String lower = allowed == null ? "" : allowed.toLowerCase(Locale.ROOT);
			if (lower.isBlank()) {
				continue;
			}
			if (lower.equals(primary) || lower.equals(secondary)) {
				return true;
			}
		}
		return false;
	}

	public void add(WalletLedgerEntry entry) {
		entries.add(0, entry);
		while (entries.size() > MAX_ENTRIES) {
			entries.remove(entries.size() - 1);
		}
	}

	public void copyFrom(WalletLedger other) {
		entries.clear();
		entries.addAll(other.entries);
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (WalletLedgerEntry entry : entries) {
			list.add(entry.serialize());
		}
		tag.put("entries", list);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		entries.clear();
		ListTag list = nbt.getList("entries", Tag.TAG_COMPOUND);
		for (Tag tag : list) {
			if (tag instanceof CompoundTag compound) {
				entries.add(WalletLedgerEntry.deserialize(compound));
			}
		}
		while (entries.size() > MAX_ENTRIES) {
			entries.remove(entries.size() - 1);
		}
	}
}
