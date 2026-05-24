package dev.tatami.virtualcraft.client.binding;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.tatami.virtualcraft.item.LaserBindingTool;
import dev.tatami.virtualcraft.network.LaserBatchBindPayload;

/**
 * 客户端钩子：在玩家手持 {@link LaserBindingTool} 时拦截
 * "Ctrl + Shift + 右键 一台机器" 这个组合，转成 {@link LaserBatchBindPayload}
 * 发给服务端，服务端从被点机器开始扫一圈把所有 FE 机器一次性绑定到选中的塔。
 *
 * <h3>为什么不在 {@link LaserBindingTool#useOn} 里直接判断 Ctrl</h3>
 * <p>{@link Player#isShiftKeyDown()} 是 vanilla 同步过的状态，但 Ctrl 不是。
 * Forge/NeoForge 都没有把 Ctrl 状态从客户端推到服务端的内置机制。所以我们必须在
 * 客户端独立的 {@link PlayerInteractEvent.RightClickBlock} 钩子里检测 Ctrl，
 * 然后显式发包到服务端走批量分支，并 {@code cancel} 默认 {@code useOn}
 * 防止它再触发单点 toggle 绑定。</p>
 *
 * <h3>触发条件</h3>
 * <ol>
 *   <li>客户端事件总线（{@link NeoForge#EVENT_BUS}）</li>
 *   <li>{@link PlayerInteractEvent.RightClickBlock} 客户端 phase（{@code level.isClientSide()}）</li>
 *   <li>主手持有 {@link LaserBindingTool}</li>
 *   <li>键盘当前按住任一 Ctrl（左/右）</li>
 *   <li>键盘当前按住任一 Shift（左/右）—— 与原版"shift+右键机器"绑定语义一致，避免误触</li>
 * </ol>
 *
 * <p>满足全部条件就发 {@link LaserBatchBindPayload}，半径硬编码 10，
 * {@code event.setCanceled(true)} 终止默认 {@code useOn} 流程。否则放行，
 * 走 {@link LaserBindingTool#useOn} 的单点 toggle 逻辑。</p>
 */
public final class LaserBindingClientHandler {

	/**
	 * 服务端现在是 BFS 连通分量算法，不看半径。但网络包 schema 还带个 {@code radius} 字段剩下，
	 * 发 1 占位。以后如果要加“在 BFS 上限不变的前提下手动限制范围”可以复用。
	 */
	private static final int CLIENT_BATCH_RADIUS = 1;

	/**
	 * 防鬼畜：玩家按住 Ctrl+Shift+右键不松手时 vanilla 每 4 tick 重复 fire {@code RightClickBlock}，
	 * 一秒足以触发 5 次批量。设 250 ms 冷却，玩家必须手动释放 + 重新按下右键才能下一次。
	 */
	private static final long COOLDOWN_NANOS = 250_000_000L;
	private static long lastTriggerNanos = 0L;

	private LaserBindingClientHandler() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(LaserBindingClientHandler::onRightClickBlock);
	}

	private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		// 客户端 phase only —— 服务端的 RightClickBlock 我们不动，照常走 useOn
		if (!event.getLevel().isClientSide()) {
			return;
		}

		Player player = event.getEntity();
		ItemStack stack = event.getItemStack();
		if (!(stack.getItem() instanceof LaserBindingTool)) {
			return;
		}

		// 必须 Ctrl + Shift 一起按，避免误触
		if (!isCtrlDown()) {
			return;
		}
		if (!player.isShiftKeyDown()) {
			return;
		}

		// 防鬼畜冷却检查：即使竟玩家按住不松发了 vanilla 重复 fire，也只走一次批量。
		// cancel 仍然要干，防止 useOn 后续被触发变成“批量 + 单点”双执行。
		long now = System.nanoTime();
		if (now - lastTriggerNanos < COOLDOWN_NANOS) {
			event.setCanceled(true);
			event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
			return;
		}
		lastTriggerNanos = now;

		// 客户端可以乐观地直接发包；服务端会自己再校验“工具上有 SourcePos”等条件
		PacketDistributor.sendToServer(new LaserBatchBindPayload(event.getPos(), CLIENT_BATCH_RADIUS));

		// 终止默认 useOn ——否则它还会走单点 shift+右键 toggle 绑定，把当前点击的机器再 toggle 一次
		event.setCanceled(true);
		event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
	}

	/**
	 * 直接读 GLFW 的 Ctrl 物理按键状态。{@link Minecraft#getWindow()} 的 GLFW 句柄
	 * 永远可用（客户端唯一的窗口），所以不用判 null。
	 *
	 * <p>用 {@link InputConstants#isKeyDown(long, int)} 而不是 {@link KeyMapping}：
	 * 我们不需要让玩家在控制设置里"重绑 Ctrl 键"，物理 Ctrl 就是 Ctrl。</p>
	 */
	private static boolean isCtrlDown() {
		long handle = Minecraft.getInstance().getWindow().getWindow();
		return InputConstants.isKeyDown(handle, InputConstants.KEY_LCONTROL)
				|| InputConstants.isKeyDown(handle, InputConstants.KEY_RCONTROL);
	}
}
