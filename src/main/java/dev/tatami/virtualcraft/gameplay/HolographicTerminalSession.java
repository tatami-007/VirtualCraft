package dev.tatami.virtualcraft.gameplay;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端的全息终端会话管理器。
 *
 * <p>历史背景：旧实现里全息终端是一个 {@code AbstractContainerMenu}（{@code HolographicShopMenu}），
 * 用 {@code containerId} 作为本次打开的会话标识。但 {@code AbstractContainerMenu} 又自动让
 * vanilla 给客户端打开 {@code AbstractContainerScreen}，被 JEI 识别为容器 GUI 然后画出
 * Ingredient Overlay——之前不得不写 {@code JeiOverlayController} 通过反射强行隐藏。</p>
 *
 * <p>新方案：抛弃 {@code AbstractContainerMenu}，直接发 {@code OpenHolographicTerminalPayload}
 * 让客户端通过 ModernUI 的 {@code MuiModApi.openScreen} 打开 {@code SimpleScreen}（非
 * {@code AbstractContainerScreen}），JEI 默认就不识别它。本类承接的工作：</p>
 * <ul>
 *   <li>分配自增的 {@code sessionId}（替代旧的 {@code containerId}）。</li>
 *   <li>记录 "玩家 UUID → 当前打开终端的 sessionId" 映射，让 ActionPayload 校验 "请求是不是来自当前打开的终端"
 *       时不再依赖 {@code player.containerMenu instanceof HolographicShopMenu}。</li>
 *   <li>提供 {@link #close(ServerPlayer)} 给 {@code CloseHolographicTerminalPayload} 调用，
 *       玩家关闭终端时清理映射。</li>
 *   <li>提供 {@link #cleanup(UUID)} 给 PlayerLoggedOut 钩子调用，避免内存泄漏。</li>
 * </ul>
 *
 * <p>{@code ConcurrentHashMap} + {@link AtomicInteger}：所有 payload handle 都在主线程通过
 * {@code enqueueWork} 跑，理论上单线程足够；但 PlayerLoggedOut 事件可能从网络线程触发，所以用并发容器
 * 防御性更强。开销可忽略。</p>
 */
public final class HolographicTerminalSession {
	/** {@code 0} 是 "无效 / 未分配" 的哨兵值，所有真实 sessionId 都 ≥ 1。 */
	public static final int NO_SESSION = 0;

	private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
	private static final ConcurrentHashMap<UUID, Integer> OPEN = new ConcurrentHashMap<>();

	private HolographicTerminalSession() {
	}

	/**
	 * 玩家打开终端时调用。返回新分配的 sessionId（≥ 1），同时把它记入活跃映射，
	 * 覆盖该玩家之前可能没有正常关闭的旧 session（比如客户端崩溃没发 close payload）。
	 */
	public static int open(ServerPlayer player) {
		if (player == null) {
			return NO_SESSION;
		}
		int id = NEXT_ID.getAndIncrement();
		// 兜底：跳过 0（哨兵值）。1..MAX_INT 之后回绕到 MIN_INT 再到 0 时跳过。
		if (id == NO_SESSION) {
			id = NEXT_ID.getAndIncrement();
		}
		OPEN.put(player.getUUID(), id);
		return id;
	}

	/** 校验给定玩家当前是否打开着 {@code expectedSessionId} 这个终端。给所有 ActionPayload.handle 用。 */
	public static boolean isOpen(ServerPlayer player, int expectedSessionId) {
		if (player == null || expectedSessionId == NO_SESSION) {
			return false;
		}
		Integer current = OPEN.get(player.getUUID());
		return current != null && current == expectedSessionId;
	}

	/**
	 * 取该玩家当前活跃的 sessionId（用于服务端推送 SyncPayload 时填充 menuId 字段，
	 * 让客户端的 {@code HolographicShopFragment.fromCurrentScreen(menuId)} 能匹配上）。
	 * 返回 {@link #NO_SESSION} 表示玩家没打开终端，调用方应跳过推送。
	 */
	public static int getOpenSessionId(ServerPlayer player) {
		if (player == null) {
			return NO_SESSION;
		}
		Integer current = OPEN.get(player.getUUID());
		return current != null ? current : NO_SESSION;
	}

	/** 玩家主动关闭终端 / 服务端强制关闭时清理。{@code CloseHolographicTerminalPayload} 调用。 */
	public static void close(ServerPlayer player) {
		if (player == null) {
			return;
		}
		OPEN.remove(player.getUUID());
	}

	/** 玩家断线时彻底清理（不需要带 sessionId 校验，断线就是无条件清掉）。 */
	public static void cleanup(UUID playerId) {
		if (playerId == null) {
			return;
		}
		OPEN.remove(playerId);
	}
}
