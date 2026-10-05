package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.combat.CombatDefenseService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Shared intent only: the following vanilla movement packet must confirm a grounded takeoff. */
public record CombatJumpIntentPayload(ResourceLocation dimension) implements CustomPacketPayload {
    // Legacy wire ID retained intentionally; no boss identity is carried in this message.
    public static final Type<CombatJumpIntentPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,"mushroom_jump_intent"));
    public static final StreamCodec<FriendlyByteBuf,CombatJumpIntentPayload> STREAM_CODEC=StreamCodec.of(
            (buffer,payload)->buffer.writeResourceLocation(payload.dimension),
            buffer->new CombatJumpIntentPayload(buffer.readResourceLocation()));
    public static void handle(CombatJumpIntentPayload payload,IPayloadContext context) {
        context.enqueueWork(()-> {
            if(context.player() instanceof ServerPlayer player && player.level().dimension().location().equals(payload.dimension)) {
                CombatDefenseService.recordJumpIntent(player);
            }
        });
    }
    @Override public Type<CombatJumpIntentPayload> type() { return TYPE; }
}
