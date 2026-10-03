package dev.tatami.virtualcraft.boss;

/** Player-wide input tokens: swapping items cannot renew an input or a broken guard. */
public final class MushroomDefenseState {
    public static final int JUMP_WINDOW = 10;
    public static final int PARRY_WINDOW = 15;
    public static final int BREAK_COOLDOWN = 100;
    public enum Guard {
        NONE(1), PRECISE(0), BROKEN(0.40F);
        private final float damageMultiplier;
        Guard(float multiplier) { damageMultiplier = multiplier; }
        public float damageMultiplier() { return damageMultiplier; }
    }
    public record Snapshot(long jumpAt, boolean jumpConsumed, long guardAt,
            boolean guardConsumed, long cooldownUntil, long jumpInvalidUntil) {}

    private long jumpAt = Long.MIN_VALUE / 2;
    private boolean jumpConsumed = true;
    private long guardAt = Long.MIN_VALUE / 2;
    private boolean guardConsumed = true;
    private long cooldownUntil;
    private long jumpInvalidUntil;

    public void jump(long now) {
        if (now < jumpInvalidUntil) return;
        jumpAt = now;
        jumpConsumed = false;
    }
    public void invalidateJump(long now) {
        jumpConsumed = true;
        jumpInvalidUntil = now + JUMP_WINDOW;
    }
    public boolean dodgeLow(long now, boolean airborne, boolean invalidMovement) {
        long age = now - jumpAt;
        if (!airborne || invalidMovement || jumpConsumed || now < jumpInvalidUntil || age < 0 || age > JUMP_WINDOW) return false;
        jumpConsumed = true;
        return true;
    }
    public boolean startGuard(long now) {
        if (coolingDown(now)) return false;
        guardAt = now;
        guardConsumed = false;
        return true;
    }
    public boolean coolingDown(long now) { return now < cooldownUntil; }
    public Guard guard(long now, boolean defending, double facingDot) {
        if (!defending || facingDot < 0.25 || coolingDown(now)) return Guard.NONE;
        long age = now - guardAt;
        if (!guardConsumed && age >= 0 && age < PARRY_WINDOW) {
            guardConsumed = true;
            return Guard.PRECISE;
        }
        guardConsumed = true;
        cooldownUntil = now + BREAK_COOLDOWN;
        return Guard.BROKEN;
    }
    public Snapshot snapshot() {
        return new Snapshot(jumpAt, jumpConsumed, guardAt, guardConsumed, cooldownUntil, jumpInvalidUntil);
    }
    public static MushroomDefenseState restore(Snapshot snapshot) {
        var state = new MushroomDefenseState();
        state.jumpAt = snapshot.jumpAt();
        state.jumpConsumed = snapshot.jumpConsumed();
        state.guardAt = snapshot.guardAt();
        state.guardConsumed = snapshot.guardConsumed();
        state.cooldownUntil = snapshot.cooldownUntil();
        state.jumpInvalidUntil = snapshot.jumpInvalidUntil();
        return state;
    }
}
