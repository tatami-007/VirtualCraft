package dev.tatami.virtualcraft.boss;

import java.util.UUID;
import dev.tatami.virtualcraft.combat.AttackKey;
import dev.tatami.virtualcraft.combat.HitLedger;

/** A launched strike owns its deadline and consumption, independently of the next combo. */
public final class MushroomRangedStrike {
    private final AttackKey key;
    private final long arrival;
    private final HitLedger<AttackKey> handled = new HitLedger<>();

    public MushroomRangedStrike(AttackKey key, long releasedAt, double distance) {
        this.key = key;
        this.arrival = releasedAt + travelTicks(distance);
    }

    public static int travelTicks(double distance) {
        return (int)Math.clamp(Math.ceil(Math.max(0, distance) / 0.8), 6, 24);
    }

    public AttackKey key() { return key; }
    public long arrival() { return arrival; }
    public boolean expired(long now) { return now >= arrival + MushroomCombatRules.HIT_WINDOW; }

    /** Geometry intentionally is not an input: only the locked player's validity and defence matter. */
    public boolean handleOnce(long now, UUID currentEncounter, UUID target, boolean valid) {
        if (!valid || !key.encounter().equals(currentEncounter) || !key.target().equals(target)
                || now < arrival || expired(now)) return false;
        return handled.consume(key);
    }
}
