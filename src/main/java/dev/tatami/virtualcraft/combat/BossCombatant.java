package dev.tatami.virtualcraft.combat;

/** Attack owners resolve defense before hurting players; generic weapon hooks must not resolve it again. */
public interface BossCombatant {
    default DefenseRules defenseRules() { return DefenseRules.STANDARD; }
}
