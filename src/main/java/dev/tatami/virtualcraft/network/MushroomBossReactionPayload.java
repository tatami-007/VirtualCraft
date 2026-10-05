package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Transient event sent only to current trackers; no historical reaction on entity spawn. */
public record MushroomBossReactionPayload(int entityId, UUID entityUuid, int sequence,
        int kind, int side, float strength) implements CustomPacketPayload {
    public static final int HURT = 0, HEAVY_HURT = 1, PRECISE_GUARD = 2, FAILED_GUARD = 3;
    public static final Type<MushroomBossReactionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,"mushroom_boss_reaction"));
    public static final StreamCodec<FriendlyByteBuf,MushroomBossReactionPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.entityId); b.writeUUID(p.entityUuid); b.writeVarInt(p.sequence); b.writeByte(p.kind); b.writeByte(p.side); b.writeFloat(p.strength); },
            b -> new MushroomBossReactionPayload(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readUnsignedByte(),b.readByte(),b.readFloat()));
    private static Consumer<MushroomBossReactionPayload> receiver = p -> {};
    public static void setClientReceiver(Consumer<MushroomBossReactionPayload> callback) { receiver=callback; }
    public static void handle(MushroomBossReactionPayload p, IPayloadContext c) { c.enqueueWork(() -> receiver.accept(p)); }
    @Override public Type<MushroomBossReactionPayload> type() { return TYPE; }
}
