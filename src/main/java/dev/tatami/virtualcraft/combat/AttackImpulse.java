package dev.tatami.virtualcraft.combat;

/** Velocity impulse in blocks/tick, before resistance. Zero vertical preserves existing vertical motion. */
public record AttackImpulse(float horizontal, float vertical, ImpactDirection direction) {
    public AttackImpulse {
        if (!Float.isFinite(horizontal) || !Float.isFinite(vertical) || horizontal<0 || vertical<0 || direction==null)
            throw new IllegalArgumentException("Invalid attack impulse");
    }
    public AttackImpulse reduced(double multiplier,double knockbackResistance) {
        double factor=Math.max(0,multiplier)*Math.clamp(1-knockbackResistance,0,1);
        return new AttackImpulse((float)(horizontal*factor),(float)(vertical*factor),direction);
    }
}
