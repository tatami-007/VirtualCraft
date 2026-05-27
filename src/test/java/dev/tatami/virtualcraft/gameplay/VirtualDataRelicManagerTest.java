package dev.tatami.virtualcraft.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VirtualDataRelicManagerTest {
	@Test
	void sectorOriginIsStablePerPlayer() {
		UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

		assertEquals(VirtualDataSectorMath.originFor(playerId), VirtualDataSectorMath.originFor(playerId));
		assertEquals(64, VirtualDataSectorMath.originFor(playerId).y());
	}

	@Test
	void sectorOriginSeparatesDifferentPlayers() {
		UUID first = UUID.fromString("00000000-0000-0000-0000-000000000123");
		UUID second = UUID.fromString("00000000-0000-0000-0000-000000000456");

		assertNotEquals(VirtualDataSectorMath.originFor(first), VirtualDataSectorMath.originFor(second));
	}
}
