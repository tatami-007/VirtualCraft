package dev.tatami.virtualcraft.combat;

import java.util.UUID;

/** Stable identity for delayed hits. Never reuse a previous encounter's generation. */
public record AttackKey(UUID encounter, int comboSequence, int strike, UUID target) {
    public AttackKey {
        java.util.Objects.requireNonNull(encounter);
        java.util.Objects.requireNonNull(target);
    }
}
