package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.item.LaserBatchBinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端 包：玩家按住 Ctrl + Shift + 右键 一台机器时触发，
 * 服务端以该机器为中心向外扫描一段半径，把范围内所有有 FE 能力的方块批量
 * 绑定到玩家选中的源塔上。
 *
 * <h3>为什么需要这个包</h3>
 * <p>{@code Ctrl} 是客户端独有的状态——服务端无法直接知道玩家有没有按 Ctrl。
 * 单纯依赖 {@link net.minecraft.world.item.context.UseOnContext#getPlayer()}
 * 在服务端调 {@code use} 时无法读取 modifier key 状态。所以我们走显式网络包：
 * 客户端在 {@link net.neoforged.neoforge.client.event.InputEvent} 风格的钩子里
 * 检测 Ctrl，发包给服务端，服务端跳过常规 {@code useOn} 直接进批量分支。</p>
 *
 * <h3>字段</h3>
 * <ul>
 *   <li>{@code center}: 玩家点击的方块（批量扫描中心）</li>
 *   <li>{@code radius}: 旧协议字段，保留用于网络兼容；服务端实际按相邻机器 BFS 扫描。</li>
 * </ul>
 */
public record LaserBatchBindPayload(BlockPos center, int radius) implements CustomPacketPayload {

	public static final Type<LaserBatchBindPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "laser_batch_bind"));

	public static final StreamCodec<FriendlyByteBuf, LaserBatchBindPayload> STREAM_CODEC =
			StreamCodec.of(LaserBatchBindPayload::write, LaserBatchBindPayload::read);

	@Override
	public Type<LaserBatchBindPayload> type() {
		return TYPE;
	}

	private static LaserBatchBindPayload read(FriendlyByteBuf buf) {
		return new LaserBatchBindPayload(buf.readBlockPos(), buf.readVarInt());
	}

	private static void write(FriendlyByteBuf buf, LaserBatchBindPayload payload) {
		buf.writeBlockPos(payload.center);
		buf.writeVarInt(payload.radius);
	}

	public static void handle(LaserBatchBindPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer sp)) {
				return;
			}
			LaserBatchBinder.bulkBindNeighbors(sp, payload.center);
		});
	}
}
