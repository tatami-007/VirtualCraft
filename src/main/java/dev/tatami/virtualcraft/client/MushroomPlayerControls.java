package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.network.MushroomPlayerControlPayload;
import dev.tatami.virtualcraft.boss.MushroomControlRules;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/** Changes movement intent only. Keys, view, jump, sneak and item-use remain untouched. */
public final class MushroomPlayerControls {
    private static ResourceLocation dimension;
    private static long sequence=-1,snareUntil,rotationUntil,dragUntil;
    private static float movementScale=1;
    private static int degrees;
    private MushroomPlayerControls() {}
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        var player=Minecraft.getInstance().player;
        if(player==null || event.getEntity()!=player || !player.input.jumping || !player.onGround()
                || !player.isAlive() || player.isCreative() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying() || player.isInWaterOrBubble() || player.onClimbable()) return;
        long now=player.level().getGameTime();
        if(player.isSprinting() && player.level().dimension().location().equals(dimension)
                && (now<snareUntil || now<rotationUntil || now<dragUntil)) {
            // Vanilla adds a fixed forward kick for sprint-jumping. Transform only
            // that kick, preserving pre-existing momentum, knockback and jump Y.
            var kick=MushroomControlRules.movement(1,0,now<snareUntil,now<rotationUntil?degrees:0,
                    now<dragUntil?movementScale:1);
            double yaw=Math.toRadians(player.getYRot());
            player.setDeltaMovement(player.getDeltaMovement().add(
                    .2*(-Math.sin(yaw)*(kick.forward()-1)+Math.cos(yaw)*kick.left()),0,
                    .2*(Math.cos(yaw)*(kick.forward()-1)+Math.sin(yaw)*kick.left())));
        }
    }
    public static void accept(MushroomPlayerControlPayload p) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || !mc.level.dimension().location().equals(p.dimension()) || p.sequence()<=sequence) return;
        dimension=p.dimension(); sequence=p.sequence(); snareUntil=p.snareUntil(); rotationUntil=p.rotationUntil(); degrees=p.degrees();
        dragUntil=p.dragUntil(); movementScale=Float.isFinite(p.movementScale())?Math.clamp(p.movementScale(),0,1):1;
    }
    public static void onInput(MovementInputUpdateEvent event) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || event.getEntity()!=mc.player || mc.player==null) return;
        if(dimension==null || !mc.level.dimension().location().equals(dimension)) { clear(); return; }
        if(!mc.player.isAlive() || mc.player.isCreative() || mc.player.isSpectator() || mc.player.isPassenger()) return;
        var input=event.getInput();
        long now=mc.level.getGameTime();
        if(now<snareUntil || now<rotationUntil || now<dragUntil) {
            var movement=MushroomControlRules.movement(input.forwardImpulse,input.leftImpulse,
                    now<snareUntil,now<rotationUntil?degrees:0,now<dragUntil?movementScale:1);
            input.forwardImpulse=movement.forward(); input.leftImpulse=movement.left();
            input.up=input.forwardImpulse>0; input.down=input.forwardImpulse<0;
            input.left=input.leftImpulse>0; input.right=input.leftImpulse<0;
            if(now<snareUntil) mc.player.setSprinting(false);
        }
    }
    public static void clear() { dimension=null; sequence=-1; snareUntil=rotationUntil=dragUntil=0; degrees=0; movementScale=1; }
}
