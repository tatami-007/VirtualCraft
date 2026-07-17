package dev.tatami.virtualcraft.boss;

public final class BossEncounterScaling {
	private static final double EXTRA_PLAYER_HEALTH = 0.45;
	private static final double MAX_HEALTH_SCALE = 2.35;

	private BossEncounterScaling() {
	}

	public static double healthScale(int players) {
		int participants = Math.max(1, players);
		return Math.min(MAX_HEALTH_SCALE, 1.0 + (participants - 1) * EXTRA_PLAYER_HEALTH);
	}
}
