package dev.tatami.virtualcraft.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DailyContractServiceTest {
	@Test
	void generationIsStableForSamePlayerAndDay() {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
		long day = 1_771_200_000L;

		assertEquals(DailyContractGenerator.generate(playerId, day), DailyContractGenerator.generate(playerId, day));
	}

	@Test
	void generationVariesAcrossPlayers() {
		long day = 1_771_200_000L;

		assertNotEquals(
				DailyContractGenerator.generate(UUID.fromString("00000000-0000-0000-0000-000000000123"), day),
				DailyContractGenerator.generate(UUID.fromString("00000000-0000-0000-0000-000000000456"), day));
	}

	@Test
	void generationProducesThreeRunnableContracts() {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
		List<DailyContract> contracts = DailyContractGenerator.generate(playerId, 1_771_200_000L);

		assertEquals(3, contracts.size());
		for (DailyContract contract : contracts) {
			assertTrue(contract.target() > 0L);
			assertTrue(contract.reward().amount() > 0L);
			assertTrue(contract.iconItemId().contains(":"));
		}
	}
}
