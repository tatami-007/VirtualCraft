package dev.tatami.virtualcraft.boss;

public final class BossDamageRules {
	private BossDamageRules() {
	}

	public static float acceptedDamage(float rawDamage, float hitCap, float dpsCap, float currentBucket,
			double sourceDistance, double fullDamageRange, boolean staggered) {
		if (rawDamage <= 0.0F || hitCap <= 0.0F || dpsCap <= currentBucket) {
			return 0.0F;
		}
		float damage = Math.min(rawDamage, hitCap);
		if (sourceDistance >= 0.0 && fullDamageRange > 0.0) {
			double zeroRange = fullDamageRange * 1.5;
			if (sourceDistance >= zeroRange) {
				return 0.0F;
			}
			if (sourceDistance > fullDamageRange) {
				damage *= (float)((zeroRange - sourceDistance) / (zeroRange - fullDamageRange));
			}
		}
		damage *= staggered ? BossPostureRules.STAGGER_DAMAGE_MULTIPLIER
				: BossPostureRules.GUARDED_DAMAGE_MULTIPLIER;
		return Math.max(0.0F, Math.min(damage, dpsCap - currentBucket));
	}

	public static float decayedBucket(float currentBucket, float dpsCap) {
		return Math.max(0.0F, currentBucket - dpsCap / 20.0F);
	}
}
