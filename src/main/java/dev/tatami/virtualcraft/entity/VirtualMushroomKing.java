package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.BossPostureRules;
import dev.tatami.virtualcraft.boss.MushroomAttackVolume;
import dev.tatami.virtualcraft.boss.MushroomComboPattern;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.boss.MushroomKingCombatDirector;
import dev.tatami.virtualcraft.boss.PostureBoss;
import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Server-authoritative core for the Virtual Mushroom King encounter.
 *
 * <p>All attack geometry is transient entity math. The boss never places,
 * removes, or destroys blocks.</p>
 */
public class VirtualMushroomKing extends Monster implements PostureBoss, GeoEntity {
	public static final float BASE_MAX_HEALTH = 520.0F;
	public static final int SUMMON_DURATION = 80;
	public static final int LEFT_SHOULDER_MAX = 50;
	public static final int RIGHT_SHOULDER_MAX = 50;
	public static final int CHEST_MAX = 90;
	public static final int BACK_MAX = 70;
	public static final int FORM_NORMAL = 0;
	public static final int FORM_RED = 1;
	public static final int FORM_GREEN = 2;
	public static final int FORM_PURPLE = 3;

	private static final int ATTACK_NONE = 0;
	private static final int FORM_EXTENSION_TICKS = 200;
	private static final float FORM_DAMAGE_MULTIPLIER = 1.35F;
	private static final float RED_FORM_INCOMING_MULTIPLIER = 1.4F;
	private static final float GREEN_FORM_COOLDOWN_MULTIPLIER = 1.5F;
	private static final int RECENT_ATTACK_LIMIT = 3;
	private static final int PLATE_BREAK_STUN = 24;
	private static final int DIRECTION_COOLDOWN_MID = 42;
	private static final int DIRECTION_COOLDOWN_HIGH = 120;
	private static final int DIRECTION_COOLDOWN_LOW = 100;
	private static final int COMBAT_SCHEMA_VERSION = 4;
	private static final int POSTURE_SCHEMA_VERSION = 2;
	private static final String[] ATTACK_ANIMATIONS = {
			"", "root_cadence", "stem_hammer", "spore_needle_volley", "cap_clap",
			"canopy_closure", "spore_breath", "mycelial_surge", "root_convergence"
	};
	private static final String[] ATTACK_CLIPS = {
			"", "attack_mid_root_cadence", "attack_mid_stem_hammer",
			"attack_mid_spore_needle_volley", "attack_mid_cap_clap",
			"attack_high_canopy_closure", "attack_high_spore_breath",
			"attack_low_mycelial_surge", "attack_low_root_convergence"
	};
	private static final String[] MID_COMBO_ANIMATIONS = {
			"mid_combo_pause", "mid_combo_fast"
	};
	private static final String[] LOW_COMBO_ANIMATIONS = {
			"low_combo_pause", "low_combo_fast", "low_combo_high_finish",
			"low_combo_long", "low_combo_pause_high"
	};
	private static final RawAnimation IDLE = RawAnimation.begin()
			.thenLoop("animation.virtual_mushroom_king.idle");
	private static final RawAnimation WALK = RawAnimation.begin()
			.thenLoop("animation.virtual_mushroom_king.walk_heavy");
	private static final RawAnimation COMBAT_IDLE = RawAnimation.begin()
			.thenLoop("animation.virtual_mushroom_king.combat_idle");

	private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_TICK =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_DIRECTION =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_VARIANT =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_YAW =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_X =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_Y =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_Z =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_SUMMON_TICKS =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_POSTURE =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STAGGER_TICKS =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_RECOVERY_TICKS =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LEFT_SHOULDER =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_RIGHT_SHOULDER =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CHEST =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BACK =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_BREAKING_PLATE =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PLATE_BREAK_TICKS =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORM =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FORM_TICKS =
			SynchedEntityData.defineId(VirtualMushroomKing.class, EntityDataSerializers.INT);
	private static final int DODGE_INPUT_WINDOW_TICKS = 10;

