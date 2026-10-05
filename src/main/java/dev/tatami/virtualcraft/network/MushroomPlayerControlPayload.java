package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Absolute server deadlines, so delayed control updates do not extend a debuff. */
public record MushroomPlayerControlPayload(ResourceLocation dimension, long sequence,
        long snareUntil, long rotationUntil, int degrees,long dragUntil,float movementScale) implements CustomPacketPayload {
    public static final Type<MushroomPlayerControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,"mushroom_player_control"));
    public static final StreamCodec<FriendlyByteBuf,MushroomPlayerControlPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeResourceLocation(p.dimension); b.writeVarLong(p.sequence); b.writeLong(p.snareUntil); b.writeLong(p.rotationUntil); b.writeInt(p.degrees); b.writeLong(p.dragUntil); b.writeFloat(p.movementScale); },
            b -> new MushroomPlayerControlPayload(b.readResourceLocation(),b.readVarLong(),b.readLong(),b.readLong(),b.readInt(),b.readLong(),b.readFloat()));
    private static Consumer<MushroomPlayerControlPayload> receiver = p -> {};
    public static void setClientReceiver(Consumer<MushroomPlayerControlPayload> callback) { receiver=callback; }
    public static void handle(MushroomPlayerControlPayload p, IPayloadContext c) { c.enqueueWork(() -> receiver.accept(p)); }
    @Override public Type<MushroomPlayerControlPayload> type() { return TYPE; }
}
