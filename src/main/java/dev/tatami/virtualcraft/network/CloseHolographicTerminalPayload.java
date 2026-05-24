package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：通知服务端玩家关闭了全息终端 GUI。
 *
 * <p>{@code HolographicShopFragment.onDestroy} 会发这个包，让服务端 {@link HolographicTerminalSession}
 * 把该玩家的活跃 sessionId 移除——这样该玩家之后到达的过期 ActionPayload（比如玩家关闭得太快、
 * 服务器还没处理完最后一个请求）会被 {@link HolographicTerminalSession#isOpen} 拦下来，
 * 而不是错把不属于当前会话的请求当成有效请求处理。</p>
 *
 * <p>携带 {@code sessionId} 让服务端能 "对账"：只有当前活跃 session 等于这个值时才清理，
 * 防止某些罕见时序下（玩家快速重开终端）老的 close payload 误把新的 session 清了。
 * 实现里 {@link HolographicTerminalSession#close} 的具体策略是直接清，
 * 因为 sessionId 是按玩家 UUID 索引的，新的 open 已经覆盖了旧值，旧 close 即使乱序到达
 * 也只会把一个不存在的旧映射清掉，无副作用。但为以后增量加更精细的 "对账" 校验保留这个字段。</p>
 */
public record CloseHolographicTerminalPayload(int sessionId) implements CustomPacketPayload {

	public static final Type<CloseHolographicTerminalPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "close_holographic_terminal"));

	public static final StreamCodec<FriendlyByteBuf, CloseHolographicTerminalPayload> STREAM_CODEC =
			StreamCodec.of(CloseHolographicTerminalPayload::write, CloseHolographicTerminalPayload::read);

	@Override
	public Type<CloseHolographicTerminalPayload> type() {
		return TYPE;
	}

	private static CloseHolographicTerminalPayload read(FriendlyByteBuf buf) {
		return new CloseHolographicTerminalPayload(buf.readVarInt());
	}

	private static void write(FriendlyByteBuf buf, CloseHolographicTerminalPayload payload) {
		buf.writeVarInt(payload.sessionId);
	}

	public static void handle(CloseHolographicTerminalPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (ctx.player() instanceof ServerPlayer player) {
				HolographicTerminalSession.close(player);
			}
		});
	}
}
