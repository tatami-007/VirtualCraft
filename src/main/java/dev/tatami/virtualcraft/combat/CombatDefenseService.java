package dev.tatami.virtualcraft.combat;

import dev.tatami.virtualcraft.combat.DefenseState;
import dev.tatami.virtualcraft.item.VirtualSwordItem;
import dev.tatami.virtualcraft.network.CombatDefenseSuccessPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Server-authoritative defense entry point shared by bosses; no client class references. */
public final class CombatDefenseService {
    public record GuardResolution(DefenseState.Guard result, ItemStack item, EquipmentSlot slot) {}
    private static final String KEY = "virtual_craft.combat_defense";
    private static final String LEGACY_KEY = "virtual_craft.mushroom_defense";
    private static final Map<ServerPlayer,Long> JUMP_INTENTS=new WeakHashMap<>();
    private CombatDefenseService() {}

    public static boolean defensive(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.getItem() instanceof VirtualSwordItem;
    }

    public static void recordJumpIntent(ServerPlayer player) {
        if(player.isAlive() && !player.isCreative() && !player.isSpectator() && player.onGround() && !invalidMovement(player)) {
            JUMP_INTENTS.put(player,player.serverLevel().getGameTime());
        }
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long intent=JUMP_INTENTS.remove(player);
        long now=player.serverLevel().getGameTime();
        if(intent==null || now<intent || now-intent>4 || !player.onGround() || invalidMovement(player)) return;
        var state = load(player);
        state.confirmGroundJump(now);
        save(player, state);
    }

    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.isCanceled() || event.getStrength()<=0 || !(event.getEntity() instanceof ServerPlayer player)
                || player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)>=1) return;
        invalidateJump(player);
    }

    public static void invalidateJump(ServerPlayer player) {
        JUMP_INTENTS.remove(player);
        var state = load(player);
        state.invalidateJump(player.serverLevel().getGameTime());
        save(player, state);
    }

    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !defensive(event.getItem())) return;
        var state = load(player);
        if (!state.startGuard(player.serverLevel().getGameTime())) event.setCanceled(true);
        save(player, state);
    }

    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer player && defensive(event.getItem())
                && load(player).coolingDown(player.serverLevel().getGameTime())) event.setCanceled(true);
    }

    public static boolean dodgeLow(ServerPlayer player) {
        return dodgeLow(player,DefenseRules.STANDARD);
    }
    public static boolean dodgeLow(ServerPlayer player, DefenseRules rules) {
        var state = load(player);
        boolean result = state.dodgeLow(player.serverLevel().getGameTime(), !player.onGround(), invalidMovement(player),rules);
        save(player, state);
        return result;
    }

    public static GuardResolution guard(ServerPlayer player, double facingDot) {
        return guard(player,facingDot,DefenseRules.STANDARD);
    }
    public static GuardResolution guard(ServerPlayer player, double facingDot, DefenseRules rules) {
        var state = load(player);
        ItemStack item = player.getUseItem();
        boolean defending = player.isUsingItem() && defensive(item) && !player.getCooldowns().isOnCooldown(item.getItem());
        var result = state.guard(player.serverLevel().getGameTime(), defending, facingDot,rules);
        save(player, state);
        EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        return new GuardResolution(result, item, slot);
    }

    /** Failed guard damage goes through the normal reduction pipeline before item break effects. */
    public static void finishGuard(ServerPlayer player, GuardResolution resolution) {
        finishGuard(player,resolution,DefenseRules.STANDARD);
    }
    private static void finishGuard(ServerPlayer player, GuardResolution resolution, DefenseRules rules) {
        var result = resolution.result();
        if (result == DefenseState.Guard.NONE) return;
        ItemStack item = resolution.item();
        var type = item.getItem();
        item.hurtAndBreak(result == DefenseState.Guard.PRECISE ? rules.preciseWear() : rules.failedWear(), player, resolution.slot());
        if (result == DefenseState.Guard.BROKEN) {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(type, rules.breakCooldown());
        }
    }

    private static boolean invalidMovement(ServerPlayer player) {
        return player.getAbilities().flying || player.isFallFlying() || player.isSwimming() || player.isInWaterOrBubble()
                || player.isPassenger() || player.onClimbable();
    }

    private static DefenseState load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY) && !root.contains(LEGACY_KEY)) return new DefenseState();
        CompoundTag tag = root.getCompound(root.contains(KEY)?KEY:LEGACY_KEY);
        return DefenseState.restore(new DefenseState.Snapshot(
                tag.getLong("JumpAt"), tag.getBoolean("JumpConsumed"), tag.getLong("GuardAt"),
                tag.getBoolean("GuardConsumed"), tag.getLong("CooldownUntil"), tag.getLong("JumpInvalidUntil")));
    }

    private static void save(ServerPlayer player, DefenseState state) {
        var data = state.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putLong("JumpAt", data.jumpAt());
        tag.putBoolean("JumpConsumed", data.jumpConsumed());
        tag.putLong("GuardAt", data.guardAt());
        tag.putBoolean("GuardConsumed", data.guardConsumed());
        tag.putLong("CooldownUntil", data.cooldownUntil());
        tag.putLong("JumpInvalidUntil", data.jumpInvalidUntil());
        player.getPersistentData().put(KEY, tag);
        player.getPersistentData().remove(LEGACY_KEY);
    }

    /** Call only AFTER target validity, range/lock and per-strike de-duplication have passed. */
    public static Resolution resolve(ServerPlayer player, Vec3 attackOrigin, AttackType type, DefenseRules rules) {
        GuardResolution guard=null;
        int success=-1;
        if (type==AttackType.HIGH && player.isCrouching()) success=CombatDefenseSuccessPayload.HIGH_DODGE;
        else if (type==AttackType.LOW && dodgeLow(player,rules)) success=CombatDefenseSuccessPayload.LOW_DODGE;
        else if (type==AttackType.MID) {
            Vec3 toward=attackOrigin.subtract(player.position()).multiply(1,0,1);
            Vec3 view=player.getViewVector(1).multiply(1,0,1);
            double dot=toward.lengthSqr()<1e-8?1:view.normalize().dot(toward.normalize());
            guard=guard(player,dot,rules);
            if (guard.result()==DefenseState.Guard.PRECISE) success=CombatDefenseSuccessPayload.PRECISE_GUARD;
        }
        if(success>=0) feedback(player,success);
        return new Resolution(player,guard,rules,success>=0);
    }

    /** Legacy boss adapters may use the same cosmetics after their own authoritative rules succeed. */
    public static void feedback(ServerPlayer player,int kind) {
        boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)
                ^ (player.getUsedItemHand()==InteractionHand.OFF_HAND);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,new CombatDefenseSuccessPayload(
                player.level().dimension().location(),player.getId(),player.getUUID(),java.util.UUID.randomUUID(),kind,left));
    }

    /** Close once AFTER damage reduction, so failed-guard durability cannot change that hit's armor chain. */
    public static final class Resolution implements AutoCloseable {
        private final ServerPlayer player;
        private final GuardResolution guard;
        private final DefenseRules rules;
        private final boolean avoided;
        private boolean closed;
        private Resolution(ServerPlayer player,GuardResolution guard,DefenseRules rules,boolean avoided) {
            this.player=player; this.guard=guard; this.rules=rules; this.avoided=avoided;
        }
        public boolean avoided() { return avoided; }
        public DefenseState.Guard guard() { return guard==null?DefenseState.Guard.NONE:guard.result(); }
        public float damageMultiplier() { return avoided?0:guard()==DefenseState.Guard.BROKEN?rules.failedGuardMultiplier():1; }
        @Override public void close() {
            if (!closed) { closed=true; if (guard!=null) finishGuard(player,guard,rules); }
        }
    }
}
