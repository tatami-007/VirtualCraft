package dev.tatami.virtualcraft.boss;
import dev.tatami.virtualcraft.combat.AttackType;
import dev.tatami.virtualcraft.combat.DefenseState;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MushroomRangedStrikeTest {
    private final UUID encounter = UUID.randomUUID(), target = UUID.randomUUID();

    private MushroomRangedStrike strike(int combo, int hit, long release, double distance) {
        return new MushroomRangedStrike(new dev.tatami.virtualcraft.combat.AttackKey(encounter,combo,hit,target),release,distance);
    }

    @Test void distanceControlsTravelButIsBoundedAndDeadlineNeverChasesThePlayer() {
        assertEquals(6,MushroomRangedStrike.travelTicks(0));
        assertEquals(6,MushroomRangedStrike.travelTicks(4.8));
        assertEquals(7,MushroomRangedStrike.travelTicks(4.81));
        assertEquals(15,MushroomRangedStrike.travelTicks(12));
        assertEquals(23,MushroomRangedStrike.travelTicks(18));
        assertEquals(24,MushroomRangedStrike.travelTicks(1000));
        var attack = strike(1,0,24,12);
        assertEquals(39,attack.arrival());
        assertFalse(attack.handleOnce(38,encounter,target,true));
        assertTrue(attack.handleOnce(39,encounter,target,true));
        assertFalse(attack.handleOnce(40,encounter,target,true));
    }

    @Test void arrivalSecondTickStillWorksButLateCatchupDoesNot() {
        var attack = strike(1,0,0,12);
        assertTrue(attack.handleOnce(16,encounter,target,true));
        assertFalse(strike(1,0,0,12).handleOnce(17,encounter,target,true));
    }

    @Test void targetAndEncounterCannotBeReplacedAndInvalidPlayersGetNoDamage() {
        var attack = strike(1,0,0,12);
        assertFalse(attack.handleOnce(15,encounter,UUID.randomUUID(),true));
        assertFalse(attack.handleOnce(15,UUID.randomUUID(),target,true));
        assertFalse(attack.handleOnce(15,encounter,target,false));
        assertTrue(attack.handleOnce(15,encounter,target,true));
    }

    @Test void delayedAttacksRemainIndependentAcrossComboAndHitBoundaries() {
        var first = strike(1,0,0,12);
        var second = strike(2,0,0,12);
        var nextHit = strike(1,1,0,12);
        assertNotEquals(first.key(),second.key());
        assertNotEquals(first.key(),nextHit.key());
        assertTrue(first.handleOnce(15,encounter,target,true));
        assertTrue(second.handleOnce(15,encounter,target,true));
        assertTrue(nextHit.handleOnce(15,encounter,target,true));
    }

    @Test void jumpIsConsumedPerStrikeAndSuccessAlsoConsumesArrival() {
        var defence = new DefenseState();
        defence.jump(10);
        var attack = strike(1,0,0,12);
        assertTrue(attack.handleOnce(15,encounter,target,true));
        assertTrue(defence.dodgeLow(15,true,false));
        assertFalse(attack.handleOnce(16,encounter,target,true));
        var next = strike(1,1,0,12);
        assertTrue(next.handleOnce(15,encounter,target,true));
        assertFalse(defence.dodgeLow(15,true,false));
        defence.jump(16);
        assertTrue(defence.dodgeLow(18,true,false));
    }

    @Test void formsAreIndependentOfHeightAndMatchAllSevenSkillRoles() {
        var combos = MushroomCombatRules.COMBOS;
        assertFalse(combos.get(0).hasRanged());
        assertTrue(combos.get(1).hits().stream().allMatch(h -> h.form() == MushroomAttackForm.HUNTING_CORE));
        assertEquals(MushroomAttackForm.HIGH_TENDRIL,combos.get(2).hits().getLast().form());
        assertTrue(combos.get(3).hits().stream().allMatch(h -> h.form() == MushroomAttackForm.ROOT_SPIKES));
        assertEquals(MushroomAttackForm.ROOT_SPIKES,combos.get(4).hits().get(2).form());
        assertEquals(MushroomAttackForm.HIGH_TENDRIL,combos.get(4).hits().get(3).form());
        for (int id : new int[]{5,6}) assertTrue(combos.get(id).hits().stream()
                .allMatch(h -> h.form() == MushroomAttackForm.MELEE && h.type() == AttackType.MID));
        assertFalse(MushroomCombatRules.canStart(combos.get(1),6,0,true));
        assertTrue(MushroomCombatRules.canStart(combos.get(1),6.001,0,true));
        assertTrue(MushroomCombatRules.canStart(combos.get(3),18,3,true));
        assertFalse(MushroomCombatRules.canStart(combos.get(3),18.001,0,true));
    }
}
