package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Shared cosmetic confirmation for server-authoritative combat; never grants immunity. */
public record CombatDefenseSuccessPayload(ResourceLocation dimension, int playerId, UUID playerUuid,
        UUID eventId, int kind, boolean leftHand) implements CustomPacketPayload {
    public static final int HIGH_DODGE = 0, LOW_DODGE = 1, PRECISE_GUARD = 2;
    // Retain the original wire identifier/layout for compatibility; the API is boss-independent.
    public static final Type<CombatDefenseSuccessPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "mushroom_defense_success"));
    public static final StreamCodec<FriendlyByteBuf, CombatDefenseSuccessPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeResourceLocation(p.dimension); b.writeVarInt(p.playerId); b.writeUUID(p.playerUuid);
                b.writeUUID(p.eventId); b.writeByte(p.kind); b.writeBoolean(p.leftHand); },
            b -> new CombatDefenseSuccessPayload(b.readResourceLocation(), b.readVarInt(), b.readUUID(),
                    b.readUUID(), b.readUnsignedByte(), b.readBoolean()));
    private static Consumer<CombatDefenseSuccessPayload> receiver = p -> {};
    public static void setClientReceiver(Consumer<CombatDefenseSuccessPayload> callback) { receiver = callback; }
    public static void handle(CombatDefenseSuccessPayload p, IPayloadContext c) { c.enqueueWork(() -> receiver.accept(p)); }
    @Override public Type<CombatDefenseSuccessPayload> type() { return TYPE; }
}
