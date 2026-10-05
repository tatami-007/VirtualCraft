package dev.tatami.virtualcraft.boss;

/** Delivery is independent of the posture required to defend against an attack. */
public enum MushroomAttackForm {
    MELEE, HUNTING_CORE, ROOT_SPIKES, HIGH_TENDRIL;

    public boolean ranged() { return this != MELEE; }
}
