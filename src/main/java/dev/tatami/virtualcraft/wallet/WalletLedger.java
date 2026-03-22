package dev.tatami.virtualcraft.wallet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class WalletLedger implements INBTSerializable<CompoundTag> {
	private static final int MAX_ENTRIES = 32;
	private final List<WalletLedgerEntry> entries = new ArrayList<>();

	public List<WalletLedgerEntry> entries() {
		return List.copyOf(entries);
	}

	public List<WalletLedgerEntry> recent(int limit) {
		return entries.subList(0, Math.min(limit, entries.size()));
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
