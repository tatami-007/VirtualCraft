package dev.tatami.virtualcraft.boss;

public final class SnakeKingAttackRules {
	private SnakeKingAttackRules() {
	}

	public static BossAttackDirection directionFor(int attackId) {
		return switch (attackId) {
			case 4, 6, 10 -> BossAttackDirection.HIGH;
			case 5, 8, 9 -> BossAttackDirection.LOW;
			default -> BossAttackDirection.MID;
		};
	}
}
