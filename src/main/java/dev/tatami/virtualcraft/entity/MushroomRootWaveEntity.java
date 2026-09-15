package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.registry.ModParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Moving root ridge used by low attacks; damage remains server-authoritative on the boss timeline. */
public final class MushroomRootWaveEntity extends Entity {
	private static final EntityDataAccessor<Integer> LIFETIME =
			SynchedEntityData.defineId(MushroomRootWaveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> VARIANT =
			SynchedEntityData.defineId(MushroomRootWaveEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE =
			SynchedEntityData.defineId(MushroomRootWaveEntity.class, EntityDataSerializers.FLOAT);

	public MushroomRootWaveEntity(EntityType<? extends MushroomRootWaveEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	public void configure(int lifetime, int variant, float scale) {
		entityData.set(LIFETIME, Math.max(1, lifetime));
		entityData.set(VARIANT, Math.floorMod(variant, 4));
		entityData.set(SCALE, Math.max(0.25F, scale));
	}

	public float visualScale(float partialTick) {
		float progress = Math.min(1.0F, (tickCount + partialTick) / Math.max(1.0F, getLifetime()));
		return getScale() * (float)Math.sin(Math.PI * Math.min(1.0F, progress * 1.2F));
	}

	public float visualAlpha(float partialTick) {
		float progress = Math.min(1.0F, (tickCount + partialTick) / Math.max(1.0F, getLifetime()));
		return Math.max(0.0F, 1.0F - Math.max(0.0F, progress - 0.55F) / 0.45F);
	}

	public int getVariant() {
		return entityData.get(VARIANT);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LIFETIME, 16);
		builder.define(VARIANT, 0);
		builder.define(SCALE, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		setPos(position().add(getDeltaMovement()));
		setDeltaMovement(getDeltaMovement().scale(0.94));
		if (level() instanceof ServerLevel serverLevel && tickCount % 2 == 0) {
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
					getX(), getY() + 0.18, getZ(), 2,
					0.28, 0.08, 0.28, 0.025);
			if (tickCount % 4 == 0) {
				serverLevel.sendParticles(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
						getX(), getY() + 0.42, getZ(), 1,
						0.18, 0.12, 0.18, 0.008);
			}
		}
		if (tickCount >= getLifetime()) {
			discard();
		}
	}

	private int getLifetime() {
		return entityData.get(LIFETIME);
	}

	private float getScale() {
		return entityData.get(SCALE);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		configure(tag.getInt("Lifetime"), tag.getInt("Variant"), tag.getFloat("Scale"));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("Lifetime", getLifetime());
		tag.putInt("Variant", getVariant());
		tag.putFloat("Scale", getScale());
	}
}
