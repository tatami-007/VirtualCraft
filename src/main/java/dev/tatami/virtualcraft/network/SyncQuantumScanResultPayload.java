package dev.tatami.virtualcraft.network;

import java.util.ArrayList;
import java.util.List;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.machine.advanced.QuantumProgram;
import dev.tatami.virtualcraft.machine.advanced.QuantumScanService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncQuantumScanResultPayload(
		QuantumProgram program,
		ResourceKey<Level> dimension,
		List<QuantumScanService.ScanPoint> points) implements CustomPacketPayload {
	public static final Type<SyncQuantumScanResultPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_quantum_scan_result"));
	public static final StreamCodec<FriendlyByteBuf, SyncQuantumScanResultPayload> STREAM_CODEC =
			StreamCodec.of(SyncQuantumScanResultPayload::write, SyncQuantumScanResultPayload::read);

	public static SyncQuantumScanResultPayload from(QuantumScanService.ScanResult result) {
		return new SyncQuantumScanResultPayload(result.program(), result.dimension(), result.points());
	}

	@Override
	public Type<SyncQuantumScanResultPayload> type() {
		return TYPE;
	}

	private static void write(FriendlyByteBuf buffer, SyncQuantumScanResultPayload payload) {
		buffer.writeEnum(payload.program);
		buffer.writeResourceLocation(payload.dimension.location());
		buffer.writeVarInt(payload.points.size());
		for (QuantumScanService.ScanPoint point : payload.points) {
			buffer.writeBlockPos(point.pos());
			buffer.writeVarInt(point.count());
		}
	}

	private static SyncQuantumScanResultPayload read(FriendlyByteBuf buffer) {
		QuantumProgram program = buffer.readEnum(QuantumProgram.class);
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
		int size = Math.min(8, Math.max(0, buffer.readVarInt()));
		List<QuantumScanService.ScanPoint> points = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			points.add(new QuantumScanService.ScanPoint(buffer.readBlockPos(), buffer.readVarInt()));
		}
		return new SyncQuantumScanResultPayload(program, dimension, List.copyOf(points));
	}

	public static void handle(SyncQuantumScanResultPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.apply(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void apply(SyncQuantumScanResultPayload payload) {
			dev.tatami.virtualcraft.client.highlight.QuantumScanClientState.set(
					payload.program(), payload.dimension(), payload.points());
		}
	}
}
