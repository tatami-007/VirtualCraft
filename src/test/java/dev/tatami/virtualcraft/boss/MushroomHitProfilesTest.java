package dev.tatami.virtualcraft.boss;
import dev.tatami.virtualcraft.combat.AttackType;
import dev.tatami.virtualcraft.combat.DefenseState;

import static org.junit.jupiter.api.Assertions.*;
import static dev.tatami.virtualcraft.boss.MushroomAfflictionType.*;
import static dev.tatami.virtualcraft.combat.ImpactDirection.*;
import dev.tatami.virtualcraft.combat.AttackImpulse;
import org.junit.jupiter.api.Test;

class MushroomHitProfilesTest {
    @Test void everyStrikeHasTheApprovedHorizontalAndVerticalImpulse() {
        float[][] horizontal={{.18F,.18F,.48F},{.12F,.12F,.28F},{.16F,.16F,.24F,.20F},
                {.10F,.10F,.16F,.24F},{.16F,.16F,.14F,.22F},{.24F,.24F,.75F},{.20F,.20F,.48F}};
        float[][] vertical={{0,0,.08F},{0,0,.06F},{0,0,.04F,0},{0,0,0,.04F},
                {0,0,0,0},{.04F,.04F,.16F},{.02F,.02F,.08F}};
        for(var combo:MushroomCombatRules.COMBOS) for(int i=0;i<combo.hits().size();i++) {
            var hit=combo.hits().get(i);
            assertEquals(horizontal[combo.id()][i],hit.impulse().horizontal(),1e-6);
            assertEquals(vertical[combo.id()][i],hit.impulse().vertical(),1e-6);
            assertTrue(hit.damage()>0);
            assertEquals(combo.id()>=5,hit.type()==AttackType.MID);
            if(hit.type()==AttackType.MID) assertTrue(hit.afflictions().isEmpty());
        }
        assertEquals(LEFT_SWEEP,MushroomCombatRules.COMBOS.get(0).hits().getFirst().impulse().direction());
        assertEquals(RIGHT_SWEEP,MushroomCombatRules.COMBOS.get(0).hits().get(1).impulse().direction());
        assertEquals(SIDE,MushroomCombatRules.COMBOS.get(2).hits().getLast().impulse().direction());
        assertEquals(PULL,MushroomCombatRules.COMBOS.get(4).hits().getLast().impulse().direction());
        assertEquals(LEFT_SWEEP,MushroomCombatRules.COMBOS.get(6).hits().getLast().impulse().direction());
    }

    private void effect(int combo,int strike,MushroomAfflictionType type,int duration,int level) {
        var effects=MushroomCombatRules.COMBOS.get(combo).hits().get(strike).afflictions();
        assertTrue(effects.contains(new MushroomCombatRules.Affliction(type,duration,level)));
    }

    @Test void onlyTheSpecifiedHighAndLowStrikesApplyTheSpecifiedControl() {
        assertTrue(MushroomCombatRules.COMBOS.get(0).hits().getFirst().afflictions().isEmpty());
        assertTrue(MushroomCombatRules.COMBOS.get(0).hits().get(1).afflictions().isEmpty());
        effect(0,2,SPORE_DRAG,40,0);
        effect(1,0,SPORE_DRAG,40,0); effect(1,1,SPORE_DRAG,40,0);
        effect(1,2,SPORE_MISDIRECTION,40,0);
        for(int i=0;i<3;i++) assertTrue(MushroomCombatRules.COMBOS.get(2).hits().get(i).afflictions().isEmpty());
        effect(2,3,SPORE_REVERSAL,60,0);
        int[] roots={5,5,6,8};
        for(int i=0;i<4;i++) {
            effect(3,i,MYCELIAL_SNARE,roots[i],0);
            effect(3,i,SPORE_DRAG,i==3?60:40,i==3?1:0);
        }
        effect(4,2,MYCELIAL_SNARE,6,0); effect(4,2,SPORE_DRAG,40,0);
        effect(4,3,SPORE_MISDIRECTION,60,0);
    }

    @Test void ultimatesKeepTheirClockAndUseTheSharedHitConfiguration() {
        var first=MushroomCombatRules.ultimate(0);
        var second=MushroomCombatRules.ultimate(1);
        for(var hit:new MushroomCombatRules.Hit[]{first,second}) {
            assertEquals(AttackType.ULTIMATE,hit.type());
            assertEquals(80,hit.tick());
            assertEquals(RADIAL,hit.impulse().direction());
        }
        assertEquals(40,first.damage()); assertEquals(48,second.damage());
        assertEquals(.55F,first.impulse().horizontal()); assertEquals(.12F,first.impulse().vertical());
        assertEquals(.65F,second.impulse().horizontal()); assertEquals(.18F,second.impulse().vertical());
        assertEquals(java.util.List.of(new MushroomCombatRules.Affliction(SPORE_DRAG,60,1)),first.afflictions());
        assertEquals(java.util.List.of(new MushroomCombatRules.Affliction(MYCELIAL_SNARE,8,0),
                new MushroomCombatRules.Affliction(SPORE_REVERSAL,60,0)),second.afflictions());
    }

    @Test void failedGuardAndArmorReduceBothImpulseAxesWithoutAForcedMinimum() {
        var impulse=MushroomCombatRules.COMBOS.get(5).hits().getLast().impulse();
        var snake=impulse.reduced(1,.40);
        assertEquals(.45,snake.horizontal(),1e-6); assertEquals(.096,snake.vertical(),1e-6);
        var mushroom=impulse.reduced(1,.60);
        assertEquals(.30,mushroom.horizontal(),1e-6); assertEquals(.064,mushroom.vertical(),1e-6);
        var failed=impulse.reduced(DefenseState.Guard.BROKEN.damageMultiplier(),.40);
        assertEquals(.18,failed.horizontal(),1e-6); assertEquals(.0384,failed.vertical(),1e-6);
        for(var reduced:new AttackImpulse[]{impulse.reduced(0,.40),impulse.reduced(1,1),impulse.reduced(1,2)}) {
            assertEquals(0,reduced.horizontal()); assertEquals(0,reduced.vertical());
        }
        assertEquals(0,MushroomCombatRules.COMBOS.get(1).hits().getFirst().impulse().reduced(1,.4).vertical());
    }
}
