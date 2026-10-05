package dev.tatami.virtualcraft.boss;

import java.util.UUID;

/** Short-lived target motion history. Never changes damage, defence or an active attack's clock. */
public final class MushroomPursuitState {
    private UUID target;
    private long sampledAt = Long.MIN_VALUE;
    private double lastX, lastZ, lastBearing;
    private double velocityX, velocityZ;
    private int orbitTicks, elevatedTicks, blockedTicks;
    private int orbitDirection;
    private long counterUntil;
    private boolean elevatedSupport;

    public void sample(UUID id, long now, double x, double z, double bossX, double bossZ,
            double height, boolean grounded) {
        if (!id.equals(target)) {
            reset();
            target = id;
        }
        double dx = x - bossX, dz = z - bossZ;
        double distance = Math.hypot(dx, dz);
        double bearing = Math.toDegrees(Math.atan2(dz, dx));
        if (sampledAt == now - 1) {
            double mx = x - lastX, mz = z - lastZ;
            double speed = Math.hypot(mx, mz);
            if (speed <= 1) {
                velocityX += (mx - velocityX) * 0.4;
                velocityZ += (mz - velocityZ) * 0.4;
                double angle = wrap(bearing - lastBearing);
                double tangential = distance < 0.01 ? 0 : Math.abs(dx * mz - dz * mx) / distance;
                int direction = angle < 0 ? -1 : 1;
                boolean orbiting = distance <= 10 && tangential >= 0.12 && Math.abs(angle) >= 1.5;
                if (orbiting) {
                    orbitTicks = orbitDirection == direction ? orbitTicks + 1 : 1;
                    orbitDirection = direction;
                } else orbitTicks = Math.max(0, orbitTicks - 2);
            } else {
                // Teleports and network discontinuities are not evidence of circling.
                velocityX = velocityZ = 0;
                orbitTicks = 0;
            }
        } else {
            velocityX = velocityZ = 0;
            orbitTicks = 0;
        }
        boolean raised = Math.abs(height) > MushroomCombatRules.MAX_HEIGHT_DIFFERENCE;
        if (grounded || !raised) elevatedSupport = grounded && raised;
        // Jumping repeatedly on top of a pillar must not erase its known support
        // height. A normal jump from the arena floor never sets this flag.
        elevatedTicks = raised && (grounded || elevatedSupport)
                ? elevatedTicks + 1 : Math.max(0, elevatedTicks - 2);
        if (orbitTicks >= 12 || elevatedTicks >= 12) counterUntil = now + 60;
        lastX = x; lastZ = z; lastBearing = bearing; sampledAt = now;
    }

    public void navigationProgress(long now, boolean pursuing, boolean routeFailed, double movement) {
        if (!pursuing) {
            blockedTicks = Math.max(0, blockedTicks - 2);
            return;
        }
        blockedTicks = routeFailed || movement < 0.015 ? blockedTicks + 1 : Math.max(0, blockedTicks - 3);
        if (blockedTicks >= 30) counterUntil = now + 60;
    }

    public boolean counterRanged(long now) { return now < counterUntil; }
    public double speed() { return Math.hypot(velocityX, velocityZ); }
    public double leadX(double ticks) { return velocityX * ticks * leadScale(ticks); }
    public double leadZ(double ticks) { return velocityZ * ticks * leadScale(ticks); }
    private double leadScale(double ticks) { return Math.min(1, 2.5 / Math.max(0.001, speed() * ticks)); }

    public static double wrap(double angle) { return (angle % 360 + 540) % 360 - 180; }
    public static float turn(float current, float wanted, float limit) {
        return (float)wrap(current + Math.clamp(wrap(wanted - current), -limit, limit));
    }

    public void reset() {
        target = null;
        sampledAt = Long.MIN_VALUE;
        velocityX = velocityZ = 0;
        orbitTicks = elevatedTicks = blockedTicks = orbitDirection = 0;
        counterUntil = 0;
        elevatedSupport = false;
    }
}
