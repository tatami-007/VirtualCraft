package dev.tatami.virtualcraft.boss;

import java.util.List;

/** Server-authoritative, animation-independent first encounter tuning. Times are ticks. */
public final class MushroomCombatRules {
    public static final float MAX_HEALTH = 300;
    public static final int HIT_WINDOW = 2;
    public static final int COMBO_COOLDOWN = 200;
    public static final int AIM_TICKS = 12;
    public static final int DISENGAGE_TICKS = 200;
    public static final int ULTIMATE_WINDUP = 80;
    public static final int ULTIMATE_RECOVERY = 40;
    public static final double ACQUIRE_RANGE = 24;
    public static final double MAX_HEIGHT_DIFFERENCE = 3;

    public record Hit(MushroomAttackType type, int tick, float damage) {}
    public record Combo(int id, String rhythm, List<List<Hit>> groups, int recoveryTicks) {
        public Combo {
            groups = groups.stream().map(List::copyOf).toList();
        }
        public List<Hit> hits() { return groups.stream().flatMap(List::stream).toList(); }
        public int duration() { return hits().getLast().tick() + recoveryTicks; }
        public double range() { return hits().getFirst().type() == MushroomAttackType.MID ? 4 : 6; }
    }

    private static Hit low(int tick, float damage) { return new Hit(MushroomAttackType.LOW, tick, damage); }
    private static Hit high(int tick, float damage) { return new Hit(MushroomAttackType.HIGH, tick, damage); }
    private static Hit mid(int tick, float damage) { return new Hit(MushroomAttackType.MID, tick, damage); }

    public static final List<Combo> COMBOS = List.of(
            new Combo(0, "下下 下", List.of(List.of(low(24,18), low(42,18)), List.of(low(78,24))), 28),
            new Combo(1, "下下下", List.of(List.of(low(24,18), low(42,18), low(60,24))), 28),
            new Combo(2, "下下下上", List.of(List.of(low(24,18), low(42,18), low(60,22), high(78,26))), 32),
            new Combo(3, "下下 下 下", List.of(List.of(low(24,18), low(42,18)), List.of(low(78,22)), List.of(low(114,26))), 32),
            new Combo(4, "下下 下上", List.of(List.of(low(24,18), low(42,18)), List.of(low(78,22), high(96,26))), 32),
            new Combo(5, "中中 中", List.of(List.of(mid(24,20), mid(42,20)), List.of(mid(78,26))), 28),
            new Combo(6, "中中中", List.of(List.of(mid(24,20), mid(42,20), mid(60,26))), 28));

    private static final int[][] WEIGHTS = {
            {25,25,10,10,10,10,10}, {15,20,15,10,15,10,15}, {10,15,20,15,20,10,10}};

    private MushroomCombatRules() {}

    public static int phase(float health) { return health > 210 ? 0 : health > 105 ? 1 : 2; }
    public static int weight(int phase, int combo) { return WEIGHTS[phase][combo]; }
    public static float ultimateDamage(int index) { return index == 0 ? 40 : 48; }
    public static boolean canAim(int elapsed) { return elapsed < AIM_TICKS; }

    public static boolean canStart(Combo combo, double distance, double height, boolean visible) {
        return visible && distance <= combo.range() && Math.abs(height) <= MAX_HEIGHT_DIFFERENCE;
    }

    /** Horizontal sectors intentionally do not use the static model's arm positions. */
    public static boolean inHitVolume(MushroomAttackType type, double dx, double dz,
            double height, double forwardX, double forwardZ, boolean visible) {
        if (!visible || Math.abs(height) > MAX_HEIGHT_DIFFERENCE) return false;
        double range = type == MushroomAttackType.MID ? 4 : 6;
        double length = Math.hypot(dx, dz);
        if (length > range + 1.0e-9) return false;
        if (length < 1.0e-6) return true;
        double forwardLength = Math.hypot(forwardX, forwardZ);
        if (forwardLength < 1.0e-6) return false;
        double cosine = (dx * forwardX + dz * forwardZ) / (length * forwardLength);
        return cosine + 1.0e-9 >= Math.cos(Math.toRadians(type == MushroomAttackType.MID ? 40 : 60));
    }

    public static boolean validParticipant(boolean alive, boolean creative, boolean spectator, boolean sameLevel) {
        return alive && !creative && !spectator && sameLevel;
    }
}
