package dev.tatami.virtualcraft.boss;
import dev.tatami.virtualcraft.combat.AttackType;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MushroomPursuitTest {
    @Test void sustainedCirclingEnablesACounterButABriefSidestepDoesNot() {
        var state = new MushroomPursuitState();
        var player = UUID.randomUUID();
        for (int t=0;t<=12;t++) {
            double angle=Math.toRadians(t*4);
            state.sample(player,t,4*Math.cos(angle),4*Math.sin(angle),0,0,0,true);
            assertEquals(t==12,state.counterRanged(t));
        }
        assertFalse(state.counterRanged(72));
        state.sample(UUID.randomUUID(),73,4,0,0,0,0,true);
        assertFalse(state.counterRanged(73));
    }

    @Test void groundedElevationAndFailedPursuitTriggerCountersButNormalJumpingDoesNot() {
        var id=UUID.randomUUID();
        var grounded=new MushroomPursuitState();
        var jumping=new MushroomPursuitState();
        for(int t=0;t<12;t++) {
            grounded.sample(id,t,2,0,0,0,5,true);
            jumping.sample(id,t,2,0,0,0,5,false);
        }
        assertTrue(grounded.counterRanged(11));
        assertFalse(jumping.counterRanged(11));
        var pillarJumping=new MushroomPursuitState();
        pillarJumping.sample(id,0,2,0,0,0,5,true);
        for(int t=1;t<12;t++) pillarJumping.sample(id,t,2,0,0,0,6,false);
        assertTrue(pillarJumping.counterRanged(11));
        for(int t=12;t<42;t++) jumping.navigationProgress(t,true,true,0);
        assertTrue(jumping.counterRanged(41));
        jumping.reset();
        assertFalse(jumping.counterRanged(42));
    }

    @Test void LeadIsBoundedAndYawUsesTheShortestTurnAcrossTheSeam() {
        var state=new MushroomPursuitState();
        var id=UUID.randomUUID();
        for(int t=0;t<12;t++) state.sample(id,t,t*.8,5,0,0,0,true);
        assertTrue(Math.hypot(state.leadX(8),state.leadZ(8))<=2.5+1e-9);
        float turned=MushroomPursuitState.turn(179,-179,30);
        assertEquals(2,MushroomPursuitState.wrap(turned-179),1e-6);
        assertEquals(30,Math.abs(MushroomPursuitState.turn(0,180,30)),1e-6);
    }

    @Test void RearAndElevatedPlayersGetRangedResponsesWithoutWideningMeleeHitVolumes() {
        var director=new MushroomCombatDirector();
        var closeCounter=new MushroomCombatRules.SelectionContext(3,0,true,180,true);
        var elevatedCounter=new MushroomCombatRules.SelectionContext(2,8,true,0,true);
        for(int i=0;i<100;i++) {
            assertTrue(director.select(300,0,closeCounter,i/100.0)==1
                    || director.select(300,0,closeCounter,i/100.0)==3);
            int raised=director.select(300,0,elevatedCounter,i/100.0);
            assertTrue(raised==1||raised==3);
        }
        assertEquals(-1,director.select(300,0,3,8,true,0));
        assertEquals(-1,director.select(300,0,new MushroomCombatRules.SelectionContext(2,8,false,0,true),0));
        assertFalse(MushroomCombatRules.inHitVolume(AttackType.LOW,0,3,8,0,1,true));
        assertFalse(MushroomCombatRules.inHitVolume(AttackType.MID,0,-3,0,0,1,true));
    }

    @Test void CounterSelectionStillRespectsCooldownRepetitionAndUltimateSpacing() {
        var director=new MushroomCombatDirector();
        var context=new MushroomCombatRules.SelectionContext(2,8,true,0,true);
        director.observeHealth(100);
        director.beginUltimate();
        for(int tick=1;tick<=120;tick++) director.advance(tick);
        assertEquals(-1,director.pendingUltimate());
        assertEquals(1,director.select(100,120,context,0));
        director.beginCombo(1);
        for(int tick=121;tick<=208;tick++) director.advance(tick);
        assertEquals(1,director.pendingUltimate());
        assertEquals(3,director.select(100,208,context,0));
        director.beginCombo(3);
        for(int tick=209;tick<=354;tick++) director.advance(tick);
        assertEquals(-1,director.select(100,354,context,0));
        assertEquals(1,director.select(100,408,context,0));
    }
}
