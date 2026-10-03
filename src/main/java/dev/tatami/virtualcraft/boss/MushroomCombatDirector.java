package dev.tatami.virtualcraft.boss;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure tick state machine: the entity supplies targets, geometry and actual damage. */
public final class MushroomCombatDirector {
    public enum Mode { IDLE, COMBO, ULTIMATE }
    public record IndexedHit(int index, MushroomCombatRules.Hit hit) {}
    public record TickResult(List<IndexedHit> hits, int ultimateToResolve) {}
    public record Snapshot(int triggered, int resolved, boolean needsCombo, int activeUltimate,
            int lastCombo, long[] readyAt) {
        public Snapshot { readyAt = readyAt.clone(); }
        @Override public long[] readyAt() { return readyAt.clone(); }
    }
    private record HitKey(int strike, UUID player) {}

    private Mode mode = Mode.IDLE;
    private int elapsed;
    private int combo = -1;
    private int lastCombo = -1;
    private int ultimate = -1;
    private int triggered;
    private int resolved;
    private boolean needsCombo;
    private final long[] readyAt = new long[MushroomCombatRules.COMBOS.size()];
    private final Set<HitKey> handled = new HashSet<>();

    public Mode mode() { return mode; }
    public int elapsed() { return elapsed; }
    public int combo() { return combo; }
    public int ultimate() { return ultimate; }
    public boolean needsCombo() { return needsCombo; }

    public void observeHealth(float health) {
        if (health <= 210) triggered |= 1;
        if (health <= 105) triggered |= 2;
    }

    public int pendingUltimate() {
        if (needsCombo) return -1;
        int pending = triggered & ~resolved;
        return (pending & 1) != 0 ? 0 : (pending & 2) != 0 ? 1 : -1;
    }

    public int select(float health, long now, double distance, double height, boolean visible, double random) {
        if (mode != Mode.IDLE) return -1;
        int phase = MushroomCombatRules.phase(health);
        int total = 0;
        for (var candidate : MushroomCombatRules.COMBOS) {
            if (eligible(candidate, now, distance, height, visible)) total += MushroomCombatRules.weight(phase, candidate.id());
        }
        if (total == 0) return -1;
        double roll = Math.max(0, Math.min(Math.nextDown(1.0), random)) * total;
        for (var candidate : MushroomCombatRules.COMBOS) {
            if (!eligible(candidate, now, distance, height, visible)) continue;
            roll -= MushroomCombatRules.weight(phase, candidate.id());
            if (roll < 0) return candidate.id();
        }
        return -1;
    }

    private boolean eligible(MushroomCombatRules.Combo candidate, long now, double distance, double height, boolean visible) {
        return candidate.id() != lastCombo && now >= readyAt[candidate.id()]
                && MushroomCombatRules.canStart(candidate, distance, height, visible);
    }

    public void beginCombo(int id) {
        if (mode != Mode.IDLE || id < 0 || id >= readyAt.length) throw new IllegalStateException("Invalid combo start");
        mode = Mode.COMBO;
        combo = lastCombo = id;
        elapsed = 0;
        handled.clear();
    }

    public void beginUltimate() {
        int pending = pendingUltimate();
        if (mode != Mode.IDLE || pending < 0) throw new IllegalStateException("No ready ultimate");
        mode = Mode.ULTIMATE;
        ultimate = pending;
        elapsed = 0;
        handled.clear();
    }

    /** Called once per subsequent server tick, not on the start tick. */
    public TickResult advance(long now) {
        if (mode == Mode.IDLE) return new TickResult(List.of(), -1);
        elapsed++;
        List<IndexedHit> hits = new ArrayList<>();
        int release = -1;
        if (mode == Mode.COMBO) {
            var definition = MushroomCombatRules.COMBOS.get(combo);
            var sequence = definition.hits();
            for (int i = 0; i < sequence.size(); i++) {
                var hit = sequence.get(i);
                if (elapsed >= hit.tick() && elapsed < hit.tick() + MushroomCombatRules.HIT_WINDOW) {
                    hits.add(new IndexedHit(i, hit));
                }
            }
            if (elapsed >= definition.duration()) {
                readyAt[combo] = now + MushroomCombatRules.COMBO_COOLDOWN;
                needsCombo = false;
                mode = Mode.IDLE;
                combo = -1;
            }
        } else {
            if (elapsed == MushroomCombatRules.ULTIMATE_WINDUP && (resolved & (1 << ultimate)) == 0) {
                resolved |= 1 << ultimate;
                needsCombo = true;
                release = ultimate;
            }
            if (elapsed >= MushroomCombatRules.ULTIMATE_WINDUP + MushroomCombatRules.ULTIMATE_RECOVERY) {
                mode = Mode.IDLE;
                ultimate = -1;
            }
        }
        return new TickResult(List.copyOf(hits), release);
    }

    public boolean handleOnce(int strike, UUID player) { return handled.add(new HitKey(strike, player)); }

    public Snapshot snapshot() {
        return new Snapshot(triggered, resolved, needsCombo, ultimate, lastCombo, readyAt);
    }

    public void restore(Snapshot snapshot) {
        reset();
        triggered = snapshot.triggered() & 3;
        resolved = snapshot.resolved() & triggered;
        needsCombo = snapshot.needsCombo();
        lastCombo = snapshot.lastCombo() >= 0 && snapshot.lastCombo() < readyAt.length ? snapshot.lastCombo() : -1;
        long[] cooldowns = snapshot.readyAt();
        if (cooldowns.length == readyAt.length) System.arraycopy(cooldowns, 0, readyAt, 0, readyAt.length);
        int active = snapshot.activeUltimate();
        if (active >= 0 && active <= 1 && (triggered & (1 << active)) != 0 && (resolved & (1 << active)) == 0) {
            mode = Mode.ULTIMATE;
            ultimate = active; // Restart uncommitted windup, retain its locked targets in the entity.
        }
    }

    public void reset() {
        mode = Mode.IDLE;
        elapsed = triggered = resolved = 0;
        combo = lastCombo = ultimate = -1;
        needsCombo = false;
        Arrays.fill(readyAt, 0);
        handled.clear();
    }
}
