package dev.tatami.virtualcraft.gameplay;

import dev.tatami.virtualcraft.boss.MushroomDefenseState;
import dev.tatami.virtualcraft.item.VirtualSwordItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** No feedback effects: only records real server-side input and shared guard cooldown. */
public final class MushroomDefenseHandler {
    public record GuardResolution(MushroomDefenseState.Guard result, ItemStack item, EquipmentSlot slot) {}
    private static final String KEY = "virtual_craft.mushroom_defense";
    private MushroomDefenseHandler() {}

    public static boolean defensive(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.getItem() instanceof VirtualSwordItem;
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || invalidMovement(player) || player.hurtTime > 0) return;
        var state = load(player);
        state.jump(player.serverLevel().getGameTime());
        save(player, state);
    }

    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
        var state = load(player);
        boolean result = state.dodgeLow(player.serverLevel().getGameTime(), !player.onGround(), invalidMovement(player));
        save(player, state);
        return result;
    }

    public static GuardResolution guard(ServerPlayer player, double facingDot) {
        var state = load(player);
        ItemStack item = player.getUseItem();
        boolean defending = player.isUsingItem() && defensive(item) && !player.getCooldowns().isOnCooldown(item.getItem());
        var result = state.guard(player.serverLevel().getGameTime(), defending, facingDot);
        save(player, state);
        EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        return new GuardResolution(result, item, slot);
    }

    /** Failed guard damage goes through the normal reduction pipeline before item break effects. */
    public static void finishGuard(ServerPlayer player, GuardResolution resolution) {
        var result = resolution.result();
        if (result == MushroomDefenseState.Guard.NONE) return;
        ItemStack item = resolution.item();
        var type = item.getItem();
        item.hurtAndBreak(result == MushroomDefenseState.Guard.PRECISE ? 1 : 2, player, resolution.slot());
        if (result == MushroomDefenseState.Guard.BROKEN) {
            player.stopUsingItem();
            player.getCooldowns().addCooldown(type, MushroomDefenseState.BREAK_COOLDOWN);
        }
    }

    private static boolean invalidMovement(ServerPlayer player) {
        return player.getAbilities().flying || player.isFallFlying() || player.isSwimming() || player.isInWaterOrBubble();
    }

    private static MushroomDefenseState load(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(KEY)) return new MushroomDefenseState();
        CompoundTag tag = root.getCompound(KEY);
        return MushroomDefenseState.restore(new MushroomDefenseState.Snapshot(
                tag.getLong("JumpAt"), tag.getBoolean("JumpConsumed"), tag.getLong("GuardAt"),
                tag.getBoolean("GuardConsumed"), tag.getLong("CooldownUntil"), tag.getLong("JumpInvalidUntil")));
    }

    private static void save(ServerPlayer player, MushroomDefenseState state) {
        var data = state.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putLong("JumpAt", data.jumpAt());
        tag.putBoolean("JumpConsumed", data.jumpConsumed());
        tag.putLong("GuardAt", data.guardAt());
        tag.putBoolean("GuardConsumed", data.guardConsumed());
        tag.putLong("CooldownUntil", data.cooldownUntil());
        tag.putLong("JumpInvalidUntil", data.jumpInvalidUntil());
        player.getPersistentData().put(KEY, tag);
    }
}
