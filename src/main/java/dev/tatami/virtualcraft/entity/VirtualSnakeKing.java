package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.BossDamageRules;
import dev.tatami.virtualcraft.boss.BossEncounterScaling;
import dev.tatami.virtualcraft.boss.BossPostureRules;
import dev.tatami.virtualcraft.boss.PostureBoss;
import dev.tatami.virtualcraft.boss.SnakeKingAttackRules;
import dev.tatami.virtualcraft.boss.SnakeAttackVolume;
import dev.tatami.virtualcraft.boss.SnakeAttackMotionRules;
import dev.tatami.virtualcraft.boss.SnakeDashRules;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
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
	public static final int SIGNAL_AMBER = 2;
	public static final int SIGNAL_BLUE = 3;

	private static final int[] ATTACK_DURATIONS = {
			0, 34, 68, 44, 48, 50, 62, 68, 72, 104, 54
	};
	private static final double COMBAT_HOLD_DISTANCE = 5.8;
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
	private double dashTravelSpeed;
	private int clientVisualAttackState = ATTACK_NONE;
	private double clientVisualAttackTick;
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
		// Dimensions follow the rendered cuboids instead of the boss-wide scale.
		// The hood remains targetable, while the body no longer has a box twice
		// as wide as its visible segment.
		headPart = new VirtualSnakeKingPart(this, "head", 2.35F, 1.7F, 1.25F);
		bodyPart = new VirtualSnakeKingPart(this, "body", 1.55F, 1.35F, 1.0F);
		middlePart = new VirtualSnakeKingPart(this, "middle", 1.4F, 1.15F, 0.9F);
		tailPart = new VirtualSnakeKingPart(this, "tail", 1.1F, 0.85F, 0.75F);
		tailMidPart = new VirtualSnakeKingPart(this, "tail_mid", 0.78F, 0.62F, 0.65F);
		tailTipPart = new VirtualSnakeKingPart(this, "tail_tip", 0.5F, 0.42F, 0.55F);
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
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
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
		builder.define(DATA_POSTURE, BossPostureRules.SNAKE_MAX_POSTURE);
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
	public void knockback(double strength, double x, double z) {
		// Boss positioning is controlled by its attack timeline and multipart body.
	}

	@Override
	public void push(double x, double y, double z) {
		// Ignore direct impulses from attacks, explosions, and entity collisions.
		// Scripted boss movement uses setDeltaMovement directly.
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
			Vec3 movement = player.getDeltaMovement();
			AABB playerBounds = player.getBoundingBox();
			AABB obstacleBounds = nearest.getBoundingBox();
			Vec3 separation = horizontalSeparation(playerBounds, obstacleBounds);
			if (!trySeparatePlayer(player, separation)) {
				trySeparatePlayer(player, alternateHorizontalSeparation(
						playerBounds, obstacleBounds, separation));
			}
			player.setDeltaMovement(movement);
			restoreStandingPoseAfterBossOverlap(player);
		}
	}

	private boolean trySeparatePlayer(Player player, Vec3 separation) {
		if (separation.horizontalDistanceSqr() < 1.0E-8) {
			return false;
		}
		AABB movedBounds = player.getBoundingBox().move(separation);
		if (!level().noCollision(player, movedBounds)) {
			return false;
		}
		player.setPos(player.getX() + separation.x, player.getY(),
				player.getZ() + separation.z);
		return true;
	}

	private static Vec3 horizontalSeparation(AABB player, AABB obstacle) {
		double negativeX = obstacle.minX - player.maxX - 0.002;
		double positiveX = obstacle.maxX - player.minX + 0.002;
		double x = Math.abs(negativeX) < Math.abs(positiveX) ? negativeX : positiveX;
		double negativeZ = obstacle.minZ - player.maxZ - 0.002;
		double positiveZ = obstacle.maxZ - player.minZ + 0.002;
		double z = Math.abs(negativeZ) < Math.abs(positiveZ) ? negativeZ : positiveZ;
		return Math.abs(x) <= Math.abs(z) ? new Vec3(x, 0.0, 0.0) : new Vec3(0.0, 0.0, z);
	}

	private static Vec3 alternateHorizontalSeparation(AABB player, AABB obstacle, Vec3 primary) {
		if (primary.x != 0.0) {
			double negativeZ = obstacle.minZ - player.maxZ - 0.002;
			double positiveZ = obstacle.maxZ - player.minZ + 0.002;
			return new Vec3(0.0, 0.0,
					Math.abs(negativeZ) < Math.abs(positiveZ) ? negativeZ : positiveZ);
		}
		double negativeX = obstacle.minX - player.maxX - 0.002;
		double positiveX = obstacle.maxX - player.minX + 0.002;
		return new Vec3(Math.abs(negativeX) < Math.abs(positiveX) ? negativeX : positiveX,
				0.0, 0.0);
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
			String animation = ATTACK_ANIMATIONS[attack];
			if (!animation.isEmpty()) {
				combat.triggerableAnim(animation, RawAnimation.begin()
						.thenPlay("animation.virtual_snake_king." + animation));
			}
		}
		combat.triggerableAnim("tail_sweep_left", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.tail_sweep_left"));
		combat.triggerableAnim("combo_ready", RawAnimation.begin()
				.thenPlay("animation.virtual_snake_king.combo_ready"));
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
					|| (attack == ATTACK_DOUBLE_HUNT
							&& SnakeHuntPattern.isComboOpening(getAttackTick()))
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
		} else {
			tickClientVisualAttackClock();
		}
		updatePartPositions();
	}

	private void tickClientVisualAttackClock() {
		int state = getAttackState();
		int syncedTick = Math.max(0, entityData.get(DATA_ATTACK_TICK));
		if (state == ATTACK_NONE) {
			clientVisualAttackState = ATTACK_NONE;
			clientVisualAttackTick = 0.0;
			return;
		}
		if (state != clientVisualAttackState) {
			clientVisualAttackState = state;
			clientVisualAttackTick = syncedTick;
			return;
		}
		clientVisualAttackTick += 1.0;
		double drift = syncedTick - clientVisualAttackTick;
		if (Math.abs(drift) > 1.5) {
			clientVisualAttackTick += Mth.clamp(drift, -0.2, 0.2);
		}
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
				if (SnakeHuntPattern.isComboOpening(getAttackTick())) {
					triggerAnim("combat", "combo_ready");
				} else {
					triggerComboAnimation(Math.min(getHuntPattern().segmentCount() - 1,
							getHuntPattern().comboSegmentAt(getAttackTick())));
				}
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
		entityData.set(DATA_SIGNAL_TYPE, progress < 0.62F ? SIGNAL_BLUE : SIGNAL_AMBER);
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
		movePart(headPart, headBase.add(forward.scale(1.35)).add(0.0, 1.15, 0.0));
		movePart(bodyPart, trailPoint(0, 0.5, 0.0).add(0.0, 0.72, 0.0));
		movePart(middlePart, trailPoint(1, 2.35, 0.24).add(0.0, 0.62, 0.0));
		movePart(tailPart, trailPoint(2, 4.15, 0.62).add(0.0, 0.48, 0.0));
		movePart(tailMidPart, trailPoint(3, 5.55, 1.0).add(0.0, 0.36, 0.0));
		movePart(tailTipPart, trailPoint(5, 6.75, 1.42).add(0.0, 0.24, 0.0));
		int tailSweepTick = activeTailSweepTick();
		if (tailSweepTick >= 0 && tailSweepTick <= SnakeHuntPattern.TAIL_HIT_TICK) {
			float direction = SnakeHuntPattern.tailSweepDirection(entityData.get(DATA_SWEEP_LEFT));
			float bodyAngle = getYRot() * Mth.DEG_TO_RAD + direction
					* (float)SnakeHuntPattern.tailBodySpinPhase(tailSweepTick);
			float baseAngle = bodyAngle + Mth.PI;
			SnakeHuntPattern.TailPose pose = SnakeHuntPattern.tailPose(tailSweepTick);
			float rootAngle = baseAngle + direction * (float)pose.root();
			float middleAngle = baseAngle + direction * (float)pose.middle();
			float tipAngle = baseAngle + direction * (float)pose.tip();
			movePart(headPart, sweepTailPoint(bodyAngle, 1.35, 1.15));
			movePart(bodyPart, sweepTailPoint(baseAngle, 0.5, 0.72));
			movePart(middlePart, sweepTailPoint(baseAngle, 2.35, 0.62));
			movePart(tailPart, sweepTailPoint(rootAngle, 4.45, 0.58));
			movePart(tailMidPart, sweepTailPoint(middleAngle, 5.7, 0.42));
			movePart(tailTipPart, sweepTailPoint(tipAngle, 6.85, 0.28));
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
		return isComboTailStage() ? getHuntPattern().comboLocalTickAt(getAttackTick()) : -1;
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
				entityData.set(DATA_POSTURE, BossPostureRules.SNAKE_MAX_POSTURE);
				posturePressure = 0.0F;
				nextAttackTick = tickCount + 20;
				triggerAnim("reaction", "recover");
			}
			return;
		}
		if (getTarget() == null && getPosture() < getMaxPosture()
				&& tickCount - lastCombatTick >= BossPostureRules.DISENGAGE_RESET_TICKS) {
			entityData.set(DATA_POSTURE, BossPostureRules.SNAKE_MAX_POSTURE);
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
			case ATTACK_CROWN_CHAIN -> tickCrownChain(tick);
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
			lockAttackTargetAndFace(target);
			play(ModSounds.SNAKE_KING_BITE_WINDUP.get(), 1.35F, 0.96F);
		}
		if (tick <= SnakeHuntPattern.BITE_LUNGE_TICK && (tick & 1) == 0) {
			lockAttackTargetAndFace(target);
		}
		if (tick == SnakeHuntPattern.BITE_LUNGE_TICK) {
			lungeLocked(1.25);
		}
		if (tick >= 7 && tick < SnakeHuntPattern.BITE_HIT_TICK && (tick & 1) == 1) {
			spawnConeCue(BossAttackDirection.MID, SnakeAttackVolume.BITE_RANGE,
					SnakeAttackVolume.BITE_HALF_ANGLE,
					tick / (double)SnakeHuntPattern.BITE_HIT_TICK, getY() + 1.0, 7);
		}
		if (tick >= SnakeHuntPattern.BITE_HIT_TICK
				&& tick < SnakeHuntPattern.BITE_HIT_TICK + SnakeHuntPattern.ACTIVE_WINDOW_TICKS) {
			damageBite(8.0F, 1.1);
			if (tick == SnakeHuntPattern.BITE_HIT_TICK) {
				spawnConeImpact(BossAttackDirection.MID, SnakeAttackVolume.BITE_RANGE,
						SnakeAttackVolume.BITE_HALF_ANGLE, getY() + 1.0, 11);
				spawnReleaseBurst(BossAttackDirection.MID, headPart.position(), 1.0);
				play(ModSounds.SNAKE_KING_BITE_HIT.get(), 1.5F, 0.94F);
			}
		}
	}

	private void tickDoubleHunt(int tick, LivingEntity target) {
		SnakeHuntPattern pattern = getHuntPattern();
		if (SnakeHuntPattern.isComboOpening(tick)) {
			if ((tick & 1) == 0) {
				lockAttackTargetAndFace(target);
			}
			return;
		}
		int segment = pattern.comboSegmentAt(tick);
		if (segment >= pattern.segmentCount()) {
			return;
		}
		int localTick = pattern.comboLocalTickAt(tick);
		SnakeHuntPattern.Move move = pattern.move(segment);
		if (comboAnimationStep != segment) {
			attackHits.clear();
			attackStrike = segment;
			lockAttackTargetAndFace(target);
			triggerComboAnimation(segment);
		}
		int trackingEnd = move == SnakeHuntPattern.Move.BITE
				? SnakeHuntPattern.COMBO_BITE_LUNGE_TICK : SnakeHuntPattern.TAIL_HIT_TICK - 4;
		if (localTick <= trackingEnd && (localTick & 1) == 0) {
			lockAttackTargetAndFace(target);
		}
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
		if (localTick == SnakeHuntPattern.COMBO_BITE_LUNGE_TICK) {
			lungeLocked(1.08 + segment * 0.08);
		}
		if (localTick >= 4 && localTick < SnakeHuntPattern.COMBO_BITE_HIT_TICK && (localTick & 1) == 0) {
			spawnConeCue(BossAttackDirection.MID, SnakeAttackVolume.BITE_RANGE,
					SnakeAttackVolume.BITE_HALF_ANGLE,
					localTick / (double)SnakeHuntPattern.COMBO_BITE_HIT_TICK, getY() + 1.0, 7);
		}
		if (localTick >= SnakeHuntPattern.COMBO_BITE_HIT_TICK
				&& localTick < SnakeHuntPattern.COMBO_BITE_HIT_TICK + SnakeHuntPattern.ACTIVE_WINDOW_TICKS) {
			damageBite(6.0F, 0.95);
			if (localTick == SnakeHuntPattern.COMBO_BITE_HIT_TICK) {
				spawnConeImpact(BossAttackDirection.MID, SnakeAttackVolume.BITE_RANGE,
						SnakeAttackVolume.BITE_HALF_ANGLE, getY() + 1.0, 10);
				spawnReleaseBurst(BossAttackDirection.MID, headPart.position(), 0.9);
				play(ModSounds.SNAKE_KING_BITE_HIT.get(), 1.35F, 0.9F + segment * 0.035F);
			}
		}
	}

	private void tickComboTail(int localTick) {
		if (localTick == 3) {
			play(ModSounds.SNAKE_KING_TAIL_WINDUP.get(), 1.4F, 0.9F);
		}
		if (localTick == SnakeHuntPattern.TAIL_HIT_TICK - 4) {
			play(ModSounds.SNAKE_KING_TAIL.get(), 1.5F, 0.94F);
		}
		if (localTick >= 9 && localTick < SnakeHuntPattern.TAIL_HIT_TICK && (localTick & 1) == 1) {
			spawnRingCue(BossAttackDirection.MID,
					Mth.lerp(localTick / (double)SnakeHuntPattern.TAIL_HIT_TICK,
							SnakeAttackVolume.TAIL_INNER_RADIUS,
							SnakeAttackVolume.TAIL_OUTER_RADIUS), getY() + 0.75, 10);
		}
		if (SnakeHuntPattern.isTailDamageActive(localTick)) {
			damageTailSweep(7.0F, 2.0);
			if (localTick == SnakeHuntPattern.TAIL_HIT_TICK) {
				spawnRingImpact(BossAttackDirection.MID, SnakeAttackVolume.TAIL_INNER_RADIUS,
						SnakeAttackVolume.TAIL_OUTER_RADIUS, getY() + 0.72, 18);
				spawnReleaseBurst(BossAttackDirection.MID, tailTipPart.position(), 1.15);
				play(ModSounds.SNAKE_KING_TAIL_HIT.get(), 1.55F, 0.9F);
			}
		}
	}

	private void tickTailSweep(int tick) {
		if (tick == 3) {
			play(ModSounds.SNAKE_KING_TAIL_WINDUP.get(), 1.5F, 0.88F);
		}
		if (tick == SnakeHuntPattern.TAIL_HIT_TICK - 4) {
			play(ModSounds.SNAKE_KING_TAIL.get(), 1.6F, 0.92F);
		}
		if (tick >= 9 && tick < SnakeHuntPattern.TAIL_HIT_TICK && (tick & 1) == 1) {
			spawnRingCue(BossAttackDirection.MID,
					Mth.lerp(tick / (double)SnakeHuntPattern.TAIL_HIT_TICK,
							SnakeAttackVolume.TAIL_INNER_RADIUS,
							SnakeAttackVolume.TAIL_OUTER_RADIUS), getY() + 0.75, 12);
		}
		if (SnakeHuntPattern.isTailDamageActive(tick)) {
			damageTailSweep(8.0F, 2.4);
			if (tick == SnakeHuntPattern.TAIL_HIT_TICK) {
				spawnRingImpact(BossAttackDirection.MID, SnakeAttackVolume.TAIL_INNER_RADIUS,
						SnakeAttackVolume.TAIL_OUTER_RADIUS, getY() + 0.72, 22);
				spawnReleaseBurst(BossAttackDirection.MID, tailTipPart.position(), 1.3);
				play(ModSounds.SNAKE_KING_TAIL_HIT.get(), 1.7F, 0.88F);
			}
		}
	}

	private void damageTailSweep(float damage, double knockback) {
		int sweepTick = activeTailSweepTick();
		if (sweepTick < 0) {
			return;
		}
		for (Player player : combatPlayers(SnakeAttackVolume.TAIL_OUTER_RADIUS + 1.5)) {
			if (intersectsTailSweep(player, sweepTick)
					&& resolveUniqueDirectedHit(player, damageSources().mobAttack(this), damage,
							knockback, false, BossAttackDirection.MID)) {
				attackStrike++;
			}
		}
	}

	private boolean intersectsTailSweep(Player player, double sweepTick) {
		Vec3 target = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		double radius = player.getBbWidth() * 0.5 + scaled(1.12);
		double radiusSquared = radius * radius;
		Vec3[] previous = tailSweepCollisionPoints(Math.max(0.0, sweepTick - 1.0));
		Vec3[] current = tailSweepCollisionPoints(sweepTick);
		for (int index = 0; index < current.length; index++) {
			if (distanceToSegmentSquared(target, previous[index], current[index]) <= radiusSquared) {
				return true;
			}
		}
		for (int index = 0; index + 1 < current.length; index++) {
			if (distanceToSegmentSquared(target, current[index], current[index + 1]) <= radiusSquared
					|| distanceToSegmentSquared(target, previous[index],
							previous[index + 1]) <= radiusSquared) {
				return true;
			}
		}
		return false;
	}

	private Vec3[] tailSweepCollisionPoints(double sweepTick) {
		float direction = SnakeHuntPattern.tailSweepDirection(isSweepLeft());
		float bodyAngle = getYRot() * Mth.DEG_TO_RAD + direction
				* (float)SnakeHuntPattern.tailBodySpinPhase(sweepTick);
		float baseAngle = bodyAngle + Mth.PI;
		SnakeHuntPattern.TailPose pose = SnakeHuntPattern.tailPose(sweepTick);
		float rootAngle = baseAngle + direction * (float)pose.root();
		float middleAngle = baseAngle + direction * (float)pose.middle();
		float tipAngle = baseAngle + direction * (float)pose.tip();
		return new Vec3[]{
				sweepTailPoint(baseAngle, 2.35, 0.62),
				sweepTailPoint(rootAngle, 4.45, 0.58),
				sweepTailPoint(middleAngle, 5.7, 0.42),
				sweepTailPoint(tipAngle, 6.85, 0.28)
		};
	}

	private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSquared = segment.lengthSqr();
		if (lengthSquared < 1.0E-8) {
			return point.distanceToSqr(start);
		}
		double amount = Mth.clamp(point.subtract(start).dot(segment) / lengthSquared, 0.0, 1.0);
		return point.distanceToSqr(start.add(segment.scale(amount)));
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
			if (facing >= minimumFacing
					&& resolveUniqueDirectedHit(player, damageSources().mobAttack(this), damage,
							knockback, corrosion, direction)) {
				hits++;
			}
		}
		return hits;
	}

	private void tickDataFangs(int tick, LivingEntity target) {
		if (tick == SnakeKingAttackRules.SPECIAL_SIGNAL_START) {
			lockAttackTargetAndFace(target);
			play(ModSounds.SNAKE_KING_POISON_HEAD_CHARGE.get(), 1.45F, 0.96F);
		}
		if (tick == SnakeKingAttackRules.SPECIAL_ANIMATION_START) {
			triggerAnim("combat", ATTACK_ANIMATIONS[ATTACK_DATA_FANGS]);
		}
		if (tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeKingAttackRules.HIGH_POISON_HIT_TICK - 6 && (tick & 1) == 0) {
			lockAttackTargetAndFace(target);
		}
		if (tick == 24) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.3F, 1.12F);
		}
		if (tick == SnakeKingAttackRules.HIGH_POISON_HIT_TICK - 5) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.45F, 1.08F);
		}
		if (tick == SnakeKingAttackRules.HIGH_POISON_HIT_TICK) {
			spawnBeamImpact(BossAttackDirection.HIGH, SnakeAttackVolume.HIGH_POISON_RANGE,
					SnakeAttackVolume.HIGH_POISON_HALF_WIDTH, getLockedTargetY(), 10);
			spawnReleaseBurst(BossAttackDirection.HIGH, headPart.position(), 1.15);
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.5F, 1.02F);
		}
		if (tick >= SnakeKingAttackRules.HIGH_POISON_WARNING_START
				&& tick <= SnakeKingAttackRules.HIGH_POISON_HIT_TICK && tick % 3 == 0) {
			spawnBeamCue(BossAttackDirection.HIGH, SnakeAttackVolume.HIGH_POISON_RANGE,
					SnakeAttackVolume.HIGH_POISON_HALF_WIDTH, getLockedTargetY(), 7);
		}
		if (tick >= SnakeKingAttackRules.HIGH_POISON_HIT_TICK
				&& tick <= SnakeKingAttackRules.HIGH_POISON_HIT_TICK + 2) {
			attackStrike += damageHeadPoisonBeam(target, 10.0F, 60);
		}
		if (tick == SnakeKingAttackRules.HIGH_POISON_HIT_TICK + 2) {
			resolveImmediateHighAttack(attackStrike);
			play(ModSounds.SNAKE_KING_POISON_HEAD_RELEASE.get(), 1.55F, 0.94F);
		}
	}

	private void tickRuneSigil(int tick, LivingEntity target) {
		if (tick == SnakeKingAttackRules.SPECIAL_SIGNAL_START) {
			lockAttackTargetAndFace(target);
			play(ModSounds.SNAKE_KING_POISON_GROUND_CHARGE.get(), 1.45F, 0.9F);
		}
		if (tick == SnakeKingAttackRules.SPECIAL_ANIMATION_START) {
			triggerAnim("combat", ATTACK_ANIMATIONS[ATTACK_RUNE_SIGIL]);
		}
		if (tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeKingAttackRules.GROUND_POISON_HIT_TICK - 6 && (tick & 1) == 0) {
			lockAttackTargetAndFace(target);
		}
		if (tick == 24) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.3F, 0.84F);
		}
		if (tick == SnakeKingAttackRules.GROUND_POISON_HIT_TICK - 5) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.4F, 0.88F);
		}
		if (tick >= SnakeKingAttackRules.GROUND_POISON_WARNING_START
				&& tick < SnakeKingAttackRules.GROUND_POISON_HIT_TICK && tick % 3 == 0) {
			spawnConeCue(BossAttackDirection.LOW, SnakeAttackVolume.GROUND_POISON_RANGE,
					SnakeAttackVolume.GROUND_POISON_HALF_ANGLE,
					tick / (double)SnakeKingAttackRules.GROUND_POISON_HIT_TICK, getY() + 0.12, 11);
		}
		if (tick >= SnakeKingAttackRules.GROUND_POISON_HIT_TICK
				&& tick <= SnakeKingAttackRules.GROUND_POISON_HIT_TICK + 2) {
			damageGroundPoisonFan(SnakeAttackVolume.GROUND_POISON_RANGE,
					SnakeAttackVolume.GROUND_POISON_HALF_ANGLE, 8.0F, 80);
		}
		if (tick == SnakeKingAttackRules.GROUND_POISON_HIT_TICK) {
			spawnConeImpact(BossAttackDirection.LOW, SnakeAttackVolume.GROUND_POISON_RANGE,
					SnakeAttackVolume.GROUND_POISON_HALF_ANGLE, getY() + 0.12, 16);
			spawnReleaseBurst(BossAttackDirection.LOW, headPart.position(), 1.2);
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.55F, 0.86F);
			play(ModSounds.SNAKE_KING_POISON_GROUND_RELEASE.get(), 1.55F, 0.92F);
		}
	}

	private void tickCrownChain(int tick) {
		if (tick == 9) {
			play(ModSounds.SNAKE_KING_CAST.get(), 1.55F, 0.94F);
		}
		if (tick == 18) {
			play(ModSounds.SNAKE_KING_CAST_PULSE.get(), 1.45F, 0.78F);
		}
		if (tick == SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK - 4) {
			play(ModSounds.SNAKE_KING_CAST_LOCK.get(), 1.55F, 0.94F);
		}
		if (tick == SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK) {
			play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.7F, 0.92F);
		}
		if (tick >= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK
				&& tick <= SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK + 2
				&& level() instanceof ServerLevel) {
			attackStrike += damagePlayersInRadius(18.0, 5.0, 18.0F, 0.8, false,
					BossAttackDirection.HIGH);
		}
		if (tick == SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK + 2) {
			play(ModSounds.SNAKE_KING_CHAIN.get(), 1.75F, 0.9F);
			resolveImmediateHighAttack(attackStrike);
		}
	}

	private int damageHeadPoisonBeam(LivingEntity target, float damage, int poisonTicks) {
		SnakeAttackVolume volume = lockedBeam(SnakeAttackVolume.HIGH_POISON_RANGE,
				SnakeAttackVolume.HIGH_POISON_HALF_WIDTH,
				getLockedTargetY() - getY() - 0.55, getLockedTargetY() - getY() + 0.55);
		int hits = 0;
		for (Player player : combatPlayers(13.0)) {
			AABB bounds = player.getBoundingBox();
			if (!volume.intersects(bounds.minX, bounds.minY, bounds.minZ,
					bounds.maxX, bounds.maxY, bounds.maxZ)) {
				continue;
			}
			if (resolveUniquePoisonHit(player, damage, 0.65, BossAttackDirection.HIGH, poisonTicks)) {
				hits++;
			}
		}
		return hits;
	}

	private int damageGroundPoisonFan(double range, double halfAngle, float damage, int poisonTicks) {
		SnakeAttackVolume volume = lockedCone(range, halfAngle, -0.6, 2.5);
		int hits = 0;
		for (Player player : combatPlayers(range + 1.0)) {
			AABB bounds = player.getBoundingBox();
			if (!volume.intersects(bounds.minX, bounds.minY, bounds.minZ,
					bounds.maxX, bounds.maxY, bounds.maxZ)) {
				continue;
			}
			if (resolveUniquePoisonHit(player, damage, 0.9, BossAttackDirection.LOW, poisonTicks)) {
				hits++;
			}
		}
		return hits;
	}

	private boolean resolveUniquePoisonHit(Player player, float damage, double knockback,
			BossAttackDirection direction, int poisonTicks) {
		boolean hit = resolveUniqueDirectedHit(player, damageSources().mobAttack(this), damage,
				knockback, false, direction);
		if (hit) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.POISON, poisonTicks, 0), this);
		}
		return hit;
	}

	private void tickVoidDash(int tick, LivingEntity target) {
		if (tick == SnakeKingAttackRules.SPECIAL_ANIMATION_START) {
			triggerAnim("combat", ATTACK_ANIMATIONS[ATTACK_VOID_DASH]);
		}
		boolean firstTrack = tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeDashRules.FIRST_START - 6;
		boolean secondTrack = tick >= SnakeDashRules.FIRST_START + SnakeDashRules.ACTIVE_TICKS
				&& tick <= SnakeDashRules.SECOND_START - 6;
		if ((firstTrack || secondTrack) && (tick & 1) == 0) {
			lockAttackTargetAndFace(target);
			dashTravelSpeed = SnakeDashRules.speedForDistance(horizontalDistanceTo(target));
		}
		if (SnakeDashRules.isPassStart(tick)) {
			if (tick == SnakeDashRules.SECOND_START) {
				attackHits.clear();
			}
			spawnBeamImpact(BossAttackDirection.LOW, SnakeAttackVolume.DASH_RANGE,
					SnakeAttackVolume.DASH_HALF_WIDTH, getY() + 1.0, 10);
			spawnReleaseBurst(BossAttackDirection.LOW,
					position().add(lockedForward().scale(1.2)).add(0.0, 0.9, 0.0), 1.1);
			play(ModSounds.SNAKE_KING_DASH.get(), 1.5F,
					tick == SnakeDashRules.FIRST_START ? 0.98F : 0.9F);
		}
		if ((tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeDashRules.FIRST_START && (tick & 1) == 0)
				|| (tick >= SnakeDashRules.SECOND_START - 12 && tick <= SnakeDashRules.SECOND_START
						&& (tick & 1) == 0)) {
			spawnBeamCue(BossAttackDirection.LOW, SnakeAttackVolume.DASH_RANGE,
					SnakeAttackVolume.DASH_HALF_WIDTH, getY() + 1.0, 8);
		}
		if (SnakeDashRules.isActive(tick)) {
			if (dashTravelSpeed <= 0.0) {
				dashTravelSpeed = SnakeDashRules.speedForDistance(horizontalDistanceTo(target));
			}
			Vec3 dash = lockedForward().scale(dashTravelSpeed);
			setDeltaMovement(dash.x, getDeltaMovement().y, dash.z);
			damageVolume(lockedBeam(SnakeAttackVolume.DASH_RANGE, SnakeAttackVolume.DASH_HALF_WIDTH,
					-scaled(0.5), scaled(2.5)),
					9.0F, 1.4, false, BossAttackDirection.LOW);
		}
		if (SnakeDashRules.isPassEnd(tick)) {
			Vec3 movement = getDeltaMovement();
			setDeltaMovement(0.0, movement.y, 0.0);
		}
	}

	private double horizontalDistanceTo(Entity target) {
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		return Math.sqrt(dx * dx + dz * dz);
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
		if (tick >= SnakeKingAttackRules.COIL_RELEASE_START
				&& tick <= SnakeKingAttackRules.COIL_RELEASE_END) {
			damageExpandingCoilRing(tick, 16.0F);
			if (tick == SnakeKingAttackRules.COIL_RELEASE_START) {
				play(ModSounds.SNAKE_KING_CAST_RELEASE.get(), 1.65F, 0.76F);
			}
			if (tick == SnakeKingAttackRules.COIL_RELEASE_END) {
				play(ModSounds.SNAKE_KING_IMPACT.get(), 1.8F, 0.94F);
			}
		}
	}

	private void damageExpandingCoilRing(int attackTick, float damage) {
		double radius = SnakeKingAttackRules.coilRadius(attackTick);
		SnakeAttackVolume volume = SnakeAttackVolume.ring(getX(), getY(), getZ(),
				radius, 1.05, -0.6, 2.4);
		damageVolume(volume, damage, 2.2, true, BossAttackDirection.LOW);
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
		if (tick == SnakeKingAttackRules.SPECIAL_SIGNAL_START) {
			lockAttackTargetAndFace(target);
		}
		if (tick == SnakeKingAttackRules.SPECIAL_ANIMATION_START) {
			triggerAnim("combat", ATTACK_ANIMATIONS[ATTACK_OVERHEAD_FANG]);
		}
		if (tick >= SnakeKingAttackRules.SPECIAL_ANIMATION_START
				&& tick <= SnakeKingAttackRules.OVERHEAD_HIT_TICK - 6 && (tick & 1) == 0) {
			lockAttackTargetAndFace(target);
		}
		if (tick == 16) {
			play(ModSounds.SNAKE_KING_ROAR.get(), 1.35F, 1.0F);
		}
		if (tick == SnakeKingAttackRules.OVERHEAD_HIT_TICK - 3) {
			lungeLocked(0.9);
		}
		if (tick >= SnakeKingAttackRules.OVERHEAD_WARNING_START
				&& tick <= SnakeKingAttackRules.OVERHEAD_HIT_TICK && (tick & 1) == 0) {
			spawnConeCue(BossAttackDirection.HIGH, SnakeAttackVolume.OVERHEAD_RANGE,
					SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
					tick / (double)SnakeKingAttackRules.OVERHEAD_HIT_TICK,
					Math.max(getY() + 1.4, getLockedTargetY()), 9);
		}
		if (tick >= SnakeKingAttackRules.OVERHEAD_HIT_TICK
				&& tick <= SnakeKingAttackRules.OVERHEAD_HIT_TICK + 2) {
			attackStrike += damageVolume(lockedCone(SnakeAttackVolume.OVERHEAD_RANGE,
					SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
					-scaled(0.4), scaled(2.8)), 18.0F, 1.8, true, BossAttackDirection.HIGH);
		}
		if (tick == SnakeKingAttackRules.OVERHEAD_HIT_TICK) {
			spawnConeImpact(BossAttackDirection.HIGH, SnakeAttackVolume.OVERHEAD_RANGE,
					SnakeAttackVolume.OVERHEAD_HALF_ANGLE,
					Math.max(getY() + 1.4, getLockedTargetY()), 14);
			spawnReleaseBurst(BossAttackDirection.HIGH, headPart.position(), 1.3);
		}
		if (tick == SnakeKingAttackRules.OVERHEAD_HIT_TICK + 2) {
			resolveImmediateHighAttack(attackStrike);
			play(ModSounds.SNAKE_KING_IMPACT.get(), 1.6F, 0.92F);
		}
	}

	private void lungeLocked(double speed) {
		Vec3 direction = lockedForward().scale(speed);
		setDeltaMovement(direction.x, 0.0, direction.z);
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
			if (resolveUniqueDirectedHit(player, damageSources().mobAttack(this), amount,
					knockback, corrosion, direction)) {
				hits++;
			}
		}
		return hits;
	}

	private int damageVolume(SnakeAttackVolume volume, float amount, double knockback, boolean corrosion,
			BossAttackDirection direction) {
		int hits = 0;
		for (Player player : combatPlayers(volume.range() + 2.0)) {
			AABB bounds = player.getBoundingBox();
			if (!volume.intersects(bounds.minX, bounds.minY, bounds.minZ,
					bounds.maxX, bounds.maxY, bounds.maxZ)) {
				continue;
			}
			if (resolveUniqueDirectedHit(player, damageSources().mobAttack(this), amount,
					knockback, corrosion, direction)) {
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

	private void spawnConeCue(BossAttackDirection direction, double range, double halfAngle,
			double progress, double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 forward = lockedForward();
		double centerAngle = Math.atan2(forward.z, forward.x);
		double radius = range * Mth.clamp(0.24 + progress * 0.76, 0.24, 1.0);
		ParticleOptions cue = cueParticle(direction);
		for (int i = 0; i < samples; i++) {
			double ratio = samples <= 1 ? 0.5 : i / (double)(samples - 1);
			double angle = centerAngle + Mth.lerp(ratio, -halfAngle, halfAngle);
			double px = getX() + Math.cos(angle) * radius;
			double pz = getZ() + Math.sin(angle) * radius;
			serverLevel.sendParticles(i == samples / 2 ? ModParticleTypes.SNAKE_DATA_RUNE.get() : cue,
					px, y + (i & 1) * 0.035, pz, 1, 0.018, 0.018, 0.018, 0.0);
		}
	}

	private void spawnRingCue(BossAttackDirection direction, double radius, double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ParticleOptions cue = cueParticle(direction);
		double phase = getAttackTick() * 0.11;
		for (int i = 0; i < samples; i++) {
			double angle = phase + Mth.TWO_PI * i / samples;
			ParticleOptions particle = direction == BossAttackDirection.MID && i % 3 == 0
					? ModParticleTypes.SNAKE_ARC.get() : cue;
			serverLevel.sendParticles(particle, getX() + Math.cos(angle) * radius,
					y + (i & 1) * 0.06, getZ() + Math.sin(angle) * radius,
					1, 0.025, 0.025, 0.025, 0.0);
		}
	}

	private void spawnBeamCue(BossAttackDirection direction, double range, double halfWidth,
			double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 forward = lockedForward();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		ParticleOptions cue = cueParticle(direction);
		for (int i = 1; i <= samples; i++) {
			double distance = range * i / samples;
			Vec3 center = position().add(forward.scale(distance));
			for (int rail = -1; rail <= 1; rail += 2) {
				Vec3 point = center.add(side.scale(halfWidth * rail));
				ParticleOptions particle = i % 3 == 0 ? ModParticleTypes.SNAKE_ARC.get() : cue;
				serverLevel.sendParticles(particle, point.x, y + (i & 1) * 0.045, point.z,
						1, 0.018, 0.018, 0.018, 0.0);
			}
		}
	}

	private void spawnConeImpact(BossAttackDirection direction, double range, double halfAngle,
			double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 forward = lockedForward();
		double centerAngle = Math.atan2(forward.z, forward.x);
		ParticleOptions cue = cueParticle(direction);
		for (int layer = 0; layer < 2; layer++) {
			double radius = range * (layer == 0 ? 0.62 : 1.0);
			for (int i = 0; i < samples; i++) {
				double ratio = samples <= 1 ? 0.5 : i / (double)(samples - 1);
				double angle = centerAngle + Mth.lerp(ratio, -halfAngle, halfAngle);
				ParticleOptions particle = i % 4 == 0 ? ModParticleTypes.SNAKE_ARC.get()
						: i % 3 == 0 ? ModParticleTypes.SNAKE_SHARD.get() : cue;
				serverLevel.sendParticles(particle, getX() + Math.cos(angle) * radius,
						y + layer * 0.16, getZ() + Math.sin(angle) * radius,
						1, 0.055, 0.07, 0.055, 0.035);
			}
		}
		Vec3 center = position().add(forward.scale(range * 0.82));
		serverLevel.sendParticles(ModParticleTypes.SNAKE_DATA_RUNE.get(), center.x, y + 0.12, center.z,
				4, 0.28, 0.18, 0.28, 0.025);
	}

	private void spawnRingImpact(BossAttackDirection direction, double innerRadius, double outerRadius,
			double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ParticleOptions cue = cueParticle(direction);
		double phase = getAttackTick() * 0.17;
		for (int i = 0; i < samples; i++) {
			double angle = phase + Mth.TWO_PI * i / samples;
			double radius = (i & 1) == 0 ? innerRadius : outerRadius;
			ParticleOptions particle = i % 4 == 0 ? ModParticleTypes.SNAKE_ARC.get()
					: i % 3 == 0 ? ModParticleTypes.SNAKE_SHARD.get() : cue;
			serverLevel.sendParticles(particle, getX() + Math.cos(angle) * radius,
					y + (i & 1) * 0.14, getZ() + Math.sin(angle) * radius,
					1, 0.065, 0.08, 0.065, 0.045);
		}
		serverLevel.sendParticles(ModParticleTypes.SNAKE_DATA_RUNE.get(), getX(), y + 0.12, getZ(),
				6, Math.max(0.4, innerRadius * 0.25), 0.18, Math.max(0.4, innerRadius * 0.25), 0.03);
	}

	private void spawnBeamImpact(BossAttackDirection direction, double range, double halfWidth,
			double y, int samples) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		Vec3 forward = lockedForward();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		ParticleOptions cue = cueParticle(direction);
		for (int i = 1; i <= samples; i++) {
			double distance = range * i / samples;
			Vec3 center = position().add(forward.scale(distance));
			for (int rail = -1; rail <= 1; rail++) {
				Vec3 point = center.add(side.scale(halfWidth * rail));
				ParticleOptions particle = rail == 0 ? ModParticleTypes.SNAKE_TRAIL.get()
						: i % 3 == 0 ? ModParticleTypes.SNAKE_ARC.get() : cue;
				serverLevel.sendParticles(particle, point.x, y + rail * 0.06, point.z,
						1, 0.04, 0.055, 0.04, 0.04);
			}
		}
	}

	private void spawnReleaseBurst(BossAttackDirection direction, Vec3 origin, double scale) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		double spread = 0.34 * scale;
		serverLevel.sendParticles(cueParticle(direction), origin.x, origin.y, origin.z,
				8, spread, spread * 0.7, spread, 0.045 * scale);
		serverLevel.sendParticles(ModParticleTypes.SNAKE_ARC.get(), origin.x, origin.y, origin.z,
				6, spread * 0.8, spread * 0.55, spread * 0.8, 0.035 * scale);
		serverLevel.sendParticles(ModParticleTypes.SNAKE_SHARD.get(), origin.x, origin.y, origin.z,
				5, spread * 0.65, spread * 0.5, spread * 0.65, 0.075 * scale);
	}

	private static ParticleOptions cueParticle(BossAttackDirection direction) {
		return switch (direction) {
			case HIGH -> ModParticleTypes.SNAKE_HIGH_WARNING.get();
			case MID -> ModParticleTypes.SNAKE_MID_WARNING.get();
			case LOW -> ModParticleTypes.SNAKE_LOW_WARNING.get();
		};
	}

	public void hurtTarget(Player player, float amount, double knockback, boolean corrosion) {
		resolveDirectedHit(player, damageSources().mobAttack(this), amount, knockback, corrosion,
				getAttackDirection());
	}

	public boolean resolveDirectedHit(Player player, DamageSource source, float amount, double knockback,
			boolean corrosion, BossAttackDirection direction) {
		return resolveDirectedHitResult(player, source, amount, knockback, corrosion, direction)
				== DirectedHitResult.DAMAGED;
	}

	private boolean resolveUniqueDirectedHit(Player player, DamageSource source, float amount,
			double knockback, boolean corrosion, BossAttackDirection direction) {
		UUID playerId = player.getUUID();
		if (attackHits.contains(playerId)) {
			return false;
		}
		DirectedHitResult result = resolveDirectedHitResult(player, source, amount, knockback,
				corrosion, direction);
		if (result != DirectedHitResult.REJECTED) {
			attackHits.add(playerId);
		}
		return result == DirectedHitResult.DAMAGED;
	}

	private DirectedHitResult resolveDirectedHitResult(Player player, DamageSource source, float amount,
			double knockback, boolean corrosion, BossAttackDirection direction) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || isStaggered()) {
			return DirectedHitResult.REJECTED;
		}
		if (direction == BossAttackDirection.HIGH && player.isShiftKeyDown()) {
			registerSuccessfulDodge(activeAttackInstance, player, direction, false);
			return DirectedHitResult.AVOIDED;
		}
		if (direction == BossAttackDirection.LOW && isValidLowDodge(player)) {
			registerSuccessfulDodge(activeAttackInstance, player, direction, true);
			return DirectedHitResult.AVOIDED;
		}
		boolean weaponParry = direction == BossAttackDirection.MID
				&& dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.isParrying(player, source);
		boolean preciseParry = direction == BossAttackDirection.MID
				&& (weaponParry || isPreciseParry(player, source)
						|| isChainedComboBiteParry(player, source));
		if (preciseParry) {
			if (getAttackState() == ATTACK_DOUBLE_HUNT) {
				comboParrySegments.put(player.getUUID(), getComboSegment());
			}
			int postureBefore = getPosture();
			int postureDamage = Math.round(BossPostureRules.preciseParryDamage());
			damagePosture(postureDamage, player);
			if (weaponParry) {
				dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.completeParry(player, this);
			} else {
				playParryFeedback(player, postureBefore - getPosture());
			}
			return DirectedHitResult.AVOIDED;
		}
		boolean weaponBlock = direction == BossAttackDirection.MID
				&& dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.isBlocking(player, source);
		if (weaponBlock) {
			boolean delayedTailPunish = isDelayedComboTailPunish(player, source);
			dev.tatami.virtualcraft.gameplay.VirtualWeaponParryHandler.failParry(player, this);
			if (!delayedTailPunish) {
				return DirectedHitResult.AVOIDED;
			}
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
				return DirectedHitResult.AVOIDED;
			}
		}
		if (direction != BossAttackDirection.MID && isUsingShield(player)) {
			// HIGH and LOW are movement checks, never shield checks. Stop this use
			// without applying the MID shield-break cooldown.
			player.stopUsingItem();
		}
		if (!player.hurt(source, configuredDamage)) {
			return DirectedHitResult.REJECTED;
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
		return DirectedHitResult.DAMAGED;
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
				&& player.getTicksUsingItem() < SnakeKingAttackRules.MID_PARRY_WINDOW_TICKS
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
				|| !isUsingMidGuard(player) || !isFacingAttack(player, source)
				|| player.getTicksUsingItem() < SnakeKingAttackRules.MID_PARRY_WINDOW_TICKS) {
			return false;
		}
		int segment = getComboSegment();
		return segment > 0 && comboParrySegments.getOrDefault(player.getUUID(), -2) == segment - 1;
	}

	private static boolean isUsingShield(Player player) {
		return player.isUsingItem() && player.getUseItem().getItem() instanceof ShieldItem;
	}

	private static boolean isUsingMidGuard(Player player) {
		return player.isUsingItem() && (player.getUseItem().getItem() instanceof ShieldItem
				|| player.getUseItem().getItem()
						instanceof dev.tatami.virtualcraft.item.VirtualSwordItem);
	}

	private boolean isFacingAttack(Player player, DamageSource source) {
		Vec3 sourcePosition = source.getSourcePosition();
		if (sourcePosition == null) {
			sourcePosition = position();
		}
		Vec3 towardSource = sourcePosition.subtract(player.getEyePosition()).multiply(1.0, 0.0, 1.0);
		Vec3 view = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		return towardSource.lengthSqr() > 1.0E-5 && view.lengthSqr() > 1.0E-5
				&& towardSource.normalize().dot(view.normalize()) >= 0.25;
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
					SoundSource.HOSTILE, 1.2F, 0.72F);
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

	private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level().playSound(null, blockPosition(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	private void updateModelSignal(int attack, int tick) {
		int previousSignal = entityData.get(DATA_SIGNAL_TYPE);
		int signal = SIGNAL_NONE;
		float strength = 0.0F;
		if (attack == ATTACK_DATA_FANGS) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START,
					SnakeKingAttackRules.HIGH_POISON_HIT_TICK,
					SnakeKingAttackRules.HIGH_POISON_HIT_TICK + 3);
			signal = strength > 0.0F ? SIGNAL_BLUE : SIGNAL_NONE;
		} else if (attack == ATTACK_OVERHEAD_FANG) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START,
					SnakeKingAttackRules.OVERHEAD_HIT_TICK,
					SnakeKingAttackRules.OVERHEAD_HIT_TICK + 3);
			signal = strength > 0.0F ? SIGNAL_BLUE : SIGNAL_NONE;
		} else if (attack == ATTACK_CROWN_CHAIN) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START,
					SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK,
					SnakeKingAttackRules.CROWN_CHAIN_HIT_TICK + 3);
			signal = strength > 0.0F ? SIGNAL_BLUE : SIGNAL_NONE;
		} else if (attack == ATTACK_RUNE_SIGIL) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START,
					SnakeKingAttackRules.GROUND_POISON_HIT_TICK,
					SnakeKingAttackRules.GROUND_POISON_HIT_TICK + 3);
			signal = strength > 0.0F ? SIGNAL_AMBER : SIGNAL_NONE;
		} else if (attack == ATTACK_VOID_DASH) {
			int impact = tick < (SnakeDashRules.FIRST_START + SnakeDashRules.SECOND_START) / 2
					? SnakeDashRules.FIRST_START : SnakeDashRules.SECOND_START;
			int warningStart = impact == SnakeDashRules.FIRST_START
					? SnakeKingAttackRules.SPECIAL_SIGNAL_START : impact - 12;
			strength = signalEnvelope(tick, warningStart, impact, impact + 3);
			signal = strength > 0.0F ? SIGNAL_AMBER : SIGNAL_NONE;
		} else if (attack == ATTACK_CORROSIVE_COIL) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START, 52, 55);
			signal = strength > 0.0F ? SIGNAL_AMBER : SIGNAL_NONE;
		} else if (attack == ATTACK_CROWN_JUDGMENT) {
			strength = signalEnvelope(tick, SnakeKingAttackRules.SPECIAL_SIGNAL_START, 62, 65);
			signal = strength > 0.0F ? SIGNAL_AMBER : SIGNAL_NONE;
		}
		if (!level().isClientSide && signal != SIGNAL_NONE && previousSignal != signal) {
			announceDirectionCue(signal);
		}
		entityData.set(DATA_SIGNAL_TYPE, signal);
		entityData.set(DATA_SIGNAL_STRENGTH, strength);
	}

	private void announceDirectionCue(int signal) {
		int feedback = signal == SIGNAL_BLUE ? SnakeCombatFeedbackPayload.HIGH_CUE
				: signal == SIGNAL_AMBER ? SnakeCombatFeedbackPayload.LOW_CUE
				: SnakeCombatFeedbackPayload.MID_CUE;
		for (Player player : combatPlayers(32.0)) {
			if (player instanceof ServerPlayer serverPlayer) {
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
			return 0.40F + progress * 0.60F;
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
		directionReadyTicks[direction.ordinal()] = state == ATTACK_RUNE_SIGIL ? tickCount + 100
				: switch (direction) {
			case HIGH -> tickCount + 160 + random.nextInt(81);
			case MID -> tickCount + attackDuration(state) + 40 + random.nextInt(17);
			case LOW -> tickCount + attackDuration(state) + 46 + random.nextInt(21);
		};
		if (!level().isClientSide && !usesDelayedSpecialAnimation(state)) {
			if (state == ATTACK_DOUBLE_HUNT) {
				triggerAnim("combat", "combo_ready");
			} else {
				String animation = state == ATTACK_TAIL_SWEEP && entityData.get(DATA_SWEEP_LEFT)
						? "tail_sweep_left"
						: ATTACK_ANIMATIONS[state];
				triggerAnim("combat", animation);
			}
		}
	}

	private static boolean usesDelayedSpecialAnimation(int state) {
		return state == ATTACK_DATA_FANGS || state == ATTACK_RUNE_SIGIL
				|| state == ATTACK_VOID_DASH || state == ATTACK_OVERHEAD_FANG;
	}

	private void lockAttackTarget(LivingEntity target) {
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		entityData.set(DATA_LOCKED_ATTACK_YAW, (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
		entityData.set(DATA_LOCKED_TARGET_Y, (float)target.getEyeY());
	}

	private void lockAttackTargetAndFace(LivingEntity target) {
		lockAttackTarget(target);
		float lockedYaw = getLockedAttackYaw();
		setYRot(lockedYaw);
		yBodyRot = lockedYaw;
		yHeadRot = lockedYaw;
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
		dashTravelSpeed = 0.0;
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

	public double getVisualAttackTick(float partialTick) {
		if (!level().isClientSide) {
			return getAttackTick() + partialTick;
		}
		return Math.max(0.0, clientVisualAttackTick + partialTick);
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
		return Math.min(getHuntPattern().segmentCount() - 1,
				getHuntPattern().comboSegmentAt(getAttackTick()));
	}

	public boolean isComboActiveSegment() {
		return getAttackState() == ATTACK_DOUBLE_HUNT
				&& !SnakeHuntPattern.isComboOpening(getAttackTick())
				&& getHuntPattern().comboSegmentAt(getAttackTick()) < getHuntPattern().segmentCount();
	}

	public int getComboLocalTick() {
		return getHuntPattern().comboLocalTickAt(getAttackTick());
	}

	public boolean isComboTailStage() {
		return getAttackState() == ATTACK_DOUBLE_HUNT
				&& !SnakeHuntPattern.isComboOpening(getAttackTick())
				&& getHuntPattern().comboSegmentAt(getAttackTick()) < getHuntPattern().segmentCount()
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
		return BossPostureRules.SNAKE_MAX_POSTURE;
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
		Vec3 movementBeforeHit = getDeltaMovement();
		boolean hurt = super.hurt(source, appliedDamage);
		if (hurt) {
			setDeltaMovement(movementBeforeHit);
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
		entityData.set(DATA_SIGNAL_TYPE, SIGNAL_AMBER);
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
		tag.putInt("PostureCapacity", BossPostureRules.SNAKE_MAX_POSTURE);
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
		queuedAttack = sanitizeQueuedAttack(tag.getInt("QueuedAttack"));
		int savedPostureCapacity = tag.contains("PostureCapacity")
				? Math.max(1, tag.getInt("PostureCapacity"))
				: BossPostureRules.MAX_POSTURE;
		int savedPosture = tag.contains("Posture")
				? Mth.clamp(tag.getInt("Posture"), 0, savedPostureCapacity)
				: savedPostureCapacity;
		entityData.set(DATA_POSTURE, Mth.clamp(Math.round(savedPosture
				* (BossPostureRules.SNAKE_MAX_POSTURE / (float)savedPostureCapacity)),
				0, BossPostureRules.SNAKE_MAX_POSTURE));
		entityData.set(DATA_STAGGER_TICKS, Math.max(0, tag.getInt("StaggerTicks")));
		posturePressure = Mth.clamp(tag.getFloat("PosturePressure"), 0.0F, 0.999F);
		damageBucket = Math.max(0.0F, tag.getFloat("DamageBucket"));
		encounterPlayerCount = Math.max(1, tag.getInt("EncounterPlayers"));
		activeAttackInstance = tag.hasUUID("AttackInstance") ? tag.getUUID("AttackInstance") : null;
		activeFangVolley = tag.hasUUID("FangVolley") ? tag.getUUID("FangVolley") : null;
		pendingFangProjectiles = Math.max(0, tag.getInt("PendingFangs"));
		fangVolleyHitPlayer = tag.getBoolean("FangVolleyHit");
		// Triggered GeckoLib animations cannot resume from an arbitrary saved frame.
		// Cancel an in-flight attack so visuals and server damage never desynchronise after reload.
		queuedAttack = ATTACK_NONE;
		activeAttackInstance = null;
		activeFangVolley = null;
		pendingFangProjectiles = 0;
		fangVolleyHitPlayer = false;
		entityData.set(DATA_ATTACK_STATE, ATTACK_NONE);
		entityData.set(DATA_ATTACK_DIRECTION, BossAttackDirection.MID.ordinal());
		entityData.set(DATA_ATTACK_START, tickCount);
		entityData.set(DATA_ATTACK_TICK, 0);
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

	private static int sanitizeQueuedAttack(int attack) {
		int sanitized = sanitizeAttack(attack);
		return sanitized == ATTACK_BITE || sanitized == ATTACK_TAIL_SWEEP
				? ATTACK_DOUBLE_HUNT : sanitized;
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

	private enum DirectedHitResult {
		DAMAGED,
		AVOIDED,
		REJECTED
	}
}
