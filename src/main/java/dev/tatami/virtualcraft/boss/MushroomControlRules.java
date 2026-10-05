package dev.tatami.virtualcraft.boss;

/** Transforms horizontal intent only; never contains jump, sneak or camera state. */
public final class MushroomControlRules {
    public record Movement(float forward, float left) {
        public Movement {
            if(forward==0) forward=0;
            if(left==0) left=0;
        }
    }
    private MushroomControlRules() {}

    public static Movement movement(float forward,float left,boolean snared,int rotation) {
        return movement(forward,left,snared,rotation,1);
    }

    public static Movement movement(float forward,float left,boolean snared,int rotation,float movementScale) {
        if(snared) return new Movement(0,0);
        // Vanilla normalizes diagonal intent later. Normalize before attenuation,
        // otherwise diagonal input can partly bypass a movement restriction.
        // Reversal alone is slower; a simultaneous stronger drag wins, rather than multiplying slows.
        float factor=Math.min(Math.clamp(movementScale,0,1),rotation==180?.60F:1F);
        if(factor<1) {
            factor/=(float)Math.max(1,Math.hypot(forward,left));
            forward*=factor; left*=factor;
        }
        return switch(rotation) {
            case 180 -> new Movement(-forward,-left);
            case 90 -> new Movement(-left,forward);
            case -90 -> new Movement(left,-forward);
            default -> new Movement(forward,left);
        };
    }

    public static float dragMovementScale(int level) { return level==0?.15F:.05F; }
}