	private final ServerBossEvent bossEvent = new ServerBossEvent(
			Component.translatable("entity.virtual_craft.virtual_mushroom_king"),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
	private final int[] attackReadyTicks = new int[MushroomKingAttackRules.ATTACK_COUNT + 1];
	private final int[] directionReadyTicks = new int[BossAttackDirection.values().length];
	private final Deque<Integer> recentAttacks = new ArrayDeque<>();
	private final Set<HitKey> attackHits = new HashSet<>();
	private final Map<UUID, Integer> activeJumpWindows = new HashMap<>();
	private final Map<UUID, Integer> lowDodgeLandingGrace = new HashMap<>();
	private final Map<UUID, Integer> highDodgeWindows = new HashMap<>();
	private final Map<UUID, Boolean> crouchStates = new HashMap<>();
	private final Map<UUID, Boolean> airborneStates = new HashMap<>();
	private final Map<UUID, Integer> jumpStrikeWindows = new HashMap<>();
	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

	private int nextAttackTick;
	private int combatTicks;
	private int lastCombatTick;
	private int nextMidComboSequence;
	private int nextLowComboSequence;
	private int nextMixedComboSequence;
	private float posturePressure;
	private boolean attackLanded;
	@Nullable
	private BlockPos homePos;
	@Nullable
	private UUID summonerUuid;

	public VirtualMushroomKing(EntityType<? extends VirtualMushroomKing> type, Level level) {
		super(type, level);
		xpReward = 800;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
				.add(Attributes.ARMOR, 18.0)
				.add(Attributes.ARMOR_TOUGHNESS, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.21)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ATTACK_DAMAGE, 18.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.4)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.25);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "movement", 5, state -> {
			if (getSummonTicks() > 0 || getAttackState() != ATTACK_NONE || isStaggered()
					|| deathTime > 0) {
				return PlayState.STOP;
			}
			if (state.isMoving()) {
				return state.setAndContinue(WALK);
			}
			return state.setAndContinue(getTarget() != null ? COMBAT_IDLE : IDLE);
		}));
		AnimationController<VirtualMushroomKing> combat = new AnimationController<>(
				this, "combat", 2, state -> PlayState.STOP);
		for (int attack = 1; attack < ATTACK_ANIMATIONS.length; attack++) {
			combat.triggerableAnim(ATTACK_ANIMATIONS[attack], RawAnimation.begin()
					.thenPlay("animation.virtual_mushroom_king." + ATTACK_CLIPS[attack]));
		}
		combat.setAnimationSpeedHandler(boss -> {
			// Step clips are authored so their contact pose lands exactly on the
			// server impact tick. Speeding the whole combo up erased the wind-up and
			// made later beats appear to twitch back to idle before dealing damage.
			if (boss.getComboPattern() != null) {
				return 1.0D;
			}
			return switch (boss.getAttackState()) {
				case MushroomKingAttackRules.MYCELIAL_SURGE -> 1.12D;
				default -> 1.0D;
			};
		});
		combat.triggerableAnim(MID_COMBO_ANIMATIONS[0], RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_pause"));
		combat.triggerableAnim(MID_COMBO_ANIMATIONS[1], RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_fast"));
		for (int variant = 0; variant < LOW_COMBO_ANIMATIONS.length; variant++) {
			combat.triggerableAnim(LOW_COMBO_ANIMATIONS[variant], RawAnimation.begin()
					.thenPlay("animation.virtual_mushroom_king.combo_low_" + variant));
		}
		combat.triggerableAnim("combo_mid_step_left", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_left"));
		combat.triggerableAnim("combo_mid_step_right", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_right"));
		combat.triggerableAnim("combo_mid_step_slam", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_slam"));
		combat.triggerableAnim("combo_mid_step_clap", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_clap"));
		combat.triggerableAnim("combo_mid_step_drive", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_drive"));
		combat.triggerableAnim("combo_mid_step_uppercut", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_uppercut"));
		combat.triggerableAnim("combo_mid_step_rush", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_mid_step_rush"));
		combat.triggerableAnim("combo_low_step_left", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_left"));
		combat.triggerableAnim("combo_low_step_right", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_right"));
		combat.triggerableAnim("combo_low_step_surge", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_surge"));
		combat.triggerableAnim("combo_low_step_clamp", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_clamp"));
		combat.triggerableAnim("combo_low_step_quake", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_quake"));
		combat.triggerableAnim("combo_low_step_erupt", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_low_step_erupt"));
		combat.triggerableAnim("combo_high_step_finish", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_high_step_finish"));
		combat.triggerableAnim("combo_high_step_breath", RawAnimation.begin()
				.thenPlay("animation.virtual_mushroom_king.combo_high_step_breath"));
		controllers.add(combat);
		controllers.add(new AnimationController<>(this, "reaction", 1, state -> PlayState.STOP)
				.triggerableAnim("hurt", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.hurt_short"))
				.triggerableAnim("summon", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.summon"))
				.triggerableAnim("roar", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.roar"))
				.triggerableAnim("stagger", RawAnimation.begin()
						.thenPlayAndHold("animation.virtual_mushroom_king.stagger"))
				.triggerableAnim("plate_break_left", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.plate_break_left"))
				.triggerableAnim("plate_break_right", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.plate_break_right"))
				.triggerableAnim("plate_break_chest", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.plate_break_chest"))
				.triggerableAnim("plate_break_back", RawAnimation.begin()
						.thenPlay("animation.virtual_mushroom_king.plate_break_back"))
				.triggerableAnim("death", RawAnimation.begin()
						.thenPlayAndHold("animation.virtual_mushroom_king.death")));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACK_STATE, ATTACK_NONE);
		builder.define(DATA_ATTACK_TICK, 0);
		builder.define(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		builder.define(DATA_ATTACK_VARIANT, 0);
		builder.define(DATA_LOCKED_YAW, 0.0F);
		builder.define(DATA_LOCKED_X, 0.0F);
		builder.define(DATA_LOCKED_Y, 0.0F);
		builder.define(DATA_LOCKED_Z, 0.0F);
		builder.define(DATA_SUMMON_TICKS, 0);
		builder.define(DATA_POSTURE, BossPostureRules.MUSHROOM_MAX_POSTURE);
		builder.define(DATA_STAGGER_TICKS, 0);
		builder.define(DATA_RECOVERY_TICKS, 0);
		builder.define(DATA_LEFT_SHOULDER, LEFT_SHOULDER_MAX);
		builder.define(DATA_RIGHT_SHOULDER, RIGHT_SHOULDER_MAX);
		builder.define(DATA_CHEST, CHEST_MAX);
		builder.define(DATA_BACK, BACK_MAX);
		builder.define(DATA_BREAKING_PLATE, -1);
		builder.define(DATA_PLATE_BREAK_TICKS, 0);
		builder.define(DATA_FORM, FORM_NORMAL);
		builder.define(DATA_FORM_TICKS, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new MushroomKingCombatGoal());
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.72D, 40));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		tickForm();
		bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
		if (getSummonTicks() > 0) {
			tickSummoning();
			return;
		}
		tickJumpWindows();
		tickRecoveryAndPosture();
	}

	@Override
	public void onAddedToLevel() {
		super.onAddedToLevel();
		if (level().isClientSide) {
			return;
		}
		if (getSummonTicks() > 0) {
			triggerAnim("reaction", "summon");
		} else if (isStaggered()) {
			triggerAnim("reaction", "stagger");
		} else if (MushroomKingAttackRules.isCombatAttack(getAttackState())) {
			MushroomComboPattern combo = getComboPattern();
			if (combo == null) {
				triggerAnim("combat", animationTriggerFor(getAttackState(), getAttackVariant()));
			} else {
				int stepIndex = combo.cueStepIndex(getAttackTick());
				triggerAnim("combat", comboStepAnimation(combo.step(stepIndex)));
			}
		}
	}

	public void beginSummoning(BlockPos baseCenter, @Nullable UUID summoner) {
		homePos = baseCenter.immutable();
		summonerUuid = summoner;
		entityData.set(DATA_SUMMON_TICKS, SUMMON_DURATION);
		entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
		entityData.set(DATA_ATTACK_TICK, 0);
		setNoAi(true);
		setNoGravity(true);
		noPhysics = true;
		bossEvent.setVisible(false);
		setDeltaMovement(Vec3.ZERO);
		fallDistance = 0.0F;
		if (!level().isClientSide) {
			triggerAnim("reaction", "summon");
			playSound(ModSounds.MUSHROOM_KING_SUMMON.get(), 2.2F, 0.9F);
		}
	}

	private void tickSummoning() {
		int remaining = getSummonTicks();
		if (homePos == null) {
			finishSummoning();
			return;
		}
		float progress = 1.0F - remaining / (float)SUMMON_DURATION;
		double y = homePos.getY() - 3.25 + 4.25 * smoothStep(progress);
		setPos(homePos.getX() + 0.5, y, homePos.getZ() + 0.5);
		setDeltaMovement(Vec3.ZERO);
		if (level() instanceof ServerLevel serverLevel && remaining % 4 == 0) {
			serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
					getX(), getY() + 2.2, getZ(), 10, 1.4, 1.7, 1.4, 0.01);
			if (remaining % 12 == 0) {
				serverLevel.sendParticles(ParticleTypes.MYCELIUM,
						getX(), getY() + 0.25, getZ(), 8, 1.1, 0.2, 1.1, 0.03);
			}
		}
		remaining--;
		entityData.set(DATA_SUMMON_TICKS, Math.max(0, remaining));
		if (remaining <= 0) {
			finishSummoning();
		}
	}

	private void finishSummoning() {
		entityData.set(DATA_SUMMON_TICKS, 0);
		if (homePos != null) {
			setPos(homePos.getX() + 0.5, homePos.getY() + 1.0, homePos.getZ() + 0.5);
		}
		setNoAi(false);
		setNoGravity(false);
		noPhysics = false;
		bossEvent.setVisible(true);
		if (!level().isClientSide) {
			triggerAnim("reaction", "roar");
			playSound(ModSounds.MUSHROOM_KING_ROAR.get(), 2.4F, 0.92F);
			if (level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
						getX(), getY() + 2.6, getZ(), 55, 2.2, 2.5, 2.2, 0.04);
				serverLevel.sendParticles(ParticleTypes.MYCELIUM,
						getX(), getY() + 0.3, getZ(), 34, 2.1, 0.25, 2.1, 0.08);
			}
		}
		nextAttackTick = tickCount + 45;
		setDeltaMovement(Vec3.ZERO);
		if (summonerUuid != null && level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			Player player = serverLevel.getPlayerByUUID(summonerUuid);
			if (isCombatPlayer(player)) {
				setTarget(player);
			}
		}
	}

	private static double smoothStep(float value) {
		float t = Mth.clamp(value, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private void tickRecoveryAndPosture() {
		int plateBreakTicks = getPlateBreakTicks();
		if (plateBreakTicks > 0) {
			entityData.set(DATA_PLATE_BREAK_TICKS, plateBreakTicks - 1);
			if (plateBreakTicks == 1) {
				entityData.set(DATA_BREAKING_PLATE, -1);
			}
		}
		int stagger = getStaggerTicks();
		if (stagger > 0) {
			getNavigation().stop();
			setDeltaMovement(Vec3.ZERO);
			entityData.set(DATA_STAGGER_TICKS, stagger - 1);
			if (stagger == 1) {
				entityData.set(DATA_POSTURE, getMaxPosture());
				posturePressure = 0.0F;
				nextAttackTick = tickCount + 25;
			}
		}
		int recovery = getRecoveryTicks();
		if (recovery > 0) {
			getNavigation().stop();
			setDeltaMovement(Vec3.ZERO);
			entityData.set(DATA_RECOVERY_TICKS, recovery - 1);
		}
		if (getTarget() == null && getPosture() < getMaxPosture()
				&& tickCount - lastCombatTick >= BossPostureRules.DISENGAGE_RESET_TICKS) {
			entityData.set(DATA_POSTURE, getMaxPosture());
			posturePressure = 0.0F;
		}
	}

	private void tickCombat(LivingEntity target) {
		if (getSummonTicks() > 0 || isStaggered() || getRecoveryTicks() > 0) {
			return;
		}
		lastCombatTick = tickCount;
		combatTicks++;
		int attack = getAttackState();
		if (attack != ATTACK_NONE) {
			getNavigation().stop();
			updateAttackTracking(target, attack);
			faceLockedYaw();
			tickAttack(attack);
			return;
		}

		double distance = distanceTo(target);
		getLookControl().setLookAt(target, 42.0F, 24.0F);
		faceTarget(target, 38.0F);
		if (distance > 3.7) {
			double speed = distance > 12.0 ? 1.18 : 0.95;
			if (getNavigation().isDone() || tickCount % 8 == 0) {
				boolean hasPath = getNavigation().moveTo(target, speed);
				if (!hasPath) {
					getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
				}
			}
		} else {
			getNavigation().stop();
		}
		if (tickCount >= nextAttackTick) {
			int selected = selectAttack(distance);
			if (selected == ATTACK_NONE) {
				nextAttackTick = tickCount + 10;
			} else {
				startAttack(selected, target);
			}
		}
	}

	private int selectAttack(double distance) {
		int[] recent = recentAttacks.stream().mapToInt(Integer::intValue).toArray();
		int nearbyPlayers = combatPlayers(24.0).size();
		int last = recentAttacks.isEmpty() ? ATTACK_NONE : recentAttacks.getLast();
		return MushroomKingCombatDirector.select(new MushroomKingCombatDirector.Context(
				distance, tickCount, combatTicks, directionReadyTicks, attackReadyTicks,
				recent, last, Math.max(1, nearbyPlayers), getHealth() / getMaxHealth()),
				random.nextDouble());
	}

	private void startAttack(int attack, LivingEntity target) {
		int variant = nextAttackVariant(attack);
		MushroomComboPattern combo = MushroomComboPattern.forAttack(attack, variant);
		BossAttackDirection direction = combo == null
				? MushroomKingAttackRules.directionFor(attack) : combo.step(0).direction();
		entityData.set(DATA_ATTACK_STATE, attack);
		entityData.set(DATA_ATTACK_TICK, 0);
		entityData.set(DATA_ATTACK_DIRECTION, direction.ordinal());
		entityData.set(DATA_ATTACK_VARIANT, variant);
		entityData.set(DATA_LOCKED_YAW, yawToward(target.position()));
		entityData.set(DATA_LOCKED_X, (float)target.getX());
		entityData.set(DATA_LOCKED_Y, (float)target.getY());
		entityData.set(DATA_LOCKED_Z, (float)target.getZ());
		attackHits.clear();
		attackLanded = false;
		recentAttacks.addLast(attack);
		while (recentAttacks.size() > RECENT_ATTACK_LIMIT) {
			recentAttacks.removeFirst();
		}
		attackReadyTicks[attack] = tickCount + scaledCooldown(MushroomKingCombatDirector.cooldown(attack));
		if (combo == null && !MushroomKingAttackRules.isUnavoidableAttack(attack)) {
			directionReadyTicks[direction.ordinal()] = tickCount + scaledCooldown(directionCooldown(direction));
		} else if (combo != null) {
			for (BossAttackDirection comboDirection : BossAttackDirection.values()) {
				if (combo.contains(comboDirection)) {
					directionReadyTicks[comboDirection.ordinal()] =
							tickCount + scaledCooldown(directionCooldown(comboDirection));
				}
			}
		}
		faceLockedYaw();
		// Combo bones are driven beat-by-beat. Playing the old full combo clip here
		// would fight the step controller and make later strikes twitch or disappear.
		if (combo == null) {
			triggerAnim("combat", animationTriggerFor(attack, variant));
		}
		playSound(ModSounds.MUSHROOM_KING_WINDUP.get(), 1.8F,
				direction == BossAttackDirection.MID ? 0.88F
						: direction == BossAttackDirection.HIGH ? 1.02F : 0.74F);
		if (combo != null) {
			announceComboCue();
		} else if (MushroomKingAttackRules.isUnavoidableAttack(attack)) {
			// The arena-wide ultimate has no fake high/low/parry cue.
		} else if (direction == BossAttackDirection.MID) {
			announceMidAttackCue(attack);
		} else {
			announceDirectionCue(direction);
		}
	}

	private int nextAttackVariant(int attack) {
		if (MushroomComboPattern.isComboAttack(attack)) {
			int sequence = switch (attack) {
				case MushroomKingAttackRules.ROOT_CADENCE -> nextMidComboSequence++;
				case MushroomKingAttackRules.MYCELIAL_SURGE -> nextLowComboSequence++;
				case MushroomKingAttackRules.CANOPY_CLOSURE -> nextMixedComboSequence++;
				default -> 0;
			};
			int variant = MushroomComboPattern.variantForAttackSequence(
					attack, sequence);
			return variant;
		}
		return MushroomComboPattern.randomVariant(attack, random.nextInt());
	}

	private void announceComboCue() {
		for (Player player : combatPlayers(32.0)) {
			sendCombatFeedback(player, SnakeCombatFeedbackPayload.MUSHROOM_ROOT_COMBO_CUE, 0.0F);
		}
	}

	private void announceMidAttackCue(int attack) {
		int feedback = switch (attack) {
			case MushroomKingAttackRules.ROOT_CADENCE ->
					SnakeCombatFeedbackPayload.MUSHROOM_ROOT_COMBO_CUE;
			case MushroomKingAttackRules.STEM_HAMMER ->
					SnakeCombatFeedbackPayload.MUSHROOM_HAMMER_CUE;
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY ->
					SnakeCombatFeedbackPayload.MUSHROOM_NEEDLE_COMBO_CUE;
			case MushroomKingAttackRules.CAP_CLAP ->
					SnakeCombatFeedbackPayload.MUSHROOM_CLAP_CUE;
			default -> SnakeCombatFeedbackPayload.MID_CUE;
		};
		for (Player player : combatPlayers(32.0)) {
			sendCombatFeedback(player, feedback, 0.0F);
		}
	}

	private void announceDirectionCue(BossAttackDirection direction) {
		int feedback = switch (direction) {
			case HIGH -> SnakeCombatFeedbackPayload.HIGH_CUE;
			case MID -> SnakeCombatFeedbackPayload.MID_CUE;
			case LOW -> SnakeCombatFeedbackPayload.LOW_CUE;
		};
		for (Player player : combatPlayers(32.0)) {
			sendCombatFeedback(player, feedback, 0.0F);
		}
	}

	private static int directionCooldown(BossAttackDirection direction) {
		return switch (direction) {
			case HIGH -> DIRECTION_COOLDOWN_HIGH;
			case LOW -> DIRECTION_COOLDOWN_LOW;
			case MID -> DIRECTION_COOLDOWN_MID;
		};
	}

	private int scaledCooldown(int ticks) {
		return getForm() == FORM_GREEN
				? Math.max(1, Math.round(ticks * GREEN_FORM_COOLDOWN_MULTIPLIER))
				: ticks;
	}

	private float getFormDamageMultiplier() {
		return getForm() == FORM_NORMAL ? 1.0F : FORM_DAMAGE_MULTIPLIER;
	}

	private void tickForm() {
		int remaining = getFormTicks();
		if (remaining <= 0) {
			if (getForm() != FORM_NORMAL) {
				entityData.set(DATA_FORM, FORM_NORMAL);
			}
			return;
		}
		entityData.set(DATA_FORM_TICKS, remaining - 1);
		if (remaining == 1) {
			entityData.set(DATA_FORM, FORM_NORMAL);
		}
	}

	private int extendedFormDuration(int form, int incomingDuration) {
		int duration = Math.max(1, incomingDuration);
		if (getForm() == form && getFormTicks() > 0) {
			return Math.max(duration, getFormTicks()) + FORM_EXTENSION_TICKS;
		}
		return duration;
	}

	private void setForm(int form, int duration) {
		entityData.set(DATA_FORM, Mth.clamp(form, FORM_NORMAL, FORM_PURPLE));
		entityData.set(DATA_FORM_TICKS, Math.max(1, duration));
	}

	private static int formForEffect(MobEffectInstance effect) {
		if (effect.getEffect().equals(MobEffects.POISON)) {
			return FORM_GREEN;
		}
		if (effect.getEffect().equals(MobEffects.WITHER)) {
			return FORM_PURPLE;
		}
		return FORM_NORMAL;
	}

	private void updateAttackTracking(LivingEntity target, int attack) {
		MushroomComboPattern combo = getComboPattern();
		if (combo != null) {
			int stepIndex = combo.cueStepIndex(getAttackTick());
			if (getAttackTick() >= combo.lockTick(stepIndex)) {
				return;
			}
		} else if (getAttackTick() >= MushroomKingAttackRules.lockTick(attack)) {
			return;
		}
		Vec3 predicted = target.position().add(target.getDeltaMovement().multiply(0.65, 0.0, 0.65));
		float desiredYaw = yawToward(predicted);
		float trackedYaw = Mth.approachDegrees(getLockedYaw(), desiredYaw, 60.0F);
		entityData.set(DATA_LOCKED_YAW, trackedYaw);
		entityData.set(DATA_LOCKED_X, (float)predicted.x);
		entityData.set(DATA_LOCKED_Y, (float)target.getY());
		entityData.set(DATA_LOCKED_Z, (float)predicted.z);
	}

	private void tickAttack(int attack) {
		int attackTick = getAttackTick() + 1;
		entityData.set(DATA_ATTACK_TICK, attackTick);
		MushroomComboPattern combo = getComboPattern();
		if (combo != null) {
			tickComboCue(combo, attackTick);
			advanceComboMelee(combo, attackTick);
			tickComboAttack(attackTick);
			return;
		}
		emitAttackChargeParticles(attack, attackTick);
		switch (attack) {
			case MushroomKingAttackRules.STEM_HAMMER -> tickStemHammer(attackTick);
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY -> tickSporeNeedleVolley(attackTick);
			case MushroomKingAttackRules.CAP_CLAP -> tickCapClap(attackTick);
			case MushroomKingAttackRules.CANOPY_CLOSURE -> tickCanopyClosure(attackTick);
			case MushroomKingAttackRules.SPORE_BREATH -> tickSporeBreath(attackTick);
			case MushroomKingAttackRules.ROOT_CONVERGENCE -> tickRootConvergence(attackTick);
			default -> finishAttack();
		}
	}

	private void advanceComboMelee(MushroomComboPattern combo, int tick) {
		int stepIndex = combo.cueStepIndex(tick);
		MushroomComboPattern.Step step = combo.step(stepIndex);
		if (step.direction() != BossAttackDirection.MID
				|| tick < combo.cueTick(stepIndex)
				|| tick >= combo.lockTick(stepIndex)) {
			return;
		}
		LivingEntity target = getTarget();
		if (target == null) {
			return;
		}
		Vec3 toTarget = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
		double distance = toTarget.length();
		if (distance <= 1.85D || distance < 1.0E-4D) {
			return;
		}

		// Step toward the live target instead of sliding along the original cast axis.
		// This keeps the visible arm/cap contact aligned when a player circles at melee range.
		int remaining = combo.lockTick(stepIndex) - tick;
		double approach = remaining <= 3
				? Math.min(0.34D, Math.max(0.20D, (distance - 1.85D) * 0.32D))
				: Math.min(0.20D, Math.max(0.08D, (distance - 1.85D) * 0.10D));
		Vec3 targetDirection = toTarget.scale(1.0D / distance);
		Vec3 committedDirection = targetDirection.scale(0.82D)
				.add(lockedForward().scale(0.18D)).normalize();
		move(MoverType.SELF, committedDirection.scale(approach));
	}

	private void tickComboCue(MushroomComboPattern combo, int tick) {
		int stepIndex = combo.cueStepIndex(tick);
		MushroomComboPattern.Step step = combo.step(stepIndex);
		BossAttackDirection direction = step.direction();
		if (getAttackDirection() != direction) {
			entityData.set(DATA_ATTACK_DIRECTION, direction.ordinal());
		}
		if (tick == combo.cueTick(stepIndex)) {
			triggerAnim("combat", comboStepAnimation(step));
			if (direction == BossAttackDirection.MID) {
				playMidBeatCue(stepIndex);
			} else if (stepIndex == 0
					|| combo.step(stepIndex - 1).direction() != direction) {
				announceDirectionCue(direction);
			}
		}
		emitComboChargeParticles(combo, tick, stepIndex);
	}

	private static String comboStepAnimation(MushroomComboPattern.Step step) {
		return switch (step.move()) {
			case ROOT_SWEEP_LEFT -> "combo_mid_step_left";
			case ROOT_SWEEP_RIGHT -> "combo_mid_step_right";
			case CAP_CLAP -> "combo_mid_step_clap";
			case STEM_HAMMER -> "combo_mid_step_slam";
			case CAP_DRIVE -> "combo_mid_step_drive";
			case ROOT_UPPERCUT -> "combo_mid_step_uppercut";
			case SHOULDER_RUSH -> "combo_mid_step_rush";
			case ROOT_STOMP_LEFT -> "combo_low_step_left";
			case ROOT_STOMP_RIGHT -> "combo_low_step_right";
			case ROOT_SURGE -> "combo_low_step_surge";
			case ROOT_CLAMP -> "combo_low_step_clamp";
			case CAP_QUAKE -> "combo_low_step_quake";
			case MYCELIUM_ERUPTION -> "combo_low_step_erupt";
			case CANOPY_CLOSURE -> "combo_high_step_finish";
			case SPORE_BREATH -> "combo_high_step_breath";
		};
	}

	private void playMidBeatCue(int stepIndex) {
		playSound(ModSounds.MUSHROOM_KING_WINDUP.get(), 1.35F,
				stepIndex == 0 ? 0.82F : 0.92F + stepIndex * 0.05F);
	}

	private void tickComboAttack(int tick) {
		MushroomComboPattern combo = getComboPattern();
		if (combo == null) {
			finishAttack();
			return;
		}
		int stepIndex = combo.activeStepAt(tick);
		if (stepIndex >= 0) {
			MushroomComboPattern.Step step = combo.step(stepIndex);
			if (tick == step.impactTick()) {
				playComboImpact(step, stepIndex, combo.stepCount());
			}
			resolveComboStep(step, stepIndex);
		}
		if (tick >= combo.endTick()) {
			finishAttack();
		}
	}

	private void playComboImpact(MushroomComboPattern.Step step, int stepIndex, int stepCount) {
		BossAttackDirection direction = step.direction();
		float pitch = stepIndex == stepCount - 1 ? 0.78F : 0.92F + stepIndex * 0.04F;
		playSound(comboImpactSound(step.move()), 2.15F, pitch);
		Vec3 center = position().add(lockedForward().scale(direction == BossAttackDirection.HIGH ? 4.8 : 3.4))
				.add(0.0, direction == BossAttackDirection.HIGH ? 2.15 : 0.35, 0.0);
		if (direction != BossAttackDirection.MID) {
			emitFungalImpact(center, stepIndex == stepCount - 1 ? 15 : 9);
		}
		if (direction == BossAttackDirection.LOW) {
			MushroomComboPattern combo = getComboPattern();
			if (step.move() == MushroomComboPattern.Move.ROOT_SURGE) {
				double start = combo == null ? 0.35 : combo.advanceStart(stepIndex);
				spawnRootWave(position().add(lockedForward().scale(start)).add(0.0, 0.08, 0.0),
						lockedForward(), stepIndex, stepIndex == stepCount - 1 ? 1.45F : 1.15F);
			} else if (step.move() == MushroomComboPattern.Move.ROOT_CLAMP) {
				Vec3 forward = lockedForward();
				Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
				Vec3 clampCenter = position().add(forward.scale(2.2)).add(0.0, 0.06, 0.0);
				spawnRootWave(clampCenter.add(side.scale(2.2)),
						forward.scale(0.35).add(side.scale(-1.0)).normalize(), stepIndex, 1.18F);
				spawnRootWave(clampCenter.add(side.scale(-2.2)),
						forward.scale(0.35).add(side).normalize(), stepIndex + 4, 1.18F);
			} else if (step.move() == MushroomComboPattern.Move.CAP_QUAKE) {
				spawnRootBurst(position().add(lockedForward().scale(1.8)).add(0.0, 0.04, 0.0),
						stepIndex + 6, stepIndex == stepCount - 1 ? 1.7F : 1.4F);
			} else if (step.move() == MushroomComboPattern.Move.MYCELIUM_ERUPTION) {
				Vec3 forward = lockedForward();
				for (int burst = 1; burst <= 3; burst++) {
					spawnRootBurst(position().add(forward.scale(1.15 + burst * 1.05))
							.add(0.0, 0.04, 0.0), stepIndex * 4 + burst, 0.82F + burst * 0.16F);
				}
			} else {
				Vec3 forward = lockedForward();
				Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
				double footSide = step.move() == MushroomComboPattern.Move.ROOT_STOMP_LEFT ? 0.95 : -0.95;
				spawnRootBurst(position().add(forward.scale(0.75)).add(side.scale(footSide))
						.add(0.0, 0.04, 0.0), stepIndex,
						stepIndex == stepCount - 1 ? 1.28F : 1.0F);
			}
		} else if (direction == BossAttackDirection.HIGH) {
			if (step.move() == MushroomComboPattern.Move.SPORE_BREATH) {
				Vec3 mouth = position().add(lockedForward().scale(0.9)).add(0.0, 3.0, 0.0);
				for (double spread : new double[] {-13.0, 0.0, 13.0}) {
					spawnVisualSporeMass(mouth, rotateY(lockedForward(), spread),
							stepIndex == stepCount - 1 ? 1.05F : 0.86F, stepIndex + 1);
				}
			} else {
				Vec3 canopyContact = position().add(lockedForward().scale(3.25)).add(0.0, 2.65, 0.0);
				emitFungalImpact(canopyContact, stepIndex == stepCount - 1 ? 14 : 9);
			}
		} else {
			Vec3 forward = lockedForward();
			Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
			Vec3 contact = switch (step.move()) {
				case ROOT_SWEEP_LEFT -> position().add(forward.scale(2.0))
						.add(side.scale(1.45)).add(0.0, 1.55, 0.0);
				case ROOT_SWEEP_RIGHT -> position().add(forward.scale(2.0))
						.add(side.scale(-1.45)).add(0.0, 1.55, 0.0);
				case CAP_CLAP -> position().add(forward.scale(2.45)).add(0.0, 2.15, 0.0);
				case STEM_HAMMER -> position().add(forward.scale(2.75)).add(0.0, 0.55, 0.0);
				case CAP_DRIVE -> position().add(forward.scale(2.65)).add(0.0, 1.65, 0.0);
				case ROOT_UPPERCUT -> position().add(forward.scale(2.45)).add(0.0, 2.05, 0.0);
				case SHOULDER_RUSH -> position().add(forward.scale(2.8)).add(0.0, 1.55, 0.0);
				default -> position().add(forward.scale(2.2)).add(0.0, 1.2, 0.0);
			};
			emitMeleeImpact(contact, step.move(), stepIndex == stepCount - 1);
		}
	}

	private SoundEvent comboImpactSound(MushroomComboPattern.Move move) {
		return switch (move) {
			case ROOT_SWEEP_LEFT, ROOT_SWEEP_RIGHT, ROOT_UPPERCUT ->
					ModSounds.MUSHROOM_KING_ROOT_CADENCE.get();
			case CAP_CLAP -> ModSounds.MUSHROOM_KING_CAP_CLAP.get();
			case STEM_HAMMER -> ModSounds.MUSHROOM_KING_STEM_HAMMER.get();
			case CAP_DRIVE, SHOULDER_RUSH -> ModSounds.MUSHROOM_KING_STRIKE.get();
			case ROOT_STOMP_LEFT, ROOT_STOMP_RIGHT -> ModSounds.MUSHROOM_KING_MYCELIAL_SURGE.get();
			case ROOT_SURGE -> ModSounds.MUSHROOM_KING_ROOT_CONVERGENCE.get();
			case ROOT_CLAMP -> ModSounds.MUSHROOM_KING_ROOT_CONVERGENCE.get();
			case CAP_QUAKE -> ModSounds.MUSHROOM_KING_STEM_HAMMER.get();
			case MYCELIUM_ERUPTION -> ModSounds.MUSHROOM_KING_MYCELIAL_SURGE.get();
			case CANOPY_CLOSURE -> ModSounds.MUSHROOM_KING_CANOPY_CLOSURE.get();
			case SPORE_BREATH -> ModSounds.MUSHROOM_KING_SPORE_BREATH.get();
		};
	}

	private void resolveComboStep(MushroomComboPattern.Step step, int stepIndex) {
		BossAttackDirection direction = step.direction();
		int hitId = stepIndex + 1;
		if (direction == BossAttackDirection.MID) {
			resolveMeleeComboStep(step.move(), hitId);
			return;
		}
		if (direction == BossAttackDirection.HIGH) {
			hitRibbon(position().add(lockedForward().scale(0.15)), lockedForward(), 4.15, 2.35,
					16.0F, direction, hitId, 0.75);
			return;
		}
		MushroomComboPattern combo = getComboPattern();
		Vec3 forward = lockedForward();
		if (step.move() == MushroomComboPattern.Move.ROOT_SURGE) {
			double start = combo == null ? 0.35 : combo.advanceStart(stepIndex);
			double length = combo == null ? MushroomAttackVolume.SURGE_LENGTH
					: combo.advanceLength(stepIndex);
			Vec3 origin = position().add(forward.scale(start));
			hitRibbon(origin, forward, length, MushroomAttackVolume.SURGE_HALF_WIDTH,
					12.0F, direction, hitId, 0.9);
			return;
		}
		if (step.move() == MushroomComboPattern.Move.ROOT_CLAMP) {
			Vec3 center = position().add(forward.scale(2.15));
			hitRadial(center, 3.15, 13.0F, direction, hitId, 0.95);
			return;
		}
		if (step.move() == MushroomComboPattern.Move.CAP_QUAKE) {
			Vec3 center = position().add(forward.scale(1.65));
			hitRadial(center, 3.35, 14.0F, direction, hitId, 1.05);
			return;
		}
		if (step.move() == MushroomComboPattern.Move.MYCELIUM_ERUPTION) {
			hitRibbon(position().add(forward.scale(0.25)), forward, 4.75, 2.45,
					13.0F, direction, hitId, 1.0);
			return;
		}
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double footSide = step.move() == MushroomComboPattern.Move.ROOT_STOMP_LEFT ? 0.95 : -0.95;
		Vec3 center = position().add(forward.scale(0.75)).add(side.scale(footSide));
		hitRadial(center, 2.85, 12.0F, direction, hitId, 0.85);
	}

	private void emitMeleeImpact(Vec3 center, MushroomComboPattern.Move move, boolean finisher) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		int flashCount = finisher ? 2 : 1;
		int shardCount = finisher ? 9 : 6;
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
				center.x, center.y, center.z, flashCount, 0.18, 0.16, 0.18, 0.008);
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
				center.x, center.y, center.z, shardCount,
				move == MushroomComboPattern.Move.CAP_CLAP ? 0.5 : 0.32,
				0.18, 0.32, finisher ? 0.11 : 0.075);
	}

	private void resolveMeleeComboStep(MushroomComboPattern.Move move, int hitId) {
		Vec3 forward = lockedForward();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		switch (move) {
			case ROOT_SWEEP_LEFT, ROOT_SWEEP_RIGHT -> {
				double handSide = move == MushroomComboPattern.Move.ROOT_SWEEP_LEFT ? 1.0 : -1.0;
				Vec3 hand = position().add(forward.scale(0.35)).add(side.scale(handSide * 1.65));
				Vec3 sweepEnd = position().add(forward.scale(3.65)).add(side.scale(-handSide * 0.8));
				Vec3 sweep = sweepEnd.subtract(hand);
				hitRibbon(hand, sweep, sweep.length(), 1.5,
						7.0F, BossAttackDirection.MID, hitId, 0.65);
				hitRibbon(position().add(forward.scale(-0.7)), forward, 3.05, 2.15,
						7.0F, BossAttackDirection.MID, hitId, 0.65);
			}
			case CAP_CLAP -> {
				Vec3 leftCap = position().add(forward.scale(0.55)).add(side.scale(2.0));
				Vec3 rightCap = position().add(forward.scale(0.55)).add(side.scale(-2.0));
				Vec3 center = position().add(forward.scale(2.65));
				hitRibbon(leftCap, center.subtract(leftCap), leftCap.distanceTo(center), 1.35,
						8.0F, BossAttackDirection.MID, hitId, 0.75);
				hitRibbon(rightCap, center.subtract(rightCap), rightCap.distanceTo(center), 1.35,
						8.0F, BossAttackDirection.MID, hitId, 0.75);
				hitRibbon(position().add(forward.scale(-0.4)), forward, 3.35, 2.3,
						8.0F, BossAttackDirection.MID, hitId, 0.75);
			}
			case STEM_HAMMER -> {
				hitRibbon(position().add(forward.scale(-0.45)), forward, 3.7, 2.35,
						10.0F, BossAttackDirection.MID, hitId, 1.05);
				hitCone(3.75, 76.0, 10.0F, BossAttackDirection.MID, hitId, 1.05);
			}
			case CAP_DRIVE -> {
				hitRibbon(position().add(forward.scale(-0.7)), forward, 3.85, 2.45,
						10.0F, BossAttackDirection.MID, hitId, 0.9);
				hitCone(4.0, 82.0, 10.0F, BossAttackDirection.MID, hitId, 0.9);
			}
			case ROOT_UPPERCUT -> {
				Vec3 lowRoot = position().add(forward.scale(0.2)).add(side.scale(-0.65));
				Vec3 highContact = position().add(forward.scale(3.25)).add(side.scale(0.45));
				Vec3 risingSweep = highContact.subtract(lowRoot);
				hitRibbon(lowRoot, risingSweep, risingSweep.length(), 1.55,
						9.0F, BossAttackDirection.MID, hitId, 0.82);
				hitRibbon(position().add(forward.scale(-0.45)), forward, 3.5, 1.85,
						9.0F, BossAttackDirection.MID, hitId, 0.82);
			}
			case SHOULDER_RUSH -> {
				hitRibbon(position().add(forward.scale(-0.85)), forward, 4.15, 2.25,
						10.0F, BossAttackDirection.MID, hitId, 0.96);
				hitCone(4.2, 68.0, 10.0F, BossAttackDirection.MID, hitId, 0.96);
			}
			default -> {
				// Non-mid moves never reach this resolver.
			}
		}
	}

	private void emitComboChargeParticles(MushroomComboPattern combo, int tick, int stepIndex) {
		int impact = combo.step(stepIndex).impactTick();
		int remaining = impact - tick;
		if (remaining < 0 || (remaining > 8 && tick % 5 != 0)
				|| (remaining <= 8 && tick % 2 != 0)) {
			return;
		}
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		BossAttackDirection direction = combo.step(stepIndex).direction();
		if (direction == BossAttackDirection.MID) {
			return;
		}
		Vec3 forward = lockedForward();
		Vec3 center = switch (direction) {
			case HIGH -> position().add(forward.scale(0.5)).add(0.0, 3.6, 0.0);
			case MID -> position().add(forward.scale(1.2)).add(0.0, 1.8, 0.0);
			case LOW -> position().add(forward.scale(0.7)).add(0.0, 0.2, 0.0);
		};
		serverLevel.sendParticles(cueParticle(direction), center.x, center.y, center.z,
				remaining <= 3 ? 4 : 2, 0.34, direction == BossAttackDirection.LOW ? 0.06 : 0.25,
				0.34, 0.01);
	}

	private void tickStemHammer(int tick) {
		int impact = MushroomKingAttackRules.impactTick(MushroomKingAttackRules.STEM_HAMMER, 1);
		if (tick == impact) {
			playSound(ModSounds.MUSHROOM_KING_STEM_HAMMER.get(), 2.25F, 0.72F);
			emitFungalImpact(position().add(lockedForward().scale(3.4)).add(0.0, 0.4, 0.0), 15);
			emitPathParticles(position(), lockedForward(),
					MushroomAttackVolume.radius(MushroomKingAttackRules.STEM_HAMMER, 1),
					BossAttackDirection.MID, 7, 0.38);
		}
		if (tick >= impact && tick <= impact + 2) {
			hitCone(MushroomAttackVolume.radius(MushroomKingAttackRules.STEM_HAMMER, 1),
					MushroomAttackVolume.angleDegrees(MushroomKingAttackRules.STEM_HAMMER, 1),
					15.0F, BossAttackDirection.MID, 1, 1.2);
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.STEM_HAMMER)) {
			finishAttack();
		}
	}

	private void tickSporeNeedleVolley(int tick) {
		int wave = MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, tick);
		if (wave > 0) {
			if (tick == MushroomKingAttackRules.impactTick(
					MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, wave)) {
				playSound(ModSounds.MUSHROOM_KING_SPORE_NEEDLE.get(),
						1.8F, 1.08F + wave * 0.05F);
			}
			Vec3 side = new Vec3(-lockedForward().z, 0.0, lockedForward().x);
			Vec3 origin = position().add(side.scale(wave == 2 ? 0.9 : -0.9));
			if (tick == MushroomKingAttackRules.impactTick(
					MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY, wave)) {
				emitPathParticles(origin, lockedForward(), MushroomAttackVolume.NEEDLE_LENGTH,
						BossAttackDirection.MID, 7, 1.55);
			}
			hitRibbon(origin, lockedForward(), MushroomAttackVolume.NEEDLE_LENGTH,
					MushroomAttackVolume.NEEDLE_HALF_WIDTH, 6.0F,
					BossAttackDirection.MID, wave, 0.3);
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY)) {
			finishAttack();
		}
	}

	private void tickCapClap(int tick) {
		int impact = MushroomKingAttackRules.impactTick(MushroomKingAttackRules.CAP_CLAP, 1);
		if (tick == impact) {
			playSound(ModSounds.MUSHROOM_KING_CAP_CLAP.get(), 2.2F, 0.78F);
			emitFungalImpact(position().add(lockedForward().scale(3.0)).add(0.0, 1.8, 0.0), 14);
			emitPathParticles(position(), lockedForward(),
					MushroomAttackVolume.radius(MushroomKingAttackRules.CAP_CLAP, 1),
					BossAttackDirection.MID, 7, 1.75);
		}
		if (tick >= impact && tick <= impact + 2) {
			hitCone(MushroomAttackVolume.radius(MushroomKingAttackRules.CAP_CLAP, 1),
					MushroomAttackVolume.angleDegrees(MushroomKingAttackRules.CAP_CLAP, 1),
					13.0F, BossAttackDirection.MID, 1, 0.85);
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.CAP_CLAP)) {
			finishAttack();
		}
	}

	private void tickCanopyClosure(int tick) {
		int impact = MushroomKingAttackRules.impactTick(MushroomKingAttackRules.CANOPY_CLOSURE, 1);
		if (tick == impact) {
			playSound(ModSounds.MUSHROOM_KING_CANOPY_CLOSURE.get(), 2.3F, 0.8F);
			emitFungalImpact(position().add(lockedForward().scale(3.6)).add(0.0, 2.2, 0.0), 17);
		}
		if (tick >= impact && tick <= impact + 2) {
			hitCone(MushroomAttackVolume.radius(MushroomKingAttackRules.CANOPY_CLOSURE, 1),
					MushroomAttackVolume.angleDegrees(MushroomKingAttackRules.CANOPY_CLOSURE, 1),
					18.0F, BossAttackDirection.HIGH, 1, 1.1);
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.CANOPY_CLOSURE)) {
			finishAttack();
		}
	}

	private void tickSporeBreath(int tick) {
		int wave = MushroomKingAttackRules.activeWaveAtTick(
				MushroomKingAttackRules.SPORE_BREATH, tick);
		if (wave > 0) {
			int impact = MushroomKingAttackRules.impactTick(
					MushroomKingAttackRules.SPORE_BREATH, wave);
			Vec3 direction = lockedForward();
			if (tick == impact) {
				playSound(ModSounds.MUSHROOM_KING_SPORE_BREATH.get(),
						2.1F, 0.76F + wave * 0.08F);
				Vec3 mouth = position().add(direction.scale(0.9)).add(0.0, 3.0, 0.0);
				for (double spread : new double[] {-6.0, 0.0, 6.0}) {
					spawnSporeMass(mouth, rotateY(direction, spread),
							1.10F + wave * 0.16F, wave);
				}
			}
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.SPORE_BREATH)) {
			finishAttack();
		}
	}


	private void tickRootConvergence(int tick) {
		if (tick == 18 || tick == 34 || tick == 48) {
			int stage = tick == 18 ? 1 : tick == 34 ? 2 : 3;
			playSound(ModSounds.MUSHROOM_KING_ROOT_CONVERGENCE.get(),
					1.55F + stage * 0.18F, 0.56F + stage * 0.10F);
			spawnConvergenceRoots(stage);
			emitFungalImpact(position().add(0.0, 0.18 + stage * 0.28, 0.0), 8 + stage * 3);
		}
		int impact = MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CONVERGENCE, 1);
		if (tick == impact) {
			playSound(ModSounds.MUSHROOM_KING_ROOT_CONVERGENCE.get(), 3.2F, 0.48F);
			spawnConvergenceRoots(4);
			emitConvergenceParticles(MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS, 0.0);
			hitUnavoidableArena(position(), MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS,
					12.0F);
		}
		if (tick >= MushroomKingAttackRules.endTick(MushroomKingAttackRules.ROOT_CONVERGENCE)) {
			finishAttack();
		}
	}

	private void emitFungalImpact(Vec3 center, int count) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
				center.x, center.y, center.z, Math.max(2, count / 4),
				0.34, 0.26, 0.34, 0.015);
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
				center.x, center.y, center.z, Math.max(4, count / 2),
				0.55, 0.32, 0.55, 0.025);
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
				center.x, center.y, center.z, Math.max(3, count / 3),
				0.48, 0.22, 0.48, 0.075);
	}

	private void spawnSporeMass(Vec3 origin, Vec3 direction, float scale, int wave) {
		spawnSporeMass(origin, direction, scale, wave, getId());
	}

	private void spawnVisualSporeMass(Vec3 origin, Vec3 direction, float scale, int wave) {
		spawnSporeMass(origin, direction, scale, wave, -1);
	}

	private void spawnSporeMass(Vec3 origin, Vec3 direction, float scale, int wave, int ownerId) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		MushroomSporeMassEntity mass = ModEntityTypes.MUSHROOM_SPORE_MASS.get().create(serverLevel);
		if (mass == null) {
			return;
		}
		mass.moveTo(origin.x, origin.y, origin.z, getLockedYaw(), 0.0F);
		mass.configure(28, scale * 1.12F, ownerId, wave);
		mass.setDeltaMovement(direction.normalize().scale(0.54).add(0.0, 0.055, 0.0));
		serverLevel.addFreshEntity(mass);
	}

	public boolean resolveSporeMassContact(Vec3 center, double radius, int wave) {
		AABB area = new AABB(center, center).inflate(radius, radius * 0.72, radius);
		boolean contacted = false;
		for (Player player : level().getEntitiesOfClass(Player.class, area,
				VirtualMushroomKing::isCombatPlayer)) {
			contacted = true;
			resolveHit(player, 12.0F, BossAttackDirection.HIGH, wave, 0.35);
		}
		return contacted;
	}

	private void spawnRootWave(Vec3 origin, Vec3 direction, int variant, float scale) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		MushroomRootWaveEntity wave = ModEntityTypes.MUSHROOM_ROOT_WAVE.get().create(serverLevel);
		if (wave == null) {
			return;
		}
		float yaw = (float)(Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
		wave.moveTo(origin.x, origin.y, origin.z, yaw, 0.0F);
		wave.configure(20, variant, scale * 1.12F);
		wave.setDeltaMovement(direction.normalize().scale(0.58));
		serverLevel.addFreshEntity(wave);
	}

	private void spawnRootBurst(Vec3 center, int variant, float scale) {
		for (int ray = 0; ray < 6; ray++) {
			double angle = Mth.TWO_PI * ray / 6.0 + variant * 0.21;
			Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			spawnRootWave(center.add(direction.scale(0.25)), direction, variant + ray,
					scale * (0.78F + (ray & 1) * 0.12F));
		}
	}

	private void spawnConvergenceRoots(int stage) {
		double radius = stage >= 4 ? 3.0 : 15.0 - stage * 3.0;
		int roots = stage >= 4 ? 16 : 8 + stage * 2;
		double rotation = getAttackVariant() * 0.37 + stage * 0.23;
		for (int index = 0; index < roots; index++) {
			double angle = Mth.TWO_PI * index / roots + rotation;
			Vec3 inward = new Vec3(-Math.cos(angle), 0.0, -Math.sin(angle));
			Vec3 origin = position().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
			spawnRootWave(origin, inward, index + stage,
					stage >= 4 ? 1.65F : 0.92F + stage * 0.16F);
		}
	}

	private void emitAttackChargeParticles(int attack, int tick) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (MushroomKingAttackRules.isUnavoidableAttack(attack)) {
			emitUltimateChargeParticles(serverLevel, tick);
			return;
		}
		int nextImpact = nextImpactTick(attack, tick);
		if (nextImpact < 0) {
			return;
		}
		int remaining = nextImpact - tick;
		if (remaining > 8 && tick % 5 != 0) {
			return;
		}
		if (remaining <= 8 && tick % 2 != 0) {
			return;
		}
		BossAttackDirection direction = MushroomKingAttackRules.directionFor(attack);
		Vec3 forward = lockedForward();
		Vec3 center = switch (direction) {
			case HIGH -> position().add(forward.scale(0.35)).add(0.0, 3.65, 0.0);
			case MID -> position().add(forward.scale(1.25)).add(0.0, 1.85, 0.0);
			case LOW -> position().add(forward.scale(0.65)).add(0.0, 0.2, 0.0);
		};
		double spreadY = direction == BossAttackDirection.LOW ? 0.08 : 0.28;
		serverLevel.sendParticles(cueParticle(direction), center.x, center.y, center.z,
				remaining <= 3 ? 4 : 2, 0.38, spreadY, 0.38, 0.01);
		serverLevel.sendParticles(direction == BossAttackDirection.LOW
						? ModParticleTypes.MUSHROOM_ROOT_SHARD.get()
						: ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
				center.x, center.y, center.z, remaining <= 3 ? 3 : 1,
				0.3, spreadY, 0.3, 0.018);
		if (remaining == 1) {
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
					center.x, center.y, center.z, 3, 0.18, 0.18, 0.18, 0.0);
		}
	}

	private void emitUltimateChargeParticles(ServerLevel serverLevel, int tick) {
		int impact = MushroomKingAttackRules.impactTick(
				MushroomKingAttackRules.ROOT_CONVERGENCE, 1);
		if (tick > impact || tick % (tick >= impact - 10 ? 2 : 4) != 0) {
			return;
		}
		float progress = Mth.clamp(tick / (float)impact, 0.0F, 1.0F);
		double radius = 1.8 + progress * 3.2;
		int points = tick >= impact - 10 ? 6 : 3;
		for (int point = 0; point < points; point++) {
			double angle = tick * 0.11 + Mth.TWO_PI * point / points;
			Vec3 position = position().add(Math.cos(angle) * radius,
					0.45 + progress * 3.5, Math.sin(angle) * radius);
			serverLevel.sendParticles((point & 1) == 0
						? ModParticleTypes.MUSHROOM_SPORE_GLOW.get()
						: ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
					position.x, position.y, position.z, 1, 0.08, 0.10, 0.08, 0.012);
		}
		if (tick >= impact - 6) {
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
					getX(), getY() + 3.0, getZ(), 2, 0.35, 0.42, 0.35, 0.0);
		}
	}

	private int nextImpactTick(int attack, int tick) {
		for (int wave = 1; wave <= MushroomKingAttackRules.waveCount(attack); wave++) {
			int impact = MushroomKingAttackRules.impactTick(attack, wave);
			if (impact >= tick) {
				return impact;
			}
		}
		return -1;
	}

	private void emitPathParticles(Vec3 origin, Vec3 direction, double length,
			BossAttackDirection attackDirection, int samples, double yOffset) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 forward = direction.multiply(1.0, 0.0, 1.0).normalize();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		for (int sample = 1; sample <= samples; sample++) {
			double progress = sample / (double)samples;
			double weave = Math.sin(progress * Math.PI * 3.0 + getAttackVariant()) * 0.16;
			Vec3 point = origin.add(forward.scale(length * progress))
					.add(side.scale(weave)).add(0.0, yOffset, 0.0);
			ParticleOptions particle = sample % 3 == 0
					? cueParticle(attackDirection)
					: attackDirection == BossAttackDirection.LOW
							? ModParticleTypes.MUSHROOM_ROOT_SHARD.get()
							: ModParticleTypes.MUSHROOM_SPORE_GLOW.get();
			serverLevel.sendParticles(particle, point.x, point.y, point.z,
					1, 0.08, attackDirection == BossAttackDirection.LOW ? 0.05 : 0.14,
					0.08, attackDirection == BossAttackDirection.LOW ? 0.045 : 0.018);
		}
	}

	private void emitConvergenceParticles(double radius, double gapAngleDegrees) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		for (int sample = 0; sample < 24; sample++) {
			double angle = Mth.TWO_PI * sample / 24.0;
			Vec3 point = position().add(Math.cos(angle) * radius, 0.14,
					Math.sin(angle) * radius);
			serverLevel.sendParticles(sample % 3 == 0
							? ModParticleTypes.MUSHROOM_SPORE_GLOW.get()
							: ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
					point.x, point.y, point.z, 1, 0.08, 0.04, 0.08, 0.055);
		}
		serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
				getX(), getY() + 0.18, getZ(), 4, 0.35, 0.08, 0.35, 0.0);
	}

	private static ParticleOptions cueParticle(BossAttackDirection direction) {
		return switch (direction) {
			case HIGH -> ModParticleTypes.SNAKE_HIGH_WARNING.get();
			case MID -> ModParticleTypes.SNAKE_MID_WARNING.get();
			case LOW -> ModParticleTypes.SNAKE_LOW_WARNING.get();
		};
	}

	private void hitCone(double radius, double angleDegrees, float damage,
			BossAttackDirection direction, int wave, double knockback) {
		Vec3 origin = position();
		Vec3 forward = lockedForward();
		double minimumDot = Math.cos(Math.toRadians(angleDegrees * 0.5));
		for (Player player : combatPlayers(radius + 1.0)) {
			if (direction == BossAttackDirection.HIGH
					&& (player.getBoundingBox().maxY < origin.y + MushroomAttackVolume.HIGH_MIN_Y
							|| player.getBoundingBox().minY > origin.y + MushroomAttackVolume.HIGH_MAX_Y)) {
				continue;
			}
			Vec3 offset = player.position().subtract(origin).multiply(1.0, 0.0, 1.0);
			if (offset.lengthSqr() > radius * radius || offset.lengthSqr() < 1.0E-6) {
				continue;
			}
			if (offset.normalize().dot(forward) >= minimumDot) {
				resolveHit(player, damage, direction, wave, knockback);
			}
		}
	}

	private void hitRibbon(Vec3 origin, Vec3 direction, double length, double halfWidth,
			float damage, BossAttackDirection attackDirection, int wave, double knockback) {
		Vec3 forward = direction.multiply(1.0, 0.0, 1.0).normalize();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		AABB area = new AABB(origin, origin.add(forward.scale(length)))
				.inflate(halfWidth + 1.0, 3.0, halfWidth + 1.0);
		for (Player player : level().getEntitiesOfClass(Player.class, area,
				VirtualMushroomKing::isCombatPlayer)) {
			Vec3 offset = player.position().subtract(origin).multiply(1.0, 0.0, 1.0);
			double along = offset.dot(forward);
			double across = Math.abs(offset.dot(side));
			if (along >= -0.5 && along <= length + 0.5
					&& across <= halfWidth + player.getBbWidth() * 0.5) {
				resolveHit(player, damage, attackDirection, wave, knockback);
			}
		}
	}

	private void hitRadial(Vec3 center, double radius, float damage,
			BossAttackDirection direction, int wave, double knockback) {
		AABB area = new AABB(center, center).inflate(radius, 2.5, radius);
		for (Player player : level().getEntitiesOfClass(Player.class, area,
				VirtualMushroomKing::isCombatPlayer)) {
			Vec3 offset = player.position().subtract(center).multiply(1.0, 0.0, 1.0);
			if (offset.lengthSqr() <= radius * radius) {
				resolveHit(player, damage, direction, wave, knockback);
			}
		}
	}

	private void hitUnavoidableArena(Vec3 center, double radius, float damage) {
		AABB area = new AABB(center, center).inflate(radius, 6.0, radius);
		for (Player player : level().getEntitiesOfClass(Player.class, area,
				VirtualMushroomKing::isCombatPlayer)) {
			Vec3 offset = player.position().subtract(center).multiply(1.0, 0.0, 1.0);
			if (offset.lengthSqr() <= radius * radius) {
				resolveUnavoidableHit(player, damage);
			}
		}
	}

	private boolean resolveUnavoidableHit(Player player, float damage) {
		HitKey key = new HitKey(player.getUUID(), 1);
		if (!attackHits.add(key)) {
			return false;
		}
		if (player.isUsingItem()) {
			player.stopUsingItem();
		}
		DamageSource source = damageSources().mobAttack(this);
		float finalDamage = damage * getFormDamageMultiplier();
		if (!player.hurt(source, finalDamage)) {
			return false;
		}
		attackLanded = true;
		applyFungalAttackEffect(player);
		if (level() instanceof ServerLevel serverLevel) {
			Vec3 contact = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
					contact.x, contact.y, contact.z, 10, 0.42, 0.62, 0.42, 0.025);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
					contact.x, contact.y, contact.z, 12, 0.58, 0.28, 0.58, 0.085);
		}
		sendCombatFeedback(player, SnakeCombatFeedbackPayload.HIT, 1.25F);
		return true;
	}

	private boolean resolveHit(Player player, float damage, BossAttackDirection direction,
			int wave, double knockback) {
		HitKey key = new HitKey(player.getUUID(), wave);
		if (!attackHits.add(key)) {
			return false;
		}
		damage *= getFormDamageMultiplier();
		if (direction == BossAttackDirection.HIGH && isTimedHighDodge(player)) {
			sendCombatFeedback(player, SnakeCombatFeedbackPayload.HIGH_DODGE, 0.28F);
			return false;
		}
		if (direction == BossAttackDirection.LOW && isAirborneDodge(player)) {
			jumpStrikeWindows.put(player.getUUID(),
					tickCount + BossPostureRules.JUMP_STRIKE_WINDOW_TICKS);
			sendCombatFeedback(player, SnakeCombatFeedbackPayload.LOW_DODGE, 0.28F);
			return false;
		}
		DamageSource source = damageSources().mobAttack(this);
		boolean weaponParry = direction == BossAttackDirection.MID
				&& dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.isParrying(player, source);
		if (direction == BossAttackDirection.MID && (weaponParry || isPreciseParry(player, source))) {
			damagePosture(Math.round(BossPostureRules.preciseParryDamage()), player);
			if (weaponParry) {
				dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.completeParry(player, this);
			} else if (!isStaggered()) {
				sendCombatFeedback(player, SnakeCombatFeedbackPayload.PARRY, 0.72F);
			}
			return false;
		}
		if (direction == BossAttackDirection.MID
				&& dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.isBlocking(player, source)) {
			dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.failParry(player, this);
			return false;
		}
		if (direction == BossAttackDirection.MID && isShieldBlock(player, source)) {
			player.hurt(source, damage);
			player.disableShield();
			player.stopUsingItem();
			sendCombatFeedback(player, SnakeCombatFeedbackPayload.SHIELD_BREAK, 0.55F);
			return false;
		}
		if (direction != BossAttackDirection.MID
				&& player.isUsingItem() && player.getUseItem().getItem() instanceof ShieldItem) {
			player.stopUsingItem();
		}
		if (!player.hurt(source, damage)) {
			return false;
		}
		attackLanded = true;
		applyFungalAttackEffect(player);
		if (level() instanceof ServerLevel serverLevel) {
			Vec3 contact = player.position().add(0.0, player.getBbHeight() * 0.56, 0.0);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_CAP_FLASH.get(),
					contact.x, contact.y, contact.z, 5, 0.24, 0.34, 0.24, 0.018);
			serverLevel.sendParticles(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(),
					contact.x, contact.y - 0.18, contact.z, 7, 0.34, 0.22, 0.34, 0.055);
			serverLevel.playSound(null, player.blockPosition(), ModSounds.MUSHROOM_KING_STRIKE.get(),
					SoundSource.HOSTILE, 1.65F, 0.74F + random.nextFloat() * 0.08F);
		}
		sendCombatFeedback(player, SnakeCombatFeedbackPayload.HIT,
				Mth.clamp(damage / 11.0F, 0.58F, 1.35F));
		if (knockback > 0.0) {
			Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-6) {
				player.knockback(knockback, -away.x, -away.z);
			}
		}
		return true;
	}

	private void applyFungalAttackEffect(Player player) {
		switch (getAttackState()) {
			case MushroomKingAttackRules.STEM_HAMMER, MushroomKingAttackRules.CAP_CLAP ->
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
			case MushroomKingAttackRules.SPORE_NEEDLE_VOLLEY ->
				player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
			case MushroomKingAttackRules.SPORE_BREATH ->
				player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), this);
			case MushroomKingAttackRules.MYCELIAL_SURGE ->
				player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1), this);
			case MushroomKingAttackRules.ROOT_CONVERGENCE ->
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 0), this);
			default -> {
			}
		}
	}

	private void sendCombatFeedback(Player player, int kind, float strength) {
		if (player instanceof ServerPlayer serverPlayer) {
			PacketDistributor.sendToPlayer(serverPlayer,
					new SnakeCombatFeedbackPayload(getId(), kind, strength));
		}
	}

	private boolean isPreciseParry(Player player, DamageSource source) {
		if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof ShieldItem)
				|| !BossPostureRules.isPreciseParry(true, player.getTicksUsingItem())) {
			return false;
		}
		Vec3 towardSource = position().subtract(player.getEyePosition()).multiply(1.0, 0.0, 1.0);
		Vec3 view = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		return towardSource.lengthSqr() > 1.0E-6 && view.lengthSqr() > 1.0E-6
				&& towardSource.normalize().dot(view.normalize()) >= 0.25
				&& player.isDamageSourceBlocked(source);
	}

	private static boolean isShieldBlock(Player player, DamageSource source) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof ShieldItem
				&& player.isDamageSourceBlocked(source);
	}

	private boolean isAirborneDodge(Player player) {
		UUID playerId = player.getUUID();
		boolean invalidMovement = player.getAbilities().flying
				|| player.isFallFlying() || player.isSwimming();
		return BossPostureRules.isLowDodgeWindow(player.onGround(), invalidMovement,
				activeJumpWindows.getOrDefault(playerId, -1) >= tickCount,
				lowDodgeLandingGrace.getOrDefault(playerId, -1) >= tickCount);
	}

	private boolean isTimedHighDodge(Player player) {
		return player.isShiftKeyDown()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isSwimming()
				&& highDodgeWindows.getOrDefault(player.getUUID(), -1) >= tickCount;
	}

	private void tickJumpWindows() {
		activeJumpWindows.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		lowDodgeLandingGrace.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		highDodgeWindows.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		jumpStrikeWindows.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		for (Player player : combatPlayers(28.0)) {
			trackCrouchStart(player);
			trackNormalJump(player);
		}
	}

	private void trackCrouchStart(Player player) {
		UUID playerId = player.getUUID();
		boolean crouching = player.isShiftKeyDown();
		boolean wasCrouching = crouchStates.getOrDefault(playerId, false);
		crouchStates.put(playerId, crouching);
		if (crouching && !wasCrouching) {
			highDodgeWindows.put(playerId, tickCount + DODGE_INPUT_WINDOW_TICKS);
		} else if (!crouching) {
			highDodgeWindows.remove(playerId);
		}
	}

	private void trackNormalJump(Player player) {
		UUID playerId = player.getUUID();
		if (player.getAbilities().flying || player.isFallFlying() || player.isSwimming()) {
			activeJumpWindows.remove(playerId);
			lowDodgeLandingGrace.remove(playerId);
			airborneStates.put(playerId, false);
			return;
		}
		boolean airborne = !player.onGround();
		boolean wasAirborne = airborneStates.getOrDefault(playerId, false);
		airborneStates.put(playerId, airborne);
		if (!airborne) {
			if (wasAirborne && activeJumpWindows.remove(playerId) != null) {
				lowDodgeLandingGrace.put(playerId,
						tickCount + BossPostureRules.LOW_DODGE_LANDING_GRACE_TICKS);
			}
			return;
		}
		if (!wasAirborne
				&& BossPostureRules.isNormalJumpStart(false, false, false, false,
						player.getDeltaMovement().y, player.fallDistance)) {
			activeJumpWindows.put(playerId,
					tickCount + DODGE_INPUT_WINDOW_TICKS);
			lowDodgeLandingGrace.remove(playerId);
		}
	}

	private void finishAttack() {
		boolean highWhiff = getAttackState() != ATTACK_NONE
				&& getAttackDirection() == BossAttackDirection.HIGH && !attackLanded;
		Player postureSource = getTarget() instanceof Player player ? player : null;
		entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
		entityData.set(DATA_ATTACK_TICK, 0);
		entityData.set(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		attackHits.clear();
		attackLanded = false;
		setDeltaMovement(Vec3.ZERO);
		nextAttackTick = tickCount + scaledCooldown(24);
		if (highWhiff) {
			damagePosture(Math.round(BossPostureRules.HIGH_WHIFF_POSTURE_DAMAGE), postureSource);
		}
	}

	private void cancelAttack() {
		attackLanded = true;
		finishAttack();
		getNavigation().stop();
	}

	private void faceLockedYaw() {
		float yaw = getLockedYaw();
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	private void faceTarget(LivingEntity target, float maximumTurn) {
		float yaw = Mth.approachDegrees(getYRot(), yawToward(target.position()), maximumTurn);
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
	}

	private float yawToward(Vec3 target) {
		Vec3 delta = target.subtract(position());
		return (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	private Vec3 lockedForward() {
		float radians = getLockedYaw() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
	}

	private static Vec3 rotateY(Vec3 direction, double degrees) {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(direction.x * cos - direction.z * sin, 0.0,
				direction.x * sin + direction.z * cos);
	}

	private Vec3 lockedTarget() {
		return new Vec3(entityData.get(DATA_LOCKED_X), entityData.get(DATA_LOCKED_Y),
				entityData.get(DATA_LOCKED_Z));
	}

	private java.util.List<Player> combatPlayers(double radius) {
		return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius),
				VirtualMushroomKing::isCombatPlayer);
	}

	private static boolean isCombatPlayer(@Nullable Player player) {
		return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (getSummonTicks() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (getForm() == FORM_PURPLE
				&& (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL))) {
			amount *= 2.0F;
		}
		Plate plate = selectPlate(source);
		float bodyDamage = amount;
		if (getPlateDurability(plate) > 0) {
			damagePlate(plate, Math.max(1, Mth.ceil(amount)),
					source.getEntity() instanceof Player player ? player : null);
			bodyDamage *= 0.15F;
		}
		bodyDamage *= isStaggered() ? BossPostureRules.STAGGER_DAMAGE_MULTIPLIER
				: BossPostureRules.GUARDED_DAMAGE_MULTIPLIER;
		if (getForm() == FORM_RED) {
			bodyDamage *= RED_FORM_INCOMING_MULTIPLIER;
		}
		Vec3 movement = getDeltaMovement();
		boolean result = super.hurt(source, bodyDamage);
		setDeltaMovement(movement);
		if (result) {
			lastCombatTick = tickCount;
			if (getAttackState() == ATTACK_NONE && !isStaggered()) {
				triggerAnim("reaction", "hurt");
			}
			if (!isStaggered() && source.getEntity() instanceof Player player
					&& source.getDirectEntity() == player && isCombatPlayer(player)) {
				float pressure = BossPostureRules.chargedMeleePressure(amount,
						player.getAttackStrengthScale(0.5F));
				addPosturePressure(pressure, player);
				if (jumpStrikeWindows.getOrDefault(player.getUUID(), -1) >= tickCount
						&& player.getDeltaMovement().y < -0.05 && player.fallDistance > 0.0F) {
					jumpStrikeWindows.remove(player.getUUID());
					damagePosture(Math.round(BossPostureRules.jumpStrikeDamage()), player);
				}
			}
		}
		return result;
	}

	@Override
	public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity source) {
		int form = formForEffect(effectInstance);
		if (form != FORM_NORMAL && !level().isClientSide) {
			int duration = extendedFormDuration(form, effectInstance.getDuration());
			effectInstance = new MobEffectInstance(effectInstance.getEffect(), duration,
					effectInstance.getAmplifier(), effectInstance.isAmbient(),
					effectInstance.isVisible(), effectInstance.showIcon());
			setForm(form, duration);
		}
		return super.addEffect(effectInstance, source);
	}

	@Override
	public void setRemainingFireTicks(int remainingFireTicks) {
		int previous = getRemainingFireTicks();
		super.setRemainingFireTicks(remainingFireTicks);
		if (remainingFireTicks > previous && remainingFireTicks > 0
				&& !level().isClientSide) {
			setForm(FORM_RED, extendedFormDuration(FORM_RED, remainingFireTicks));
		}
	}

	@Override
	public void die(DamageSource source) {
		if (!level().isClientSide) {
			triggerAnim("reaction", "death");
			playSound(ModSounds.MUSHROOM_KING_DEATH.get(), 2.5F, 0.9F);
		}
		super.die(source);
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		setDeltaMovement(Vec3.ZERO);
		if (!level().isClientSide && level() instanceof ServerLevel serverLevel && deathTime % 4 == 0) {
			serverLevel.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
					getX(), getY() + 2.4, getZ(), 12, 1.8, 2.2, 1.8, 0.025);
		}
		if (deathTime >= 64 && !level().isClientSide && !isRemoved()) {
			if (level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.MYCELIUM,
						getX(), getY() + 1.0, getZ(), 60, 2.4, 1.2, 2.4, 0.1);
			}
			level().broadcastEntityEvent(this, (byte)60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	private Plate selectPlate(DamageSource source) {
		if (source.getDirectEntity() instanceof Player player) {
			Plate aimedPlate = selectPlateFromPlayerAim(player);
			if (aimedPlate != null) {
				return aimedPlate;
			}
		}
		Vec3 sourcePosition = source.getSourcePosition();
		if (sourcePosition == null && source.getDirectEntity() != null) {
			sourcePosition = source.getDirectEntity().position();
		}
		if (sourcePosition == null) {
			return Plate.CHEST;
		}
		Vec3 offset = sourcePosition.subtract(position());
		Vec3 horizontal = offset.multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return Plate.CHEST;
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 toward = horizontal.normalize();
		double front = toward.dot(forward);
		if (front < -0.35) {
			return Plate.BACK;
		}
		if (offset.y > getBbHeight() * 0.52) {
			Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
			return toward.dot(right) >= 0.0 ? Plate.RIGHT_SHOULDER : Plate.LEFT_SHOULDER;
		}
		return Plate.CHEST;
	}

	@Nullable
	private Plate selectPlateFromPlayerAim(Player player) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0F).scale(7.0));
		Plate best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Plate plate : Plate.values()) {
			var hit = plateBounds(plate).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}
			double distance = start.distanceToSqr(hit.get());
			if (distance < bestDistance) {
				best = plate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private AABB plateBounds(Plate plate) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot())
				.multiply(1.0, 0.0, 1.0).normalize();
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 center = switch (plate) {
			case LEFT_SHOULDER -> position().add(right.scale(-1.05)).add(0.0, 3.65, 0.0);
			case RIGHT_SHOULDER -> position().add(right.scale(1.05)).add(0.0, 3.65, 0.0);
			case CHEST -> position().add(forward.scale(0.58)).add(0.0, 2.65, 0.0);
			case BACK -> position().add(forward.scale(-0.58)).add(0.0, 2.65, 0.0);
		};
		return switch (plate) {
			case LEFT_SHOULDER, RIGHT_SHOULDER -> new AABB(center, center).inflate(0.62, 0.58, 0.62);
			case CHEST, BACK -> new AABB(center, center).inflate(1.05, 1.0, 0.48);
		};
	}

	private void damagePlate(Plate plate, int amount, @Nullable Player source) {
		int oldDurability = getPlateDurability(plate);
		if (oldDurability <= 0) {
			return;
		}
		int durability = Math.max(0, oldDurability - amount);
		entityData.set(plate.dataAccessor, durability);
		if (durability == 0) {
			entityData.set(DATA_BREAKING_PLATE, plate.ordinal());
			entityData.set(DATA_PLATE_BREAK_TICKS, 16);
			triggerAnim("reaction", switch (plate) {
				case LEFT_SHOULDER -> "plate_break_left";
				case RIGHT_SHOULDER -> "plate_break_right";
				case CHEST -> "plate_break_chest";
				case BACK -> "plate_break_back";
			});
			entityData.set(DATA_RECOVERY_TICKS, Math.max(getRecoveryTicks(), PLATE_BREAK_STUN));
			cancelAttack();
			damagePosture(10, source);
			playSound(ModSounds.MUSHROOM_KING_PLATE_BREAK.get(), 2.2F, 0.9F);
			if (source != null) {
				sendCombatFeedback(source, SnakeCombatFeedbackPayload.HIT, 0.9F);
			}
		}
	}

	private void addPosturePressure(float amount, @Nullable Player source) {
		if (amount <= 0.0F || isStaggered()) {
			return;
		}
		posturePressure += amount;
		int whole = (int)posturePressure;
		if (whole > 0) {
			posturePressure -= whole;
			damagePosture(whole, source);
		}
	}

	@Override
	public int getPosture() {
		return entityData.get(DATA_POSTURE);
	}

	@Override
	public int getMaxPosture() {
		return BossPostureRules.MUSHROOM_MAX_POSTURE;
	}

	@Override
	public int getStaggerTicks() {
		return entityData.get(DATA_STAGGER_TICKS);
	}

	@Override
	public BossAttackDirection getAttackDirection() {
		return BossAttackDirection.fromId(entityData.get(DATA_ATTACK_DIRECTION));
	}

	@Override
	public void damagePosture(int amount, @Nullable Player source) {
		if (level().isClientSide || amount <= 0 || isStaggered() || getSummonTicks() > 0) {
			return;
		}
		lastCombatTick = tickCount;
		int posture = Math.max(0, getPosture() - amount);
		entityData.set(DATA_POSTURE, posture);
		if (posture == 0) {
			posturePressure = 0.0F;
			entityData.set(DATA_STAGGER_TICKS, BossPostureRules.STAGGER_TICKS);
			entityData.set(DATA_RECOVERY_TICKS, 0);
			cancelAttack();
			triggerAnim("reaction", "stagger");
			playSound(ModSounds.MUSHROOM_KING_STAGGER.get(), 2.3F, 0.88F);
			if (source != null) {
				sendCombatFeedback(source, SnakeCombatFeedbackPayload.POSTURE_BREAK, 1.15F);
			}
		}
	}

	public int getAttackState() {
		return entityData.get(DATA_ATTACK_STATE);
	}

	public int getAttackTick() {
		return entityData.get(DATA_ATTACK_TICK);
	}

	public int getAttackVariant() {
		return entityData.get(DATA_ATTACK_VARIANT);
	}

	@Nullable
	public MushroomComboPattern getComboPattern() {
		return MushroomComboPattern.forAttack(getAttackState(), getAttackVariant());
	}

	private static String animationTriggerFor(int attack, int variant) {
		if (attack == MushroomKingAttackRules.ROOT_CADENCE) {
			return MID_COMBO_ANIMATIONS[Math.floorMod(variant, MID_COMBO_ANIMATIONS.length)];
		}
		if (attack == MushroomKingAttackRules.MYCELIAL_SURGE) {
			return LOW_COMBO_ANIMATIONS[Math.floorMod(variant, LOW_COMBO_ANIMATIONS.length)];
		}
		return ATTACK_ANIMATIONS[attack];
	}

	public int getSummonTicks() {
		return entityData.get(DATA_SUMMON_TICKS);
	}

	public int getRecoveryTicks() {
		return entityData.get(DATA_RECOVERY_TICKS);
	}

	public int getForm() {
		return entityData.get(DATA_FORM);
	}

	public int getFormTicks() {
		return entityData.get(DATA_FORM_TICKS);
	}

	public boolean isCombatActive() {
		return getSummonTicks() <= 0 && isAlive()
				&& (getTarget() != null || combatTicks > 0 && tickCount - lastCombatTick < 200);
	}

	public float getLockedYaw() {
		return entityData.get(DATA_LOCKED_YAW);
	}

	public Vec3 getLockedTarget() {
		return new Vec3(entityData.get(DATA_LOCKED_X), entityData.get(DATA_LOCKED_Y),
				entityData.get(DATA_LOCKED_Z));
	}

	public int getLeftShoulderArmor() {
		return entityData.get(DATA_LEFT_SHOULDER);
	}

	public int getRightShoulderArmor() {
		return entityData.get(DATA_RIGHT_SHOULDER);
	}

	public int getChestArmor() {
		return entityData.get(DATA_CHEST);
	}

	public int getBackArmor() {
		return entityData.get(DATA_BACK);
	}

	public int getBreakingPlate() {
		return entityData.get(DATA_BREAKING_PLATE);
	}

	public int getPlateBreakTicks() {
		return entityData.get(DATA_PLATE_BREAK_TICKS);
	}

	private int getPlateDurability(Plate plate) {
		return entityData.get(plate.dataAccessor);
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		// Participate in mob collision so players cannot walk through the body.
		// The push(double, double, double) override below keeps the boss immovable.
		return isAlive() && getSummonTicks() <= 0;
	}

	@Override
	public void knockback(double strength, double x, double z) {
		// The Mushroom King is immovable; scripted charge movement is explicit.
	}

	@Override
	public void push(double x, double y, double z) {
		// Ignore external impulses without changing collision behavior.
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof Player player && !isCombatPlayer(player)) {
			target = null;
		}
		super.setTarget(target);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(@Nullable Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("AttackState", getAttackState());
		tag.putInt("AttackTick", getAttackTick());
		tag.putInt("AttackDirection", getAttackDirection().ordinal());
		tag.putInt("AttackVariant", getAttackVariant());
		tag.putInt("CombatSchema", COMBAT_SCHEMA_VERSION);
		tag.putInt("PostureSchema", POSTURE_SCHEMA_VERSION);
		tag.putFloat("LockedYaw", getLockedYaw());
		tag.putFloat("LockedX", entityData.get(DATA_LOCKED_X));
		tag.putFloat("LockedY", entityData.get(DATA_LOCKED_Y));
		tag.putFloat("LockedZ", entityData.get(DATA_LOCKED_Z));
		tag.putInt("SummonTicks", getSummonTicks());
		tag.putInt("Posture", getPosture());
		tag.putInt("StaggerTicks", getStaggerTicks());
		tag.putInt("RecoveryTicks", getRecoveryTicks());
		tag.putFloat("PosturePressure", posturePressure);
		tag.putInt("LeftShoulderArmor", getLeftShoulderArmor());
		tag.putInt("RightShoulderArmor", getRightShoulderArmor());
		tag.putInt("ChestArmor", getChestArmor());
		tag.putInt("BackArmor", getBackArmor());
		tag.putInt("BreakingPlate", getBreakingPlate());
		tag.putInt("PlateBreakTicks", getPlateBreakTicks());
		tag.putInt("MushroomForm", getForm());
		tag.putInt("MushroomFormTicks", getFormTicks());
		tag.putInt("NextAttackDelay", Math.max(0, nextAttackTick - tickCount));
		tag.putInt("MidComboSequence", nextMidComboSequence);
		tag.putInt("LowComboSequence", nextLowComboSequence);
		tag.putInt("MixedComboSequence", nextMixedComboSequence);
		tag.putInt("CombatTicks", combatTicks);
		tag.putInt("LastCombatAge", Math.max(0, tickCount - lastCombatTick));
		tag.putIntArray("AttackReadyDelays", remainingTicks(attackReadyTicks));
		tag.putIntArray("DirectionReadyDelays", remainingTicks(directionReadyTicks));
		tag.putIntArray("RecentAttacks", recentAttacks.stream().mapToInt(Integer::intValue).toArray());
		if (homePos != null) {
			tag.putLong("HomePos", homePos.asLong());
		}
		if (summonerUuid != null) {
			tag.putUUID("Summoner", summonerUuid);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		boolean currentCombatSchema = tag.getInt("CombatSchema") >= COMBAT_SCHEMA_VERSION;
		int attack = currentCombatSchema ? tag.getInt("AttackState") : ATTACK_NONE;
		entityData.set(DATA_ATTACK_STATE,
				MushroomKingAttackRules.isCombatAttack(attack) ? attack : ATTACK_NONE);
		entityData.set(DATA_ATTACK_TICK,
				currentCombatSchema ? Math.max(0, tag.getInt("AttackTick")) : 0);
		entityData.set(DATA_ATTACK_DIRECTION,
				currentCombatSchema
						? BossAttackDirection.fromId(tag.getInt("AttackDirection")).ordinal()
						: BossAttackDirection.MID.ordinal());
		entityData.set(DATA_ATTACK_VARIANT, currentCombatSchema
				? Math.floorMod(tag.getInt("AttackVariant"), MushroomComboPattern.PATTERN_COUNT)
				: 0);
		entityData.set(DATA_LOCKED_YAW, tag.getFloat("LockedYaw"));
		entityData.set(DATA_LOCKED_X, tag.getFloat("LockedX"));
		entityData.set(DATA_LOCKED_Y, tag.getFloat("LockedY"));
		entityData.set(DATA_LOCKED_Z, tag.getFloat("LockedZ"));
		entityData.set(DATA_SUMMON_TICKS, Math.max(0, tag.getInt("SummonTicks")));
		int loadedPosture = getMaxPosture();
		if (tag.contains("Posture")) {
			loadedPosture = tag.getInt("Posture");
			if (tag.getInt("PostureSchema") < POSTURE_SCHEMA_VERSION) {
				loadedPosture = BossPostureRules.scaleSavedPosture(
						loadedPosture, BossPostureRules.MAX_POSTURE, getMaxPosture());
			}
		}
		entityData.set(DATA_POSTURE, Mth.clamp(loadedPosture, 0, getMaxPosture()));
		entityData.set(DATA_STAGGER_TICKS, Math.max(0, tag.getInt("StaggerTicks")));
		entityData.set(DATA_RECOVERY_TICKS, Math.max(0, tag.getInt("RecoveryTicks")));
		posturePressure = Mth.clamp(tag.getFloat("PosturePressure"), 0.0F, 0.999F);
		entityData.set(DATA_LEFT_SHOULDER, readDurability(tag, "LeftShoulderArmor", LEFT_SHOULDER_MAX));
		entityData.set(DATA_RIGHT_SHOULDER, readDurability(tag, "RightShoulderArmor", RIGHT_SHOULDER_MAX));
		entityData.set(DATA_CHEST, readDurability(tag, "ChestArmor", CHEST_MAX));
		entityData.set(DATA_BACK, readDurability(tag, "BackArmor", BACK_MAX));
		entityData.set(DATA_BREAKING_PLATE, tag.contains("BreakingPlate")
				? Mth.clamp(tag.getInt("BreakingPlate"), -1, Plate.values().length - 1) : -1);
		entityData.set(DATA_PLATE_BREAK_TICKS, Math.max(0, tag.getInt("PlateBreakTicks")));
		int formTicks = Math.max(0, tag.getInt("MushroomFormTicks"));
		int form = formTicks > 0
				? Mth.clamp(tag.getInt("MushroomForm"), FORM_NORMAL, FORM_PURPLE)
				: FORM_NORMAL;
		entityData.set(DATA_FORM, form);
		entityData.set(DATA_FORM_TICKS, formTicks);
		nextAttackTick = tickCount + Math.max(0, tag.getInt("NextAttackDelay"));
		int legacySequence = Math.floorMod(tag.getInt("ComboSequence"),
				MushroomComboPattern.PATTERN_COUNT);
		nextMidComboSequence = tag.contains("MidComboSequence")
				? Math.max(0, tag.getInt("MidComboSequence")) : legacySequence;
		nextLowComboSequence = tag.contains("LowComboSequence")
				? Math.max(0, tag.getInt("LowComboSequence")) : legacySequence;
		nextMixedComboSequence = tag.contains("MixedComboSequence")
				? Math.max(0, tag.getInt("MixedComboSequence")) : legacySequence;
		combatTicks = Math.max(0, tag.getInt("CombatTicks"));
		lastCombatTick = tickCount - Math.max(0, tag.getInt("LastCombatAge"));
		if (currentCombatSchema) {
			restoreReadyTicks(tag.getIntArray("AttackReadyDelays"), attackReadyTicks);
			restoreReadyTicks(tag.getIntArray("DirectionReadyDelays"), directionReadyTicks);
		} else {
			Arrays.fill(attackReadyTicks, 0);
			Arrays.fill(directionReadyTicks, 0);
		}
		recentAttacks.clear();
		for (int recent : currentCombatSchema ? tag.getIntArray("RecentAttacks") : new int[0]) {
			if (MushroomKingAttackRules.isCombatAttack(recent)) {
				recentAttacks.addLast(recent);
			}
			if (recentAttacks.size() >= RECENT_ATTACK_LIMIT) {
				break;
			}
		}
		if (tag.contains("HomePos")) {
			homePos = BlockPos.of(tag.getLong("HomePos"));
		}
		if (tag.hasUUID("Summoner")) {
			summonerUuid = tag.getUUID("Summoner");
		}
		boolean summoning = getSummonTicks() > 0;
		setNoAi(summoning);
		setNoGravity(summoning);
		noPhysics = summoning;
		bossEvent.setName(getDisplayName());
		bossEvent.setVisible(!summoning);
	}

	private static int readDurability(CompoundTag tag, String key, int maximum) {
		return tag.contains(key) ? Mth.clamp(tag.getInt(key), 0, maximum) : maximum;
	}

	private int[] remainingTicks(int[] readyTicks) {
		int[] delays = new int[readyTicks.length];
		for (int i = 0; i < readyTicks.length; i++) {
			delays[i] = Math.max(0, readyTicks[i] - tickCount);
		}
		return delays;
	}

	private void restoreReadyTicks(int[] saved, int[] target) {
		Arrays.fill(target, 0);
		for (int i = 0; i < Math.min(saved.length, target.length); i++) {
			target[i] = tickCount + Math.max(0, saved[i]);
		}
	}

	private enum Plate {
		LEFT_SHOULDER(DATA_LEFT_SHOULDER),
		RIGHT_SHOULDER(DATA_RIGHT_SHOULDER),
		CHEST(DATA_CHEST),
		BACK(DATA_BACK);

		private final EntityDataAccessor<Integer> dataAccessor;

		Plate(EntityDataAccessor<Integer> dataAccessor) {
			this.dataAccessor = dataAccessor;
		}
	}

	private record HitKey(UUID playerId, int wave) {
	}

	private final class MushroomKingCombatGoal extends Goal {
		private MushroomKingCombatGoal() {
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return getTarget() instanceof Player player
					&& isCombatPlayer(player) && getSummonTicks() <= 0;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target != null) {
				tickCombat(target);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
			if (getAttackState() != ATTACK_NONE) {
				cancelAttack();
			}
		}
	}
}
