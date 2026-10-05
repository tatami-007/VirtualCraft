package dev.tatami.virtualcraft.boss;

import java.util.List;
import dev.tatami.virtualcraft.combat.AttackType;
import dev.tatami.virtualcraft.combat.AttackImpulse;
import dev.tatami.virtualcraft.combat.ImpactDirection;

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
    public static final double COUNTER_RANGED_HEIGHT = 24;
    public static final double START_FACING_TOLERANCE = 30;

    public record SelectionContext(double distance, double height, boolean visible,
            double facingError, boolean counterRanged) {}

    public record Affliction(MushroomAfflictionType type, int ticks, int level) {}
    public record Hit(AttackType type, int tick, float damage, MushroomAttackForm form,
            AttackImpulse impulse, List<Affliction> afflictions) {
        public Hit { afflictions = List.copyOf(afflictions); }
        public Hit(AttackType type, int tick, float damage, MushroomAttackForm form) {
            this(type, tick, damage, form, new AttackImpulse(0,0,ImpactDirection.RADIAL), List.of());
        }
    }
    public record Combo(int id, String rhythm, List<List<Hit>> groups, int recoveryTicks) {
        public Combo {
            groups = groups.stream().map(List::copyOf).toList();
        }
        public List<Hit> hits() { return groups.stream().flatMap(List::stream).toList(); }
        public int duration() { return hits().getLast().tick() + recoveryTicks; }
        public double range() { return pureRanged() ? 18 : id >= 5 ? 4 : 6; }
        public boolean pureRanged() { return id == 1 || id == 3; }
        public boolean hasRanged() { return hits().stream().anyMatch(hit -> hit.form().ranged()); }
        public String nameKey() { return "skill.virtual_craft.mushroom_king." + id; }
        public String animationId() { return "animation.mushroom_king.combo_" + id; }
    }

    private static Affliction drag(int ticks, int level) { return new Affliction(MushroomAfflictionType.SPORE_DRAG,ticks,level); }
    private static Affliction snare(int ticks) { return new Affliction(MushroomAfflictionType.MYCELIAL_SNARE,ticks,0); }
    private static Affliction side(int ticks) { return new Affliction(MushroomAfflictionType.SPORE_MISDIRECTION,ticks,0); }
    private static Affliction reverse(int ticks) { return new Affliction(MushroomAfflictionType.SPORE_REVERSAL,ticks,0); }
    private static Hit hit(AttackType type, MushroomAttackForm form, int tick, float damage,
            float horizontal, float vertical, ImpactDirection direction, Affliction... effects) {
        return new Hit(type,tick,damage,form,new AttackImpulse(horizontal,vertical,direction),List.of(effects));
    }
    private static Hit low(int tick,float damage,float h,float v,boolean left,Affliction... effects) {
        return hit(AttackType.LOW,MushroomAttackForm.MELEE,tick,damage,h,v,
                left?ImpactDirection.LEFT_SWEEP:ImpactDirection.RIGHT_SWEEP,effects);
    }
    private static Hit high(int tick,float damage,float h,ImpactDirection direction,Affliction... effects) {
        return hit(AttackType.HIGH,MushroomAttackForm.HIGH_TENDRIL,tick,damage,h,0,direction,effects);
    }
    private static Hit mid(int tick,float damage,float h,float v,ImpactDirection direction) {
        return hit(AttackType.MID,MushroomAttackForm.MELEE,tick,damage,h,v,direction);
    }
    private static Hit core(int tick,float damage,float h,float v,Affliction... effects) {
        return hit(AttackType.LOW,MushroomAttackForm.HUNTING_CORE,tick,damage,h,v,ImpactDirection.RADIAL,effects);
    }
    private static Hit root(int tick,float damage,float h,float v,int snareTicks,int slowTicks,int level) {
        return hit(AttackType.LOW,MushroomAttackForm.ROOT_SPIKES,tick,damage,h,v,ImpactDirection.RADIAL,
                snare(snareTicks),drag(slowTicks,level));
    }

    public static final List<Combo> COMBOS = List.of(
            new Combo(0, "下下 下", List.of(List.of(low(24,18,.18F,0,true), low(42,18,.18F,0,false)), List.of(low(78,24,.48F,.08F,true,drag(40,0)))), 28),
            new Combo(1, "下下下", List.of(List.of(core(24,14,.12F,0,drag(40,0)), core(42,14,.12F,0,drag(40,0)), core(60,18,.28F,.06F,side(40)))), 28),
            new Combo(2, "下下下上", List.of(List.of(low(24,16,.16F,0,true), low(42,16,.16F,0,false), low(60,18,.24F,.04F,true), high(78,22,.20F,ImpactDirection.SIDE,reverse(60)))), 32),
            new Combo(3, "下下 下 下", List.of(List.of(root(24,12,.10F,0,5,40,0), root(42,12,.10F,0,5,40,0)), List.of(root(78,16,.16F,0,6,40,0)), List.of(root(114,20,.24F,.04F,8,60,1))), 32),
            new Combo(4, "下下 下上", List.of(List.of(low(24,16,.16F,0,true), low(42,16,.16F,0,false)), List.of(root(78,14,.14F,0,6,40,0), high(96,20,.22F,ImpactDirection.PULL,side(60)))), 32),
            new Combo(5, "中中 中", List.of(List.of(mid(24,20,.24F,.04F,ImpactDirection.RADIAL), mid(42,20,.24F,.04F,ImpactDirection.RADIAL)), List.of(mid(78,28,.75F,.16F,ImpactDirection.RADIAL))), 28),
            new Combo(6, "中中中", List.of(List.of(mid(24,18,.20F,.02F,ImpactDirection.RADIAL), mid(42,18,.20F,.02F,ImpactDirection.RADIAL), mid(60,24,.48F,.08F,ImpactDirection.LEFT_SWEEP))), 28));

    public static Hit ultimate(int index) {
        return index == 0 ? hit(AttackType.ULTIMATE,MushroomAttackForm.MELEE,80,40,.55F,.12F,ImpactDirection.RADIAL,drag(60,1))
                : hit(AttackType.ULTIMATE,MushroomAttackForm.MELEE,80,48,.65F,.18F,ImpactDirection.RADIAL,snare(8),reverse(60));
    }

    private static final int[][] WEIGHTS = {
            {25,25,10,10,10,10,10}, {15,20,15,10,15,10,15}, {10,15,20,15,20,10,10}};

    private MushroomCombatRules() {}

    public static int phase(float health) { return health > 210 ? 0 : health > 105 ? 1 : 2; }
    public static int weight(int phase, int combo) { return WEIGHTS[phase][combo]; }
    public static float ultimateDamage(int index) { return index == 0 ? 40 : 48; }
    public static boolean canAim(int elapsed) { return elapsed < AIM_TICKS; }

    public static boolean canStart(Combo combo, double distance, double height, boolean visible) {
        return visible && distance >= 0 && distance <= combo.range() && (!combo.pureRanged() || distance > 6)
                && Math.abs(height) <= MAX_HEIGHT_DIFFERENCE;
    }

    public static boolean canStart(Combo combo, SelectionContext context) {
        if (combo.pureRanged() && context.counterRanged()) {
            return context.visible() && context.distance() >= 0 && context.distance() <= ACQUIRE_RANGE
                    && Math.abs(context.height()) <= COUNTER_RANGED_HEIGHT;
        }
        return canStart(combo, context.distance(), context.height(), context.visible())
                && (combo.pureRanged() || Math.abs(context.facingError()) <= START_FACING_TOLERANCE);
    }

    public static int selectionWeight(int phase, Combo combo, SelectionContext context) {
        int multiplier = context.counterRanged() ? combo.pureRanged() ? 6 : combo.hasRanged() ? 3 : 1 : 1;
        return weight(phase, combo.id()) * multiplier;
    }

    /** Horizontal sectors intentionally do not use the static model's arm positions. */
    public static boolean inHitVolume(AttackType type, double dx, double dz,
            double height, double forwardX, double forwardZ, boolean visible) {
        return dev.tatami.virtualcraft.combat.CombatTargeting.sector(dx,dz,height,forwardX,forwardZ,visible,
                type==AttackType.MID?4:6,type==AttackType.MID?40:60,MAX_HEIGHT_DIFFERENCE);
    }

    public static boolean validParticipant(boolean alive, boolean creative, boolean spectator, boolean sameLevel) {
        return dev.tatami.virtualcraft.combat.CombatTargeting.validParticipant(alive,creative,spectator,sameLevel);
    }
}
