package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.combat.AttackType;
import dev.tatami.virtualcraft.boss.MushroomCombatDirector;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import dev.tatami.virtualcraft.combat.DefenseState;
import dev.tatami.virtualcraft.boss.MushroomRangedStrike;
import dev.tatami.virtualcraft.boss.MushroomPursuitState;
import dev.tatami.virtualcraft.combat.CombatDefenseService;
import dev.tatami.virtualcraft.combat.CombatImpactService;
import dev.tatami.virtualcraft.gameplay.MushroomAfflictionHandler;
import dev.tatami.virtualcraft.network.MushroomBossReactionPayload;
import dev.tatami.virtualcraft.registry.MushroomDamageTypes;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative encounter; animation clocks never control damage. */
public final class VirtualMushroomKing extends Monster implements GeoEntity, dev.tatami.virtualcraft.combat.BossCombatant {
    public static final float MODEL_HEIGHT = 103.0F / 16.0F;
    public static final float BODY_WIDTH = 5.25F;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final MushroomCombatDirector combat = new MushroomCombatDirector();
    private final MushroomPursuitState pursuit = new MushroomPursuitState();
    private final Map<UUID, Long> participants = new HashMap<>();
    private final Set<UUID> ultimateTargets = new HashSet<>();
    private int emptyTicks;
    private float lockedYaw;
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ACTION_START = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ACTION_SEQUENCE = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PARRY_SIDE = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> PARRY_START = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> PARRY_WEIGHT = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> IMPACT_START = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> IMPACT_WEIGHT = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> IMPACT_SIDE = SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
    private UUID encounterId = UUID.randomUUID();
    private UUID comboTarget;
    private int comboSequence;
    private final List<MushroomAttackEntity> rangedAttacks = new ArrayList<>();
    private double walkPhase, previousWalkPhase;
    private float turnPhase, previousTurnPhase;
    private double animationMovement;
    private float animationTurn;
    private float walkWeight, previousWalkWeight;
    private float turnWeight, previousTurnWeight;
    private float steeringYaw;
    private boolean steeringActive, pursuing, routeFailed;
    private long nextPathAt;
    private Vec3 navigationGoal;
    private int reactionSequence;

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, -1);
        builder.define(ACTION_START, 0L);
        builder.define(ACTION_SEQUENCE, 0);
        builder.define(PARRY_SIDE, 0);
        builder.define(PARRY_START, -100L);
        builder.define(PARRY_WEIGHT, 0F);
        builder.define(IMPACT_START, -100L);
        builder.define(IMPACT_WEIGHT, 0F);
        builder.define(IMPACT_SIDE, 1F);
    }

    public int animationAction() { return entityData.get(ACTION); }
    public int animationSequence() { return entityData.get(ACTION_SEQUENCE); }
    public double animationAge(float partialTick) { return Math.max(0, level().getGameTime() - entityData.get(ACTION_START) + partialTick); }
    public double parryAge(float partialTick) { return level().getGameTime() - entityData.get(PARRY_START) + partialTick; }
    public int parrySide() { return entityData.get(PARRY_SIDE); }
    public float parryWeight() { return entityData.get(PARRY_WEIGHT); }
    public double impactAge(float partialTick) { return level().getGameTime() - entityData.get(IMPACT_START) + partialTick; }
    public float impactWeight() { return entityData.get(IMPACT_WEIGHT); }
    public float impactSide() { return entityData.get(IMPACT_SIDE); }
    public double walkAnimationTick(float partialTick) { return Mth.lerp(partialTick, previousWalkPhase, walkPhase); }
    public float turnAnimationTick(float partialTick) { return Mth.lerp(partialTick, previousTurnPhase, turnPhase); }
    public double animationMovement() { return animationMovement; }
    public float animationTurn() { return animationTurn; }
    public float walkAnimationWeight(float partial) { return Mth.lerp(partial, previousWalkWeight, walkWeight); }
    public float turnAnimationWeight(float partial) { return Mth.lerp(partial, previousTurnWeight, turnWeight); }
    public UUID encounterId() { return encounterId; }

    @Override public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(isAlive() ? 3 : 7, 1, isAlive() ? 3 : 7);
    }

    private void startAnimation(int action) {
        entityData.set(ACTION_START, level().getGameTime());
        entityData.set(ACTION_SEQUENCE, entityData.get(ACTION_SEQUENCE) + 1);
        entityData.set(ACTION, action);
    }

    public VirtualMushroomKing(EntityType<? extends VirtualMushroomKing> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MushroomCombatRules.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
        // Navigation/selection are exclusively owned by the server combat director.
    }

    /** Read-only server diagnostics; no client animation/controller depends on these. */
    public MushroomCombatDirector.Mode getCombatMode() { return combat.mode(); }
    public int getCombatTick() { return combat.elapsed(); }
    public int getComboId() { return combat.combo(); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        long now = server.getGameTime();
        // A reflected hit (e.g. Thorns) can kill the boss and clear this list during resolution.
        for (var attack : List.copyOf(rangedAttacks)) {
            if (!isAlive()) break;
            if (!attack.advanceAttack(this)) rangedAttacks.remove(attack);
        }
        if (!isAlive()) return;
        if (animationAction() == 9 && animationAge(0) >= 8) startAnimation(-1);
        combat.observeHealth(getHealth());
        List<ServerPlayer> active = refreshParticipants(server, now);
        // Recent roster entries permit a short pursuit, but must not add a second
        // 200-tick grace period on top of the specified disengage timer.
        boolean present = active.stream().anyMatch(player ->
                withinEngagementRange(player)
                || participants.getOrDefault(player.getUUID(), Long.MIN_VALUE) == now
                || (combat.mode() == MushroomCombatDirector.Mode.ULTIMATE && ultimateTargets.contains(player.getUUID())));
        if (!present) {
            if (++emptyTicks == MushroomCombatRules.DISENGAGE_TICKS) {
                resetEncounter();
                return;
            }
        } else {
            emptyTicks = 0;
        }

        ServerPlayer target = getTarget() instanceof ServerPlayer player && active.contains(player) ? player : null;
        if (target == null) {
            target = active.stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
            setTarget(target);
            nextPathAt = 0;
            navigationGoal = null;
            pursuing = routeFailed = false;
        }
        if (target != null) {
            pursuit.sample(target.getUUID(), now, target.getX(), target.getZ(), getX(), getZ(),
                    target.getY() - getY(), target.onGround());
            pursuit.navigationProgress(now, pursuing && combat.mode() == MushroomCombatDirector.Mode.IDLE,
                    routeFailed, animationMovement);
        } else {
            pursuit.reset();
            steeringActive = false;
        }

        if (combat.mode() != MushroomCombatDirector.Mode.IDLE) {
            stopWalking();
            if (combat.mode() == MushroomCombatDirector.Mode.COMBO && MushroomCombatRules.canAim(combat.elapsed())) {
                if (target != null) aimAt(target, 5, 24);
                lockedYaw = getYRot();
            }
            applyLockedYaw();
            var result = combat.advance(now);
            for (var hit : result.hits()) {
                if (hit.hit().form().ranged()) releaseRanged(server, hit);
                else for (var player : server.players()) resolveHit(player, hit);
            }
            if (!isAlive()) return;
            if (result.ultimateToResolve() >= 0) resolveUltimate(server, result.ultimateToResolve());
            if (!isAlive()) return;
            if (combat.mode() == MushroomCombatDirector.Mode.IDLE) {
                ultimateTargets.clear();
                comboTarget = null;
                startAnimation(-1);
            }
        }
        if (combat.mode() != MushroomCombatDirector.Mode.IDLE) return;
        if (active.isEmpty()) {
            stopWalking();
            return;
        }

        if (combat.pendingUltimate() >= 0) {
            ultimateTargets.clear();
            active.forEach(player -> ultimateTargets.add(player.getUUID()));
            lockedYaw = getYRot();
            int ultimate = combat.pendingUltimate();
            combat.beginUltimate();
            startAnimation(7 + ultimate);
            stopWalking();
            return;
        }
        if (target == null) return;
        double distance = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        double height = target.getY() - getY();
        boolean visible = hasLineOfSight(target);
        aimAt(target, 2, 30);
        double facingError = Mth.wrapDegrees(targetYaw(target, 0) - getYRot());
        var context = new MushroomCombatRules.SelectionContext(distance, height, visible, facingError, pursuit.counterRanged(now));
        int chosen = combat.select(getHealth(), now, context, random.nextDouble());
        if (chosen >= 0) {
            lockedYaw = getYRot();
            combat.beginCombo(chosen);
            comboTarget = target.getUUID();
            comboSequence++;
            startAnimation(chosen);
            stopWalking();
        } else {
            // At close range with everything cooling down, wait rather than push into a player.
            if (distance > 3.75 || !visible || Math.abs(height) > MushroomCombatRules.MAX_HEIGHT_DIFFERENCE) {
                pursue(target, now, height);
            } else {
                stopWalking();
            }
        }
    }

    @Override
    public void tick() {
        double oldX = getX(), oldZ = getZ();
        float oldYaw = yBodyRot;
        super.tick();
        if (!level().isClientSide) {
            if (!isAlive()) {
                combat.reset();
                ultimateTargets.clear();
                getNavigation().stop();
            } else if (combat.mode() != MushroomCombatDirector.Mode.IDLE) {
                applyLockedYaw(); // Move/look/body controls cannot rotate a locked attack afterwards.
            } else if (steeringActive) {
                setYRot(steeringYaw);
                yBodyRot = yHeadRot = steeringYaw;
            }
        }
        previousWalkPhase = walkPhase;
        previousTurnPhase = turnPhase;
        previousWalkWeight = walkWeight;
        previousTurnWeight = turnWeight;
        animationMovement = Math.min(0.5, Math.hypot(getX() - oldX, getZ() - oldZ));
        float turn = Mth.wrapDegrees(yBodyRot - oldYaw);
        animationTurn += (turn - animationTurn) * 0.35F;
        walkWeight = Mth.approach(walkWeight, (float)Math.min(1, animationMovement / 0.06), 0.2F);
        turnWeight = Mth.approach(turnWeight, Math.min(1, Math.abs(animationTurn) / 8), 0.2F);
        walkPhase += animationMovement * 40 / 2.4;
        turnPhase += Math.abs(turn) * 20 / 90;
    }

    private List<ServerPlayer> refreshParticipants(ServerLevel server, long now) {
        for (ServerPlayer player : server.players()) {
            if (valid(player) && withinEngagementRange(player)
                    && hasLineOfSight(player)) participants.put(player.getUUID(), now);
        }
        List<ServerPlayer> active = new ArrayList<>();
        participants.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            boolean locked = combat.mode() == MushroomCombatDirector.Mode.ULTIMATE && ultimateTargets.contains(entry.getKey());
            if (player == null || !valid(player) || (!locked && now - entry.getValue() >= MushroomCombatRules.DISENGAGE_TICKS)) return true;
            active.add(player);
            return false;
        });
        // Re-loaded windups retain their original roster, including players who walked far away.
        if (combat.mode() == MushroomCombatDirector.Mode.ULTIMATE) {
            for (UUID id : ultimateTargets) {
                ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
                if (player != null && valid(player) && !active.contains(player)) active.add(player);
            }
        }
        return active;
    }

    private boolean valid(ServerPlayer player) {
        return MushroomCombatRules.validParticipant(player.isAlive(), player.isCreative(), player.isSpectator(), player.level() == level());
    }

    private boolean withinEngagementRange(ServerPlayer player) {
        return Math.hypot(player.getX() - getX(), player.getZ() - getZ()) <= MushroomCombatRules.ACQUIRE_RANGE
                && Math.abs(player.getY() - getY()) <= MushroomCombatRules.COUNTER_RANGED_HEIGHT;
    }

    private void resolveHit(ServerPlayer player, MushroomCombatDirector.IndexedHit strike) {
        if (!isAlive() || !valid(player)) return;
        var hit = strike.hit();
        double yaw = Math.toRadians(lockedYaw);
        if (!MushroomCombatRules.inHitVolume(hit.type(), player.getX() - getX(), player.getZ() - getZ(),
                player.getY() - getY(), -Math.sin(yaw), Math.cos(yaw), hasLineOfSight(player))
                || !combat.handleOnce(strike.index(), player.getUUID())) return;
        resolveDefence(player, hit, strike.index(), lockedYaw);
    }

    public boolean validRangedTarget(ServerPlayer player) { return valid(player); }

    public void resolveRangedHit(ServerPlayer player, MushroomCombatRules.Hit hit, float releaseYaw) {
        if (isAlive() && valid(player)) resolveDefence(player, hit, -1, releaseYaw);
    }

    private void resolveDefence(ServerPlayer player, MushroomCombatRules.Hit hit, int strikeIndex, float releaseYaw) {
        try (var defense=CombatDefenseService.resolve(player,position(),hit.type(),defenseRules())) {
            if (defense.guard()==DefenseState.Guard.PRECISE) showGuardImpact(strikeIndex,1F);
            else if (defense.guard()==DefenseState.Guard.BROKEN) showGuardImpact(strikeIndex,.35F);
            if (defense.avoided()) return;
            float multiplier=defense.damageMultiplier();
            if (CombatImpactService.hurt(player,strikeSource(false),hit.damage()*multiplier) && valid(player)) {
                finishHit(player,hit,releaseYaw,multiplier);
            }
        }
    }

    private void showGuardImpact(int strikeIndex, float weight) {
        long now = level().getGameTime();
        // A second player's failed guard must not hide a precise guard on the same hit.
        if (entityData.get(PARRY_START) == now && parryWeight() >= weight) return;
        entityData.set(PARRY_SIDE, combat.combo() == 5 && strikeIndex == 2 ? 2 : strikeIndex % 2);
        entityData.set(PARRY_WEIGHT, weight);
        entityData.set(PARRY_START, now);
        reaction(weight==1?MushroomBossReactionPayload.PRECISE_GUARD:MushroomBossReactionPayload.FAILED_GUARD,
                parrySide(),weight);
    }

    private void finishHit(ServerPlayer player,MushroomCombatRules.Hit hit,float releaseYaw,float multiplier) {
        CombatImpactService.apply(player,position(),releaseYaw,hit.impulse(),multiplier);
        if(!isAlive()) return; // Thorns may have killed the owner during player.hurt.
        double yaw=Math.toRadians(releaseYaw);
        int rotation=player.position().subtract(position()).dot(new Vec3(Math.cos(yaw),0,Math.sin(yaw)))<0?-90:90;
        MushroomAfflictionHandler.apply(player,encounterId,hit.afflictions(),rotation);
    }

    private void reaction(int kind,int side,float strength) {
        PacketDistributor.sendToPlayersTrackingEntity(this,new MushroomBossReactionPayload(getId(),getUUID(),++reactionSequence,kind,side,strength));
    }

    private void releaseRanged(ServerLevel server, MushroomCombatDirector.IndexedHit indexed) {
        ServerPlayer target = comboTarget == null ? null : server.getServer().getPlayerList().getPlayer(comboTarget);
        if (target == null || !valid(target)) return;
        MushroomAttackEntity attack = ModEntityTypes.MUSHROOM_ATTACK.get().create(server);
        if (attack == null) return;
        double yaw = Math.toRadians(lockedYaw);
        double side = combat.combo() == 1 && indexed.index() == 2 ? 0 : indexed.index() % 2 == 0 ? -1.9 : 1.9;
        Vec3 origin = position().add(Math.cos(yaw) * side - Math.sin(yaw) * 2.4, 0.45,
                Math.sin(yaw) * side + Math.cos(yaw) * 2.4);
        attack.launch(this, target, new dev.tatami.virtualcraft.combat.AttackKey(encounterId, comboSequence, indexed.index(), comboTarget), indexed.hit(), origin);
        if (server.addFreshEntity(attack)) rangedAttacks.add(attack);
    }

    private void clearRangedAttacks() {
        if(!level().isClientSide) MushroomAfflictionHandler.clearSource(getServer(),encounterId);
        rangedAttacks.forEach(MushroomAttackEntity::discard);
        rangedAttacks.clear();
        comboTarget = null;
        encounterId = UUID.randomUUID();
    }

    private void resolveUltimate(ServerLevel server, int index) {
        for (UUID id : ultimateTargets) {
            if (!isAlive()) break;
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if (player != null && valid(player) && combat.handleOnce(0, id)) {
                // No distance, sight, posture or guard check. Damage tags preserve armour/totems.
                var hit=MushroomCombatRules.ultimate(index);
                if(CombatImpactService.hurt(player,strikeSource(true),hit.damage()) && valid(player)) finishHit(player,hit,lockedYaw,1);
            }
        }
    }

    private DamageSource strikeSource(boolean ultimate) {
        var holder = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ultimate ? MushroomDamageTypes.ULTIMATE : MushroomDamageTypes.STRIKE);
        return new DamageSource(holder, this);
    }

    private void stopWalking() {
        pursuing = routeFailed = false;
        navigationGoal = null;
        nextPathAt = 0;
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
        setSpeed(0);
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
    }

    private float targetYaw(ServerPlayer target, double leadTicks) {
        double dx = target.getX() + pursuit.leadX(leadTicks) - getX();
        double dz = target.getZ() + pursuit.leadZ(leadTicks) - getZ();
        return dx * dx + dz * dz < 1.0e-8 ? getYRot() : (float)Math.toDegrees(Math.atan2(dz, dx)) - 90;
    }

    private void aimAt(ServerPlayer target, double leadTicks, float maxTurn) {
        steeringYaw = MushroomPursuitState.turn(steeringActive ? steeringYaw : yBodyRot, targetYaw(target, leadTicks), maxTurn);
        steeringActive = true;
        setYRot(steeringYaw);
        yBodyRot = yHeadRot = steeringYaw;
    }

    private void pursue(ServerPlayer target, long now, double height) {
        pursuing = true;
        double speed = pursuit.speed() > 0.22 ? 1.15 : 1.0;
        Vec3 goal = new Vec3(target.getX() + pursuit.leadX(8),
                Math.abs(height) <= MushroomCombatRules.MAX_HEIGHT_DIFFERENCE ? target.getY() : getY(),
                target.getZ() + pursuit.leadZ(8));
        // Replace stale paths at a bounded rate, with faster updates against sprinting targets.
        if (now >= nextPathAt) {
            routeFailed = !getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
            navigationGoal = goal;
            nextPathAt = now + (pursuit.speed() > 0.18 ? 4 : 8);
        } else if (navigationGoal != null && navigationGoal.distanceToSqr(goal) > 4) {
            nextPathAt = Math.min(nextPathAt, now + 2);
        }
    }

    private void applyLockedYaw() {
        steeringYaw = lockedYaw;
        setYRot(lockedYaw);
        yBodyRot = yHeadRot = lockedYaw;
    }

    private void resetEncounter() {
        clearRangedAttacks();
        clearImpactFeedback();
        startAnimation(-1);
        combat.reset();
        participants.clear();
        pursuit.reset();
        steeringActive = false;
        ultimateTargets.clear();
        setTarget(null);
        setHealth(MushroomCombatRules.MAX_HEALTH);
        stopWalking();
    }

    private void clearImpactFeedback() {
        entityData.set(PARRY_START, -100L);
        entityData.set(PARRY_WEIGHT, 0F);
        entityData.set(IMPACT_START, -100L);
        entityData.set(IMPACT_WEIGHT, 0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player && valid(player)) {
            participants.put(player.getUUID(), player.serverLevel().getGameTime());
        }
        float before = getHealth() + getAbsorptionAmount();
        boolean accepted = super.hurt(source, amount);
        if (!level().isClientSide && isAlive()) {
            combat.observeHealth(getHealth());
            float lost = before - getHealth() - getAbsorptionAmount();
            if (accepted && lost > 0) {
                Vec3 origin = source.getSourcePosition();
                double yaw = Math.toRadians(yBodyRot);
                double side = origin == null ? 0 : origin.subtract(position()).dot(new Vec3(Math.cos(yaw), 0, Math.sin(yaw)));
                entityData.set(IMPACT_SIDE, side < 0 ? -1F : 1F);
                entityData.set(IMPACT_WEIGHT, Mth.clamp(0.45F + lost / 16F, 0.5F, 1F));
                entityData.set(IMPACT_START, level().getGameTime());
                reaction(lost>=8?MushroomBossReactionPayload.HEAVY_HURT:MushroomBossReactionPayload.HURT,
                        side<0?-1:1,lost>=8?1:Mth.clamp(.75F+lost/16F,.8F,1F));
                // A visual overlay only: never restart a combo, its recovery, or its clock.
            }
        }
        return accepted;
    }

    @Override public void die(DamageSource source) {
        if (!level().isClientSide) {
            clearRangedAttacks();
            pursuit.reset();
            steeringActive = false;
            combat.reset();
            ultimateTargets.clear();
            stopWalking();
            startAnimation(10);
        }
        super.die(source);
    }

    @Override protected void tickDeath() {
        deathTime++;
        if (!level().isClientSide && deathTime >= 60) remove(RemovalReason.KILLED);
    }

    @Override public void remove(RemovalReason reason) {
        if (!level().isClientSide) clearRangedAttacks();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        var snapshot = combat.snapshot();
        CompoundTag state = new CompoundTag();
        state.putInt("Triggered", snapshot.triggered());
        state.putInt("Resolved", snapshot.resolved());
        state.putBoolean("NeedsCombo", snapshot.needsCombo());
        state.putInt("ActiveUltimate", snapshot.activeUltimate());
        state.putInt("LastCombo", snapshot.lastCombo());
        state.putLongArray("ReadyAt", snapshot.readyAt());
        state.putInt("EmptyTicks", emptyTicks);
        state.putFloat("LockedYaw", lockedYaw);
        ListTag roster = new ListTag();
        participants.forEach((id, lastSeen) -> {
            CompoundTag player = new CompoundTag();
            player.putUUID("Id", id);
            player.putLong("LastSeen", lastSeen);
            roster.add(player);
        });
        state.put("Participants", roster);
        ListTag locked = new ListTag();
        ultimateTargets.forEach(id -> {
            CompoundTag player = new CompoundTag();
            player.putUUID("Id", id);
            locked.add(player);
        });
        state.put("UltimateTargets", locked);
        tag.put("MushroomCombat", state);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        clearRangedAttacks();
        clearImpactFeedback();
        pursuit.reset();
        steeringActive = false;
        float previousMax = getMaxHealth();
        float previousHealth = getHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MushroomCombatRules.MAX_HEALTH);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.20);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0);
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.0);
        combat.reset();
        participants.clear();
        ultimateTargets.clear();
        emptyTicks = 0;
        if (!tag.contains("MushroomCombat")) {
            // Existing static-model entities used 200 HP/zero speed. Preserve their health ratio.
            setHealth(previousMax > 0 ? previousHealth / previousMax * MushroomCombatRules.MAX_HEALTH : MushroomCombatRules.MAX_HEALTH);
            return;
        }
        CompoundTag state = tag.getCompound("MushroomCombat");
        combat.restore(new MushroomCombatDirector.Snapshot(state.getInt("Triggered"), state.getInt("Resolved"),
                state.getBoolean("NeedsCombo"), state.getInt("ActiveUltimate"), state.getInt("LastCombo"), state.getLongArray("ReadyAt")));
        emptyTicks = Math.min(MushroomCombatRules.DISENGAGE_TICKS - 1, Math.max(0, state.getInt("EmptyTicks")));
        lockedYaw = state.getFloat("LockedYaw");
        for (Tag item : state.getList("Participants", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag)item;
            if (player.hasUUID("Id")) participants.put(player.getUUID("Id"), player.getLong("LastSeen"));
        }
        for (Tag item : state.getList("UltimateTargets", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag)item;
            if (player.hasUUID("Id")) ultimateTargets.add(player.getUUID("Id"));
        }
        if (combat.mode() != MushroomCombatDirector.Mode.ULTIMATE) ultimateTargets.clear();
        startAnimation(combat.mode() == MushroomCombatDirector.Mode.ULTIMATE ? 7 + combat.ultimate() : -1);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Client GeoModel samples authored clips using synchronized absolute ticks.
        // No animation callback can release damage or restart a late-joined attack.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
