package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Keeps one smoothly fading, non-positional music instance for the nearest active snake king. */
final class SnakeBossMusic {
	private static final double MUSIC_RANGE = 72.0;
	@Nullable
	private static MusicInstance current;

	private SnakeBossMusic() {
	}

	static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			fadeCurrent();
			return;
		}
		VirtualSnakeKing boss = null;
		double nearestDistance = MUSIC_RANGE * MUSIC_RANGE;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualSnakeKing snake) || !snake.isAlive()
					|| !snake.isCombatActive()) {
				continue;
			}
			double distance = snake.distanceToSqr(minecraft.player);
			if (distance <= nearestDistance) {
				boss = snake;
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
			super(ModSounds.SNAKE_KING_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());
			this.bossId = bossId;
			this.looping = true;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.02F;
		}

		private void follow(int bossId) {
			this.bossId = bossId;
			this.fadingOut = false;
		}

		private void fadeOut() {
			this.fadingOut = true;
		}

		private int age() {
			return age;
		}

		@Override
		public void tick() {
			age++;
			Minecraft minecraft = Minecraft.getInstance();
			boolean validBoss = minecraft.level != null
					&& minecraft.level.getEntity(bossId) instanceof VirtualSnakeKing snake
					&& snake.isAlive() && snake.isCombatActive()
					&& minecraft.player != null
					&& snake.distanceToSqr(minecraft.player) <= MUSIC_RANGE * MUSIC_RANGE;
			if (!validBoss) {
				fadingOut = true;
			}
			float target = fadingOut ? 0.0F : 0.78F;
			volume = Mth.lerp(fadingOut ? 0.065F : 0.075F, volume, target);
			if (fadingOut && volume <= 0.01F) {
				stop();
			}
		}
	}
}
