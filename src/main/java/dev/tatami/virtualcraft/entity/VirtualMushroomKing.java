package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.MushroomAttackType;
import dev.tatami.virtualcraft.boss.MushroomCombatDirector;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import dev.tatami.virtualcraft.boss.MushroomDefenseState;
import dev.tatami.virtualcraft.gameplay.MushroomDefenseHandler;
import dev.tatami.virtualcraft.registry.MushroomDamageTypes;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** New numeric encounter. Rendering remains the authored, static model. */
public final class VirtualMushroomKing extends Monster implements GeoEntity {
    public static final float MODEL_HEIGHT = 103.0F / 16.0F;
    public static final float BODY_WIDTH = 5.25F;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final MushroomCombatDirector combat = new MushroomCombatDirector();
    private final Map<UUID, Long> participants = new HashMap<>();
    private final Set<UUID> ultimateTargets = new HashSet<>();
    private int emptyTicks;
    private float lockedYaw;

    public VirtualMushroomKing(EntityType<? extends VirtualMushroomKing> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MushroomCombatRules.MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.20)
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
        combat.observeHealth(getHealth());
        List<ServerPlayer> active = refreshParticipants(server, now);
        // Recent roster entries permit a short pursuit, but must not add a second
        // 200-tick grace period on top of the specified disengage timer.
        boolean present = active.stream().anyMatch(player ->
                distanceToSqr(player) <= MushroomCombatRules.ACQUIRE_RANGE * MushroomCombatRules.ACQUIRE_RANGE
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
        }

        if (combat.mode() != MushroomCombatDirector.Mode.IDLE) {
            stopWalking();
            if (combat.mode() == MushroomCombatDirector.Mode.COMBO && MushroomCombatRules.canAim(combat.elapsed())) {
                if (target != null) aimAt(target);
                lockedYaw = getYRot();
            }
            applyLockedYaw();
            var result = combat.advance(now);
            for (var hit : result.hits()) {
                // Area hits may affect every valid player, not only the selected target.
                for (var player : server.players()) resolveHit(player, hit);
            }
            if (result.ultimateToResolve() >= 0) resolveUltimate(server, result.ultimateToResolve());
            if (combat.mode() == MushroomCombatDirector.Mode.IDLE) ultimateTargets.clear();
        }
        if (combat.mode() != MushroomCombatDirector.Mode.IDLE || active.isEmpty()) return;

        if (combat.pendingUltimate() >= 0) {
            ultimateTargets.clear();
            active.forEach(player -> ultimateTargets.add(player.getUUID()));
            lockedYaw = getYRot();
            combat.beginUltimate();
            stopWalking();
            return;
        }
        if (target == null) return;
        double distance = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        int chosen = combat.select(getHealth(), now, distance, target.getY() - getY(), hasLineOfSight(target), random.nextDouble());
        if (chosen >= 0) {
            aimAt(target);
            lockedYaw = getYRot();
            combat.beginCombo(chosen);
            stopWalking();
        } else {
            // At close range with everything cooling down, wait rather than push into a player.
            if (distance > 4 || !hasLineOfSight(target) || Math.abs(target.getY() - getY()) > 3) {
                getNavigation().moveTo(target, 1.0);
            } else {
                stopWalking();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!isAlive()) {
                combat.reset();
                ultimateTargets.clear();
                getNavigation().stop();
            } else if (combat.mode() != MushroomCombatDirector.Mode.IDLE) {
                applyLockedYaw(); // Move/look/body controls cannot rotate a locked attack afterwards.
            }
        }
    }

    private List<ServerPlayer> refreshParticipants(ServerLevel server, long now) {
        for (ServerPlayer player : server.players()) {
            if (valid(player) && distanceToSqr(player) <= MushroomCombatRules.ACQUIRE_RANGE * MushroomCombatRules.ACQUIRE_RANGE
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

    private void resolveHit(ServerPlayer player, MushroomCombatDirector.IndexedHit strike) {
        if (!isAlive() || !valid(player)) return;
        var hit = strike.hit();
        double yaw = Math.toRadians(lockedYaw);
        if (!MushroomCombatRules.inHitVolume(hit.type(), player.getX() - getX(), player.getZ() - getZ(),
                player.getY() - getY(), -Math.sin(yaw), Math.cos(yaw), hasLineOfSight(player))
                || !combat.handleOnce(strike.index(), player.getUUID())) return;
        if (hit.type() == MushroomAttackType.HIGH && player.isCrouching()) return;
        if (hit.type() == MushroomAttackType.LOW && MushroomDefenseHandler.dodgeLow(player)) return;
        float damage = hit.damage();
        MushroomDefenseHandler.GuardResolution guard = null;
        if (hit.type() == MushroomAttackType.MID) {
            var towardBoss = position().subtract(player.position()).multiply(1, 0, 1);
            var view = player.getViewVector(1).multiply(1, 0, 1);
            double dot = towardBoss.lengthSqr() < 1.0e-8 ? 1 : view.normalize().dot(towardBoss.normalize());
            guard = MushroomDefenseHandler.guard(player, dot);
            if (guard.result() == MushroomDefenseState.Guard.PRECISE) {
                MushroomDefenseHandler.finishGuard(player, guard);
                return;
            }
            damage *= guard.result().damageMultiplier();
        }
        player.hurt(strikeSource(false), damage);
        if (guard != null) MushroomDefenseHandler.finishGuard(player, guard);
    }

    private void resolveUltimate(ServerLevel server, int index) {
        for (UUID id : ultimateTargets) {
            if (!isAlive()) break;
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if (player != null && valid(player) && combat.handleOnce(0, id)) {
                // No distance, sight, posture or guard check. Damage tags preserve armour/totems.
                player.hurt(strikeSource(true), MushroomCombatRules.ultimateDamage(index));
            }
        }
    }

    private DamageSource strikeSource(boolean ultimate) {
        var holder = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ultimate ? MushroomDamageTypes.ULTIMATE : MushroomDamageTypes.STRIKE);
        return new DamageSource(holder, this);
    }

    private void stopWalking() {
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
        setSpeed(0);
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
    }

    private void aimAt(ServerPlayer target) {
        setYRot((float)Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90);
    }

    private void applyLockedYaw() {
        setYRot(lockedYaw);
        yBodyRot = yHeadRot = lockedYaw;
    }

    private void resetEncounter() {
        combat.reset();
        participants.clear();
        ultimateTargets.clear();
        setTarget(null);
        setHealth(MushroomCombatRules.MAX_HEALTH);
        stopWalking();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player && valid(player)) {
            participants.put(player.getUUID(), player.serverLevel().getGameTime());
        }
        boolean accepted = super.hurt(source, amount);
        if (!level().isClientSide && isAlive()) combat.observeHealth(getHealth());
        return accepted;
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
        float previousMax = getMaxHealth();
        float previousHealth = getHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(MushroomCombatRules.MAX_HEALTH);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.20);
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
        // Static rest pose. Never play old clips against the new skeleton.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
