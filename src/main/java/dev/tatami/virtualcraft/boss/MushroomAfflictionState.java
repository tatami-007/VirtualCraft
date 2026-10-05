package dev.tatami.virtualcraft.boss;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-encounter ownership. Absolute tick deadlines; no additive control durations. */
public final class MushroomAfflictionState {
    public record Active(int level, int rotation, long until) {}
    public record Control(long snareUntil, long rotationUntil, int degrees,long dragUntil,float movementScale) {}
    private record Source(UUID encounter, int level) {}
    private final Map<MushroomAfflictionType, Map<Source, Active>> sources = new EnumMap<>(MushroomAfflictionType.class);
    private long snareUntil, snareImmuneUntil;

    public void prune(long now) {
        if (snareUntil > 0 && now >= snareUntil) {
            snareImmuneUntil = Math.max(snareImmuneUntil, snareUntil + 8);
            snareUntil = 0;
        }
        sources.values().forEach(entries -> entries.values().removeIf(e -> e.until() <= now));
    }

    public boolean apply(UUID source, MushroomAfflictionType type, int duration, int level, int rotation, long now) {
        prune(now);
        if (duration <= 0) return false;
        if (type == MushroomAfflictionType.MYCELIAL_SNARE) {
            if (snareUntil > now || now < snareImmuneUntil) return false;
            duration = Math.min(8, duration);
            snareUntil = now + duration;
        }
        var entries = sources.computeIfAbsent(type, ignored -> new HashMap<>());
        var key = new Source(source, level);
        var old = entries.get(key);
        // Separate strength lifetimes: a weaker refresh cannot extend a stronger
        // debuff, and remains available when that stronger application expires.
        entries.put(key, new Active(level, old == null ? rotation : old.rotation(),
                Math.max(now + duration, old == null ? 0 : old.until())));
        return true;
    }

    public Active active(MushroomAfflictionType type, long now) {
        Active result = null;
        for (var entry : sources.getOrDefault(type, Map.of()).values()) {
            if (entry.until() <= now) continue;
            if (result == null || entry.level() > result.level()
                    || entry.level() == result.level() && entry.until() > result.until()) result = entry;
        }
        return result;
    }

    public Control control(long now) {
        var root = active(MushroomAfflictionType.MYCELIAL_SNARE, now);
        var reverse = active(MushroomAfflictionType.SPORE_REVERSAL, now);
        var side = active(MushroomAfflictionType.SPORE_MISDIRECTION, now);
        var drag = active(MushroomAfflictionType.SPORE_DRAG, now);
        var rotation = reverse != null ? reverse : side;
        return new Control(root == null ? 0 : root.until(), rotation == null ? 0 : rotation.until(),
                reverse != null ? 180 : side == null ? 0 : side.rotation(),drag==null?0:drag.until(),
                drag==null?1:MushroomControlRules.dragMovementScale(drag.level()));
    }

    public void clear(MushroomAfflictionType type, long now) {
        if (type == MushroomAfflictionType.MYCELIAL_SNARE && snareUntil > now) {
            snareImmuneUntil = now + 8;
            snareUntil = 0;
        }
        sources.remove(type);
    }

    public void removeSource(UUID source, long now) {
        boolean hadRoot = active(MushroomAfflictionType.MYCELIAL_SNARE, now) != null;
        sources.values().forEach(entries -> entries.keySet().removeIf(key -> key.encounter().equals(source)));
        if (hadRoot && active(MushroomAfflictionType.MYCELIAL_SNARE, now) == null) {
            snareUntil = 0;
            snareImmuneUntil = now + 8;
        }
    }

    public boolean empty(long now) {
        return sources.values().stream().allMatch(Map::isEmpty) && now >= snareImmuneUntil;
    }
}
