package dev.tatami.virtualcraft.boss;

import java.util.ArrayList;
import java.util.List;

/** Distance-aware selector with authored combos as the only mid-direction attacks. */
public final class MushroomKingCombatDirector {
	public static final int ATTACK_COUNT = MushroomKingAttackRules.ATTACK_COUNT;
	private static final Profile[] PROFILES = new Profile[ATTACK_COUNT + 1];

	static {
		profile(MushroomKingAttackRules.ROOT_CADENCE, 0.0, 8.5, 58.0, 28, false);
		disabled(MushroomKingAttackRules.STEM_HAMMER);
		disabled(MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY);
		disabled(MushroomKingAttackRules.CAP_CLAP);
		profile(MushroomKingAttackRules.CANOPY_CLOSURE, 0.0, 10.0, 25.0, 54, true);
		disabled(MushroomKingAttackRules.SPORE_BREATH);
		profile(MushroomKingAttackRules.MYCELIAL_SURGE, 0.0, 9.5, 31.0, 44, true);
		profile(MushroomKingAttackRules.ROOT_CONVERGENCE, 0.0, 24.0, 12.0, 720, true);
	}

	private MushroomKingCombatDirector() {
	}

	public static int select(Context context, double roll) {
		validate(context);
		List<Integer> readyAttacks = new ArrayList<>();
		for (int attack = 1; attack <= ATTACK_COUNT; attack++) {
			if (isReady(context, attack, PROFILES[attack])) {
				readyAttacks.add(attack);
			}
		}
		boolean canChangeFamily = readyAttacks.stream()
				.anyMatch(attack -> attack != context.lastAttack());
		List<WeightedAttack> candidates = new ArrayList<>();
		double totalWeight = 0.0;
		for (int attack : readyAttacks) {
			Profile profile = PROFILES[attack];
			if (canChangeFamily && attack == context.lastAttack()) {
				continue;
			}
			double weight = adjustedWeight(context, attack, profile);
			if (weight <= 0.0) {
				continue;
			}
			totalWeight += weight;
			candidates.add(new WeightedAttack(attack, totalWeight));
		}
		if (candidates.isEmpty()) {
			return 0;
		}
		double target = Math.max(0.0, Math.min(0.999999, roll)) * totalWeight;
		for (WeightedAttack candidate : candidates) {
			if (target < candidate.cumulativeWeight()) {
				return candidate.attack();
			}
		}
		return candidates.getLast().attack();
	}

	public static int cooldown(int attackId) {
		return MushroomKingAttackRules.isKnownAttack(attackId)
				? PROFILES[attackId].cooldown() : 40;
	}

	private static boolean isReady(Context context, int attack, Profile profile) {
		if (attack == MushroomKingAttackRules.ROOT_CONVERGENCE
				&& context.combatTicks() < MushroomKingAttackRules.ULTIMATE_UNLOCK_COMBAT_TICKS) {
			return false;
		}
		if (context.tick() < context.readyTicks()[attack]
				|| context.distance() < profile.minRange()
				|| context.distance() > profile.maxRange()) {
			return false;
		}
		return context.tick() >= context.directionReadyTicks()[
				MushroomKingAttackRules.directionFor(attack).ordinal()];
	}

	private static double adjustedWeight(Context context, int attack, Profile profile) {
		double weight = profile.weight();
		if (attack == MushroomKingAttackRules.ROOT_CONVERGENCE) {
			// Once unlocked it steadily becomes more likely instead of relying on a tiny roll.
			weight *= 1.0 + Math.min(3.0,
					Math.max(0, context.combatTicks() - 650) / 180.0);
		}
		BossAttackDirection direction = MushroomKingAttackRules.directionFor(attack);
		for (int recent : context.recentAttacks()) {
			if (recent == attack) {
				weight *= 0.04;
			}
		}
		if (lastTwoShareDirection(context.recentAttacks(), direction)) {
			weight *= 0.22;
		}
		if (context.lastAttack() > 0
				&& MushroomKingAttackRules.directionFor(context.lastAttack()) != direction) {
			weight *= 1.15;
		}
		if (profile.areaAttack() && context.playerCount() > 1) {
			weight *= 1.0 + Math.min(0.75, (context.playerCount() - 1) * 0.2);
		}
		if (direction == BossAttackDirection.MID && context.distance() <= 6.0) {
			weight *= 1.35;
		}
		if (context.distance() <= 6.5 && attack == MushroomKingAttackRules.ROOT_CADENCE) {
			weight *= 1.55;
		}
		return weight;
	}

	private static boolean lastTwoShareDirection(int[] recent, BossAttackDirection direction) {
		return recent.length >= 2
				&& MushroomKingAttackRules.isKnownAttack(recent[recent.length - 1])
				&& MushroomKingAttackRules.isKnownAttack(recent[recent.length - 2])
				&& MushroomKingAttackRules.directionFor(recent[recent.length - 1]) == direction
				&& MushroomKingAttackRules.directionFor(recent[recent.length - 2]) == direction;
	}

	private static void validate(Context context) {
		if (context.directionReadyTicks().length < BossAttackDirection.values().length) {
			throw new IllegalArgumentException("directionReadyTicks is too short");
		}
		if (context.readyTicks().length <= ATTACK_COUNT) {
			throw new IllegalArgumentException("readyTicks is too short");
		}
	}

	private static void profile(int attack, double minRange, double maxRange, double weight,
			int cooldown, boolean areaAttack) {
		PROFILES[attack] = new Profile(minRange, maxRange, weight, cooldown, areaAttack);
	}

	private static void disabled(int attack) {
		profile(attack, 0.0, 0.0, 0.0, Integer.MAX_VALUE, false);
	}

	public record Context(double distance, int tick, int combatTicks, int[] directionReadyTicks,
			int[] readyTicks, int[] recentAttacks, int lastAttack, int playerCount,
			double healthRatio) {
	}

	private record Profile(double minRange, double maxRange, double weight, int cooldown,
			boolean areaAttack) {
	}

	private record WeightedAttack(int attack, double cumulativeWeight) {
	}
}
