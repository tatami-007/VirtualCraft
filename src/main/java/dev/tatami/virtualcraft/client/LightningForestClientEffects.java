package dev.tatami.virtualcraft.client;

import org.joml.Vector3f;

import dev.tatami.virtualcraft.world.LightningForestEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Sparse yellow air arcs for the lightning forest biome. */
public final class LightningForestClientEffects {
	private static final DustParticleOptions ARC_GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.76F, 0.08F), 0.72F);

	private LightningForestClientEffects() {
	}

	public static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()
				|| minecraft.level.getGameTime() % 12 != 0
				|| !minecraft.level.getBiome(minecraft.player.blockPosition()).is(LightningForestEffects.LIGHTNING_FOREST)) {
			return;
		}

		RandomSource random = minecraft.player.getRandom();
		if (random.nextFloat() > 0.48F) {
			return;
		}

		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = 4.0 + random.nextDouble() * 8.0;
		double x = minecraft.player.getX() + Math.cos(angle) * distance;
		double z = minecraft.player.getZ() + Math.sin(angle) * distance;
		double y = minecraft.player.getY() + 2.0 + random.nextDouble() * 4.0;
		int segments = 4 + random.nextInt(3);

		for (int segment = 0; segment < segments; segment++) {
			x += (random.nextDouble() - 0.5) * 0.28;
			z += (random.nextDouble() - 0.5) * 0.28;
			y -= 0.48 + random.nextDouble() * 0.16;
			minecraft.level.addParticle(ARC_GOLD, x, y, z, 0.0, 0.0, 0.0);
			minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, -0.015, 0.0);
		}
	}
}
