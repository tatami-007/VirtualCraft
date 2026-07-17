package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.BossDamageRules;
import dev.tatami.virtualcraft.boss.BossEncounterScaling;
import dev.tatami.virtualcraft.boss.BossPostureRules;
import dev.tatami.virtualcraft.boss.PostureBoss;
import dev.tatami.virtualcraft.boss.SnakeKingAttackRules;
import dev.tatami.virtualcraft.boss.SnakeAttackVolume;
import dev.tatami.virtualcraft.boss.SnakeAttackMotionRules;
import dev.tatami.virtualcraft.boss.SnakeKingCombatDirector;
import dev.tatami.virtualcraft.boss.SnakeHuntPattern;
import dev.tatami.virtualcraft.config.SnakeBossConfig;
import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import dev.tatami.virtualcraft.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ShieldItem;
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

public class VirtualSnakeKing extends Monster implements GeoEntity, PostureBoss {
	private static final double BASE_MAX_HEALTH = 360.0;
	/** Keeps visual scale, multipart hitboxes, and attack reach on one shared boss scale. */
	public static final float GAMEPLAY_SCALE = 1.30F;
	public static final float RENDER_SCALE = 1.46F;

	public static final int ATTACK_NONE = 0;
	public static final int ATTACK_BITE = 1;
	public static final int ATTACK_DOUBLE_HUNT = 2;
	public static final int ATTACK_TAIL_SWEEP = 3;
	public static final int ATTACK_DATA_FANGS = 4;
	public static final int ATTACK_RUNE_SIGIL = 5;
	public static final int ATTACK_CROWN_CHAIN = 6;
	public static final int ATTACK_VOID_DASH = 7;
	public static final int ATTACK_CORROSIVE_COIL = 8;
	public static final int ATTACK_CROWN_JUDGMENT = 9;
	public static final int ATTACK_OVERHEAD_FANG = 10;
	public static final int SIGNAL_NONE = 0;
	public static final int SIGNAL_WHITE = 1;
	public static final int SIGNAL_RED = 2;
	public static final int SIGNAL_BLUE = 3;

