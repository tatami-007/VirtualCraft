package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.*;
import static dev.tatami.virtualcraft.boss.MushroomAfflictionType.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MushroomAfflictionTest {
    private final UUID first=UUID.randomUUID(),second=UUID.randomUUID();

    @Test void snareDoesNotRefreshAcrossHitsOrBossesAndHasEightTickReleaseImmunity() {
        var state=new MushroomAfflictionState();
        assertTrue(state.apply(first,MYCELIAL_SNARE,8,0,0,100));
        assertFalse(state.apply(first,MYCELIAL_SNARE,12,0,0,104));
        assertFalse(state.apply(second,MYCELIAL_SNARE,12,0,0,107));
        assertEquals(108,state.control(107).snareUntil());
        assertEquals(0,state.control(108).snareUntil());
        assertFalse(state.apply(second,MYCELIAL_SNARE,12,0,0,108));
        assertFalse(state.apply(second,MYCELIAL_SNARE,12,0,0,115));
        assertTrue(state.apply(second,MYCELIAL_SNARE,12,0,0,116));
        assertEquals(124,state.control(116).snareUntil());
    }

    @Test void milkAndSourceRemovalReleaseImmediatelyButDoNotRemoveSnareImmunity() {
        var state=new MushroomAfflictionState();
        state.apply(first,MYCELIAL_SNARE,12,0,0,0);
        state.clear(MYCELIAL_SNARE,3);
        assertEquals(0,state.control(3).snareUntil());
        assertFalse(state.apply(second,MYCELIAL_SNARE,12,0,0,10));
        assertTrue(state.apply(second,MYCELIAL_SNARE,12,0,0,11));
        state.removeSource(second,12);
        assertFalse(state.apply(first,MYCELIAL_SNARE,12,0,0,19));
        assertTrue(state.apply(first,MYCELIAL_SNARE,12,0,0,20));
    }

    @Test void sameSourceSlowLevelsHaveIndependentLifetimesAndWeakRefreshDoesNotExtendStrong() {
        var state=new MushroomAfflictionState();
        state.apply(first,SPORE_DRAG,20,1,0,0);
        state.apply(first,SPORE_DRAG,40,0,0,10);
        assertEquals(1,state.active(SPORE_DRAG,19).level());
        assertEquals(20,state.active(SPORE_DRAG,19).until());
        state.prune(20);
        assertEquals(0,state.active(SPORE_DRAG,20).level());
        assertEquals(50,state.active(SPORE_DRAG,20).until());
        state.apply(first,SPORE_DRAG,40,0,0,25);
        assertEquals(65,state.active(SPORE_DRAG,25).until());
        assertNull(state.active(SPORE_DRAG,65));
    }

    @Test void strongerApplicationDoesNotInheritRemainingWeakerDurationAndSourcesAreIndependent() {
        var state=new MushroomAfflictionState();
        state.apply(first,SPORE_DRAG,60,0,0,0);
        state.apply(second,SPORE_DRAG,20,1,0,1);
        assertEquals(1,state.active(SPORE_DRAG,20).level());
        state.removeSource(second,20);
        assertEquals(0,state.active(SPORE_DRAG,20).level());
        assertEquals(60,state.active(SPORE_DRAG,20).until());
        state.apply(first,SPORE_DRAG,10,1,0,20);
        assertEquals(30,state.active(SPORE_DRAG,20).until());
        state.prune(30);
        assertEquals(0,state.active(SPORE_DRAG,30).level());
    }

    @Test void reversalHasPriorityAndSideDirectionStaysFixedThroughRefresh() {
        var state=new MushroomAfflictionState();
        state.apply(first,SPORE_MISDIRECTION,60,0,90,0);
        state.apply(first,SPORE_MISDIRECTION,60,0,-90,10);
        assertEquals(90,state.control(10).degrees());
        state.apply(second,SPORE_REVERSAL,20,0,90,10);
        assertEquals(180,state.control(29).degrees());
        assertEquals(30,state.control(29).rotationUntil());
        state.prune(30);
        assertEquals(90,state.control(30).degrees());
        assertEquals(70,state.control(30).rotationUntil());
        state.removeSource(first,31);
        assertEquals(new MushroomAfflictionState.Control(0,0,0,0,1),state.control(31));
    }

    @Test void allCuredEffectsStayClearedAndRootImmunityKeepsTheSessionAliveOnlyUntilItsDeadline() {
        var state=new MushroomAfflictionState();
        for(var type:values()) state.apply(first,type,12,0,90,0);
        for(var type:values()) state.clear(type,5);
        for(var type:values()) assertNull(state.active(type,5));
        assertFalse(state.empty(12));
        assertTrue(state.empty(13));
        state.apply(first,MYCELIAL_SNARE,100,0,0,13);
        assertEquals(21,state.control(13).snareUntil(),"Root duration is bounded at eight ticks");
    }

    @Test void sideRotationPreservesMagnitudeWhileReversalSlowsAndRootStopsIntent() {
        assertEquals(new MushroomControlRules.Movement(0,1),MushroomControlRules.movement(1,0,false,90));
        assertEquals(new MushroomControlRules.Movement(0,-1),MushroomControlRules.movement(1,0,false,-90));
        assertEquals(new MushroomControlRules.Movement(-.6F,0),MushroomControlRules.movement(1,0,false,180));
        var reverseDiagonal=MushroomControlRules.movement(1,1,false,180);
        assertEquals(.6,Math.hypot(reverseDiagonal.forward(),reverseDiagonal.left()),1e-6);
        assertEquals(new MushroomControlRules.Movement(-.5F,1),MushroomControlRules.movement(1,.5F,false,90));
        assertEquals(new MushroomControlRules.Movement(0,0),MushroomControlRules.movement(1,1,true,180));
        assertEquals(new MushroomControlRules.Movement(1,.5F),MushroomControlRules.movement(1,.5F,false,0));
        assertEquals(.15F,MushroomControlRules.dragMovementScale(0));
        assertEquals(.05F,MushroomControlRules.dragMovementScale(1));
    }

    @Test void dragAttenuatesIntentIncludingDiagonalsAndSneakWithoutChangingOtherMovementState() {
        for(float scale:new float[]{.15F,.05F}) {
            var straight=MushroomControlRules.movement(1,0,false,0,scale);
            var diagonal=MushroomControlRules.movement(1,1,false,90,scale);
            assertEquals(scale,straight.forward(),1e-6);
            assertEquals(scale,Math.hypot(diagonal.forward(),diagonal.left()),1e-6);
            assertTrue(diagonal.forward()<0 && diagonal.left()>0);
            var crouched=MushroomControlRules.movement(.3F,0,false,180,scale);
            assertEquals(-.3*scale,crouched.forward(),1e-6);
            assertEquals(new MushroomControlRules.Movement(0,0),MushroomControlRules.movement(1,1,true,90,scale));
        }
        assertEquals(new MushroomControlRules.Movement(1,1),MushroomControlRules.movement(1,1,false,0,1));
    }

    @Test void snapshotCarriesDragStrengthAndRestoresMovementAtExpiryOrCure() {
        var state=new MushroomAfflictionState();
        state.apply(first,SPORE_DRAG,60,0,0,0);
        state.apply(second,SPORE_DRAG,20,1,0,0);
        assertEquals(.05F,state.control(19).movementScale());
        assertEquals(20,state.control(19).dragUntil());
        state.prune(20);
        assertEquals(.15F,state.control(20).movementScale());
        assertEquals(60,state.control(20).dragUntil());
        assertEquals(1,state.control(60).movementScale());
        assertEquals(0,state.control(60).dragUntil());
        state.clear(SPORE_DRAG,30);
        assertEquals(1,state.control(30).movementScale());
        assertEquals(0,state.control(30).dragUntil());
    }

    @Test void reversalExpiryAndMilkRestoreSpeedWithoutChangingIndependentDrag() {
        var state=new MushroomAfflictionState();
        state.apply(first,SPORE_REVERSAL,60,0,180,10);
        assertEquals(-.6F,MushroomControlRules.movement(1,0,false,state.control(69).degrees()).forward());
        assertEquals(1F,MushroomControlRules.movement(1,0,false,state.control(70).degrees()).forward());
        state.clear(SPORE_REVERSAL,20);
        assertEquals(1F,MushroomControlRules.movement(1,0,false,state.control(20).degrees()).forward());
        state.apply(first,SPORE_REVERSAL,60,0,180,21);
        state.apply(second,SPORE_DRAG,40,0,0,21);
        var control=state.control(22);
        assertEquals(-.15F,MushroomControlRules.movement(1,0,false,control.degrees(),control.movementScale()).forward());
    }
}
