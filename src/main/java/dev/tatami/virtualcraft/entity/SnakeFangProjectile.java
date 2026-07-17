package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import dev.tatami.virtualcraft.registry.ModSounds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SnakeFangProjectile extends ThrowableProjectile {
	private static final EntityDataAccessor<Integer> TARGET_ID =
			SynchedEntityData.defineId(SnakeFangProjectile.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DIRECTION =
			SynchedEntityData.defineId(SnakeFangProjectile.class, EntityDataSerializers.INT);
	@Nullable
	private UUID targetUuid;
	@Nullable
	private UUID attackInstanceUuid;
	private boolean resolved;

	public SnakeFangProjectile(EntityType<? extends SnakeFangProjectile> type, Level level) {
		super(type, level);
	}

	public void setTarget(@Nullable LivingEntity target) {
		entityData.set(TARGET_ID, target == null ? -1 : target.getId());
		targetUuid = target == null ? null : target.getUUID();
	}

	public void setAttackInstance(@Nullable UUID attackInstanceUuid, BossAttackDirection direction) {
		this.attackInstanceUuid = attackInstanceUuid;
		entityData.set(DIRECTION, direction.ordinal());
	}

	@Nullable
	private LivingEntity target() {
		Entity target = level().getEntity(entityData.get(TARGET_ID));
		if (target instanceof LivingEntity living && living.isAlive()) {
			return living;
		}
		if (targetUuid != null && level() instanceof ServerLevel serverLevel
				&& serverLevel.getEntity(targetUuid) instanceof LivingEntity living && living.isAlive()) {
			entityData.set(TARGET_ID, living.getId());
			return living;
		}
		return null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TARGET_ID, -1);
		builder.define(DIRECTION, BossAttackDirection.HIGH.ordinal());
	}

	@Override
	public void tick() {
		if (!level().isClientSide && getOwner() instanceof VirtualSnakeKing snake
				&& !snake.isAttackInstanceActive(attackInstanceUuid)) {
			resolve(false);
			discard();
			return;
		}
		LivingEntity target = target();
		if (target != null && tickCount > 5) {
			Vec3 desired = target.getEyePosition().subtract(position()).normalize().scale(0.72);
			setDeltaMovement(getDeltaMovement().lerp(desired, 0.075));
		}
		super.tick();
		if (level().isClientSide) {
			level().addParticle(ModParticleTypes.SNAKE_TRAIL.get(), getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
		if (tickCount > 70) {
			if (!level().isClientSide) {
				resolve(false);
			}
			discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!level().isClientSide && (!(result.getEntity() instanceof Player player)
				|| player.isCreative() || player.isSpectator())) {
			return;
		}
		if (!level().isClientSide && result.getEntity() instanceof Player target && result.getEntity() != getOwner()) {
			LivingEntity owner = getOwner() instanceof LivingEntity living ? living : null;
			boolean hitPlayer = false;
			if (owner instanceof VirtualSnakeKing snake
					&& snake.canReceiveDataFangHit(attackInstanceUuid, target.getUUID())) {
				hitPlayer = snake.resolveDirectedHit(target, damageSources().mobProjectile(this, owner), 9.0F,
						0.45, true, BossAttackDirection.fromId(entityData.get(DIRECTION)));
				if (hitPlayer) {
					snake.registerDataFangHit(attackInstanceUuid, target.getUUID());
				}
			}
			resolve(hitPlayer);
			burst();
			discard();
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide && result.getType() == HitResult.Type.BLOCK) {
			resolve(false);
			burst();
			discard();
		}
	}

	private void burst() {
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), getX(), getY(), getZ(), 3,
					0.28, 0.28, 0.28, 0.045);
			serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), getX(), getY(), getZ(), 5,
					0.38, 0.38, 0.38, 0.018);
			serverLevel.playSound(null, blockPosition(), ModSounds.SNAKE_KING_IMPACT.get(),
					SoundSource.HOSTILE, 1.1F, 0.94F);
		}
	}

	private void resolve(boolean hitPlayer) {
		if (resolved) {
			return;
		}
		resolved = true;
		if (getOwner() instanceof VirtualSnakeKing snake) {
			snake.resolveDataFang(attackInstanceUuid, hitPlayer);
		}
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (targetUuid != null) {
			tag.putUUID("Target", targetUuid);
		}
		if (attackInstanceUuid != null) {
			tag.putUUID("AttackInstance", attackInstanceUuid);
		}
		tag.putInt("Direction", entityData.get(DIRECTION));
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
		attackInstanceUuid = tag.hasUUID("AttackInstance") ? tag.getUUID("AttackInstance")
				: tag.hasUUID("Volley") ? tag.getUUID("Volley") : null;
		entityData.set(DIRECTION, BossAttackDirection.fromId(tag.getInt("Direction")).ordinal());
	}
}
