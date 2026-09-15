package dev.tatami.virtualcraft.boss;

import java.util.ArrayList;
import java.util.List;

public final class SnakeKingCombatDirector {
	public static final int ATTACK_COUNT = 10;
	private static final Profile[] PROFILES = new Profile[ATTACK_COUNT + 1];

	static {
		// Ordinary melee is always taught through a three-step hunt pattern.
		// Single bite and tail IDs remain load-compatible but are not selected.
		profile(2, 0.0, 7.5, 72.0, 95, false, false);
		profile(4, 5.0, 12.0, 16.0, 160, false, false);
		profile(5, 2.5, 9.0, 14.0, 100, true, false);
		profile(6, 6.0, 16.0, 10.0, 260, true, false);
		// The former double dash remains disabled by design. The other legacy
		// attacks stay in the encounter at lower weights so melee remains primary.
		profile(8, 0.0, 7.5, 8.0, 240, true, false);
		profile(9, 3.0, 12.0, 3.0, 800, true, false);
		profile(10, 0.0, 6.5, 14.0, 200, false, false);
	}

	private SnakeKingCombatDirector() {
	}

	public static int select(Context context, double roll) {
		List<WeightedAttack> candidates = new ArrayList<>();
		double total = 0.0;
		for (int attack = 1; attack <= ATTACK_COUNT; attack++) {
			Profile profile = PROFILES[attack];
			if (profile == null) {
				continue;
			}
			if (context.tick() < context.readyTicks()[attack]
					|| context.distance() < profile.minRange() || context.distance() > profile.maxRange()) {
				continue;
			}
			BossAttackDirection direction = SnakeKingAttackRules.directionFor(attack);
			if (context.tick() < context.directionReadyTicks()[direction.ordinal()]) {
				continue;
			}
			double weight = profile.weight();
			for (int recent : context.recentAttacks()) {
				if (recent == attack) {
					weight *= 0.08;
				}
			}
			if (context.lastAttack() == attack) {
				weight *= 0.05;
			}
			if (lastTwoShareCategory(context.recentAttacks(), attack)) {
				weight *= 0.18;
			}
			if (lastTwoShareDirection(context.recentAttacks(), direction)) {
				weight *= 0.25;
			}
			if (context.lastAttack() > 0
					&& SnakeKingAttackRules.directionFor(context.lastAttack()) != direction) {
				weight *= 1.28;
			}
			if (profile.multiTarget() && context.playerCount() > 1) {
				weight *= 1.0 + Math.min(0.75, (context.playerCount() - 1) * 0.2);
			}
			if (profile.gapCloser() && context.distance() > 9.0) {
				weight *= 1.55;
			}
			if (context.healthRatio() < 0.35 && attack == 2) {
				weight *= 1.22;
			}
			if (weight > 0.0) {
				total += weight;
				candidates.add(new WeightedAttack(attack, total));
			}
		}
		if (candidates.isEmpty()) {
			return 0;
		}
		double target = Math.max(0.0, Math.min(0.999999, roll)) * total;
		for (WeightedAttack candidate : candidates) {
			if (target < candidate.cumulativeWeight()) {
				return candidate.attack();
			}
		}
		return candidates.getLast().attack();
	}

	private static boolean lastTwoShareCategory(int[] recent, int attack) {
		if (recent.length < 2) {
			return false;
		}
		boolean category = isCloseCombat(attack);
		return isCloseCombat(recent[recent.length - 1]) == category
				&& isCloseCombat(recent[recent.length - 2]) == category;
	}

	private static boolean lastTwoShareDirection(int[] recent, BossAttackDirection direction) {
		return recent.length >= 2
				&& SnakeKingAttackRules.directionFor(recent[recent.length - 1]) == direction
				&& SnakeKingAttackRules.directionFor(recent[recent.length - 2]) == direction;
	}

	private static boolean isCloseCombat(int attack) {
		return attack == 1 || attack == 2 || attack == 3 || attack == 10;
	}

	public static int cooldown(int attack) {
		return attack > 0 && attack < PROFILES.length && PROFILES[attack] != null
				? PROFILES[attack].cooldown() : 40;
	}

	public static int comboFollowUp(int attack, double distance, int tick, int[] readyTicks, double roll) {
		return 0;
	}

	private static void profile(int attack, double minRange, double maxRange, double weight, int cooldown,
			boolean multiTarget, boolean gapCloser) {
		PROFILES[attack] = new Profile(minRange, maxRange, weight, cooldown, multiTarget, gapCloser);
	}

	public record Context(double distance, int tick, int[] directionReadyTicks, int[] readyTicks,
			int[] recentAttacks, int lastAttack, int playerCount, double healthRatio) {
	}

	private record Profile(double minRange, double maxRange, double weight, int cooldown,
			boolean multiTarget, boolean gapCloser) {
	}

	private record WeightedAttack(int attack, double cumulativeWeight) {
	}
}
