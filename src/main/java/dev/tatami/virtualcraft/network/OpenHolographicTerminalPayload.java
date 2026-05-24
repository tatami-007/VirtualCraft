package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：通知客户端打开全息终端 GUI。
 *
 * <p>替换了旧的 {@code openMenu(MenuProvider, ...)} 路径，因为旧路径总会触发 vanilla 创建
 * {@code AbstractContainerScreen}——而那是被 JEI 识别为容器 GUI 进而绘制 Ingredient Overlay 的
 * 类型。新路径：客户端用 {@code MuiModApi.openScreen(new HolographicShopFragment(sessionId,...))}
 * 打开 ModernUI 的 {@code SimpleScreen}（普通 {@code Screen}，非 {@code AbstractContainerScreen}），
 * JEI 默认对它根本不画 overlay，从根上消除了反射 hack 的需要。</p>
 *
 * <p>载荷字段：</p>
 * <ul>
 *   <li>{@code sessionId}：服务端 {@code HolographicTerminalSession} 分配的会话标识，
 *       后续 {@code ActionPayload}/{@code SyncPayload} 都用它替代旧的 {@code containerId}。</li>
 *   <li>{@code itemMode}：是否为手持物品打开（{@code true}=Holographic Terminal 物品，
 *       {@code false}=Holographic Shop 方块）。Fragment 用它决定标题/某些功能可见性。</li>
 *   <li>{@code blockPos}：方块版终端的方块坐标；物品模式下为 {@link BlockPos#ZERO}。</li>
 * </ul>
 */
public record OpenHolographicTerminalPayload(int sessionId, boolean itemMode, BlockPos blockPos) implements CustomPacketPayload {

	public static final Type<OpenHolographicTerminalPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "open_holographic_terminal"));

	public static final StreamCodec<FriendlyByteBuf, OpenHolographicTerminalPayload> STREAM_CODEC =
			StreamCodec.of(OpenHolographicTerminalPayload::write, OpenHolographicTerminalPayload::read);

	@Override
	public Type<OpenHolographicTerminalPayload> type() {
		return TYPE;
	}

	private static OpenHolographicTerminalPayload read(FriendlyByteBuf buf) {
		int sessionId = buf.readVarInt();
		boolean itemMode = buf.readBoolean();
		BlockPos blockPos = buf.readBlockPos();
		return new OpenHolographicTerminalPayload(sessionId, itemMode, blockPos);
	}

	private static void write(FriendlyByteBuf buf, OpenHolographicTerminalPayload payload) {
		buf.writeVarInt(payload.sessionId);
		buf.writeBoolean(payload.itemMode);
		buf.writeBlockPos(payload.blockPos);
	}

	public static void handle(OpenHolographicTerminalPayload payload, IPayloadContext ctx) {
		// 只在客户端 dist 路径里调用 ModernUI——服务端收到这个包是协议异常，直接 NO-OP。
		ctx.enqueueWork(() -> ClientHooks.handle(payload));
	}

	/**
	 * 客户端代码隔离在嵌套类里 + {@code @OnlyIn(Dist.CLIENT)}，避免专用服务器加载 ModernUI
	 * 的客户端类时 NoClassDefFoundError。NeoForge classload 时按需触发嵌套类，所以 dedicated
	 * server 永远不会触达 {@code ClientHooks}。
	 */
	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(OpenHolographicTerminalPayload payload) {
			icyllis.modernui.mc.MuiModApi.openScreen(
					new dev.tatami.virtualcraft.client.screen.holographic.HolographicShopFragment(
							payload.sessionId(), payload.itemMode(), payload.blockPos()));
		}
	}
}
