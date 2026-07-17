package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import dev.tatami.virtualcraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Local-only camera and flash response sent to the player who actually interacted with an attack. */
public final class SnakeBossCombatFeedback {
	private static int shakeTicks;
	private static int shakeDuration;
	private static float shakeStrength;
	private static int flashTicks;
	private static int flashDuration;
	private static int feedbackKind = SnakeCombatFeedbackPayload.HIT;
	private static float lateralDirection;
	private static float forwardDirection = 1.0F;
	private static int comboTutorialTicks;
	private static int comboTutorialDuration;
	private static int comboTutorialKind = -1;

	private SnakeBossCombatFeedback() {
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			reset();
			return;
		}
		if (shakeTicks > 0) {
			shakeTicks--;
			if (shakeTicks == 0) {
				shakeDuration = 0;
				shakeStrength = 0.0F;
			}
		}
		if (flashTicks > 0) {
			flashTicks--;
			if (flashTicks == 0) {
				flashDuration = 0;
			}
		}
		if (comboTutorialTicks > 0) {
			comboTutorialTicks--;
			if (comboTutorialTicks == 0) {
				comboTutorialDuration = 0;
				comboTutorialKind = -1;
			}
		}
	}

	public static void accept(int bossEntityId, int kind, float strength) {
		if (isComboTutorial(kind)) {
			comboTutorialTicks = 72;
			comboTutorialDuration = 72;
			comboTutorialKind = kind;
			updateImpactDirection(bossEntityId);
			return;
		}
		boolean teachingCue = kind == SnakeCombatFeedbackPayload.HIGH_CUE
				|| kind == SnakeCombatFeedbackPayload.MID_CUE
				|| kind == SnakeCombatFeedbackPayload.LOW_CUE;
		int duration = switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY -> 4;
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> 6;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 9;
			case SnakeCombatFeedbackPayload.HIGH_DODGE, SnakeCombatFeedbackPayload.LOW_DODGE -> 4;
			default -> 5;
		};
		if (!teachingCue) {
			shakeTicks = Math.max(shakeTicks, duration);
			shakeDuration = Math.max(shakeDuration, duration);
			shakeStrength = Math.max(shakeStrength, Mth.clamp(strength, 0.15F, 2.0F));
		}
		int flash = switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY -> 4;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 7;
			case SnakeCombatFeedbackPayload.HIGH_DODGE, SnakeCombatFeedbackPayload.LOW_DODGE -> 4;
			case SnakeCombatFeedbackPayload.HIGH_CUE, SnakeCombatFeedbackPayload.MID_CUE,
					SnakeCombatFeedbackPayload.LOW_CUE -> 12;
			default -> 3;
		};
		flashTicks = Math.max(flashTicks, flash);
		flashDuration = Math.max(flashDuration, flash);
		feedbackKind = kind;
		updateImpactDirection(bossEntityId);
		playLocalFeedbackSound(kind);
	}

	private static void playLocalFeedbackSound(int kind) {
		Minecraft minecraft = Minecraft.getInstance();
		switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY -> {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_PARRY.get(), 0.94F, 1.8F));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_IMPACT.get(), 1.22F, 0.72F));
			}
			case SnakeCombatFeedbackPayload.HIGH_DODGE -> {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_HIGH_DODGE.get(), 1.0F, 1.75F));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_DODGE.get(), 1.18F, 0.65F));
			}
			case SnakeCombatFeedbackPayload.LOW_DODGE -> {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_LOW_DODGE.get(), 0.96F, 1.8F));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_DODGE.get(), 0.82F, 0.68F));
			}
			default -> {
			}
		}
	}

	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		if (shakeTicks <= 0 || shakeDuration <= 0) {
			return;
		}
		float elapsed = shakeDuration - shakeTicks + (float)event.getPartialTick();
		float fade = shakeTicks / (float)shakeDuration;
		float impulse = Mth.sin(elapsed * 1.55F) * (float)Math.exp(-elapsed * 0.19F);
		float rumble = Mth.sin(elapsed * 0.68F + 0.8F) * fade * fade;
		float strength = shakeStrength * (impulse * 0.82F + rumble * 0.18F);
		event.setYaw(event.getYaw() + strength * (0.18F + lateralDirection * 0.34F));
		event.setPitch(event.getPitch() - strength * (0.48F + forwardDirection * 0.3F));
		event.setRoll(event.getRoll() + strength * lateralDirection * 0.42F);
	}

	public static float flashIntensity() {
		return flashTicks <= 0 || flashDuration <= 0 ? 0.0F : flashTicks / (float)flashDuration;
	}

	public static int feedbackKind() {
		return feedbackKind;
	}

	public static float lateralDirection() {
		return lateralDirection;
	}

	public static float comboTutorialIntensity() {
		if (comboTutorialTicks <= 0 || comboTutorialDuration <= 0) {
			return 0.0F;
		}
		int elapsed = comboTutorialDuration - comboTutorialTicks;
		float fadeIn = Mth.clamp(elapsed / 5.0F, 0.0F, 1.0F);
		float fadeOut = Mth.clamp(comboTutorialTicks / 8.0F, 0.0F, 1.0F);
		return Math.min(fadeIn, fadeOut);
	}

	public static int comboTutorialKind() {
		return comboTutorialKind;
	}

	private static boolean isComboTutorial(int kind) {
		return kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_BITE_CUE
				|| kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE
				|| kind == SnakeCombatFeedbackPayload.COMBO_BITE_TAIL_BITE_CUE;
	}

	private static void updateImpactDirection(int bossEntityId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			lateralDirection = 0.0F;
			forwardDirection = 1.0F;
			return;
		}
		Entity boss = minecraft.level.getEntity(bossEntityId);
		if (boss == null) {
			lateralDirection = 0.0F;
			forwardDirection = 1.0F;
			return;
		}
		Vec3 towardBoss = boss.position().subtract(minecraft.player.position()).multiply(1.0, 0.0, 1.0);
		if (towardBoss.lengthSqr() < 1.0E-5) {
			lateralDirection = 0.0F;
			forwardDirection = 1.0F;
			return;
		}
		Vec3 direction = towardBoss.normalize();
		Vec3 view = minecraft.player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize();
		lateralDirection = Mth.clamp((float)(view.x * direction.z - view.z * direction.x), -1.0F, 1.0F);
		forwardDirection = Mth.clamp((float)view.dot(direction), -1.0F, 1.0F);
	}

	private static void reset() {
		shakeTicks = 0;
		shakeDuration = 0;
		shakeStrength = 0.0F;
		flashTicks = 0;
		flashDuration = 0;
		feedbackKind = SnakeCombatFeedbackPayload.HIT;
		lateralDirection = 0.0F;
		forwardDirection = 1.0F;
		comboTutorialTicks = 0;
		comboTutorialDuration = 0;
		comboTutorialKind = -1;
	}
}
