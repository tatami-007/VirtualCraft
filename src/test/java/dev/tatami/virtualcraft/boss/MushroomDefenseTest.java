package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MushroomDefenseTest {
    @Test
    void failedGuardRetainsFortyPercentOfBaseDamageBeforeArmour() {
        assertEquals(8, 20 * MushroomDefenseState.Guard.BROKEN.damageMultiplier(), 0.0001);
        assertEquals(10.4, 26 * MushroomDefenseState.Guard.BROKEN.damageMultiplier(), 0.0001);
        assertEquals(0, MushroomDefenseState.Guard.PRECISE.damageMultiplier());
        assertEquals(1, MushroomDefenseState.Guard.NONE.damageMultiplier());
    }

    @Test
    void eachLowHitNeedsItsOwnJumpAndWindowEndsAfterTenTicks() {
        var state = new MushroomDefenseState();
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
        var state = new MushroomDefenseState();
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
    void precisionWindowIsZeroThroughFourteenInclusive() {
        for (int age : new int[]{0,1,14}) {
            var state = new MushroomDefenseState();
            assertTrue(state.startGuard(100));
            assertEquals(MushroomDefenseState.Guard.PRECISE,state.guard(100+age,true,0.25));
        }
        var state = new MushroomDefenseState();
        state.startGuard(100);
        assertEquals(MushroomDefenseState.Guard.BROKEN,state.guard(115,true,1));
        assertTrue(state.coolingDown(214));
        assertFalse(state.coolingDown(215));
    }

    @Test
    void oneDefenseActivationCannotParryTwoHits() {
        var state = new MushroomDefenseState();
        state.startGuard(20);
        assertEquals(MushroomDefenseState.Guard.PRECISE,state.guard(24,true,1));
        assertEquals(MushroomDefenseState.Guard.BROKEN,state.guard(25,true,1));
        var repeated = new MushroomDefenseState();
        repeated.startGuard(20);
        assertEquals(MushroomDefenseState.Guard.PRECISE,repeated.guard(24,true,1));
        repeated.startGuard(38);
        assertEquals(MushroomDefenseState.Guard.PRECISE,repeated.guard(42,true,1));
    }

    @Test
    void wrongDirectionAndNoGuardGiveNoReductionAndDoNotConsumeValidInput() {
        var state = new MushroomDefenseState();
        state.startGuard(20);
        assertEquals(MushroomDefenseState.Guard.NONE,state.guard(24,true,0.249));
        assertEquals(MushroomDefenseState.Guard.NONE,state.guard(24,false,1));
        assertFalse(state.coolingDown(24));
        assertEquals(MushroomDefenseState.Guard.PRECISE,state.guard(24,true,0.25));
    }

    @Test
    void playerCooldownSurvivesSwappingDefensiveItemsAndSaving() {
        var state = new MushroomDefenseState();
        state.startGuard(0);
        assertEquals(MushroomDefenseState.Guard.BROKEN,state.guard(24,true,1));
        assertFalse(state.startGuard(25)); // A new sword/shield uses the same player state.
        assertEquals(MushroomDefenseState.Guard.NONE,state.guard(42,true,1));
        var loaded = MushroomDefenseState.restore(state.snapshot());
        assertFalse(loaded.startGuard(123));
        assertTrue(loaded.startGuard(124));
        assertEquals(MushroomDefenseState.Guard.PRECISE,loaded.guard(124,true,1));
    }

    @Test
    void separatePlayersHaveIndependentDefenseTokens() {
        var a = new MushroomDefenseState();
        var b = new MushroomDefenseState();
        a.jump(20);
        b.jump(20);
        assertTrue(a.dodgeLow(24,true,false));
        assertTrue(b.dodgeLow(24,true,false));
        a.startGuard(0);
        a.guard(24,true,1);
        assertTrue(b.startGuard(24));
        assertEquals(MushroomDefenseState.Guard.PRECISE,b.guard(24,true,1));
    }
}
