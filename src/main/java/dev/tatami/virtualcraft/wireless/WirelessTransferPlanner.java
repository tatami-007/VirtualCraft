package dev.tatami.virtualcraft.wireless;

import java.util.ArrayList;
import java.util.List;

public final class WirelessTransferPlanner {
	private WirelessTransferPlanner() {
	}

	public static List<Integer> planTransfers(int availableEnergy, List<Integer> receiverDemand) {
		List<Integer> planned = new ArrayList<>(receiverDemand.size());
		int remaining = Math.max(0, availableEnergy);
		for (int demand : receiverDemand) {
			int accepted = Math.min(Math.max(0, demand), remaining);
			planned.add(accepted);
			remaining -= accepted;
		}
		return planned;
	}
}
