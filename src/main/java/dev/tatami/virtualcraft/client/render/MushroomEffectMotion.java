package dev.tatami.virtualcraft.client.render;

/** Continuous profiles shared by the boss's procedural meshes; no frame-random noise. */
final class MushroomEffectMotion {
	private MushroomEffectMotion() {}

	static float smooth(float value) {
		float t = Math.max(0, Math.min(1, value));
		return t * t * (3 - 2 * t);
	}

	/** Slow preparation followed by a very short acceleration into the real impact beat. */
	static float strike(float age, float windup) {
		return 0.15F * smooth((age + windup) / windup)
				+ 0.85F * smooth((age + 1.25F) / 1.25F);
	}

	static double ribbonWidth(double progress) {
		double t = Math.max(0, Math.min(1, progress));
		return Math.sin(Math.PI * t) * (.35 + .65 * t);
	}

	static double ribbonBend(double progress) {
		return Math.sin(Math.PI * progress) * Math.sin(Math.PI * progress * 1.5);
	}

	static double rootRise(float age, float lifetime) {
		if (age <= 0 || age >= lifetime || lifetime <= 0) return 0;
		return smooth(age / 2.5F) * (1 - smooth((age - lifetime * .55F) / (lifetime * .45F)));
	}
}
