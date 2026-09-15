package dev.tatami.virtualcraft.boss;

public final class BossPostureRules {
	public static final int MAX_POSTURE = 100;
	public static final int SEGMENT_SIZE = 20;
	public static final int SNAKE_SEGMENT_COUNT = 6;
	public static final int SNAKE_MAX_POSTURE = SEGMENT_SIZE * SNAKE_SEGMENT_COUNT;
	public static final int MUSHROOM_SEGMENT_COUNT = 6;
	public static final int MUSHROOM_MAX_POSTURE = SEGMENT_SIZE * MUSHROOM_SEGMENT_COUNT;
	public static final int STAGGER_TICKS = 80;
	public static final int DISENGAGE_RESET_TICKS = 200;
	public static final int JUMP_STRIKE_WINDOW_TICKS = 30;
	public static final int LOW_DODGE_AIRBORNE_TICKS = 30;
	public static final int LOW_DODGE_LANDING_GRACE_TICKS = 5;
	public static final int PARRY_WINDOW_TICKS = 15;
	public static final int SHIELD_BREAK_TICKS = 100;
	public static final float MIN_CHARGE = 0.85F;
	public static final float GUARDED_DAMAGE_MULTIPLIER = 0.40F;
	public static final float STAGGER_DAMAGE_MULTIPLIER = 1.50F;
	public static final float PARRY_POSTURE_DAMAGE = SEGMENT_SIZE;
	public static final float JUMP_STRIKE_POSTURE_DAMAGE = 3.0F;
	public static final float HIGH_WHIFF_POSTURE_DAMAGE = 4.0F;

	private BossPostureRules() {
	}

	public static float chargedMeleePressure(float weaponDamage, float charge) {
		if (charge < MIN_CHARGE || weaponDamage <= 0.0F) {
			return 0.0F;
		}
		return clamp(weaponDamage / 16.0F, 0.35F, 0.75F);
	}

	public static float preciseParryDamage() {
		return PARRY_POSTURE_DAMAGE;
	}

	public static float jumpStrikeDamage() {
		return JUMP_STRIKE_POSTURE_DAMAGE;
	}

	public static boolean isPreciseParry(boolean usingShield, int itemUseTicks) {
		return usingShield && itemUseTicks >= 0 && itemUseTicks < PARRY_WINDOW_TICKS;
	}

	public static boolean isNormalJumpStart(boolean onGround, boolean flying, boolean fallFlying,
			boolean swimming, double verticalMovement, float fallDistance) {
		return !onGround && !flying && !fallFlying && !swimming
				&& verticalMovement > 0.045 && fallDistance <= 0.25F;
	}

	public static boolean isLowDodgeWindow(boolean onGround, boolean invalidMovement,
			boolean airborneWindow, boolean landingGrace) {
		return !invalidMovement && ((!onGround && airborneWindow) || (onGround && landingGrace));
	}

	public static int scaleSavedPosture(int savedPosture, int oldMaximum, int newMaximum) {
		if (oldMaximum <= 0 || newMaximum <= 0) {
			return Math.max(0, newMaximum);
		}
		float ratio = Math.max(0.0F, Math.min(1.0F, savedPosture / (float)oldMaximum));
		return Math.round(ratio * newMaximum);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
