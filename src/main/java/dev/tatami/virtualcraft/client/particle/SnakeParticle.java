package dev.tatami.virtualcraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class SnakeParticle extends TextureSheetParticle {
	public enum Style {
		RUNE, ARC, SHARD, TRAIL, HIGH_WARNING, MID_WARNING, LOW_WARNING,
		POISON_RED, POISON_BLUE, PARRY_FLASH
	}

	private final SpriteSet sprites;
	private final Style style;
	private final float startSize;
	private final float spin;

	private SnakeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
			SpriteSet sprites, Style style) {
		super(level, x, y, z, xd, yd, zd);
		this.sprites = sprites;
		this.style = style;
		hasPhysics = false;
		switch (style) {
			case RUNE -> {
				lifetime = 20;
				quadSize = 0.28F;
				yd += 0.004;
				gravity = 0.0F;
			}
			case ARC -> {
				lifetime = 5;
				quadSize = 0.22F;
				gravity = 0.0F;
			}
			case SHARD -> {
				lifetime = 14;
				quadSize = 0.2F;
				hasPhysics = true;
				gravity = 0.08F;
			}
			case TRAIL -> {
				lifetime = 8;
				quadSize = 0.32F;
				gravity = 0.0F;
			}
			case HIGH_WARNING, MID_WARNING, LOW_WARNING -> {
				lifetime = 6;
				quadSize = 0.22F;
				gravity = 0.0F;
			}
			case POISON_RED, POISON_BLUE -> {
				lifetime = 9;
				quadSize = 0.46F;
				gravity = 0.0F;
			}
			case PARRY_FLASH -> {
				lifetime = 3;
				quadSize = 0.42F;
				gravity = 0.0F;
			}
		}
		applyPalette(level);
		roll = style == Style.ARC ? 0.0F : level.random.nextFloat() * Mth.TWO_PI;
		oRoll = roll;
		spin = (level.random.nextBoolean() ? 1.0F : -1.0F) * switch (style) {
			case RUNE -> 0.055F;
			case ARC, HIGH_WARNING, MID_WARNING, LOW_WARNING, POISON_RED, POISON_BLUE, PARRY_FLASH -> 0.0F;
			case SHARD -> 0.16F;
			case TRAIL -> 0.035F;
		};
		startSize = quadSize;
		setSpriteFromAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		setSpriteFromAge(sprites);
		oRoll = roll;
		roll += spin;
		float life = 1.0F - age / (float)Math.max(1, lifetime);
		float peakAlpha = switch (style) {
			case ARC, HIGH_WARNING, MID_WARNING, LOW_WARNING, PARRY_FLASH -> 0.94F;
			case POISON_RED, POISON_BLUE -> 0.86F;
			case RUNE -> 0.72F;
			case SHARD -> 0.82F;
			case TRAIL -> 0.66F;
		};
		alpha = Math.min(peakAlpha, life * 1.8F);
		quadSize = switch (style) {
			case RUNE -> startSize * (0.86F + Mth.sin(age * 0.22F) * 0.04F);
			case ARC, HIGH_WARNING, MID_WARNING, LOW_WARNING -> startSize * (0.9F + life * 0.18F);
			case POISON_RED, POISON_BLUE -> startSize * (0.72F + life * 0.42F);
			case PARRY_FLASH -> startSize * (0.55F + life * 0.85F);
			case SHARD -> startSize * (0.55F + life * 0.65F);
			case TRAIL -> startSize * (0.45F + life * 0.7F);
		};
		double drag = style == Style.TRAIL ? 0.82 : style == Style.SHARD ? 0.96 : 0.9;
		xd *= drag;
		yd *= drag;
		zd *= drag;
	}

	private void applyPalette(ClientLevel level) {
		float variation = level.random.nextFloat();
		switch (style) {
			case RUNE -> setColor(0.55F + variation * 0.12F, 0.38F + variation * 0.08F, 0.78F + variation * 0.12F);
			case ARC -> setColor(1.0F, 0.76F + variation * 0.14F, 0.35F + variation * 0.2F);
			case SHARD -> setColor(0.38F + variation * 0.2F, 0.65F + variation * 0.12F, 0.72F + variation * 0.16F);
			case TRAIL -> setColor(0.34F + variation * 0.1F, 0.5F + variation * 0.12F, 0.68F + variation * 0.14F);
			case HIGH_WARNING -> setColor(1.0F, 0.68F + variation * 0.16F, 0.18F);
			case MID_WARNING -> setColor(0.96F, 0.88F + variation * 0.08F, 0.82F + variation * 0.08F);
			case LOW_WARNING -> setColor(0.28F + variation * 0.12F, 0.62F + variation * 0.12F, 0.86F + variation * 0.12F);
			case POISON_RED, POISON_BLUE, PARRY_FLASH -> setColor(1.0F, 1.0F, 1.0F);
		}
	}

	@Override
	public int getLightColor(float partialTick) {
		return switch (style) {
			case HIGH_WARNING, MID_WARNING, LOW_WARNING, ARC, POISON_RED, POISON_BLUE, PARRY_FLASH -> 0xF000F0;
			default -> 0xD000D0;
		};
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
		return (options, level, x, y, z, xd, yd, zd) -> {
			ParticleStatus setting = Minecraft.getInstance().options.particles().get();
			if ((style == Style.SHARD || style == Style.TRAIL)
					&& ((setting == ParticleStatus.MINIMAL && level.random.nextFloat() < 0.72F)
					|| (setting == ParticleStatus.DECREASED && level.random.nextFloat() < 0.42F))) {
				return null;
			}
			return new SnakeParticle(level, x, y, z, xd, yd, zd, sprites, style);
		};
	}
}
