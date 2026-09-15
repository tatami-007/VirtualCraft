package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Smooth non-positional music for the nearest active Mushroom King encounter. */
final class MushroomBossMusic {
	private static final double MUSIC_RANGE = 76.0;
	@Nullable
	private static MusicInstance current;

	private MushroomBossMusic() {
	}

	static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			fadeCurrent();
			return;
		}
		VirtualMushroomKing boss = null;
		double nearestDistance = MUSIC_RANGE * MUSIC_RANGE;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualMushroomKing mushroom) || !mushroom.isCombatActive()) {
				continue;
			}
			double distance = mushroom.distanceToSqr(minecraft.player);
			if (distance <= nearestDistance) {
				boss = mushroom;
				nearestDistance = distance;
			}
		}
		if (boss == null) {
			fadeCurrent();
			return;
		}
		minecraft.getMusicManager().stopPlaying();
		if (current == null || current.isStopped()
				|| current.age() > 20 && !minecraft.getSoundManager().isActive(current)) {
			current = new MusicInstance(boss.getId());
			minecraft.getSoundManager().play(current);
		} else {
			current.follow(boss.getId());
		}
	}

	private static void fadeCurrent() {
		if (current != null) {
			current.fadeOut();
			if (current.isStopped()) {
				current = null;
			}
		}
	}

	private static final class MusicInstance extends AbstractTickableSoundInstance {
		private int bossId;
		private int age;
		private boolean fadingOut;

		private MusicInstance(int bossId) {
			super(ModSounds.MUSHROOM_KING_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());
			this.bossId = bossId;
			looping = true;
			relative = true;
			attenuation = SoundInstance.Attenuation.NONE;
			volume = 0.02F;
		}

		private void follow(int bossId) {
			this.bossId = bossId;
			fadingOut = false;
		}

		private void fadeOut() {
			fadingOut = true;
		}

		private int age() {
			return age;
		}

		@Override
		public void tick() {
			age++;
			Minecraft minecraft = Minecraft.getInstance();
			boolean validBoss = minecraft.level != null
					&& minecraft.level.getEntity(bossId) instanceof VirtualMushroomKing mushroom
					&& mushroom.isCombatActive()
					&& minecraft.player != null
					&& mushroom.distanceToSqr(minecraft.player) <= MUSIC_RANGE * MUSIC_RANGE;
			if (!validBoss) {
				fadingOut = true;
			}
			volume = Mth.lerp(fadingOut ? 0.065F : 0.075F, volume,
					fadingOut ? 0.0F : 0.78F);
			if (fadingOut && volume <= 0.01F) {
				stop();
			}
		}
	}
}
