package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import dev.tatami.virtualcraft.registry.ModSounds;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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
	private static int feedbackBossEntityId = -1;
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
		feedbackBossEntityId = bossEntityId;
		boolean mushroomBoss = isMushroomBoss(bossEntityId);
		if (isComboTutorial(kind)) {
			int tutorialDuration = mushroomBoss ? 120 : 72;
			comboTutorialTicks = tutorialDuration;
			comboTutorialDuration = tutorialDuration;
			comboTutorialKind = kind;
			updateImpactDirection(bossEntityId);
			return;
		}
		boolean teachingCue = kind == SnakeCombatFeedbackPayload.HIGH_CUE
				|| kind == SnakeCombatFeedbackPayload.MID_CUE
				|| kind == SnakeCombatFeedbackPayload.LOW_CUE;
		int duration = switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY, SnakeCombatFeedbackPayload.WEAPON_PARRY -> 4;
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> 6;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 9;
			case SnakeCombatFeedbackPayload.HIGH_DODGE, SnakeCombatFeedbackPayload.LOW_DODGE -> 4;
			default -> mushroomBoss ? 6 : 5;
		};
		if (!teachingCue) {
			shakeTicks = Math.max(shakeTicks, duration);
			shakeDuration = Math.max(shakeDuration, duration);
			float weightedStrength = mushroomBoss && kind == SnakeCombatFeedbackPayload.HIT
					? strength * 1.28F : strength;
			shakeStrength = Math.max(shakeStrength, Mth.clamp(weightedStrength, 0.15F, 2.0F));
		}
		int flash = switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY, SnakeCombatFeedbackPayload.WEAPON_PARRY -> 10;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 14;
			case SnakeCombatFeedbackPayload.HIGH_DODGE, SnakeCombatFeedbackPayload.LOW_DODGE -> 10;
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> 12;
			case SnakeCombatFeedbackPayload.HIGH_CUE, SnakeCombatFeedbackPayload.MID_CUE,
					SnakeCombatFeedbackPayload.LOW_CUE -> 26;
			default -> mushroomBoss ? 5 : 3;
		};
		flashTicks = Math.max(flashTicks, flash);
		flashDuration = Math.max(flashDuration, flash);
		feedbackKind = kind;
		updateImpactDirection(bossEntityId);
		playLocalFeedbackSound(bossEntityId, kind);
	}

	private static void playLocalFeedbackSound(int bossEntityId, int kind) {
		Minecraft minecraft = Minecraft.getInstance();
		if (kind == SnakeCombatFeedbackPayload.WEAPON_PARRY) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					SoundEvents.ANVIL_LAND, 1.0F, 1.0F));
			return;
		}
		if (kind == SnakeCombatFeedbackPayload.PARRY) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					SoundEvents.SHIELD_BLOCK, 1.0F, 1.3F));
			return;
		}
		if (kind == SnakeCombatFeedbackPayload.HIGH_DODGE) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					ModSounds.SNAKE_KING_HIGH_DODGE.get(), 1.0F, 1.2F));
			return;
		}
		if (kind == SnakeCombatFeedbackPayload.LOW_DODGE) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					ModSounds.SNAKE_KING_LOW_DODGE.get(), 1.0F, 1.2F));
			return;
		}
		if (minecraft.level != null
				&& minecraft.level.getEntity(bossEntityId) instanceof VirtualMushroomKing) {
			playStoneFeedbackSound(minecraft, kind);
			return;
		}
		switch (kind) {
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.SNAKE_KING_SHIELD_BREAK.get(), 1.0F, 0.86F));
			}
			default -> {
			}
		}
	}

	private static void playStoneFeedbackSound(Minecraft minecraft, int kind) {
		switch (kind) {
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> minecraft.getSoundManager().play(
					SimpleSoundInstance.forUI(ModSounds.MUSHROOM_KING_SHIELD_BREAK.get(), 1.0F, 1.0F));
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> minecraft.getSoundManager().play(
					SimpleSoundInstance.forUI(ModSounds.MUSHROOM_KING_STAGGER.get(), 0.9F, 1.0F));
			case SnakeCombatFeedbackPayload.HIT -> {
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.MUSHROOM_KING_STRIKE.get(), 0.78F, 1.0F));
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
						ModSounds.MUSHROOM_KING_ROOT_CADENCE.get(), 0.58F, 0.52F));
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
		boolean mushroomImpact = isMushroomBoss(feedbackBossEntityId)
				&& (feedbackKind == SnakeCombatFeedbackPayload.HIT
						|| feedbackKind == SnakeCombatFeedbackPayload.SHIELD_BREAK);
		// Mushroom impacts kick once, then recover quickly instead of a loose vibrating camera.
		float impulse = mushroomImpact
				? Mth.cos(elapsed * 1.1F) * (float)Math.exp(-elapsed * 0.55F)
				: Mth.sin(elapsed * 1.55F) * (float)Math.exp(-elapsed * 0.19F);
		float rumble = Mth.sin(elapsed * 0.68F + 0.8F) * fade * fade;
		float strength = shakeStrength * (mushroomImpact
				? impulse * 0.94F + rumble * 0.06F : impulse * 0.82F + rumble * 0.18F);
		event.setYaw(event.getYaw() + strength * (0.18F + lateralDirection * 0.34F));
		event.setPitch(event.getPitch() - strength * (0.48F + forwardDirection * 0.3F));
		event.setRoll(event.getRoll() + strength * lateralDirection * (mushroomImpact ? 0.18F : 0.42F));
	}

	public static float flashIntensity() {
		return flashTicks <= 0 || flashDuration <= 0 ? 0.0F : flashTicks / (float)flashDuration;
	}

	public static int feedbackKind() {
		return feedbackKind;
	}

	public static int feedbackBossEntityId() {
		return feedbackBossEntityId;
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
				|| kind == SnakeCombatFeedbackPayload.COMBO_BITE_TAIL_BITE_CUE
				|| kind == SnakeCombatFeedbackPayload.MUSHROOM_ROOT_COMBO_CUE
				|| kind == SnakeCombatFeedbackPayload.MUSHROOM_HAMMER_CUE
				|| kind == SnakeCombatFeedbackPayload.MUSHROOM_NEEDLE_COMBO_CUE
				|| kind == SnakeCombatFeedbackPayload.MUSHROOM_CLAP_CUE;
	}

	private static boolean isMushroomBoss(int bossEntityId) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null
				&& minecraft.level.getEntity(bossEntityId) instanceof VirtualMushroomKing;
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
		feedbackBossEntityId = -1;
		lateralDirection = 0.0F;
		forwardDirection = 1.0F;
		comboTutorialTicks = 0;
		comboTutorialDuration = 0;
		comboTutorialKind = -1;
	}
}
