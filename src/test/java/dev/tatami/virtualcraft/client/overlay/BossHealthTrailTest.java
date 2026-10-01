package dev.tatami.virtualcraft.client.overlay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossHealthTrailTest {
	@Test void holdsDamageThenDecaysByTickNotFrame() {
		BossHealthTrail trail = new BossHealthTrail(1, 0);
		assertEquals(1, trail.update(.5F, 1));
		for (int frame=0; frame<240; frame++) assertEquals(1, trail.update(.5F, 1));
		assertEquals(1, trail.update(.5F, 9));
		assertEquals(.9F, trail.update(.5F, 17), .0001F);
		assertEquals(.5F, trail.update(.5F, 100));
	}
	@Test void healingAndWorldClockResetAreImmediate() {
		BossHealthTrail trail = new BossHealthTrail(.5F, 100);
		assertEquals(.8F, trail.update(.8F, 101));
		assertEquals(.4F, trail.update(.4F, 0));
	}
}
