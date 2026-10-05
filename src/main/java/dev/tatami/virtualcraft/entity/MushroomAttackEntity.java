package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.MushroomAttackForm;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import dev.tatami.virtualcraft.boss.MushroomRangedStrike;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Visible attack body. The owning boss advances damage even if this body's chunk stops ticking. */
public final class MushroomAttackEntity extends Entity {
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(MushroomAttackEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POWER = SynchedEntityData.defineId(MushroomAttackEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ARRIVAL = SynchedEntityData.defineId(MushroomAttackEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> RELEASE = SynchedEntityData.defineId(MushroomAttackEntity.class, EntityDataSerializers.LONG);
    private MushroomRangedStrike strike;
    private MushroomCombatRules.Hit hit;
    private VirtualMushroomKing owner;
    private Vec3 interpolationTarget;
    private int interpolationTicks;
    private float interpolationYaw;
    private float releaseYaw;

    public MushroomAttackEntity(EntityType<? extends MushroomAttackEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, 1);
        builder.define(POWER, 0);
        builder.define(ARRIVAL, 0L);
        builder.define(RELEASE, 0L);
    }

    public void launch(VirtualMushroomKing owner, ServerPlayer target, dev.tatami.virtualcraft.combat.AttackKey key,
            MushroomCombatRules.Hit hit, Vec3 origin) {
        this.owner = owner;
        this.hit = hit;
        long now = level().getGameTime();
        strike = new MushroomRangedStrike(key, now, Math.hypot(target.getX() - owner.getX(), target.getZ() - owner.getZ()));
        entityData.set(FORM, hit.form().ordinal());
        entityData.set(POWER, key.strike());
        entityData.set(RELEASE, now);
        entityData.set(ARRIVAL, strike.arrival());
        setPos(hit.form() == MushroomAttackForm.HUNTING_CORE ? origin : ground(target));
        setYRot(owner.getYRot());
        releaseYaw=owner.getYRot();
    }

    public MushroomAttackForm form() { return MushroomAttackForm.values()[entityData.get(FORM)]; }
    public int power() { return entityData.get(POWER); }
    public double impactAge(float partialTick) { return level().getGameTime() - entityData.get(ARRIVAL) + partialTick; }
    public double flightAge(float partialTick) { return level().getGameTime() - entityData.get(RELEASE) + partialTick; }

    public boolean advanceAttack(VirtualMushroomKing boss) {
        if (strike == null || boss != owner || !boss.isAlive() || !strike.key().encounter().equals(boss.encounterId())) {
            discard();
            return false;
        }
        ServerPlayer target = boss.getServer().getPlayerList().getPlayer(strike.key().target());
        if (target == null || !boss.validRangedTarget(target)) {
            discard();
            return false;
        }
        long now = level().getGameTime();
        Vec3 destination = ground(target);
        if (form() == MushroomAttackForm.HUNTING_CORE && now < strike.arrival()) {
            Vec3 delta = destination.subtract(position());
            setYRot((float)Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90);
            setPos(position().add(delta.scale(1.0 / Math.max(1, strike.arrival() - now + 1))));
        } else {
            setPos(destination);
        }
        if (strike.handleOnce(now, boss.encounterId(), target.getUUID(), true)) boss.resolveRangedHit(target, hit, releaseYaw);
        if (now >= strike.arrival() + 8) {
            discard();
            return false;
        }
        return true;
    }

    private Vec3 ground(ServerPlayer target) {
        Vec3 top = target.position().add(0, 0.1, 0);
        var result = level().clip(new ClipContext(top, top.add(0, -6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, target));
        return result.getType() == HitResult.Type.BLOCK ? result.getLocation() : target.position();
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide && interpolationTicks > 0) {
            setPos(position().lerp(interpolationTarget, 1.0 / interpolationTicks));
            setYRot(getYRot() + net.minecraft.util.Mth.wrapDegrees(interpolationYaw - getYRot()) / interpolationTicks);
            interpolationTicks--;
        }
        if (!level().isClientSide && (owner == null || owner.isRemoved() || !owner.isAlive())) discard();
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolationTarget = new Vec3(x,y,z);
        interpolationYaw = yaw;
        interpolationTicks = Math.max(1, Math.min(steps, 2));
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
}
