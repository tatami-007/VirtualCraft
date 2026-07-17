package dev.tatami.virtualcraft.boss;

public enum BossAttackDirection {
	HIGH,
	MID,
	LOW;

	public static BossAttackDirection fromId(int id) {
		BossAttackDirection[] values = values();
		return id >= 0 && id < values.length ? values[id] : MID;
	}
}