	private static final int[] ATTACK_DURATIONS = {
			0, 30, 68, 38, 42, 44, 62, 62, 72, 104, 48
	};
	private static final double COMBAT_HOLD_DISTANCE = 5.8;
	private static final double COMBAT_RETREAT_DISTANCE = 4.5;
	private static final double LUNGE_STOP_DISTANCE = 3.4;
	private static final String[] ATTACK_ANIMATIONS = {
			"", "bite", "double_hunt", "tail_sweep", "data_fangs", "rune_sigil",
			"crown_chain", "void_dash", "corrosive_coil", "crown_judgment", "overhead_fang"
	};
	private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_START =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_TICK =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SUMMON_TICKS =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_SWEEP_LEFT =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_ALERT =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_POSTURE =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_STAGGER_TICKS =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_DIRECTION =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COMBO_PATTERN =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_ATTACK_YAW =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_LOCKED_TARGET_Y =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_SIGNAL_TYPE =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_SIGNAL_STRENGTH =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_JUDGMENT_SAFE_PATH =
			SynchedEntityData.defineId(VirtualSnakeKing.class, EntityDataSerializers.INT);

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.virtual_snake_king.idle");
	private static final RawAnimation SLITHER = RawAnimation.begin().thenLoop("animation.virtual_snake_king.slither");
	private static final RawAnimation FAST_SLITHER = RawAnimation.begin().thenLoop("animation.virtual_snake_king.fast_slither");
	private static final RawAnimation TURN_LEFT = RawAnimation.begin().thenLoop("animation.virtual_snake_king.turn_left");
	private static final RawAnimation TURN_RIGHT = RawAnimation.begin().thenLoop("animation.virtual_snake_king.turn_right");
	private static final RawAnimation ALERT = RawAnimation.begin().thenLoop("animation.virtual_snake_king.alert");
	private static final RawAnimation TAIL_FLOW = RawAnimation.begin().thenLoop("animation.virtual_snake_king.tail_flow");
	private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.virtual_snake_king.hurt_short");
	private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("animation.virtual_snake_king.summon");
	private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.virtual_snake_king.roar");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.virtual_snake_king.death");

	private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bossEvent = new ServerBossEvent(
			getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
	private final VirtualSnakeKingPart headPart;
	private final VirtualSnakeKingPart bodyPart;
	private final VirtualSnakeKingPart middlePart;
	private final VirtualSnakeKingPart tailPart;
	private final VirtualSnakeKingPart tailMidPart;
	private final VirtualSnakeKingPart tailTipPart;
	private final VirtualSnakeKingPart[] parts;
	private final Vec3[] positionHistory = new Vec3[16];
	private final float[] yawHistory = new float[16];
	private final Set<UUID> attackHits = new HashSet<>();
	private final Map<UUID, Integer> fangVolleyHits = new HashMap<>();
	private final Map<UUID, Integer> jumpStrikeWindows = new HashMap<>();
	private final Map<UUID, Integer> activeJumpWindows = new HashMap<>();
	private final Map<UUID, Integer> lowDodgeLandingGrace = new HashMap<>();
	private final Map<UUID, Integer> comboParrySegments = new HashMap<>();
	private final Map<UUID, Long> delayedHitFrames = new HashMap<>();
	private final Set<DelayedDodgeKey> successfulDodges = new HashSet<>();
	private final Deque<Integer> recentAttacks = new ArrayDeque<>();
	private int historyIndex;
	private int historySize;
	private int nextAttackTick;
	private int lastCombatTick;
	private final int[] attackReadyTicks = new int[SnakeKingCombatDirector.ATTACK_COUNT + 1];
	private final int[] directionReadyTicks = new int[BossAttackDirection.values().length];
	private int queuedAttack;
	private int attackStrike;
	private int comboAnimationStep = -1;
	private int pendingFangProjectiles;
	private boolean fangVolleyHitPlayer;
	private float damageBucket;
	private float posturePressure;
	private boolean configAttributesApplied;
	private int encounterPlayerCount = 1;
	@Nullable
	private UUID activeFangVolley;
	@Nullable
	private UUID activeAttackInstance;
	@Nullable
	private BlockPos homePos;
	@Nullable
	private UUID summonerUuid;

	public VirtualSnakeKing(EntityType<? extends VirtualSnakeKing> type, Level level) {
		super(type, level);
		xpReward = 120;
		nextAttackTick = 30;
		attackReadyTicks[ATTACK_CROWN_JUDGMENT] = 600;
		setPersistenceRequired();
		headPart = new VirtualSnakeKingPart(this, "head", scaled(2.3F), scaled(1.9F), 1.25F);
		bodyPart = new VirtualSnakeKingPart(this, "body", scaled(2.8F), scaled(1.8F), 1.0F);
		middlePart = new VirtualSnakeKingPart(this, "middle", scaled(2.35F), scaled(1.55F), 0.9F);
		tailPart = new VirtualSnakeKingPart(this, "tail", scaled(1.75F), scaled(1.2F), 0.75F);
		tailMidPart = new VirtualSnakeKingPart(this, "tail_mid", scaled(1.25F), scaled(0.95F), 0.65F);
		tailTipPart = new VirtualSnakeKingPart(this, "tail_tip", scaled(0.8F), scaled(0.7F), 0.55F);
		parts = new VirtualSnakeKingPart[]{headPart, bodyPart, middlePart, tailPart, tailMidPart, tailTipPart};
		setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 6.0)
				.add(Attributes.MOVEMENT_SPEED, 0.31)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.ATTACK_DAMAGE, 15.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.8)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.STEP_HEIGHT, 1.25);
	}

	private static float scaled(float value) {
		return value * GAMEPLAY_SCALE;
	}

	private static double scaled(double value) {
		return value * GAMEPLAY_SCALE;
	}

	@Override
	public void setId(int id) {
		super.setId(id);
		if (parts != null) {
			for (int i = 0; i < parts.length; i++) {
				parts[i].setId(id + i + 1);
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACK_STATE, ATTACK_NONE);
		builder.define(DATA_ATTACK_START, 0);
		builder.define(DATA_ATTACK_TICK, 0);
		builder.define(DATA_SUMMON_TICKS, 0);
		builder.define(DATA_SWEEP_LEFT, false);
		builder.define(DATA_ALERT, false);
		builder.define(DATA_POSTURE, BossPostureRules.MAX_POSTURE);
		builder.define(DATA_STAGGER_TICKS, 0);
		builder.define(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		builder.define(DATA_COMBO_PATTERN, 0);
		builder.define(DATA_LOCKED_ATTACK_YAW, 0.0F);
		builder.define(DATA_LOCKED_TARGET_Y, 0.0F);
		builder.define(DATA_SIGNAL_TYPE, SIGNAL_NONE);
		builder.define(DATA_SIGNAL_STRENGTH, 0.0F);
		builder.define(DATA_JUDGMENT_SAFE_PATH, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new SnakeKingCombatGoal());
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 20.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushEntities() {
		if (getSummonTicks() > 0 || !isAlive()) {
			return;
		}
		AABB collisionArea = getBoundingBox();
		for (VirtualSnakeKingPart part : parts) {
			collisionArea = collisionArea.minmax(part.getBoundingBox());
		}
		for (Player player : level().getEntitiesOfClass(Player.class, collisionArea,
				player -> player.isAlive() && !player.isCreative() && !player.isSpectator())) {
			Entity nearest = this;
			double nearestDistance = horizontalDistanceSqr(player, this);
			for (VirtualSnakeKingPart part : parts) {
				if (!part.getBoundingBox().intersects(player.getBoundingBox())) {
					continue;
				}
				double distance = horizontalDistanceSqr(player, part);
				if (distance < nearestDistance) {
					nearest = part;
					nearestDistance = distance;
				}
			}
			if (!nearest.getBoundingBox().intersects(player.getBoundingBox())) {
				continue;
			}
			double dx = player.getX() - nearest.getX();
			double dz = player.getZ() - nearest.getZ();
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-4) {
				dx = -Mth.sin(getYRot() * Mth.DEG_TO_RAD);
				dz = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
				length = 1.0;
			}
			// Resolve overlap horizontally only. This blocks pass-through without
			// changing pose, vertical velocity, or pulling players with an attack.
			player.push(dx / length * 0.22, 0.0, dz / length * 0.22);
			restoreStandingPoseAfterBossOverlap(player);
		}
	}

	private void restoreStandingPoseAfterBossOverlap(Player player) {
		if (!player.isSwimming() || player.isInWaterOrBubble() || player.isFallFlying()) {
			return;
		}
		AABB standingBounds = player.getDimensions(Pose.STANDING).makeBoundingBox(player.position());
		if (level().noCollision(player, standingBounds)) {
			player.setSwimming(false);
			player.setPose(Pose.STANDING);
			player.refreshDimensions();
		}
	}

	private static double horizontalDistanceSqr(Entity first, Entity second) {
		double dx = first.getX() - second.getX();
		double dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz;
	}

	@Override
	public boolean canBeCollidedWith() {
		// Physical separation is handled by pushEntities(). Returning true here
		// makes vanilla treat the large multipart boss as a ceiling during player
		// pose checks, which forces nearby players into the crawling pose.
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "movement", 5, state -> {
			if (getAttackState() != ATTACK_NONE || getSummonTicks() > 0 || deathTime > 0) {
				return PlayState.STOP;
			}
			if (state.isMoving()) {
				float turn = Mth.wrapDegrees(getYRot() - yRotO);
				if (turn > 4.0F) {
					return state.setAndContinue(TURN_LEFT);
				}
				if (turn < -4.0F) {
					return state.setAndContinue(TURN_RIGHT);
				}
				double horizontalSpeed = getDeltaMovement().horizontalDistanceSqr();
				return state.setAndContinue(horizontalSpeed > 0.032 ? FAST_SLITHER : SLITHER);
			}
			return state.setAndContinue(entityData.get(DATA_ALERT) ? ALERT : IDLE);
		}));
		AnimationController<VirtualSnakeKing> combat = new AnimationController<>(this, "combat", 2, state -> PlayState.STOP);
		for (int attack = 1; attack < ATTACK_ANIMATIONS.length; attack++) {
			combat.triggerableAnim(ATTACK_ANIMATIONS[attack], RawAnimation.begin()
					.thenPlay("animation.virtual_snake_king." + ATTACK_ANIMATIONS[attack]));
		}
		combat.triggerableAnim("tail_sweep_left", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.tail_sweep_left"));
		combat.triggerableAnim("combo_bite_left", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.combo_bite_left"));
		combat.triggerableAnim("combo_bite_right", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.combo_bite_right"));
		combat.triggerableAnim("combo_tail_left", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.combo_tail_left"));
		combat.triggerableAnim("combo_tail_right", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.combo_tail_right"));
		controllers.add(combat);
		controllers.add(new AnimationController<>(this, "tail", 4, state -> {
			int attack = getAttackState();
			if (deathTime > 0 || getSummonTicks() > 0 || isStaggered()
					|| attack == ATTACK_TAIL_SWEEP || attack == ATTACK_CORROSIVE_COIL
					|| isComboTailStage()) {
				return PlayState.STOP;
			}
			return state.setAndContinue(TAIL_FLOW);
		}));
		controllers.add(new AnimationController<>(this, "reaction", 1, state -> PlayState.STOP)
				.triggerableAnim("hurt", HURT)
				.triggerableAnim("summon", SUMMON)
				.triggerableAnim("roar", ROAR)
				.triggerableAnim("stagger", RawAnimation.begin().thenPlayAndHold("animation.virtual_snake_king.stagger"))
				.triggerableAnim("recover", RawAnimation.begin().thenPlay("animation.virtual_snake_king.recover"))
				.triggerableAnim("death", DEATH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animationCache;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
		maintainArena();
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && getTarget() instanceof Player player
				&& (player.isCreative() || player.isSpectator())) {
			setTarget(null);
		}
		// noAi mobs do not run customServerAiStep; the summoning clock must live here.
		if (!level().isClientSide && getSummonTicks() > 0) {
			tickSummoning();
		}
		if (!level().isClientSide) {
			applyConfiguredAttributes();
			updateEncounterScaling();
			tickPostureState();
			damageBucket = BossDamageRules.decayedBucket(damageBucket,
					SnakeBossConfig.VALUES.dpsCap.get().floatValue());
		}
		updatePartPositions();
	}

	@Override
	public void onAddedToLevel() {
		super.onAddedToLevel();
		resetHistory();
		if (!level().isClientSide && getSummonTicks() > 0) {
			triggerAnim("reaction", "summon");
		} else if (!level().isClientSide && isStaggered()) {
			triggerAnim("reaction", "stagger");
		} else if (!level().isClientSide && getAttackState() != ATTACK_NONE) {
			if (getAttackState() == ATTACK_DOUBLE_HUNT) {
				triggerComboAnimation(Math.min(getHuntPattern().segmentCount() - 1,
						SnakeHuntPattern.segment(getAttackTick())));
			} else {
				String animation = getAttackState() == ATTACK_TAIL_SWEEP && entityData.get(DATA_SWEEP_LEFT)
						? "tail_sweep_left" : ATTACK_ANIMATIONS[getAttackState()];
				triggerAnim("combat", animation);
			}
		}
	}

	public void beginSummoning(BlockPos poolPos, @Nullable UUID summoner) {
		homePos = poolPos.immutable();
		summonerUuid = summoner;
		entityData.set(DATA_SUMMON_TICKS, 60);
		entityData.set(DATA_SIGNAL_TYPE, SIGNAL_BLUE);
		entityData.set(DATA_SIGNAL_STRENGTH, 0.3F);
		setNoAi(true);
		noPhysics = true;
		bossEvent.setVisible(false);
		resetHistory();
		if (!level().isClientSide) {
			triggerAnim("reaction", "summon");
			play(ModSounds.SNAKE_KING_SUMMON.get(), 1.8F, 0.96F);
		}
	}

	private void tickSummoning() {
		int remaining = getSummonTicks();
		if (homePos == null) {
			entityData.set(DATA_SUMMON_TICKS, 0);
			setNoAi(false);
			noPhysics = false;
			bossEvent.setVisible(true);
			return;
		}
		float progress = 1.0F - remaining / 60.0F;
		setPos(homePos.getX() + 0.5, homePos.getY() - 1.35 + progress * 2.35, homePos.getZ() + 0.5);
		setDeltaMovement(Vec3.ZERO);
		float signalPulse = 0.08F * (1.0F + Mth.sin(progress * Mth.TWO_PI * 5.0F));
		entityData.set(DATA_SIGNAL_TYPE, progress < 0.62F ? SIGNAL_BLUE : SIGNAL_WHITE);
		entityData.set(DATA_SIGNAL_STRENGTH, Mth.clamp(0.3F + progress * 0.58F + signalPulse, 0.0F, 1.0F));
		if (level() instanceof ServerLevel serverLevel) {
			if (remaining % 5 == 0) {
				serverLevel.sendParticles(ModParticleTypes.SNAKE_TRAIL.get(), getX(), getY() + scaled(0.8), getZ(),
						3, scaled(0.85), scaled(0.45), scaled(0.85), 0.018);
			}
			if (remaining == 48 || remaining == 30) {
				play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.4F, remaining == 48 ? 0.68F : 0.86F);
				serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), getX(), getY() + scaled(0.9), getZ(),
						14, scaled(1.35), scaled(0.65), scaled(1.35), 0.028);
			} else if (remaining == 14) {
				play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.7F, 0.92F);
				serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), getX(), getY() + scaled(1.15), getZ(),
						20, scaled(1.6), scaled(0.75), scaled(1.6), 0.04);
			} else if (remaining == 2) {
				play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.85F, 0.82F);
			}
		}
		remaining--;
		entityData.set(DATA_SUMMON_TICKS, Math.max(0, remaining));
		if (remaining <= 0) {
			setPos(homePos.getX() + 0.5, homePos.getY() + 1.05, homePos.getZ() + 0.5);
			setNoAi(false);
			noPhysics = false;
			bossEvent.setVisible(true);
			entityData.set(DATA_SIGNAL_TYPE, SIGNAL_NONE);
			entityData.set(DATA_SIGNAL_STRENGTH, 0.0F);
			resetHistory();
			nextAttackTick = tickCount + 35;
			attackReadyTicks[ATTACK_CROWN_JUDGMENT] = tickCount + 600;
			if (level() instanceof ServerLevel serverLevel) {
				triggerAnim("reaction", "roar");
				serverLevel.playSound(null, blockPosition(), ModSounds.SNAKE_KING_ROAR.get(), SoundSource.HOSTILE, 2.2F, 0.92F);
				serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), getX(), getY() + 1.2, getZ(), 18, 2.0, 1.1, 2.0, 0.1);
				if (summonerUuid != null && serverLevel.getPlayerByUUID(summonerUuid) instanceof ServerPlayer player
						&& !player.isCreative() && !player.isSpectator()) {
					setTarget(player);
				}
			}
		}
	}

	private void updatePartPositions() {
		if (historySize > 0 && position().distanceToSqr(positionHistory[historyIndex]) > 64.0) {
			resetHistory();
		}
		historyIndex = (historyIndex + 1) & 15;
		positionHistory[historyIndex] = position();
		yawHistory[historyIndex] = getYRot();
		historySize = Math.min(16, historySize + 1);

		Vec3 headBase = samplePosition(0);
		float headYaw = sampleYaw(0) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(headYaw), 0.0, Mth.cos(headYaw));
		movePart(headPart, headBase.add(forward.scale(scaled(1.55))).add(0.0, scaled(1.05), 0.0));
		movePart(bodyPart, trailPoint(2, scaled(0.8), 0.0).add(0.0, scaled(0.75), 0.0));
		movePart(middlePart, trailPoint(7, scaled(2.75), 0.28).add(0.0, scaled(0.62), 0.0));
		movePart(tailPart, trailPoint(11, scaled(4.45), 0.72).add(0.0, scaled(0.48), 0.0));
		movePart(tailMidPart, trailPoint(14, scaled(5.65), 1.18).add(0.0, scaled(0.38), 0.0));
		movePart(tailTipPart, trailPoint(15, scaled(6.75), 1.72).add(0.0, scaled(0.28), 0.0));
		int tailSweepTick = activeTailSweepTick();
		if (tailSweepTick >= 3 && tailSweepTick <= SnakeHuntPattern.TAIL_HIT_TICK) {
				float progress = (tailSweepTick - 3) / 16.0F;
				float direction = entityData.get(DATA_SWEEP_LEFT) ? -1.0F : 1.0F;
				float angle = getYRot() * Mth.DEG_TO_RAD + direction * Mth.lerp(progress, -1.35F, 1.35F);
				movePart(tailPart, sweepTailPoint(angle, scaled(4.4), scaled(0.55)));
				movePart(tailMidPart, sweepTailPoint(angle + direction * 0.14F, scaled(5.55), scaled(0.42)));
				movePart(tailTipPart, sweepTailPoint(angle + direction * 0.26F, scaled(6.65), scaled(0.3)));
		}
	}

	private Vec3 trailPoint(int delay, double distanceBehind, double swayStrength) {
		Vec3 sampled = samplePosition(delay);
		Vec3 horizontalDelta = position().subtract(sampled).multiply(1.0, 0.0, 1.0);
		double sampledDistance = horizontalDelta.length();
		float yaw = sampleYaw(delay) * Mth.DEG_TO_RAD;
		Vec3 backward = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		if (sampledDistance < distanceBehind) {
			sampled = sampled.add(backward.scale(distanceBehind - sampledDistance));
		}
		if (swayStrength > 0.0 && tailCanFlow()) {
			Vec3 side = new Vec3(-backward.z, 0.0, backward.x);
			double primary = Math.sin(tickCount * 0.13 - delay * 0.3);
			double secondary = Math.sin(tickCount * 0.071 + delay * 0.41) * 0.2;
			double wave = (primary + secondary) * scaled(swayStrength);
			sampled = sampled.add(side.scale(wave));
		}
		return sampled;
	}

	private boolean tailCanFlow() {
		int attack = getAttackState();
		return deathTime == 0 && getSummonTicks() == 0 && !isStaggered()
				&& attack != ATTACK_TAIL_SWEEP && !isComboTailStage();
	}

	private int activeTailSweepTick() {
		if (getAttackState() == ATTACK_TAIL_SWEEP) {
			return getAttackTick();
		}
		return isComboTailStage() ? SnakeHuntPattern.localTick(getAttackTick()) : -1;
	}

	private Vec3 sweepTailPoint(float angle, double radius, double height) {
		return position().add(-Mth.sin(angle) * radius, height, Mth.cos(angle) * radius);
	}

	private Vec3 samplePosition(int delay) {
		if (historySize == 0) {
			return position();
		}
		int actual = Math.min(delay, historySize - 1);
		Vec3 value = positionHistory[(historyIndex - actual) & 15];
		return value == null ? position() : value;
	}

	private float sampleYaw(int delay) {
		int actual = Math.min(delay, Math.max(0, historySize - 1));
		return yawHistory[(historyIndex - actual) & 15];
	}

	private void resetHistory() {
		for (int i = 0; i < positionHistory.length; i++) {
			positionHistory[i] = position();
			yawHistory[i] = getYRot();
		}
		historyIndex = 0;
		historySize = positionHistory.length;
	}

	private void movePart(VirtualSnakeKingPart part, Vec3 pos) {
		double oldX = part.getX();
		double oldY = part.getY();
		double oldZ = part.getZ();
		part.setPos(pos);
		part.xo = oldX;
		part.yo = oldY;
		part.zo = oldZ;
		part.xOld = oldX;
		part.yOld = oldY;
		part.zOld = oldZ;
	}

	public void setHome(BlockPos pos) {
		homePos = pos.immutable();
	}

	private void maintainArena() {
		if (homePos == null || tickCount % 10 != 0) {
			return;
		}
		double distance = homePos.distToCenterSqr(position());
		if (distance > 72.0 * 72.0) {
			teleportTo(homePos.getX() + 0.5, homePos.getY() + 1.0, homePos.getZ() + 0.5);
			resetHistory();
			setTarget(null);
		} else if (distance > 48.0 * 48.0) {
			setTarget(null);
			getNavigation().moveTo(homePos.getX() + 0.5, homePos.getY() + 1.0, homePos.getZ() + 0.5, 1.35);
		}
	}

	private void tickPostureState() {
		jumpStrikeWindows.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		activeJumpWindows.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		lowDodgeLandingGrace.entrySet().removeIf(entry -> entry.getValue() < tickCount);
		for (Player player : combatPlayers(32.0)) {
			trackNormalJump(player);
		}
		int staggerTicks = getStaggerTicks();
		if (staggerTicks > 0) {
			getNavigation().stop();
			setDeltaMovement(Vec3.ZERO);
			entityData.set(DATA_STAGGER_TICKS, staggerTicks - 1);
			if (staggerTicks == 1) {
				entityData.set(DATA_POSTURE, BossPostureRules.MAX_POSTURE);
				posturePressure = 0.0F;
				nextAttackTick = tickCount + 20;
				triggerAnim("reaction", "recover");
			}
			return;
		}
		if (getTarget() == null && getPosture() < getMaxPosture()
				&& tickCount - lastCombatTick >= BossPostureRules.DISENGAGE_RESET_TICKS) {
			entityData.set(DATA_POSTURE, BossPostureRules.MAX_POSTURE);
			posturePressure = 0.0F;
		}
	}

	private void applyConfiguredAttributes() {
		if (configAttributesApplied) {
			return;
		}
		configAttributesApplied = true;
		double oldMax = getMaxHealth();
		float healthRatio = oldMax <= 0.0 ? 1.0F : (float)(getHealth() / oldMax);
		var healthAttribute = getAttribute(Attributes.MAX_HEALTH);
		if (healthAttribute != null) {
			healthAttribute.setBaseValue(scaledEncounterHealth(encounterPlayerCount));
			setHealth(Math.max(1.0F, getMaxHealth() * healthRatio));
		}
		var damageAttribute = getAttribute(Attributes.ATTACK_DAMAGE);
		if (damageAttribute != null) {
			damageAttribute.setBaseValue(15.0 * SnakeBossConfig.VALUES.damageMultiplier.get());
		}
	}

	private void updateEncounterScaling() {
		if (tickCount % 20 != 0 || getSummonTicks() > 0 || isDeadOrDying()) {
			return;
		}
		int nearbyPlayers = Math.max(1, combatPlayers(48.0).size());
		if (nearbyPlayers <= encounterPlayerCount) {
			return;
		}
		double oldMax = getMaxHealth();
		encounterPlayerCount = nearbyPlayers;
		var healthAttribute = getAttribute(Attributes.MAX_HEALTH);
		if (healthAttribute != null) {
			double newMax = scaledEncounterHealth(encounterPlayerCount);
			healthAttribute.setBaseValue(newMax);
			setHealth((float)Math.min(newMax, getHealth() + Math.max(0.0, newMax - oldMax)));
		}
	}

	private static double scaledEncounterHealth(int players) {
		return BASE_MAX_HEALTH * SnakeBossConfig.VALUES.healthMultiplier.get()
				* BossEncounterScaling.healthScale(players);
	}

	private void tickCombat(LivingEntity target) {
		if (getSummonTicks() > 0 || isStaggered()) {
			return;
		}
		lastCombatTick = tickCount;
		int attack = getAttackState();
		entityData.set(DATA_ALERT, true);
		if (attack != ATTACK_NONE) {
			getNavigation().stop();
			float lockedYaw = getLockedAttackYaw();
			setYRot(lockedYaw);
			yBodyRot = lockedYaw;
			yHeadRot = lockedYaw;
			tickAttack(attack, target);
			return;
		}

		double distance = distanceTo(target);
		getLookControl().setLookAt(target, 35.0F, 25.0F);
		if (distance > COMBAT_HOLD_DISTANCE) {
			getNavigation().moveTo(target, distance > 12.0 ? 1.25 : 1.08);
		} else if (distance < COMBAT_RETREAT_DISTANCE) {
			getNavigation().stop();
			retreatFrom(target);
		} else {
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0.72, 1.0, 0.72));
		}
		if (tickCount >= nextAttackTick) {
			int selectedAttack = queuedAttack;
			queuedAttack = 0;
			if (selectedAttack <= 0 || tickCount < attackReadyTicks[selectedAttack]
					|| tickCount < directionReadyTicks[attackDirection(selectedAttack).ordinal()]) {
				selectedAttack = selectAttack(distance);
			}
			if (selectedAttack == ATTACK_NONE) {
				nextAttackTick = tickCount + 10;
			} else {
				setAttack(selectedAttack);
			}
		}
	}

	private int selectAttack(double distance) {
		int[] recent = recentAttacks.stream().mapToInt(Integer::intValue).toArray();
		int playerCount = combatPlayers(48.0).size();
		int lastAttack = recentAttacks.isEmpty() ? ATTACK_NONE : recentAttacks.getLast();
		return SnakeKingCombatDirector.select(new SnakeKingCombatDirector.Context(distance, tickCount,
				directionReadyTicks, attackReadyTicks, recent, lastAttack, playerCount,
				getHealth() / getMaxHealth()), random.nextDouble());
	}

	private void tickAttack(int state, LivingEntity target) {
		int tick = getAttackTick();
		entityData.set(DATA_ATTACK_TICK, tick);
		stabilizeAttackMovement(state, tick);
		updateModelSignal(state, tick);
		switch (state) {
			case ATTACK_BITE -> tickBite(tick, target);
			case ATTACK_DOUBLE_HUNT -> tickDoubleHunt(tick, target);
			case ATTACK_TAIL_SWEEP -> tickTailSweep(tick);
			case ATTACK_DATA_FANGS -> tickDataFangs(tick, target);
			case ATTACK_RUNE_SIGIL -> tickRuneSigil(tick, target);
			case ATTACK_CROWN_CHAIN -> tickCrownChain(tick, target);
			case ATTACK_VOID_DASH -> tickVoidDash(tick, target);
			case ATTACK_CORROSIVE_COIL -> tickCorrosiveCoil(tick);
			case ATTACK_CROWN_JUDGMENT -> tickCrownJudgment(tick);
			case ATTACK_OVERHEAD_FANG -> tickOverheadFang(tick, target);
			default -> {
			}
		}
		if (tick >= attackDuration(state)) {
			finishAttack(state);
		}
	}

	private int attackDuration(int state) {
		return state == ATTACK_DOUBLE_HUNT ? getHuntPattern().durationTicks() : ATTACK_DURATIONS[state];
	}

	private void stabilizeAttackMovement(int state, int tick) {
		Vec3 movement = getDeltaMovement();
		double retention = SnakeAttackMotionRules.horizontalRetention(state, tick, getHuntPattern());
		double x = Math.abs(movement.x) < 1.0E-4 ? 0.0 : movement.x * retention;
		double z = Math.abs(movement.z) < 1.0E-4 ? 0.0 : movement.z * retention;
		setDeltaMovement(x, movement.y, z);
	}

	private void tickBite(int tick, LivingEntity target) {
		if (tick == 3) {
			play(ModSounds.SNAKE_KING_BITE_WINDUP.get(), 1.35F, 0.96F);
		}
		if (tick == 8) {
			lungeToward(target.position(), 1.25);
		}
		if (tick >= SnakeHuntPattern.BITE_HIT_TICK
				&& tick < SnakeHuntPattern.BITE_HIT_TICK + SnakeHuntPattern.ACTIVE_WINDOW_TICKS) {
			damageBite(12.0F, 1.1);
			if (tick == SnakeHuntPattern.BITE_HIT_TICK) {
				play(ModSounds.SNAKE_KING_BITE_HIT.get(), 1.5F, 0.94F);
			}
		}
	}

	private void tickDoubleHunt(int tick, LivingEntity target) {
		SnakeHuntPattern pattern = getHuntPattern();
		int segment = SnakeHuntPattern.segment(tick);
		if (segment >= pattern.segmentCount()) {
			return;
		}
		int localTick = SnakeHuntPattern.localTick(tick);
		if (comboAnimationStep != segment) {
			attackHits.clear();
			attackStrike = segment;
			entityData.set(DATA_SWEEP_LEFT, ((segment + getComboPattern()) & 1) == 0);
			triggerComboAnimation(segment);
		}
		SnakeHuntPattern.Move move = pattern.move(segment);
		if (move == SnakeHuntPattern.Move.BITE) {
			tickComboBite(localTick, target, segment);
		} else {
			tickComboTail(localTick);
		}
	}

	private void tickComboBite(int localTick, LivingEntity target, int segment) {
		if (localTick == 3) {
			play(ModSounds.SNAKE_KING_BITE_WINDUP.get(), 1.25F, segment == 2 ? 0.88F : 0.98F);
		}
		if (localTick == 8) {
			lungeLocked(1.08 + segment * 0.08);
		}
		if (localTick >= SnakeHuntPattern.BITE_HIT_TICK
				&& localTick < SnakeHuntPattern.BITE_HIT_TICK + SnakeHuntPattern.ACTIVE_WINDOW_TICKS) {
			damageBite(8.0F, 0.95);
			if (localTick == SnakeHuntPattern.BITE_HIT_TICK) {
				play(ModSounds.SNAKE_KING_BITE_HIT.get(), 1.35F, 0.9F + segment * 0.035F);
			}
		}
	}

	private void tickComboTail(int localTick) {
		if (localTick == 3) {
			play(ModSounds.SNAKE_KING_TAIL_WINDUP.get(), 1.4F, 0.9F);
		}
		if (localTick == 14) {
			play(ModSounds.SNAKE_KING_TAIL.get(), 1.5F, 0.94F);
		}
		if (localTick >= SnakeHuntPattern.TAIL_HIT_TICK - 1
				&& localTick <= SnakeHuntPattern.TAIL_HIT_TICK) {
			damageTailSweep(10.0F, 2.0);
			if (localTick == SnakeHuntPattern.TAIL_HIT_TICK) {
				play(ModSounds.SNAKE_KING_TAIL_HIT.get(), 1.55F, 0.9F);
			}
		}
	}

	private void tickTailSweep(int tick) {
		if (tick == 3) {
			play(ModSounds.SNAKE_KING_TAIL_WINDUP.get(), 1.5F, 0.88F);
		}
		if (tick == 14) {
			play(ModSounds.SNAKE_KING_TAIL.get(), 1.6F, 0.92F);
		}
		if (tick >= SnakeHuntPattern.TAIL_HIT_TICK - 1
				&& tick <= SnakeHuntPattern.TAIL_HIT_TICK + 1) {
			damageTailSweep(12.0F, 2.4);
			if (tick == SnakeHuntPattern.TAIL_HIT_TICK) {
				play(ModSounds.SNAKE_KING_TAIL_HIT.get(), 1.7F, 0.88F);
			}
		}
	}

	private void damageTailSweep(float damage, double knockback) {
		damageVolume(SnakeAttackVolume.ring(getX(), getY(), getZ(), SnakeAttackVolume.TAIL_OUTER_RADIUS,
				SnakeAttackVolume.TAIL_INNER_RADIUS,
				-scaled(0.5), scaled(2.1)), damage, knockback, false, BossAttackDirection.MID);
	}

	private void damageBite(float damage, double knockback) {
		damageVolume(lockedCone(SnakeAttackVolume.BITE_RANGE, SnakeAttackVolume.BITE_HALF_ANGLE,
				-scaled(0.5), scaled(2.4)),
				damage, knockback, false, BossAttackDirection.MID);
	}

	private int damageForwardStrike(float damage, double knockback, boolean corrosion,
			BossAttackDirection direction, double range, double minimumFacing) {
		Vec3 forward = getLookAngle().multiply(1.0, 0.0, 1.0);
		forward = forward.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
		Vec3 origin = position().add(0.0, scaled(0.9), 0.0);
		int hits = 0;
		for (Player player : combatPlayers(range + scaled(0.5))) {
			Vec3 delta = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(origin);
			Vec3 horizontal = delta.multiply(1.0, 0.0, 1.0);
			double distance = horizontal.length();
			if (distance > range || Math.abs(delta.y) > scaled(2.0)) {
				continue;
			}
			double facing = distance < 1.0E-5 ? 1.0 : horizontal.normalize().dot(forward);
			if (facing >= minimumFacing && attackHits.add(player.getUUID())
					&& resolveDirectedHit(player, damageSources().mobAttack(this), damage, knockback, corrosion,
							direction)) {
				hits++;
			}
		}
		return hits;
	}

	private void tickDataFangs(int tick, LivingEntity target) {
		if (tick == 4) {
			play(ModSounds.SNAKE_KING_POISON_HEAD_CHARGE.get(), 1.45F, 0.96F);
		}
		if (tick == 11) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.3F, 1.12F);
		}
		if (tick == 18) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.45F, 1.08F);
		}
		if (tick == 20) {
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.5F, 1.02F);
		}
		if (tick >= 20 && tick <= 22) {
			attackStrike += damageHeadPoisonBeam(target, 10.0F, 60);
		}
		if (tick == 22) {
			resolveImmediateHighAttack(attackStrike);
			play(ModSounds.SNAKE_KING_POISON_HEAD_RELEASE.get(), 1.55F, 0.94F);
		}
	}

	private void tickRuneSigil(int tick, LivingEntity target) {
		if (tick == 4) {
			play(ModSounds.SNAKE_KING_POISON_GROUND_CHARGE.get(), 1.45F, 0.9F);
		}
		if (tick == 10) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.3F, 0.84F);
		}
		if (tick == 17) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.4F, 0.88F);
		}
		if (tick >= 20 && tick <= 22) {
			damageGroundPoisonFan(SnakeAttackVolume.GROUND_POISON_RANGE,
					SnakeAttackVolume.GROUND_POISON_HALF_ANGLE, 8.0F, 80);
		}
		if (tick == 20) {
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.55F, 0.86F);
			play(ModSounds.SNAKE_KING_POISON_GROUND_RELEASE.get(), 1.55F, 0.92F);
		}
	}

	private int damageHeadPoisonBeam(LivingEntity target, float damage, int poisonTicks) {
		SnakeAttackVolume volume = lockedBeam(SnakeAttackVolume.HIGH_POISON_RANGE,
				SnakeAttackVolume.HIGH_POISON_HALF_WIDTH,
				getLockedTargetY() - getY() - 1.35, getLockedTargetY() - getY() + 1.35);
		int hits = 0;
		for (Player player : combatPlayers(13.0)) {
			if (!volume.contains(player.getX(), player.getEyeY(), player.getZ())) {
				continue;
			}
			if (attackHits.add(player.getUUID()) && resolvePoisonHit(player, damage, 0.65,
					BossAttackDirection.HIGH, poisonTicks)) {
				hits++;
			}
		}
		return hits;
	}

	private int damageGroundPoisonFan(double range, double halfAngle, float damage, int poisonTicks) {
		SnakeAttackVolume volume = lockedCone(range, halfAngle, -0.6, 2.5);
		int hits = 0;
		for (Player player : combatPlayers(range + 1.0)) {
			if (!volume.contains(player.getX(), player.getY(), player.getZ())) {
				continue;
			}
			if (attackHits.add(player.getUUID()) && resolvePoisonHit(player, damage, 0.9,
					BossAttackDirection.LOW, poisonTicks)) {
				hits++;
			}
		}
		return hits;
	}

	private boolean resolvePoisonHit(Player player, float damage, double knockback,
			BossAttackDirection direction, int poisonTicks) {
		boolean hit = resolveDirectedHit(player, damageSources().mobAttack(this), damage, knockback, false, direction);
		if (hit) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.POISON, poisonTicks, 0), this);
		}
		return hit;
	}

	private void tickCrownChain(int tick, LivingEntity target) {
		if (tick == 9) {
			play(ModSounds.SNAKE_KING_CAST.get(), 1.55F, 0.94F);
		}
		if (tick == 18) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.45F, 0.78F);
		}
		if (tick == 29) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.55F, 0.94F);
		}
		if (tick == 33) {
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.7F, 0.92F);
		}
		if (tick >= 33 && tick <= 35 && level() instanceof ServerLevel) {
			attackStrike += damagePlayersInRadius(18.0, 5.0, 18.0F, 0.8, false,
					BossAttackDirection.HIGH);
		}
		if (tick == 35) {
			resolveImmediateHighAttack(attackStrike);
			play(ModSounds.SNAKE_KING_CHAIN.get(), 1.75F, 0.9F);
		}
	}

	private void tickVoidDash(int tick, LivingEntity target) {
		if (tick == 35) {
			lockAttackTarget(target);
			attackHits.clear();
		}
		if (tick == 16 || tick == 41) {
			if (tick == 41) {
				attackHits.clear();
			}
			lungeLocked(1.9);
			play(ModSounds.SNAKE_KING_DASH.get(), 1.5F, tick == 16 ? 0.98F : 0.9F);
		}
		if ((tick >= 16 && tick <= 18) || (tick >= 41 && tick <= 43)) {
			damageVolume(lockedBeam(SnakeAttackVolume.DASH_RANGE, SnakeAttackVolume.DASH_HALF_WIDTH,
					-scaled(0.5), scaled(2.5)),
					9.0F, 1.4, false, BossAttackDirection.MID);
		}
	}

	private void tickCorrosiveCoil(int tick) {
		if (tick == 10) {
			play(ModSounds.SNAKE_KING_CAST.get(), 1.4F, 0.88F);
		}
		if (tick == 28) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.45F, 0.72F);
		}
		if (tick == 48) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.55F, 0.8F);
		}
		if (tick >= 52 && tick <= 54) {
			damagePlayersOutsideGap(getBoundingBox().inflate(7.0, 3.0, 7.0), coilGapAngle(tick), 0.58,
					16.0F, 2.6, BossAttackDirection.LOW);
			if (tick == 52) {
				play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.65F, 0.76F);
				play(ModSounds.SNAKE_KING_IMPACT.get(), 1.8F, 0.94F);
			}
		}
	}

	private void tickCrownJudgment(int tick) {
		if (tick == 8) {
			play(ModSounds.SNAKE_KING_JUDGMENT.get(), 2.0F, 0.94F);
		}
		if (tick == 24 && level() instanceof ServerLevel serverLevel) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.7F, 0.68F);
			int safe = entityData.get(DATA_JUDGMENT_SAFE_PATH);
			for (int i = 0; i < 8; i++) {
				if (i == safe) {
					continue;
				}
				double angle = Math.PI * 2.0 * i / 8.0;
				for (double radius = 3.0; radius <= 12.0; radius += 3.0) {
					Vec3 pos = position().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
					spawnSpellNode(serverLevel, pos, SnakeSpellNode.JUDGMENT, 38, 2.0F,
							BossAttackDirection.LOW);
				}
			}
		}
		if (tick == 48) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.75F, 0.76F);
		}
		if (tick == 60) {
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.9F, 0.72F);
		}
		if (tick == 62) {
			play(ModSounds.SNAKE_KING_IMPACT.get(), 1.9F, 0.78F);
		}
	}

	private void spawnSpellNode(ServerLevel level, Vec3 pos, int kind, int delay, float radius,
			BossAttackDirection direction) {
		SnakeSpellNode node = ModEntityTypes.SNAKE_SPELL_NODE.get().create(level);
		if (node == null) {
			return;
		}
		node.setPos(pos.x, pos.y + 0.05, pos.z);
		node.configure(this, activeAttackInstance, direction, kind, delay, radius);
		level.addFreshEntity(node);
	}

	private void tickOverheadFang(int tick, LivingEntity target) {
		if (tick == 12) {
			play(ModSounds.SNAKE_KING_ROAR.get(), 1.35F, 1.0F);
		}
		if (tick == 19) {
			lungeLocked(0.9);
		}
		if (tick >= 22 && tick <= 24) {
			attackStrike += damageVolume(lockedCone(SnakeAttackVolume.OVERHEAD_RANGE,
					SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
					-scaled(0.4), scaled(2.8)), 18.0F, 1.8, true, BossAttackDirection.HIGH);
		}
		if (tick == 24) {
			resolveImmediateHighAttack(attackStrike);
			play(ModSounds.SNAKE_KING_IMPACT.get(), 1.6F, 0.92F);
		}
	}

	private void lungeToward(Vec3 target, double speed) {
		lungeToward(target, speed, LUNGE_STOP_DISTANCE);
	}

	private void lungeToward(Vec3 target, double speed, double stopDistance) {
		Vec3 direction = target.subtract(position()).multiply(1.0, 0.0, 1.0);
		if (direction.lengthSqr() > 1.0E-5) {
			double distance = direction.length();
			double allowedSpeed = stopDistance <= 0.0 ? speed
					: Math.min(speed, Math.max(0.18, (distance - stopDistance) * 0.42));
			direction = direction.normalize().scale(allowedSpeed);
			setDeltaMovement(direction.x, 0.0, direction.z);
		}
	}

	private void lungeLocked(double speed) {
		Vec3 direction = lockedForward().scale(speed);
		setDeltaMovement(direction.x, 0.0, direction.z);
	}

	private void retreatFrom(LivingEntity target) {
		Vec3 away = position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-5) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		away = away.normalize();
		Vec3 side = new Vec3(-away.z, 0.0, away.x).scale((tickCount & 31) < 16 ? 0.12 : -0.12);
		Vec3 retreat = away.scale(0.24 + (COMBAT_RETREAT_DISTANCE - distanceTo(target)) * 0.06).add(side);
		setDeltaMovement(getDeltaMovement().multiply(0.35, 1.0, 0.35).add(retreat.x, 0.0, retreat.z));
	}

	public boolean canReceiveDataFangHit(@Nullable UUID volley, UUID target) {
		if (volley == null) {
			return true;
		}
		if (activeFangVolley == null) {
			activeFangVolley = volley;
			fangVolleyHits.clear();
		}
		if (!activeFangVolley.equals(volley)) {
			return false;
		}
		return fangVolleyHits.getOrDefault(target, 0) < 2;
	}

	public void registerDataFangHit(@Nullable UUID volley, UUID target) {
		if (volley != null && activeFangVolley != null && activeFangVolley.equals(volley)) {
			fangVolleyHits.merge(target, 1, Integer::sum);
		}
	}

	public void resolveDataFang(@Nullable UUID volley, boolean hitPlayer) {
		if (volley == null || activeFangVolley == null || !activeFangVolley.equals(volley)) {
			return;
		}
		fangVolleyHitPlayer |= hitPlayer;
		pendingFangProjectiles = Math.max(0, pendingFangProjectiles - 1);
		if (pendingFangProjectiles == 0) {
			if (!fangVolleyHitPlayer && !isStaggered()) {
					damagePosture(Math.round(BossPostureRules.HIGH_WHIFF_POSTURE_DAMAGE), null);
			}
			activeFangVolley = null;
			fangVolleyHits.clear();
		}
	}

	public boolean isAttackInstanceActive(@Nullable UUID instance) {
		return instance != null && isAlive() && !isStaggered()
				&& (instance.equals(activeAttackInstance) || instance.equals(activeFangVolley));
	}

	private AABB sweepBounds(Entity part) {
		return part.getBoundingBox().expandTowards(part.xo - part.getX(), part.yo - part.getY(), part.zo - part.getZ());
	}

	private int damagePlayersInRadius(double radius, double verticalRange, float amount,
			double knockback, boolean corrosion,
			BossAttackDirection direction) {
		int hits = 0;
		for (Player player : level().getEntitiesOfClass(Player.class,
				getBoundingBox().inflate(radius, verticalRange, radius),
				player -> player.isAlive() && !player.isCreative() && !player.isSpectator())) {
			double dx = player.getX() - getX();
			double dz = player.getZ() - getZ();
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			if (attackHits.add(player.getUUID())
					&& resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, corrosion,
							direction)) {
				hits++;
			}
		}
		return hits;
	}

	private int damageVolume(SnakeAttackVolume volume, float amount, double knockback, boolean corrosion,
			BossAttackDirection direction) {
		int hits = 0;
		for (Player player : combatPlayers(volume.range() + 2.0)) {
			double sampleY = direction == BossAttackDirection.HIGH ? player.getEyeY()
					: player.getY() + player.getBbHeight() * 0.5;
			if (!volume.contains(player.getX(), sampleY, player.getZ()) || !attackHits.add(player.getUUID())) {
				continue;
			}
			if (resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, corrosion, direction)) {
				hits++;
			}
		}
		return hits;
	}

	private SnakeAttackVolume lockedCone(double range, double halfAngle, double minY, double maxY) {
		Vec3 forward = lockedForward();
		return SnakeAttackVolume.cone(getX(), getY(), getZ(), forward.x, forward.z,
				range, halfAngle, minY, maxY);
	}

	private SnakeAttackVolume lockedBeam(double range, double halfWidth, double minY, double maxY) {
		Vec3 forward = lockedForward();
		return SnakeAttackVolume.beam(getX(), getY(), getZ(), forward.x, forward.z,
				range, halfWidth, minY, maxY);
	}

	private Vec3 lockedForward() {
		float yaw = entityData.get(DATA_LOCKED_ATTACK_YAW) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	private int damagePlayersOutsideGap(AABB area, double gapCenter, double halfWidth, float amount,
			double knockback, BossAttackDirection direction) {
		int hits = 0;
		for (Player player : level().getEntitiesOfClass(Player.class, area,
				player -> player.isAlive() && !player.isCreative() && !player.isSpectator())) {
			double dx = player.getX() - getX();
			double dz = player.getZ() - getZ();
			if (dx * dx + dz * dz > 7.0 * 7.0) {
				continue;
			}
			double angle = Math.atan2(dz, dx);
			if (Math.abs(Mth.wrapDegrees((float)Math.toDegrees(angle - gapCenter))) <= Math.toDegrees(halfWidth)) {
				continue;
			}
			if (attackHits.add(player.getUUID())
					&& resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, true,
							direction)) {
				hits++;
			}
		}
		return hits;
	}

	public void hurtTarget(Player player, float amount, double knockback, boolean corrosion) {
		resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, corrosion,
				getAttackDirection());
	}

	public boolean resolveDirectedHit(Player player, DamageSource source, float amount, double knockback,
			boolean corrosion, BossAttackDirection direction) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || isStaggered()) {
			return false;
		}
		if (direction == BossAttackDirection.HIGH && player.isShiftKeyDown()) {
			registerSuccessfulDodge(activeAttackInstance, player, direction, false);
			return false;
		}
		if (direction == BossAttackDirection.LOW && isValidLowDodge(player)) {
			registerSuccessfulDodge(activeAttackInstance, player, direction, true);
			return false;
		}
		boolean preciseParry = direction == BossAttackDirection.MID
				&& (isPreciseParry(player, source) || isChainedComboBiteParry(player, source));
		if (preciseParry) {
			if (getAttackState() == ATTACK_DOUBLE_HUNT) {
				comboParrySegments.put(player.getUUID(), getComboSegment());
			}
			int postureBefore = getPosture();
			int postureDamage = Math.round(BossPostureRules.preciseParryDamage());
			damagePosture(postureDamage, player);
			playParryFeedback(player, postureBefore - getPosture());
			return false;
		}
		boolean blocked = direction == BossAttackDirection.MID && player.isDamageSourceBlocked(source);
		float configuredDamage = amount * SnakeBossConfig.VALUES.damageMultiplier.get().floatValue();
		if (blocked) {
			boolean delayedTailPunish = isDelayedComboTailPunish(player, source);
			if (!delayedTailPunish) {
				// Let vanilla consume shield durability before applying the axe-style disable.
				player.hurt(source, configuredDamage);
			}
			player.disableShield();
			player.stopUsingItem();
			playShieldBreakFeedback(player);
			if (!delayedTailPunish) {
				return false;
			}
		}
		if (direction != BossAttackDirection.MID && isUsingShield(player)) {
			// HIGH and LOW are movement checks, never shield checks. Stop this use
			// without applying the MID shield-break cooldown.
			player.stopUsingItem();
		}
		if (!player.hurt(source, configuredDamage)) {
			return false;
		}
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), player.getX(),
					player.getY() + player.getBbHeight() * 0.55, player.getZ(), 3,
					0.32, 0.42, 0.32, 0.07);
			serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), player.getX(),
					player.getY() + player.getBbHeight() * 0.55, player.getZ(), 6,
					0.48, 0.48, 0.48, 0.02);
			serverLevel.playSound(null, player.blockPosition(), ModSounds.SNAKE_KING_IMPACT.get(),
					SoundSource.HOSTILE, 1.45F, 0.9F + random.nextFloat() * 0.08F);
				if (player instanceof ServerPlayer serverPlayer) {
					sendCombatFeedback(serverPlayer, SnakeCombatFeedbackPayload.HIT,
							amount >= 16.0F ? 1.45F : amount >= 12.0F ? 1.05F : 0.75F);
				}
		}
		if (corrosion) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.POISON, 70, 0), this);
		}
		return true;
	}

	public boolean resolveDelayedDirectedHit(@Nullable UUID attackInstance, Player player, float amount,
			double knockback, boolean corrosion, BossAttackDirection direction) {
		if (!isAttackInstanceActive(attackInstance)) {
			return false;
		}
		if (direction == BossAttackDirection.LOW && isValidLowDodge(player)) {
			registerSuccessfulDodge(attackInstance, player, direction, true);
			return false;
		}
		long frame = level().getGameTime();
		if (delayedHitFrames.getOrDefault(player.getUUID(), Long.MIN_VALUE) == frame) {
			return false;
		}
		delayedHitFrames.put(player.getUUID(), frame);
		return resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, corrosion, direction);
	}

	private boolean isPreciseParry(Player player, DamageSource source) {
		return isUsingShield(player)
				&& BossPostureRules.isPreciseParry(true, player.getTicksUsingItem())
				&& isFacingAttack(player, source);
	}

	private boolean isChainedComboBiteParry(Player player, DamageSource source) {
		if (getAttackState() != ATTACK_DOUBLE_HUNT || !isUsingShield(player)
				|| !isFacingAttack(player, source)) {
			return false;
		}
		int segment = getComboSegment();
		return segment > 0 && getHuntPattern().move(segment) == SnakeHuntPattern.Move.BITE
				&& comboParrySegments.getOrDefault(player.getUUID(), -2) == segment - 1;
	}

	private boolean isDelayedComboTailPunish(Player player, DamageSource source) {
		if (getAttackState() != ATTACK_DOUBLE_HUNT || !isComboTailStage()
				|| !isUsingShield(player) || !isFacingAttack(player, source)
				|| player.getTicksUsingItem() < BossPostureRules.PARRY_WINDOW_TICKS) {
			return false;
		}
		int segment = getComboSegment();
		return segment > 0 && comboParrySegments.getOrDefault(player.getUUID(), -2) == segment - 1;
	}

	private static boolean isUsingShield(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof ShieldItem;
	}

	private boolean isFacingAttack(Player player, DamageSource source) {
		Vec3 sourcePosition = source.getSourcePosition();
		if (sourcePosition == null) {
			sourcePosition = position();
		}
		Vec3 towardSource = sourcePosition.subtract(player.getEyePosition()).multiply(1.0, 0.0, 1.0);
		Vec3 view = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		return towardSource.lengthSqr() > 1.0E-5 && view.lengthSqr() > 1.0E-5
				&& towardSource.normalize().dot(view.normalize()) >= 0.35;
	}

	private boolean isValidLowDodge(Player player) {
		trackNormalJump(player);
		UUID playerId = player.getUUID();
		boolean invalidMovement = player.getAbilities().flying || player.isFallFlying() || player.isSwimming();
		return BossPostureRules.isLowDodgeWindow(player.onGround(), invalidMovement,
				activeJumpWindows.getOrDefault(playerId, -1) >= tickCount,
				lowDodgeLandingGrace.getOrDefault(playerId, -1) >= tickCount);
	}

	private void trackNormalJump(Player player) {
		UUID playerId = player.getUUID();
		if (player.getAbilities().flying || player.isFallFlying() || player.isSwimming()) {
			activeJumpWindows.remove(playerId);
			lowDodgeLandingGrace.remove(playerId);
			return;
		}
		if (player.onGround()) {
			if (activeJumpWindows.remove(playerId) != null) {
				lowDodgeLandingGrace.put(playerId,
						tickCount + BossPostureRules.LOW_DODGE_LANDING_GRACE_TICKS);
			}
			return;
		}
		if (BossPostureRules.isNormalJumpStart(player.onGround(), player.getAbilities().flying,
				player.isFallFlying(), player.isSwimming(), player.getDeltaMovement().y, player.fallDistance)) {
			activeJumpWindows.put(playerId, tickCount + BossPostureRules.LOW_DODGE_AIRBORNE_TICKS);
			lowDodgeLandingGrace.remove(playerId);
		}
	}

	private void playParryFeedback(Player player, int postureDamage) {
		if (level() instanceof ServerLevel serverLevel) {
			for (int layer = 1; layer <= 4; layer++) {
				serverLevel.sendParticles(ModParticleTypes.SNAKE_PARRY_FLASH.get(), player.getX(),
						player.getY() + player.getBbHeight() * layer / 5.0, player.getZ(), 3,
						0.28, 0.12, 0.28, 0.012);
			}
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.displayClientMessage(Component.translatable(
						"message.virtual_craft.snake_king.parry_success",
						Math.max(0, postureDamage)).withStyle(ChatFormatting.BOLD,
								ChatFormatting.GOLD), true);
				sendCombatFeedback(serverPlayer, SnakeCombatFeedbackPayload.PARRY, 0.7F);
			}
		}
	}

	private void registerSuccessfulDodge(@Nullable UUID attackInstance, Player player,
			BossAttackDirection direction, boolean grantJumpStrike) {
		if (grantJumpStrike) {
			jumpStrikeWindows.put(player.getUUID(), tickCount + BossPostureRules.JUMP_STRIKE_WINDOW_TICKS);
		}
		if (attackInstance != null
				&& !successfulDodges.add(new DelayedDodgeKey(attackInstance, player.getUUID()))) {
			return;
		}
		if (level() instanceof ServerLevel serverLevel) {
			boolean high = direction == BossAttackDirection.HIGH;
			double effectY = high ? player.getEyeY() + 0.12 : player.getY() + 0.12;
			serverLevel.sendParticles(high ? ModParticleTypes.SNAKE_HIGH_WARNING.get()
							: ModParticleTypes.SNAKE_LOW_WARNING.get(),
					player.getX(), effectY, player.getZ(), 8,
					high ? 0.42 : 0.62, high ? 0.18 : 0.04, high ? 0.42 : 0.62, 0.018);
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.displayClientMessage(Component.translatable(high
						? "message.virtual_craft.snake_king.high_dodge_success"
						: "message.virtual_craft.snake_king.low_dodge_success").withStyle(
						high ? ChatFormatting.AQUA : ChatFormatting.RED,
						ChatFormatting.BOLD), true);
				sendCombatFeedback(serverPlayer,
						high ? SnakeCombatFeedbackPayload.HIGH_DODGE : SnakeCombatFeedbackPayload.LOW_DODGE,
						high ? 0.32F : 0.4F);
			}
		}
	}

	private void playShieldBreakFeedback(Player player) {
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), player.getX(),
					player.getY() + player.getBbHeight() * 0.58, player.getZ(), 5,
					0.34, 0.3, 0.34, 0.055);
			serverLevel.playSound(null, player.blockPosition(), ModSounds.SNAKE_KING_SHIELD_BREAK.get(),
					SoundSource.PLAYERS, 1.35F, 0.72F);
			if (player instanceof ServerPlayer serverPlayer) {
				sendCombatFeedback(serverPlayer, SnakeCombatFeedbackPayload.SHIELD_BREAK, 0.55F);
			}
		}
	}

	private void sendCombatFeedback(ServerPlayer player, int kind, float strength) {
		PacketDistributor.sendToPlayer(player, new SnakeCombatFeedbackPayload(getId(), kind, strength));
	}

	private void resolveImmediateHighAttack(int hits) {
		if (hits == 0 && !isStaggered()) {
			damagePosture(Math.round(BossPostureRules.HIGH_WHIFF_POSTURE_DAMAGE), null);
		}
	}

	private List<Player> combatPlayers(double radius) {
		return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 5.0, radius),
				player -> player.isAlive() && !player.isCreative() && !player.isSpectator()
						&& distanceToSqr(player) <= radius * radius);
	}

	private double coilGapAngle(int attackTick) {
		return (entityData.get(DATA_ATTACK_START) * 0.11 + attackTick * 0.035) % (Math.PI * 2.0);
	}

	public double getCoilGapAngle() {
		return coilGapAngle(getAttackTick());
	}

	private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level().playSound(null, blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	private void updateModelSignal(int attack, int tick) {
		int previousSignal = entityData.get(DATA_SIGNAL_TYPE);
		int signal = SIGNAL_NONE;
		float strength = 0.0F;
		if (attack == ATTACK_DOUBLE_HUNT) {
			SnakeHuntPattern pattern = getHuntPattern();
			int segment = SnakeHuntPattern.segment(tick);
			if (segment < pattern.segmentCount()) {
				int local = SnakeHuntPattern.localTick(tick);
				int hit = pattern.move(segment) == SnakeHuntPattern.Move.BITE
						? SnakeHuntPattern.BITE_HIT_TICK : SnakeHuntPattern.TAIL_HIT_TICK;
				int warningStart = Math.max(0, hit - 6);
				float envelope = signalEnvelope(local, warningStart, hit, hit + 3);
				if (envelope > 0.0F) {
					signal = SIGNAL_WHITE;
					strength = envelope;
				}
			}
		} else if (attack == ATTACK_BITE) {
			float envelope = signalEnvelope(tick, SnakeHuntPattern.BITE_HIT_TICK - 6,
					SnakeHuntPattern.BITE_HIT_TICK,
					SnakeHuntPattern.BITE_HIT_TICK + 3);
			if (envelope > 0.0F) {
				signal = SIGNAL_WHITE;
				strength = envelope;
			}
		} else if (attack == ATTACK_TAIL_SWEEP) {
			float envelope = signalEnvelope(tick, SnakeHuntPattern.TAIL_HIT_TICK - 6,
					SnakeHuntPattern.TAIL_HIT_TICK,
					SnakeHuntPattern.TAIL_HIT_TICK + 3);
			if (envelope > 0.0F) {
				signal = SIGNAL_WHITE;
				strength = envelope;
			}
		} else if (attack == ATTACK_VOID_DASH) {
			int hit = tick < 30 ? 16 : 41;
			float envelope = signalEnvelope(tick, hit - 8, hit, hit + 3);
			if (envelope > 0.0F) {
				signal = SIGNAL_WHITE;
				strength = envelope;
			}
		} else if (attack == ATTACK_DATA_FANGS || attack == ATTACK_CROWN_CHAIN
				|| attack == ATTACK_OVERHEAD_FANG) {
			int impact = attack == ATTACK_DATA_FANGS ? 20 : attack == ATTACK_CROWN_CHAIN ? 33 : 22;
			int lead = impact - tick;
			if (lead >= 0 && lead <= 14) {
				signal = SIGNAL_BLUE;
				strength = 0.2F + 0.8F * (1.0F - lead / 14.0F);
			}
		} else if (attack == ATTACK_RUNE_SIGIL || attack == ATTACK_CORROSIVE_COIL
				|| attack == ATTACK_CROWN_JUDGMENT) {
			int impact = attack == ATTACK_RUNE_SIGIL ? 20 : attack == ATTACK_CORROSIVE_COIL ? 52 : 62;
			int warning = attack == ATTACK_RUNE_SIGIL ? 14 : attack == ATTACK_CORROSIVE_COIL ? 20 : 18;
			int lead = impact - tick;
			if (lead >= 0 && lead <= warning) {
				signal = SIGNAL_RED;
				strength = 0.2F + 0.8F * (1.0F - lead / (float)warning);
			}
		}
		if (!level().isClientSide && signal != SIGNAL_NONE && previousSignal != signal) {
			announceDirectionCue(signal);
		}
		entityData.set(DATA_SIGNAL_TYPE, signal);
		entityData.set(DATA_SIGNAL_STRENGTH, strength);
	}

	private void announceDirectionCue(int signal) {
		String key = switch (signal) {
			case SIGNAL_BLUE -> "message.virtual_craft.snake_king.telegraph.high";
			case SIGNAL_RED -> "message.virtual_craft.snake_king.telegraph.low";
			default -> "message.virtual_craft.snake_king.telegraph.mid";
		};
		ChatFormatting color = signal == SIGNAL_BLUE ? ChatFormatting.AQUA
				: signal == SIGNAL_RED ? ChatFormatting.RED : ChatFormatting.GOLD;
		int feedback = signal == SIGNAL_BLUE ? SnakeCombatFeedbackPayload.HIGH_CUE
				: signal == SIGNAL_RED ? SnakeCombatFeedbackPayload.LOW_CUE
				: SnakeCombatFeedbackPayload.MID_CUE;
		for (Player player : combatPlayers(32.0)) {
			if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.displayClientMessage(Component.translatable(key)
						.withStyle(color, ChatFormatting.BOLD), true);
				sendCombatFeedback(serverPlayer, feedback, 0.0F);
			}
		}
	}

	private void announceComboTutorial(int patternId) {
		if (level().isClientSide) {
			return;
		}
		int feedback = switch (SnakeHuntPattern.byId(patternId)) {
			case BITE_BITE_BITE -> SnakeCombatFeedbackPayload.COMBO_BITE_BITE_BITE_CUE;
			case BITE_BITE_TAIL -> SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE;
			case BITE_TAIL_BITE -> SnakeCombatFeedbackPayload.COMBO_BITE_TAIL_BITE_CUE;
			default -> -1;
		};
		if (feedback < 0) {
			return;
		}
		for (Player player : combatPlayers(32.0)) {
			if (player instanceof ServerPlayer serverPlayer) {
				sendCombatFeedback(serverPlayer, feedback, 0.0F);
			}
		}
	}

	private static float signalEnvelope(int tick, int warningStart, int impactTick, int recoveryEnd) {
		if (tick < warningStart || tick > recoveryEnd) {
			return 0.0F;
		}
		if (tick <= impactTick) {
			float progress = (tick - warningStart) / (float)Math.max(1, impactTick - warningStart);
			return 0.32F + progress * 0.68F;
		}
		return Math.max(0.0F, 1.0F - (tick - impactTick) / (float)Math.max(1, recoveryEnd - impactTick));
	}

	private void setAttack(int state) {
		Vec3 movement = getDeltaMovement();
		setDeltaMovement(0.0, movement.y, 0.0);
		entityData.set(DATA_ATTACK_STATE, state);
		entityData.set(DATA_ATTACK_START, tickCount);
		entityData.set(DATA_ATTACK_TICK, 0);
		BossAttackDirection direction = attackDirection(state);
		entityData.set(DATA_ATTACK_DIRECTION, direction.ordinal());
		entityData.set(DATA_SWEEP_LEFT, random.nextBoolean());
		if (state == ATTACK_CROWN_JUDGMENT) {
			entityData.set(DATA_JUDGMENT_SAFE_PATH, random.nextInt(8));
		}
		LivingEntity target = getTarget();
		if (target != null) {
			lockAttackTarget(target);
		} else {
			entityData.set(DATA_LOCKED_ATTACK_YAW, getYRot());
			entityData.set(DATA_LOCKED_TARGET_Y, (float)(getY() + getEyeHeight()));
		}
		attackHits.clear();
		successfulDodges.clear();
		comboParrySegments.clear();
		attackStrike = 0;
		comboAnimationStep = -1;
		if (state == ATTACK_DOUBLE_HUNT) {
			int previous = entityData.get(DATA_COMBO_PATTERN);
			int next = SnakeHuntPattern.firstComboId()
					+ random.nextInt(SnakeHuntPattern.comboCount());
			entityData.set(DATA_COMBO_PATTERN, next == previous
					? SnakeHuntPattern.nextComboId(next) : next);
			announceComboTutorial(entityData.get(DATA_COMBO_PATTERN));
		}
		activeAttackInstance = UUID.randomUUID();
		int cooldownJitter = state == ATTACK_DATA_FANGS || state == ATTACK_RUNE_SIGIL ? 0 : random.nextInt(21);
		attackReadyTicks[state] = tickCount + SnakeKingCombatDirector.cooldown(state) + cooldownJitter;
		directionReadyTicks[direction.ordinal()] = state == ATTACK_DATA_FANGS ? tickCount + 160
				: state == ATTACK_RUNE_SIGIL ? tickCount + 100 : switch (direction) {
			case HIGH -> tickCount + 160 + random.nextInt(81);
			case MID -> tickCount + attackDuration(state) + 40 + random.nextInt(17);
			case LOW -> tickCount + attackDuration(state) + 46 + random.nextInt(21);
		};
		if (!level().isClientSide) {
			if (state == ATTACK_DOUBLE_HUNT) {
				triggerComboAnimation(0);
			} else {
				String animation = state == ATTACK_TAIL_SWEEP && entityData.get(DATA_SWEEP_LEFT)
						? "tail_sweep_left"
						: ATTACK_ANIMATIONS[state];
				triggerAnim("combat", animation);
			}
		}
	}

	private void lockAttackTarget(LivingEntity target) {
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		entityData.set(DATA_LOCKED_ATTACK_YAW, (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
		entityData.set(DATA_LOCKED_TARGET_Y, (float)target.getEyeY());
	}

	private void finishAttack(int state) {
		Vec3 movement = getDeltaMovement();
		setDeltaMovement(0.0, movement.y, 0.0);
		entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
		entityData.set(DATA_ATTACK_TICK, 0);
		entityData.set(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		entityData.set(DATA_SIGNAL_TYPE, SIGNAL_NONE);
		entityData.set(DATA_SIGNAL_STRENGTH, 0.0F);
		activeAttackInstance = null;
		comboAnimationStep = -1;
		recentAttacks.addLast(state);
		while (recentAttacks.size() > 3) {
			recentAttacks.removeFirst();
		}
		double distance = getTarget() == null ? Double.MAX_VALUE : distanceTo(getTarget());
		queuedAttack = ATTACK_NONE;
		int categoryDelay = switch (attackDirection(state)) {
			case MID -> 40 + random.nextInt(17);
			case HIGH -> 48 + random.nextInt(17);
			case LOW -> 46 + random.nextInt(21);
		};
		nextAttackTick = tickCount + (queuedAttack != ATTACK_NONE
				? Math.max(24, categoryDelay) : categoryDelay);
	}

	public static BossAttackDirection attackDirection(int attack) {
		return SnakeKingAttackRules.directionFor(attack);
	}

	public int getAttackState() {
		return entityData.get(DATA_ATTACK_STATE);
	}

	public int getAttackTick() {
		return level().isClientSide ? Math.max(0, entityData.get(DATA_ATTACK_TICK))
				: Math.max(0, tickCount - entityData.get(DATA_ATTACK_START));
	}

	public int getComboPattern() {
		return Mth.clamp(entityData.get(DATA_COMBO_PATTERN), 0, SnakeHuntPattern.values().length - 1);
	}

	public SnakeHuntPattern getHuntPattern() {
		return SnakeHuntPattern.byId(getComboPattern());
	}

	public float getLockedAttackYaw() {
		return entityData.get(DATA_LOCKED_ATTACK_YAW);
	}

	public float getLockedTargetY() {
		return entityData.get(DATA_LOCKED_TARGET_Y);
	}

	public int getSignalType() {
		return entityData.get(DATA_SIGNAL_TYPE);
	}

	public float getSignalStrength() {
		return entityData.get(DATA_SIGNAL_STRENGTH);
	}

	public int getJudgmentSafePath() {
		return entityData.get(DATA_JUDGMENT_SAFE_PATH);
	}

	public Vec3 getCombatPartPosition(int index, float partialTick) {
		VirtualSnakeKingPart part = parts[Mth.clamp(index, 0, parts.length - 1)];
		return new Vec3(
				Mth.lerp(partialTick, part.xo, part.getX()),
				Mth.lerp(partialTick, part.yo, part.getY()),
				Mth.lerp(partialTick, part.zo, part.getZ()));
	}

	public Vec3 getPreviousCombatPartPosition(int index) {
		VirtualSnakeKingPart part = parts[Mth.clamp(index, 0, parts.length - 1)];
		return new Vec3(part.xo, part.yo, part.zo);
	}

	public int getComboSegment() {
		return Math.min(getHuntPattern().segmentCount() - 1, SnakeHuntPattern.segment(getAttackTick()));
	}

	public int getComboLocalTick() {
		return SnakeHuntPattern.localTick(getAttackTick());
	}

	public boolean isComboTailStage() {
		return getAttackState() == ATTACK_DOUBLE_HUNT
				&& SnakeHuntPattern.segment(getAttackTick()) < getHuntPattern().segmentCount()
				&& getHuntPattern().move(getComboSegment()) == SnakeHuntPattern.Move.TAIL;
	}

	private void triggerComboAnimation(int segment) {
		comboAnimationStep = segment;
		SnakeHuntPattern.Move move = getHuntPattern().move(segment);
		boolean left = ((segment + getComboPattern()) & 1) == 0;
		if (move == SnakeHuntPattern.Move.TAIL) {
			left = entityData.get(DATA_SWEEP_LEFT);
		}
		if (!level().isClientSide) {
			triggerAnim("combat", "combo_" + (move == SnakeHuntPattern.Move.BITE ? "bite_" : "tail_")
					+ (left ? "left" : "right"));
		}
	}

	public int getSummonTicks() {
		return entityData.get(DATA_SUMMON_TICKS);
	}

	public boolean isSweepLeft() {
		return entityData.get(DATA_SWEEP_LEFT);
	}

	public boolean isCombatActive() {
		return entityData.get(DATA_ALERT) && getSummonTicks() == 0;
	}

	@Override
	public int getPosture() {
		return entityData.get(DATA_POSTURE);
	}

	@Override
	public int getMaxPosture() {
		return BossPostureRules.MAX_POSTURE;
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
			beginStagger();
		}
	}

	private void beginStagger() {
		posturePressure = 0.0F;
		entityData.set(DATA_STAGGER_TICKS, BossPostureRules.STAGGER_TICKS);
		entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
		entityData.set(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		entityData.set(DATA_SIGNAL_TYPE, SIGNAL_NONE);
		entityData.set(DATA_SIGNAL_STRENGTH, 0.0F);
		activeAttackInstance = null;
		comboAnimationStep = -1;
		activeFangVolley = null;
		pendingFangProjectiles = 0;
		fangVolleyHits.clear();
		attackHits.clear();
		successfulDodges.clear();
		activeJumpWindows.clear();
		lowDodgeLandingGrace.clear();
		comboParrySegments.clear();
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		triggerAnim("reaction", "stagger");
		play(ModSounds.SNAKE_KING_IMPACT.get(), 1.9F, 0.86F);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), getX(), getY() + 1.1, getZ(),
					18, 1.8, 0.8, 1.8, 0.055);
			for (Player player : combatPlayers(48.0)) {
				if (player instanceof ServerPlayer serverPlayer) {
					sendCombatFeedback(serverPlayer, SnakeCombatFeedbackPayload.POSTURE_BREAK, 1.25F);
				}
			}
		}
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			target = null;
		}
		super.setTarget(target);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (getSummonTicks() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		boolean staggeredBeforeHit = isStaggered();
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		double sourceDistance = source.getSourcePosition() == null ? -1.0
				: source.getSourcePosition().distanceTo(position());
		float appliedDamage = BossDamageRules.acceptedDamage(amount,
				SnakeBossConfig.VALUES.damageCap.get().floatValue(),
				SnakeBossConfig.VALUES.dpsCap.get().floatValue(), damageBucket, sourceDistance,
				SnakeBossConfig.VALUES.rangedDamageLimit.get(), staggeredBeforeHit);
		if (appliedDamage <= 0.0F) {
			return false;
		}
		float healthBeforeHit = getHealth();
		boolean hurt = super.hurt(source, appliedDamage);
		if (hurt) {
			float actualDamage = Math.max(0.0F, healthBeforeHit - getHealth());
			damageBucket += actualDamage;
			lastCombatTick = tickCount;
			hurtDuration = 6;
			hurtTime = Math.min(hurtTime, 6);
			if (!level().isClientSide && !staggeredBeforeHit && getAttackState() == ATTACK_NONE) {
				triggerAnim("reaction", "hurt");
			}
			if (!level().isClientSide && !staggeredBeforeHit && source.getEntity() instanceof Player player
					&& source.getDirectEntity() == player && !player.isCreative() && !player.isSpectator()) {
				float pressure = BossPostureRules.chargedMeleePressure(amount,
						player.getAttackStrengthScale(0.5F));
				Integer jumpWindow = jumpStrikeWindows.get(player.getUUID());
				if (pressure > 0.0F && jumpWindow != null && jumpWindow >= tickCount
						&& !player.onGround() && player.fallDistance > 0.0F) {
					pressure = BossPostureRules.jumpStrikeDamage();
					jumpStrikeWindows.remove(player.getUUID());
				}
				addPosturePressure(pressure, player);
			}
		}
		return hurt;
	}

	private void addPosturePressure(float amount, @Nullable Player source) {
		if (amount <= 0.0F || isStaggered()) {
			return;
		}
		posturePressure += amount;
		int wholePoints = (int)posturePressure;
		if (wholePoints > 0) {
			posturePressure -= wholePoints;
			damagePosture(wholePoints, source);
		}
	}

	public boolean hurt(VirtualSnakeKingPart part, DamageSource source, float amount) {
		return hurt(source, amount);
	}

	@Override
	public boolean isMultipartEntity() {
		return true;
	}

	@Override
	public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
		return parts;
	}

	@Override
	public void die(DamageSource source) {
		if (!isDeadOrDying() && !level().isClientSide) {
			triggerAnim("reaction", "death");
			play(ModSounds.SNAKE_KING_DEATH.get(), 2.0F, 0.94F);
		}
		super.die(source);
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		setDeltaMovement(Vec3.ZERO);
		entityData.set(DATA_SIGNAL_TYPE, SIGNAL_WHITE);
		float collapse = Mth.clamp(deathTime / 78.0F, 0.0F, 1.0F);
		float surge = deathTime == 18 || deathTime == 42 || deathTime == 66 ? 0.24F : 0.0F;
		entityData.set(DATA_SIGNAL_STRENGTH, Mth.clamp(0.22F + collapse * 0.66F + surge, 0.0F, 1.0F));
		if (level() instanceof ServerLevel serverLevel) {
			if (deathTime % 6 == 0 && deathTime < 78) {
				serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), getX(), getY() + 1.0, getZ(),
						5, 1.8, 0.95, 1.8, 0.045);
			}
			if (deathTime == 12 || deathTime == 34) {
				play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.45F, deathTime == 12 ? 0.62F : 0.78F);
			}
			if (deathTime == 54) {
				play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.75F, 0.7F);
			}
			if (deathTime == 18 || deathTime == 42 || deathTime == 66) {
				serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), getX(), getY() + 1.2, getZ(),
						deathTime == 66 ? 48 : 24, 2.2, 1.15, 2.2, 0.04);
				play(ModSounds.SNAKE_KING_IMPACT.get(), deathTime == 66 ? 1.8F : 1.15F,
						deathTime == 66 ? 0.86F : 0.94F);
			}
			if (deathTime == 70) {
				play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 2.0F, 0.66F);
				serverLevel.sendParticles(ModParticleTypes.SNAKE_TRAIL.get(), getX(), getY() + 1.15, getZ(),
						32, 2.5, 1.35, 2.5, 0.09);
			}
		}
		if (deathTime >= 90 && !level().isClientSide && !isRemoved()) {
			level().broadcastEntityEvent(this, (byte)60);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
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
		tag.putInt("NextAttack", Math.max(0, nextAttackTick - tickCount));
		int[] directionCooldowns = new int[directionReadyTicks.length];
		for (int direction = 0; direction < directionReadyTicks.length; direction++) {
			directionCooldowns[direction] = Math.max(0, directionReadyTicks[direction] - tickCount);
		}
		tag.putIntArray("DirectionCooldowns", directionCooldowns);
		int[] cooldowns = new int[attackReadyTicks.length];
		for (int attack = 1; attack < attackReadyTicks.length; attack++) {
			cooldowns[attack] = Math.max(0, attackReadyTicks[attack] - tickCount);
		}
		tag.putIntArray("AttackCooldowns", cooldowns);
		tag.putInt("QueuedAttack", queuedAttack);
		tag.putInt("Posture", getPosture());
		tag.putFloat("PosturePressure", posturePressure);
		tag.putInt("StaggerTicks", getStaggerTicks());
		tag.putFloat("DamageBucket", damageBucket);
		tag.putInt("EncounterPlayers", encounterPlayerCount);
		tag.putInt("AttackDirection", getAttackDirection().ordinal());
		tag.putInt("AttackState", getAttackState());
		tag.putInt("AttackTick", getAttackTick());
		tag.putBoolean("SweepLeft", entityData.get(DATA_SWEEP_LEFT));
		tag.putInt("ComboPattern", getComboPattern());
		tag.putFloat("LockedAttackYaw", getLockedAttackYaw());
		tag.putFloat("LockedTargetY", getLockedTargetY());
		if (activeAttackInstance != null) {
			tag.putUUID("AttackInstance", activeAttackInstance);
		}
		if (activeFangVolley != null) {
			tag.putUUID("FangVolley", activeFangVolley);
			tag.putInt("PendingFangs", pendingFangProjectiles);
			tag.putBoolean("FangVolleyHit", fangVolleyHitPlayer);
		}
		tag.putInt("SummonTicks", getSummonTicks());
		if (homePos != null) {
			tag.putLong("Home", homePos.asLong());
		}
		if (summonerUuid != null) {
			tag.putUUID("Summoner", summonerUuid);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		nextAttackTick = tickCount + Math.max(0, tag.getInt("NextAttack"));
		int[] directionCooldowns = tag.getIntArray("DirectionCooldowns");
		if (directionCooldowns.length == directionReadyTicks.length) {
			for (int direction = 0; direction < directionReadyTicks.length; direction++) {
				directionReadyTicks[direction] = tickCount + Math.max(0, directionCooldowns[direction]);
			}
		} else {
			directionReadyTicks[BossAttackDirection.HIGH.ordinal()] = tickCount
					+ Math.max(0, tag.getInt("NextHighAttack"));
		}
		int[] cooldowns = tag.getIntArray("AttackCooldowns");
		if (cooldowns.length == attackReadyTicks.length) {
			for (int attack = 1; attack < attackReadyTicks.length; attack++) {
				attackReadyTicks[attack] = tickCount + Math.max(0, cooldowns[attack]);
			}
		} else {
			attackReadyTicks[ATTACK_CROWN_JUDGMENT] = tickCount
					+ (tag.contains("NextJudgment") ? Math.max(0, tag.getInt("NextJudgment")) : 600);
		}
		queuedAttack = sanitizeAttack(tag.getInt("QueuedAttack"));
		entityData.set(DATA_POSTURE, tag.contains("Posture")
				? Mth.clamp(tag.getInt("Posture"), 0, BossPostureRules.MAX_POSTURE)
				: BossPostureRules.MAX_POSTURE);
		entityData.set(DATA_STAGGER_TICKS, Math.max(0, tag.getInt("StaggerTicks")));
		posturePressure = Mth.clamp(tag.getFloat("PosturePressure"), 0.0F, 0.999F);
		damageBucket = Math.max(0.0F, tag.getFloat("DamageBucket"));
		encounterPlayerCount = Math.max(1, tag.getInt("EncounterPlayers"));
		activeAttackInstance = tag.hasUUID("AttackInstance") ? tag.getUUID("AttackInstance") : null;
		activeFangVolley = tag.hasUUID("FangVolley") ? tag.getUUID("FangVolley") : null;
		pendingFangProjectiles = Math.max(0, tag.getInt("PendingFangs"));
		fangVolleyHitPlayer = tag.getBoolean("FangVolleyHit");
		int savedAttack = sanitizeAttack(tag.getInt("AttackState"));
		if (getStaggerTicks() > 0) {
			savedAttack = ATTACK_NONE;
			activeAttackInstance = null;
			activeFangVolley = null;
		}
		entityData.set(DATA_ATTACK_STATE, savedAttack);
		BossAttackDirection savedDirection = tag.contains("AttackDirection")
				? BossAttackDirection.fromId(tag.getInt("AttackDirection")) : attackDirection(savedAttack);
		entityData.set(DATA_ATTACK_DIRECTION, savedDirection.ordinal());
		entityData.set(DATA_ATTACK_START, tickCount - Math.max(0, tag.getInt("AttackTick")));
		entityData.set(DATA_ATTACK_TICK, Math.max(0, tag.getInt("AttackTick")));
		entityData.set(DATA_SWEEP_LEFT, tag.getBoolean("SweepLeft"));
		entityData.set(DATA_COMBO_PATTERN, Mth.clamp(tag.getInt("ComboPattern"), 0,
				SnakeHuntPattern.values().length - 1));
		entityData.set(DATA_LOCKED_ATTACK_YAW, tag.contains("LockedAttackYaw")
				? tag.getFloat("LockedAttackYaw") : getYRot());
		entityData.set(DATA_LOCKED_TARGET_Y, tag.contains("LockedTargetY")
				? tag.getFloat("LockedTargetY") : (float)(getY() + getEyeHeight()));
		comboAnimationStep = -1;
		entityData.set(DATA_SUMMON_TICKS, Math.max(0, tag.getInt("SummonTicks")));
		if (tag.contains("Home")) {
			homePos = BlockPos.of(tag.getLong("Home"));
		}
		summonerUuid = tag.hasUUID("Summoner") ? tag.getUUID("Summoner") : null;
		boolean summoning = getSummonTicks() > 0;
		setNoAi(summoning);
		noPhysics = summoning;
		bossEvent.setName(getDisplayName());
		bossEvent.setVisible(!summoning);
		resetHistory();
	}

	private static int sanitizeAttack(int attack) {
		return switch (attack) {
			case ATTACK_BITE, ATTACK_DOUBLE_HUNT, ATTACK_TAIL_SWEEP, ATTACK_DATA_FANGS,
					ATTACK_RUNE_SIGIL, ATTACK_CROWN_CHAIN, ATTACK_VOID_DASH,
					ATTACK_CORROSIVE_COIL, ATTACK_CROWN_JUDGMENT, ATTACK_OVERHEAD_FANG -> attack;
			default -> ATTACK_NONE;
		};
	}

	private final class SnakeKingCombatGoal extends Goal {
		private SnakeKingCombatGoal() {
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = getTarget();
			return getSummonTicks() == 0 && target != null && target.isAlive()
					&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
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
			entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
			entityData.set(DATA_ATTACK_TICK, 0);
			entityData.set(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
			entityData.set(DATA_SIGNAL_TYPE, SIGNAL_NONE);
			entityData.set(DATA_SIGNAL_STRENGTH, 0.0F);
			activeAttackInstance = null;
			successfulDodges.clear();
			activeJumpWindows.clear();
			lowDodgeLandingGrace.clear();
			comboParrySegments.clear();
			entityData.set(DATA_ALERT, false);
		}
	}

	private record DelayedDodgeKey(UUID attackInstance, UUID player) {
	}
}
