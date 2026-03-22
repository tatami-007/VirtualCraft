package dev.tatami.virtualcraft.wireless;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class WirelessTransferPlannerTest {
	@Test
	void directRelayDoesNotInventLocalBufferAndUsesAvailableInput() {
		assertEquals(List.of(1200, 800, 500), WirelessTransferPlanner.planTransfers(2500, List.of(1200, 800, 500)));
	}

	@Test
	void directRelayStopsAtDownstreamDemand() {
		assertEquals(List.of(300, 0, 400), WirelessTransferPlanner.planTransfers(1000, List.of(300, 0, 400)));
	}

	@Test
	void directRelaySupportsUnboundedBandwidthByPassingFullInputThrough() {
		assertEquals(List.of(5000, 8000), WirelessTransferPlanner.planTransfers(13000, List.of(5000, 8000)));
	}
}
