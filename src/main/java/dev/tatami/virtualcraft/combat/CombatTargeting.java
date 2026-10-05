package dev.tatami.virtualcraft.combat;

/** Shared geometry only. An attack's form, not HIGH/LOW, chooses whether this sector applies. */
public final class CombatTargeting {
    private CombatTargeting() {}
    public static boolean validParticipant(boolean alive,boolean creative,boolean spectator,boolean sameLevel) {
        return alive && !creative && !spectator && sameLevel;
    }
    public static boolean sector(double dx,double dz,double height,double fx,double fz,boolean visible,
            double radius,double halfAngleDegrees,double maxHeight) {
        if (!visible || !Double.isFinite(dx) || !Double.isFinite(dz) || !Double.isFinite(height)
                || Math.abs(height)>maxHeight) return false;
        double distance=Math.hypot(dx,dz);
        if (distance>radius+1e-9) return false;
        if (distance<1e-6) return true;
        double forward=Math.hypot(fx,fz);
        return forward>=1e-6 && (dx*fx+dz*fz)/(distance*forward)+1e-9>=Math.cos(Math.toRadians(halfAngleDegrees));
    }
}
