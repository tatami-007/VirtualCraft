package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		assertEquals(11, SnakeHuntPattern.localTick(51));
		assertEquals(68, SnakeHuntPattern.BITE_BITE_BITE.durationTicks());
	}
}
