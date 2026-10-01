package dev.tatami.virtualcraft.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MushroomEffectMotionTest {
	@Test void easedWindupIsClampedAndContinuous() {
		assertEquals(0, MushroomEffectMotion.smooth(-1));
		assertEquals(1, MushroomEffectMotion.smooth(2));
		assertEquals(.5F, MushroomEffectMotion.smooth(.5F));
		for(int i=0;i<100;i++) {
			assertTrue(MushroomEffectMotion.smooth(i/100F)<=MushroomEffectMotion.smooth((i+1)/100F));
		}
	}

	@Test void ribbonsTaperAndHaveStableEndpoints() {
		assertEquals(0, MushroomEffectMotion.ribbonWidth(0),1e-8);
		assertEquals(0, MushroomEffectMotion.ribbonWidth(1),1e-8);
		assertEquals(0, MushroomEffectMotion.ribbonBend(0),1e-8);
		assertEquals(0, MushroomEffectMotion.ribbonBend(1),1e-8);
		for(int i=0;i<=100;i++) {
			double t=i/100.0;
			assertTrue(MushroomEffectMotion.ribbonWidth(t)>=0);
			assertTrue(Math.abs(MushroomEffectMotion.ribbonBend(t))<=1);
		}
	}

	@Test void rootsEmergeQuicklyHoldThenRetractWithoutPopping() {
		assertEquals(0,MushroomEffectMotion.rootRise(-1,12));
		assertEquals(0,MushroomEffectMotion.rootRise(0,12));
		assertEquals(1,MushroomEffectMotion.rootRise(3,12));
		assertEquals(1,MushroomEffectMotion.rootRise(6,12));
		assertTrue(MushroomEffectMotion.rootRise(10,12)<.5);
		assertEquals(0,MushroomEffectMotion.rootRise(12,12));
		assertEquals(0,MushroomEffectMotion.rootRise(1,0));
	}
}
