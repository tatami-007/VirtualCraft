package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.network.CombatJumpIntentPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared input notification, independent of boss-specific afflictions and cosmetic feedback. */
public final class CombatInputTracker {
    private CombatInputTracker() {}
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        var player=Minecraft.getInstance().player;
        if (player==null || event.getEntity()!=player || !player.input.jumping || !player.onGround()
                || !player.isAlive() || player.isCreative() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying() || player.isInWaterOrBubble() || player.onClimbable()) return;
        // Intent only. The server confirms the following vanilla movement packet before issuing a jump token.
        PacketDistributor.sendToServer(new CombatJumpIntentPayload(player.level().dimension().location()));
    }
}
