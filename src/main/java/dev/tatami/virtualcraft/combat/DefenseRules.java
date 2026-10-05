package dev.tatami.virtualcraft.combat;

/** Tick windows and equipment costs supplied by the attack owner, not by its renderer. */
public record DefenseRules(int jumpWindow, int parryWindow, double facingDot,
        int breakCooldown, float failedGuardMultiplier, int preciseWear, int failedWear) {
    public static final DefenseRules STANDARD = new DefenseRules(10,15,.25,100,.40F,1,2);
    public DefenseRules {
        if (jumpWindow<0 || parryWindow<0 || breakCooldown<0 || preciseWear<0 || failedWear<0
                || !Double.isFinite(facingDot) || facingDot<-1 || facingDot>1
                || !Float.isFinite(failedGuardMultiplier) || failedGuardMultiplier<0 || failedGuardMultiplier>1)
            throw new IllegalArgumentException("Invalid combat defense rules");
    }
}
