package dev.tatami.virtualcraft.boss;
import dev.tatami.virtualcraft.combat.DefenseState;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MushroomDefenseTest {
    @Test
    void failedGuardRetainsFortyPercentOfBaseDamageBeforeArmour() {
        assertEquals(8, 20 * DefenseState.Guard.BROKEN.damageMultiplier(), 0.0001);
        assertEquals(10.4, 26 * DefenseState.Guard.BROKEN.damageMultiplier(), 0.0001);
        assertEquals(0, DefenseState.Guard.PRECISE.damageMultiplier());
        assertEquals(1, DefenseState.Guard.NONE.damageMultiplier());
    }

    @Test
    void eachLowHitNeedsItsOwnJumpAndWindowEndsAfterTenTicks() {
        var state = new DefenseState();
        assertFalse(state.dodgeLow(24,true,false));
        state.jump(20);
        assertTrue(state.dodgeLow(24,true,false));
        assertFalse(state.dodgeLow(25,true,false));
        state.jump(38);
        assertTrue(state.dodgeLow(42,true,false));
        state.jump(50);
        assertTrue(state.dodgeLow(60,true,false));
        state.jump(50);
        assertFalse(state.dodgeLow(61,true,false));
        state.jump(80);
        assertFalse(state.dodgeLow(79,true,false));
    }

    @Test
    void landingInvalidMovementAndKnockbackDoNotDodge() {
        var state = new DefenseState();
        state.jump(20);
        assertFalse(state.dodgeLow(24,false,false));
        assertFalse(state.dodgeLow(24,true,true));
        state.invalidateJump(23);
        assertFalse(state.dodgeLow(24,true,false));
        state.jump(24); // Knockback movement packets must not manufacture a new jump.
        assertFalse(state.dodgeLow(25,true,false));
        state.jump(33);
        assertTrue(state.dodgeLow(34,true,false));
    }

    @Test
    void confirmedNewGroundJumpWorksAfterAnEarlierHitButForcedLiftDoesNot() {
        var state=new DefenseState();
        state.jump(20);
        state.invalidateJump(21);
        state.jump(22); // Upward movement alone is not a confirmed player input.
        assertFalse(state.dodgeLow(22,true,false));
        state.confirmGroundJump(23); // Both fresh input and server-grounded takeoff were checked.
        assertTrue(state.dodgeLow(24,true,false));
        assertFalse(state.dodgeLow(25,true,false));
        state.confirmGroundJump(40);
        assertFalse(state.dodgeLow(41,false,false));
        assertFalse(state.dodgeLow(51,true,false));
        state.invalidateJump(60);
        assertFalse(DefenseState.restore(state.snapshot()).dodgeLow(61,true,false));
    }

    @Test
    void precisionWindowIsZeroThroughFourteenInclusive() {
        for (int age : new int[]{0,1,14}) {
            var state = new DefenseState();
            assertTrue(state.startGuard(100));
            assertEquals(DefenseState.Guard.PRECISE,state.guard(100+age,true,0.25));
        }
        var state = new DefenseState();
        state.startGuard(100);
        assertEquals(DefenseState.Guard.BROKEN,state.guard(115,true,1));
        assertTrue(state.coolingDown(214));
        assertFalse(state.coolingDown(215));
    }

    @Test
    void oneDefenseActivationCannotParryTwoHits() {
        var state = new DefenseState();
        state.startGuard(20);
        assertEquals(DefenseState.Guard.PRECISE,state.guard(24,true,1));
        assertEquals(DefenseState.Guard.BROKEN,state.guard(25,true,1));
        var repeated = new DefenseState();
        repeated.startGuard(20);
        assertEquals(DefenseState.Guard.PRECISE,repeated.guard(24,true,1));
        repeated.startGuard(38);
        assertEquals(DefenseState.Guard.PRECISE,repeated.guard(42,true,1));
    }

    @Test
    void wrongDirectionAndNoGuardGiveNoReductionAndDoNotConsumeValidInput() {
        var state = new DefenseState();
        state.startGuard(20);
        assertEquals(DefenseState.Guard.NONE,state.guard(24,true,0.249));
        assertEquals(DefenseState.Guard.NONE,state.guard(24,false,1));
        assertFalse(state.coolingDown(24));
        assertEquals(DefenseState.Guard.PRECISE,state.guard(24,true,0.25));
    }

    @Test
    void playerCooldownSurvivesSwappingDefensiveItemsAndSaving() {
        var state = new DefenseState();
        state.startGuard(0);
        assertEquals(DefenseState.Guard.BROKEN,state.guard(24,true,1));
        assertFalse(state.startGuard(25)); // A new sword/shield uses the same player state.
        assertEquals(DefenseState.Guard.NONE,state.guard(42,true,1));
        var loaded = DefenseState.restore(state.snapshot());
        assertFalse(loaded.startGuard(123));
        assertTrue(loaded.startGuard(124));
        assertEquals(DefenseState.Guard.PRECISE,loaded.guard(124,true,1));
    }

    @Test
    void separatePlayersHaveIndependentDefenseTokens() {
        var a = new DefenseState();
        var b = new DefenseState();
        a.jump(20);
        b.jump(20);
        assertTrue(a.dodgeLow(24,true,false));
        assertTrue(b.dodgeLow(24,true,false));
        a.startGuard(0);
        a.guard(24,true,1);
        assertTrue(b.startGuard(24));
        assertEquals(DefenseState.Guard.PRECISE,b.guard(24,true,1));
    }
}
