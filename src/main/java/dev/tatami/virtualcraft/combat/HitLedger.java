package dev.tatami.virtualcraft.combat;

import java.util.HashSet;
import java.util.Set;

/** A caller-owned attack/encounter ledger. Consume BEFORE defense, including successful dodges. */
public final class HitLedger<K> {
    private final Set<K> consumed = new HashSet<>();
    public boolean consume(K key) { return consumed.add(java.util.Objects.requireNonNull(key)); }
    public void clear() { consumed.clear(); }
}
