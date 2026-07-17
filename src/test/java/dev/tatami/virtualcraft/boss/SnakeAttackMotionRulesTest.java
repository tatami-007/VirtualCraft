package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnakeAttackMotionRulesTest {
	@Test
	void biteImpulseStopsBeforeRecoveryPose() {
		assertEquals(0.0, SnakeAttackMotionRules.horizontalRetention(1, 8, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.58, SnakeAttackMotionRules.horizontalRetention(1, 9, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.28, SnakeAttackMotionRules.horizontalRetention(1, 10, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.08, SnakeAttackMotionRules.horizontalRetention(1, 11, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.0, SnakeAttackMotionRules.horizontalRetention(1, 12, SnakeHuntPattern.BITE_BITE_BITE));
	}

	@Test
	void comboTailNeverInheritsBiteVelocity() {
		SnakeHuntPattern pattern = SnakeHuntPattern.BITE_BITE_TAIL;
		int tailSegmentTick = SnakeHuntPattern.SEGMENT_TICKS * 2 + 9;
		assertEquals(0.0, SnakeAttackMotionRules.horizontalRetention(2, tailSegmentTick, pattern));
	}

	@Test
	void dashOnlyMovesInsideItsTwoAuthoredWindows() {
		assertEquals(0.58, SnakeAttackMotionRules.horizontalRetention(7, 17, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.0, SnakeAttackMotionRules.horizontalRetention(7, 20, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.58, SnakeAttackMotionRules.horizontalRetention(7, 42, SnakeHuntPattern.BITE_BITE_BITE));
		assertEquals(0.0, SnakeAttackMotionRules.horizontalRetention(7, 45, SnakeHuntPattern.BITE_BITE_BITE));
	}
}
