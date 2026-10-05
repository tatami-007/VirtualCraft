package dev.tatami.virtualcraft.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CombatSharedRulesTest {
    @Test void differentBossPoliciesShareConsumedInputsAndPlayerWideCooldown() {
        var narrow=new DefenseRules(6,5,.5,40,.25F,1,3);
        var state=new DefenseState();
        state.startGuard(10);
        assertEquals(DefenseState.Guard.NONE,state.guard(14,true,.49,narrow));
        assertEquals(DefenseState.Guard.PRECISE,state.guard(14,true,.5,narrow));
        // Switching to another boss/policy cannot reuse the same defense startup.
        assertEquals(DefenseState.Guard.BROKEN,state.guard(14,true,1,DefenseRules.STANDARD));
        assertFalse(state.startGuard(113));
        assertTrue(state.startGuard(114));
        assertEquals(DefenseState.Guard.BROKEN,state.guard(119,true,1,narrow));
        assertFalse(state.startGuard(158));
        assertTrue(state.startGuard(159));
    }
    @Test void independentJumpWindowDoesNotAllowAnInputToDodgeTwoBosses() {
        var rules=new DefenseRules(6,15,.25,100,.4F,1,2);
        var state=new DefenseState();
        state.confirmGroundJump(20);
        assertTrue(state.dodgeLow(26,true,false,rules));
        assertFalse(state.dodgeLow(26,true,false,DefenseRules.STANDARD));
        state.confirmGroundJump(30);
        assertFalse(state.dodgeLow(37,true,false,rules));
        assertFalse(state.dodgeLow(35,true,true,rules));
        assertTrue(state.dodgeLow(36,true,false,rules));
    }
    @Test void delayedHitIdentitySeparatesEncountersActionsStrikesAndPlayers() {
        UUID encounter=UUID.randomUUID(),target=UUID.randomUUID();
        var ledger=new HitLedger<AttackKey>();
        assertTrue(ledger.consume(new AttackKey(encounter,1,0,target)));
        assertFalse(ledger.consume(new AttackKey(encounter,1,0,target)));
        assertTrue(ledger.consume(new AttackKey(encounter,2,0,target)));
        assertTrue(ledger.consume(new AttackKey(encounter,1,1,target)));
        assertTrue(ledger.consume(new AttackKey(encounter,1,0,UUID.randomUUID())));
        assertTrue(ledger.consume(new AttackKey(UUID.randomUUID(),1,0,target)));
        ledger.clear();
        assertTrue(ledger.consume(new AttackKey(encounter,1,0,target)));
    }
    @Test void invalidPolicyAndNonFiniteFacingNeverGrantGuard() {
        assertThrows(IllegalArgumentException.class,()->new DefenseRules(-1,15,.25,100,.4F,1,2));
        assertThrows(IllegalArgumentException.class,()->new DefenseRules(10,15,Double.NaN,100,.4F,1,2));
        var state=new DefenseState(); state.startGuard(10);
        assertEquals(DefenseState.Guard.NONE,state.guard(10,true,Double.NaN));
        assertFalse(CombatTargeting.sector(Double.NaN,0,0,0,1,true,6,60,3));
    }
}
