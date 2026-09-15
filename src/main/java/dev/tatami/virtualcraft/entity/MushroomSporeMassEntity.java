package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.registry.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Heavy arcing spore pod used by the Mushroom King's high attack. */
public final class MushroomSporeMassEntity extends Entity {
	private static final EntityDataAccessor<Integer> LIFETIME =
			SynchedEntityData.defineId(MushroomSporeMassEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SCALE =
			SynchedEntityData.defineId(MushroomSporeMassEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> OWNER_ID =
			SynchedEntityData.defineId(MushroomSporeMassEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> WAVE =
			SynchedEntityData.defineId(MushroomSporeMassEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> IMPACT_AGE =
			SynchedEntityData.defineId(MushroomSporeMassEntity.class, EntityDataSerializers.INT);

	public MushroomSporeMassEntity(EntityType<? extends MushroomSporeMassEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	public void configure(int lifetime, float scale, int ownerId, int wave) {
		entityData.set(LIFETIME, Math.max(1, lifetime));
		entityData.set(SCALE, Math.max(0.25F, scale));
		entityData.set(OWNER_ID, ownerId);
		entityData.set(WAVE, Math.max(1, wave));
	}

	public float visualScale(float partialTick) {
		int impactAge = entityData.get(IMPACT_AGE);
		if (impactAge >= 0) {
			float impact = Math.min(1.0F, (impactAge + partialTick) / 6.0F);
			return getScale() * (1.0F + impact * 1.35F);
		}
		float progress = Math.min(1.0F, (tickCount + partialTick) / Math.max(1.0F, getLifetime()));
		return getScale() * (0.82F + 0.18F * (float)Math.sin(progress * Math.PI * 2.0));
	}

	public float visualAlpha(float partialTick) {
		int impactAge = entityData.get(IMPACT_AGE);
		if (impactAge >= 0) {
			return Math.max(0.0F, 1.0F - (impactAge + partialTick) / 6.0F);
		}
		float progress = Math.min(1.0F, (tickCount + partialTick) / Math.max(1.0F, getLifetime()));
		return 0.9F + 0.1F * (float)Math.sin(progress * Math.PI);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LIFETIME, 18);
		builder.define(SCALE, 1.0F);
		builder.define(OWNER_ID, -1);
		builder.define(WAVE, 1);
		builder.define(IMPACT_AGE, -1);
	}

	@Override
	public void tick() {
		super.tick();
		int impactAge = entityData.get(IMPACT_AGE);
		if (impactAge >= 0) {
			if (impactAge >= 5) {
				discard();
			} else {
				entityData.set(IMPACT_AGE, impactAge + 1);
			}
			return;
		}

		Vec3 next = position().add(getDeltaMovement());
		setPos(next);
		setDeltaMovement(getDeltaMovement().scale(0.992).add(0.0, -0.0065, 0.0));
		if (level() instanceof ServerLevel serverLevel && tickCount % 2 == 0) {
			Vec3 trail = getDeltaMovement().normalize().scale(-0.42);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
					getX() + trail.x, getY() + trail.y, getZ() + trail.z,
					2, 0.18, 0.14, 0.18, 0.012);
			if (tickCount % 4 == 0) {
				serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
						getX(), getY(), getZ(), 1, 0.08, 0.08, 0.08, 0.0);
			}
		}

		boolean contacted = false;
		if (!level().isClientSide()
				&& level().getEntity(entityData.get(OWNER_ID)) instanceof VirtualMushroomKing owner) {
			contacted = owner.resolveSporeMassContact(position(), getScale() * 0.72,
					entityData.get(WAVE));
		}
		BlockPos blockPos = BlockPos.containing(next);
		boolean struckBlock = !level().getBlockState(blockPos)
				.getCollisionShape(level(), blockPos).isEmpty();
		if (contacted || struckBlock || tickCount >= getLifetime()) {
			impact();
		}
	}

	private void impact() {
		if (entityData.get(IMPACT_AGE) >= 0) {
			return;
		}
		entityData.set(IMPACT_AGE, 0);
		setDeltaMovement(Vec3.ZERO);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
					getX(), getY(), getZ(), 18, 0.72, 0.5, 0.72, 0.045);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
					getX(), getY(), getZ(), 8, 0.38, 0.3, 0.38, 0.02);
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
		configure(tag.getInt("Lifetime"), tag.getFloat("Scale"),
				tag.getInt("OwnerId"), tag.getInt("Wave"));
		entityData.set(IMPACT_AGE, tag.contains("ImpactAge") ? tag.getInt("ImpactAge") : -1);
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("Lifetime", getLifetime());
		tag.putFloat("Scale", getScale());
		tag.putInt("OwnerId", entityData.get(OWNER_ID));
		tag.putInt("Wave", entityData.get(WAVE));
		tag.putInt("ImpactAge", entityData.get(IMPACT_AGE));
	}
}
