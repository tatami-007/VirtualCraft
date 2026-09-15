package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnakeHuntPatternTest {
	@Test
	void supportsSingleStrikesAndThreeReadableCombos() {
		assertEquals(1, SnakeHuntPattern.BITE.segmentCount());
		assertEquals(1, SnakeHuntPattern.TAIL.segmentCount());
		assertEquals(3, SnakeHuntPattern.BITE_BITE_BITE.segmentCount());
		assertEquals(SnakeHuntPattern.Move.TAIL, SnakeHuntPattern.BITE_BITE_TAIL.move(2));
		assertEquals(SnakeHuntPattern.Move.TAIL, SnakeHuntPattern.BITE_TAIL_BITE.move(1));
	}

	@Test
	void eachSegmentKeepsTheTwentyTickRhythm() {
		assertEquals(0, SnakeHuntPattern.segment(19));
		assertEquals(1, SnakeHuntPattern.segment(20));
		assertEquals(15, SnakeHuntPattern.localTick(55));
		assertEquals(true, SnakeHuntPattern.isComboOpening(11));
		assertEquals(false, SnakeHuntPattern.isComboOpening(12));
		assertEquals(0, SnakeHuntPattern.comboSegment(31));
		assertEquals(1, SnakeHuntPattern.comboSegment(32));
		assertEquals(19, SnakeHuntPattern.comboLocalTick(31));
		assertEquals(80, SnakeHuntPattern.BITE_BITE_BITE.durationTicks());
	}

	@Test
	void extraWindupOnlyPrecedesTheFirstComboStrike() {
		int firstHit = SnakeHuntPattern.COMBO_OPENING_TICKS + SnakeHuntPattern.COMBO_BITE_HIT_TICK;
		int secondHit = SnakeHuntPattern.COMBO_OPENING_TICKS + SnakeHuntPattern.SEGMENT_TICKS
				+ SnakeHuntPattern.COMBO_BITE_HIT_TICK;
		assertEquals(24, firstHit);
		assertEquals(SnakeHuntPattern.SEGMENT_TICKS, secondHit - firstHit);
	}

	@Test
	void tailSegmentsUseASeparateSlowRhythm() {
		SnakeHuntPattern pattern = SnakeHuntPattern.BITE_TAIL_BITE;
		assertEquals(0, pattern.comboSegmentAt(31));
		assertEquals(1, pattern.comboSegmentAt(32));
		assertEquals(1, pattern.comboSegmentAt(63));
		assertEquals(2, pattern.comboSegmentAt(64));
		assertEquals(31, pattern.comboLocalTickAt(63));
		assertEquals(92, pattern.durationTicks());
	}

	@Test
	void tailSweepCoilsBeforeItsFastRelease() {
		double neutral = SnakeHuntPattern.tailSweepPhase(0.0);
		double start = SnakeHuntPattern.tailSweepPhase(3.0);
		double coiled = SnakeHuntPattern.tailSweepPhase(12.0);
		double release = SnakeHuntPattern.tailSweepPhase(24.0);
		double impact = SnakeHuntPattern.tailSweepPhase(SnakeHuntPattern.TAIL_HIT_TICK);

		assertEquals(0.0, neutral, 0.001);
		assertTrue(coiled < start);
		assertTrue(release > coiled);
		assertTrue(impact > release);
		assertEquals(-1.05, start, 0.001);
		assertEquals(1.55, impact, 0.001);
	}

	@Test
	void tailSweepDirectionMatchesServerAndClientConvention() {
		assertEquals(-1.0F, SnakeHuntPattern.tailSweepDirection(true));
		assertEquals(1.0F, SnakeHuntPattern.tailSweepDirection(false));
	}

	@Test
	void tailPoseKeepsAContinuousDelayedChain() {
		SnakeHuntPattern.TailPose coiled = SnakeHuntPattern.tailPose(12.0);
		SnakeHuntPattern.TailPose impact = SnakeHuntPattern.tailPose(SnakeHuntPattern.TAIL_HIT_TICK);
		assertTrue(coiled.root() < -1.0);
		assertTrue(coiled.middle() < -1.0);
		assertTrue(impact.root() > impact.middle());
		assertTrue(impact.middle() > impact.tip());

		SnakeHuntPattern.TailPose recovered = SnakeHuntPattern.tailRecoveryPose(5.0);
		assertEquals(0.0, recovered.root(), 0.0001);
		assertEquals(0.0, recovered.middle(), 0.0001);
		assertEquals(0.0, recovered.tip(), 0.0001);
	}

	@Test
	void tailStrikeTurnsTheWholeBodyOnceBeforeImpact() {
		assertEquals(0.0, SnakeHuntPattern.tailBodySpinPhase(11.0), 0.0001);
		assertTrue(SnakeHuntPattern.tailBodySpinPhase(19.0) < Math.PI);
		assertTrue(SnakeHuntPattern.tailBodySpinPhase(24.0) > Math.PI);
		assertEquals(Math.PI * 2.0,
				SnakeHuntPattern.tailBodySpinPhase(SnakeHuntPattern.TAIL_HIT_TICK), 0.0001);
	}

	@Test
	void tailDamageCoversTheVisibleReleaseInsteadOfOnlyTheLastFrame() {
		assertEquals(false, SnakeHuntPattern.isTailDamageActive(
				SnakeHuntPattern.TAIL_ACTIVE_START_TICK - 1));
		assertEquals(true, SnakeHuntPattern.isTailDamageActive(
				SnakeHuntPattern.TAIL_ACTIVE_START_TICK));
		assertEquals(true, SnakeHuntPattern.isTailDamageActive(SnakeHuntPattern.TAIL_HIT_TICK));
		assertEquals(false, SnakeHuntPattern.isTailDamageActive(SnakeHuntPattern.TAIL_HIT_TICK + 1));
	}
}
