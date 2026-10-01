package dev.tatami.virtualcraft.client.render;

import dev.tatami.virtualcraft.boss.MushroomComboPattern;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MushroomEffectStyleTest {
	@Test void everyComboMoveHasItsOwnPresentationRoute() {
		var styles=EnumSet.noneOf(MushroomEffectStyle.class);
		for(var move:MushroomComboPattern.Move.values()) styles.add(MushroomEffectStyle.forMove(move));
		assertEquals(MushroomComboPattern.Move.values().length,styles.size());
	}
	@Test void everyIndependentAttackAndWaveIsCovered() {
		for(int attack=1;attack<=MushroomKingAttackRules.ATTACK_COUNT;attack++) {
			for(int wave=1;wave<=MushroomKingAttackRules.waveCount(attack);wave++) {
				assertNotNull(MushroomEffectStyle.forAttack(attack,wave));
			}
		}
		assertThrows(IllegalArgumentException.class,()->MushroomEffectStyle.forAttack(99,1));
	}
	@Test void releaseEffectsNeverPrecedeImpactOrOutliveTheirTail() {
		assertEquals(0,MushroomEffectStyle.releaseFade(-.1F,16));
		assertEquals(1,MushroomEffectStyle.releaseFade(0,16));
		assertEquals(.5F,MushroomEffectStyle.releaseFade(8,16));
		assertEquals(0,MushroomEffectStyle.releaseFade(16,16));
		assertEquals(0,MushroomEffectStyle.releaseFade(25,16));
		assertEquals(0,MushroomEffectStyle.releaseFade(1,0));
	}
}
