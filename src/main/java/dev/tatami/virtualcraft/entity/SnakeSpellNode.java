package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SnakeSpellNode extends Entity {
	public static final int RUNE = 0;
	public static final int JUDGMENT = 1;
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SnakeSpellNode.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(SnakeSpellNode.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SnakeSpellNode.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DIRECTION = SynchedEntityData.defineId(
			SnakeSpellNode.class, EntityDataSerializers.INT);
	@Nullable
	private UUID ownerUuid;
	@Nullable
	private UUID attackInstanceUuid;

	public SnakeSpellNode(EntityType<? extends SnakeSpellNode> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	public void configure(VirtualSnakeKing owner, @Nullable UUID attackInstanceUuid,
			BossAttackDirection direction, int kind, int delay, float radius) {
		ownerUuid = owner.getUUID();
		this.attackInstanceUuid = attackInstanceUuid;
		entityData.set(DIRECTION, direction.ordinal());
		entityData.set(KIND, kind);
		entityData.set(DELAY, delay);
		entityData.set(RADIUS, radius);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, RUNE);
		builder.define(DELAY, 20);
		builder.define(RADIUS, 2.5F);
		builder.define(DIRECTION, BossAttackDirection.LOW.ordinal());
	}

	@Override
	public void tick() {
		super.tick();
		int delay = entityData.get(DELAY);
		float radius = entityData.get(RADIUS);
		if (level() instanceof ServerLevel serverLevel) {
			VirtualSnakeKing owner = owner();
			if (owner == null || !owner.isAttackInstanceActive(attackInstanceUuid)) {
				discard();
				return;
			}
			if (tickCount == delay) {
				detonate(serverLevel, radius);
			}
		}
		if (tickCount > delay + 8) {
			discard();
		}
	}

	private void detonate(ServerLevel level, float radius) {
		VirtualSnakeKing owner = owner();
		float damage = entityData.get(KIND) == JUDGMENT ? 16.0F : 10.0F;
		AABB area = getBoundingBox().inflate(radius, 2.5, radius);
		for (Player player : level.getEntitiesOfClass(Player.class, area,
				player -> player.isAlive() && !player.isCreative() && !player.isSpectator())) {
			if (distanceToSqr(player) <= radius * radius && owner != null) {
				owner.resolveDelayedDirectedHit(attackInstanceUuid, player, damage,
						entityData.get(KIND) == JUDGMENT ? 1.7 : 0.7, false,
						BossAttackDirection.fromId(entityData.get(DIRECTION)));
			}
		}
		if (entityData.get(KIND) != JUDGMENT) {
			level.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), getX(), getY() + 0.4, getZ(), 2,
					radius * 0.22, 0.2, radius * 0.22, 0.04);
		}
	}

	public int getKind() {
		return entityData.get(KIND);
	}

	public int getFuseTicks() {
		return entityData.get(DELAY);
	}

	public float getEffectRadius() {
		return entityData.get(RADIUS);
	}

	public BossAttackDirection getAttackDirection() {
		return BossAttackDirection.fromId(entityData.get(DIRECTION));
	}

	@Nullable
	private VirtualSnakeKing owner() {
		if (ownerUuid != null && level() instanceof ServerLevel serverLevel
				&& serverLevel.getEntity(ownerUuid) instanceof VirtualSnakeKing snake) {
			return snake;
		}
		return null;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerUuid != null) {
			tag.putUUID("Owner", ownerUuid);
		}
		if (attackInstanceUuid != null) {
			tag.putUUID("AttackInstance", attackInstanceUuid);
		}
		tag.putInt("Kind", entityData.get(KIND));
		tag.putInt("Delay", Math.max(1, entityData.get(DELAY) - tickCount));
		tag.putFloat("Radius", entityData.get(RADIUS));
		tag.putInt("Direction", entityData.get(DIRECTION));
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		attackInstanceUuid = tag.hasUUID("AttackInstance") ? tag.getUUID("AttackInstance") : null;
		entityData.set(KIND, tag.getInt("Kind"));
		entityData.set(DELAY, Math.max(1, tag.getInt("Delay")));
		entityData.set(RADIUS, Math.max(0.5F, tag.getFloat("Radius")));
		entityData.set(DIRECTION, BossAttackDirection.fromId(tag.getInt("Direction")).ordinal());
	}
}
