package dev.tatami.virtualcraft.client.overlay;

/** Tick-based delayed health chip, independent of monitor refresh rate. */
public final class BossHealthTrail {
	private float previous;
	private float value;
	private long lastTick;
	private long holdUntil;

	public BossHealthTrail(float health, long tick) {
		previous = value = health;
		lastTick = tick;
	}

	public float update(float health, long tick) {
		health = Math.max(0, Math.min(1, health));
		if (tick < lastTick || health > previous) {
			value = health;
			holdUntil = tick;
		} else if (health < previous) {
			holdUntil = tick + 8;
		}
		long decayTicks = Math.max(0, tick - Math.max(lastTick, holdUntil));
		value = Math.max(health, value - decayTicks * 0.0125F);
		previous = health;
		lastTick = tick;
		return value;
	}
}
