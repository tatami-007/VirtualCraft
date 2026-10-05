package dev.tatami.virtualcraft.boss;
import dev.tatami.virtualcraft.combat.AttackType;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MushroomCombatTest {
    @Test
    void allSevenRhythmsRetainTheirGroupsDamageAndTickTimes() {
        String[] rhythms = {"下下 下", "下下下", "下下下上", "下下 下 下", "下下 下上", "中中 中", "中中中"};
        int[][] ticks = {{24,42,78},{24,42,60},{24,42,60,78},{24,42,78,114},{24,42,78,96},{24,42,78},{24,42,60}};
        float[][] damage = {{18,18,24},{14,14,18},{16,16,18,22},{12,12,16,20},{16,16,14,20},{20,20,28},{18,18,24}};
        int[] durations = {106,88,110,146,128,106,88};
        assertEquals(7, MushroomCombatRules.COMBOS.size());
        for (int i = 0; i < 7; i++) {
            var combo = MushroomCombatRules.COMBOS.get(i);
            assertEquals(i, combo.id());
            assertEquals(rhythms[i], combo.rhythm());
            assertEquals(combo.rhythm().split(" ").length, combo.groups().size());
            assertArrayEquals(ticks[i], combo.hits().stream().mapToInt(MushroomCombatRules.Hit::tick).toArray());
            for (int j = 0; j < damage[i].length; j++) assertEquals(damage[i][j], combo.hits().get(j).damage());
            assertEquals(durations[i], combo.duration());
            for (var group : combo.groups()) {
                for (int j = 1; j < group.size(); j++) assertEquals(18, group.get(j).tick() - group.get(j-1).tick());
            }
            for (int j = 1; j < combo.groups().size(); j++) {
                assertEquals(36, combo.groups().get(j).getFirst().tick() - combo.groups().get(j-1).getLast().tick());
            }
        }
        assertEquals(AttackType.HIGH, MushroomCombatRules.COMBOS.get(2).hits().getLast().type());
        assertEquals(AttackType.HIGH, MushroomCombatRules.COMBOS.get(4).hits().getLast().type());
        assertTrue(MushroomCombatRules.COMBOS.get(5).hits().stream().allMatch(hit -> hit.type() == AttackType.MID));
    }

    @Test
    void meleeHasTwoActiveTicksAndRangedLaunchesExactlyOnce() {
        for (var combo : MushroomCombatRules.COMBOS) {
            var director = new MushroomCombatDirector();
            director.beginCombo(combo.id());
            for (int tick = 1; tick <= combo.duration(); tick++) {
                var result = director.advance(tick);
                final int now = tick;
                var expected = combo.hits().stream().filter(hit -> now >= hit.tick()
                        && now < hit.tick() + (hit.form().ranged() ? 1 : 2)).toList();
                assertEquals(expected, result.hits().stream().map(MushroomCombatDirector.IndexedHit::hit).toList());
                assertEquals(-1, result.ultimateToResolve());
                assertEquals(tick == combo.duration() ? MushroomCombatDirector.Mode.IDLE : MushroomCombatDirector.Mode.COMBO, director.mode());
            }
        }
    }

    @Test
    void hitsAndDodgesAreDeduplicatedPerStrikeAndPerPlayer() {
        var director = new MushroomCombatDirector();
        director.beginCombo(0);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(director.handleOnce(0, first));
        assertFalse(director.handleOnce(0, first));
        assertTrue(director.handleOnce(0, second));
        assertTrue(director.handleOnce(1, first));
        director.reset();
        director.beginCombo(0);
        assertTrue(director.handleOnce(0, first));
    }

    @Test
    void selectionHonoursRangeSightHeightAndAllCombosAreAvailableInEveryPhase() {
        var director = new MushroomCombatDirector();
        assertEquals(-1, director.select(300, 0, 18.01, 0, true, 0));
        assertEquals(-1, director.select(300, 0, 3, 3.01, true, 0));
        assertEquals(-1, director.select(300, 0, 3, 0, false, 0));
        for (float health : new float[]{300,210,105}) {
            Set<Integer> close = new HashSet<>(), far = new HashSet<>(), ranged = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                close.add(director.select(health, 0, 4, 3, true, (i+0.5)/1000));
                far.add(director.select(health, 0, 6, 0, true, (i+0.5)/1000));
                ranged.add(director.select(health, 0, 18, 0, true, (i+0.5)/1000));
            }
            assertEquals(Set.of(0,2,4,5,6), close);
            assertEquals(Set.of(0,2,4), far);
            assertEquals(Set.of(1,3), ranged);
        }
    }

    @Test
    void phaseWeightsExactlyMatchTheThreeTables() {
        int[][] expected = {{25,25,10,10,10,10,10},{15,20,15,10,15,10,15},{10,15,20,15,20,10,10}};
        assertEquals(0, MushroomCombatRules.phase(210.01F));
        assertEquals(1, MushroomCombatRules.phase(210));
        assertEquals(1, MushroomCombatRules.phase(105.01F));
        assertEquals(2, MushroomCombatRules.phase(105));
        for (int phase = 0; phase < 3; phase++) {
            for (int id = 0; id < 7; id++) assertEquals(expected[phase][id], MushroomCombatRules.weight(phase,id));
            var director = new MushroomCombatDirector();
            int first = 0;
            for (int i = 0; i < 1000; i++) {
                if (director.select(new float[]{300,210,105}[phase],0,12,0,true,(i+0.5)/1000) == 1) first++;
            }
            assertEquals(1000.0 * expected[phase][1] / (expected[phase][1]+expected[phase][3]), first, 1);
        }
    }

    @Test
    void cooldownStartsAtCompletionAndSameComboCannotRepeat() {
        var director = new MushroomCombatDirector();
        director.beginCombo(0);
        for (int tick = 1; tick <= 106; tick++) director.advance(tick);
        assertEquals(-1, director.select(300, 106, 19, 0, true, 0));
        for (int i = 0; i < 100; i++) assertNotEquals(0, director.select(300,1000,3,0,true,i/100.0));
        director.beginCombo(1);
        for (int tick = 107; tick <= 194; tick++) director.advance(tick);
        for (int i = 0; i < 100; i++) {
            int choice = director.select(300,305,3,0,true,i/100.0);
            assertNotEquals(0, choice);
            assertNotEquals(1, choice);
        }
        assertEquals(0, director.select(300,306,3,0,true,0));
        director.beginCombo(0);
        assertEquals(-1, director.select(300,1000,3,0,true,0));
    }

    @Test
    void emptyCandidateSetWaitsInsteadOfForcingAnAttack() {
        var director = new MushroomCombatDirector();
        director.restore(new MushroomCombatDirector.Snapshot(0,0,false,-1,-1,new long[]{200,200,200,200,200,200,200}));
        assertEquals(-1, director.select(300,199,3,0,true,0));
        assertEquals(0, director.select(300,200,3,0,true,0));
    }

    @Test
    void aimLocksAfterTwelveTicksAndSectorsHaveRealEdges() {
        assertTrue(MushroomCombatRules.canAim(11));
        assertFalse(MushroomCombatRules.canAim(12));
        assertTrue(volume(AttackType.LOW,6,60,0,true));
        assertFalse(volume(AttackType.LOW,6,60.1,0,true));
        assertFalse(volume(AttackType.LOW,6.01,0,0,true));
        assertTrue(volume(AttackType.MID,4,40,3,true));
        assertFalse(volume(AttackType.MID,4,40.1,0,true));
        assertFalse(volume(AttackType.MID,4.01,0,0,true));
        assertFalse(volume(AttackType.HIGH,3,0,3.01,true));
        assertFalse(volume(AttackType.LOW,3,0,0,false));
        assertFalse(volume(AttackType.MID,3,180,0,true));
    }

    @Test
    void crossTwoThresholdsQueuesBothAndRequiresACompletedComboBetweenThem() {
        var director = new MushroomCombatDirector();
        director.beginCombo(0);
        director.observeHealth(100);
        assertThrows(IllegalStateException.class, director::beginUltimate);
        for (int tick = 1; tick <= 106; tick++) director.advance(tick);
        assertEquals(0, director.pendingUltimate());
        director.beginUltimate();
        for (int tick = 1; tick <= 120; tick++) {
            var result = director.advance(106+tick);
            assertEquals(tick == 80 ? 0 : -1, result.ultimateToResolve());
        }
        assertEquals(-1, director.pendingUltimate());
        assertTrue(director.needsCombo());
        assertEquals(-1, director.select(100,226,19,0,true,0));
        assertThrows(IllegalStateException.class, director::beginUltimate);
        director.beginCombo(1);
        for (int tick = 1; tick < 88; tick++) {
            director.advance(226+tick);
            assertEquals(-1, director.pendingUltimate());
        }
        director.advance(314);
        assertEquals(1, director.pendingUltimate());
        director.beginUltimate();
        for (int tick = 1; tick <= 120; tick++) {
            assertEquals(tick == 80 ? 1 : -1, director.advance(314+tick).ultimateToResolve());
        }
        director.observeHealth(300);
        director.observeHealth(1);
        assertEquals(-1, director.pendingUltimate());
        assertEquals(40, MushroomCombatRules.ultimateDamage(0));
        assertEquals(48, MushroomCombatRules.ultimateDamage(1));
    }

    @Test
    void thresholdsAreInclusiveAndResetBeginsANewEncounter() {
        var director = new MushroomCombatDirector();
        director.observeHealth(210.01F);
        assertEquals(-1, director.pendingUltimate());
        director.observeHealth(210);
        assertEquals(0, director.pendingUltimate());
        director.reset();
        assertEquals(-1, director.pendingUltimate());
        assertEquals(0, director.select(300,0,3,0,true,0));
        assertEquals(300, MushroomCombatRules.MAX_HEALTH);
    }

    @Test
    void reloadRestartsOnlyUnresolvedUltimateAndCancelsOrdinaryCombo() {
        var director = new MushroomCombatDirector();
        director.observeHealth(100);
        director.beginUltimate();
        for (int i = 1; i <= 79; i++) director.advance(i);
        var loaded = new MushroomCombatDirector();
        loaded.restore(director.snapshot());
        assertEquals(MushroomCombatDirector.Mode.ULTIMATE, loaded.mode());
        assertEquals(0, loaded.elapsed());
        for (int i = 1; i <= 80; i++) assertEquals(i == 80 ? 0 : -1, loaded.advance(i).ultimateToResolve());
        var afterHit = new MushroomCombatDirector();
        afterHit.restore(loaded.snapshot());
        assertEquals(MushroomCombatDirector.Mode.IDLE, afterHit.mode());
        assertTrue(afterHit.needsCombo());
        assertEquals(-1, afterHit.pendingUltimate());
        afterHit.beginCombo(2);
        afterHit.advance(81);
        var canceledCombo = new MushroomCombatDirector();
        canceledCombo.restore(afterHit.snapshot());
        assertEquals(MushroomCombatDirector.Mode.IDLE, canceledCombo.mode());
        assertTrue(canceledCombo.needsCombo());
    }

    @Test
    void snapshotsRetainCooldownsButDoNotExposeMutableArrays() {
        long[] ready = {1,2,3,4,5,6,7};
        var snapshot = new MushroomCombatDirector.Snapshot(1,0,false,-1,-1,ready);
        ready[0] = 900;
        snapshot.readyAt()[0] = 800;
        assertEquals(1, snapshot.readyAt()[0]);
        var director = new MushroomCombatDirector();
        director.restore(snapshot);
        assertArrayEquals(new long[]{1,2,3,4,5,6,7}, director.snapshot().readyAt());
        assertEquals(0, director.pendingUltimate());
    }

    @Test
    void invalidPlayersAreExcludedIndependentlyInMultiplayer() {
        assertTrue(MushroomCombatRules.validParticipant(true,false,false,true));
        assertFalse(MushroomCombatRules.validParticipant(false,false,false,true));
        assertFalse(MushroomCombatRules.validParticipant(true,true,false,true));
        assertFalse(MushroomCombatRules.validParticipant(true,false,true,true));
        assertFalse(MushroomCombatRules.validParticipant(true,false,false,false));
    }

    private boolean volume(AttackType type, double radius, double angle, double height, boolean visible) {
        return MushroomCombatRules.inHitVolume(type, radius*Math.sin(Math.toRadians(angle)),
                radius*Math.cos(Math.toRadians(angle)),height,0,1,visible);
    }
}
