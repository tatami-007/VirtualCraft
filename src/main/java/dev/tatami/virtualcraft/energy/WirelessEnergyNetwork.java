package dev.tatami.virtualcraft.energy;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局无线塔管理器 —— 1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的
 * {@code com.mebeamformer.connection.WirelessEnergyNetwork}。
 *
 * <p>它存在的意义：把所有塔的"每 tick 跑一次能量转发"集中到一个 {@link ServerTickEvent.Post} 监听器里，
 * 而不是让每个 BE 自己声明 ticker。这样 N 个塔仍然只产生 1 次事件分发，CPU 占用线性可控。</p>
 *
 * <h3>关键不变量</h3>
 * <ul>
 *   <li>{@link #lastExecutedTick} 防止同 tick 内被监控方块二次触发重复执行。</li>
 *   <li>{@link #registeredTowers} 同时按 {@code BlockPos} 和 {@code Level} 索引，
 *       前者支持 O(1) 查询，后者支持按维度遍历（节省跨维度的无效迭代）。</li>
 *   <li>{@link ServerStoppedEvent} 触发 {@link #clear} 防内存泄漏。</li>
 * </ul>
 *
 * <h3>距离裁剪（{@link #processSingleTower} 内）</h3>
 * <p>每 tick 验证每个绑定的目标是否还在范围内（XZ ≤ {@value #TOWER_RANGE_XZ}，Y ≤ {@value #TOWER_RANGE_Y}），
 * 超距的目标会被自动 {@link WirelessPowerTowerBlockEntity#removeLink 摘除}。
 * 这避免了"绑了之后玩家把目标搬走"导致的能量泄漏到任意远的盲传。</p>
 *
 * <p>本类不通过 {@code @EventBusSubscriber} 注解 +{@link SubscribeEvent} 静态注册（VirtualCraft 不全程用
 * 注解扫描），而是由 {@link VirtualCraft} 主类显式调用 {@link #registerEvents(IEventBus)}。</p>
 */
public final class WirelessEnergyNetwork {

	/** 与 LaserBindingTool 共享的距离常量（绑定时检查 + tick 时再次检查双保险）。 */
	public static final int TOWER_RANGE_XZ = 20;
	public static final int TOWER_RANGE_Y = 256;

	private static volatile WirelessEnergyNetwork instance;

	/** {@code ConcurrentHashMap}：{@link #registerTower} / {@link #unregisterTower} 可能从任意 BE 加载/卸载线程触发。 */
	private final Map<BlockPos, WirelessPowerTowerBlockEntity> registeredTowers = new ConcurrentHashMap<>();
	private final Map<Level, List<WirelessPowerTowerBlockEntity>> towersByLevel = new ConcurrentHashMap<>();

	/** 防重复触发（监控方块 + 自然 tick 同一帧两次入口的去抖）。 */
	private long lastExecutedTick = -1;

	private WirelessEnergyNetwork() {
	}

	public static WirelessEnergyNetwork getInstance() {
		WirelessEnergyNetwork local = instance;
		if (local == null) {
			synchronized (WirelessEnergyNetwork.class) {
				local = instance;
				if (local == null) {
					instance = local = new WirelessEnergyNetwork();
				}
			}
		}
		return local;
	}

	/** 由 {@code VirtualCraft} 构造函数调用，把 NeoForge 游戏事件总线监听器挂上。 */
	public static void registerEvents(IEventBus eventBus) {
		eventBus.addListener(WirelessEnergyNetwork::onServerTick);
		eventBus.addListener(WirelessEnergyNetwork::onServerStopped);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		getInstance().tickIfNeeded();
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		getInstance().clear();
	}

	// ====================================================================
	// 注册 / 注销
	// ====================================================================

	public void registerTower(WirelessPowerTowerBlockEntity tower) {
		if (tower == null || tower.isRemoved()) {
			return;
		}
		Level level = tower.getLevel();
		if (level == null) {
			return;
		}
		registeredTowers.put(tower.getBlockPos(), tower);
		List<WirelessPowerTowerBlockEntity> towers = towersByLevel.computeIfAbsent(level, k -> new ArrayList<>());
		if (!towers.contains(tower)) {
			towers.add(tower);
		}
	}

	public void unregisterTower(WirelessPowerTowerBlockEntity tower) {
		if (tower == null) {
			return;
		}
		registeredTowers.remove(tower.getBlockPos());
		Level level = tower.getLevel();
		if (level != null) {
			List<WirelessPowerTowerBlockEntity> towers = towersByLevel.get(level);
			if (towers != null) {
				towers.remove(tower);
				if (towers.isEmpty()) {
					towersByLevel.remove(level);
				}
			}
		}
	}

	// ====================================================================
	// tick 调度
	// ====================================================================

	private void tickIfNeeded() {
		if (registeredTowers.isEmpty()) {
			return;
		}
		long currentTick = getCurrentGameTime();
		if (currentTick == lastExecutedTick) {
			return;
		}
		lastExecutedTick = currentTick;
		tick();
	}

	private void tick() {
		if (registeredTowers.isEmpty()) {
			return;
		}
		for (Map.Entry<Level, List<WirelessPowerTowerBlockEntity>> entry : towersByLevel.entrySet()) {
			List<WirelessPowerTowerBlockEntity> towers = entry.getValue();
			if (towers.isEmpty()) {
				continue;
			}
			processTowersInLevel(towers);
		}
	}

	private long getCurrentGameTime() {
		for (WirelessPowerTowerBlockEntity tower : registeredTowers.values()) {
			Level level = tower.getLevel();
			if (level != null) {
				return level.getGameTime();
			}
		}
		return System.currentTimeMillis() / 50L;
	}

	private void processTowersInLevel(List<WirelessPowerTowerBlockEntity> towers) {
		towers.removeIf(tower -> tower.isRemoved() || tower.getLevel() == null);
		for (WirelessPowerTowerBlockEntity tower : towers) {
			processSingleTower(tower);
		}
	}

	private void processSingleTower(WirelessPowerTowerBlockEntity tower) {
		if (tower.isRemoved()) {
			return;
		}
		Level level = tower.getLevel();
		if (level == null) {
			return;
		}
		if (!tower.isOperational()) {
			return;
		}

		Set<BlockPos> links = tower.getLinks();
		if (links.isEmpty()) {
			return;
		}

		Set<BlockPos> validLinks = new HashSet<>();
		for (BlockPos targetPos : new HashSet<>(links)) {
			BlockEntity targetBE = level.getBlockEntity(targetPos);
			if (targetBE == null) {
				tower.removeLink(targetPos);
				continue;
			}

			// 距离裁剪：仅对非塔目标做（塔↔塔双向绑定时已校验过，避免重复算）
			if (!(targetBE instanceof WirelessPowerTowerBlockEntity)) {
				BlockPos sourcePos = tower.getBlockPos();
				int dx = Math.abs(targetPos.getX() - sourcePos.getX());
				int dy = Math.abs(targetPos.getY() - sourcePos.getY());
				int dz = Math.abs(targetPos.getZ() - sourcePos.getZ());
				if (dx > TOWER_RANGE_XZ || dz > TOWER_RANGE_XZ || dy > TOWER_RANGE_Y) {
					tower.removeLink(targetPos);
					continue;
				}
			}

			tower.pushEnergyToTarget(targetBE);
			validLinks.add(targetPos);
		}

		// 仅当列表变化时同步给客户端，避免每 tick 都打包广播
		if (!validLinks.equals(tower.getLastSyncedLinks())) {
			tower.updateSyncedLinks(validLinks);
		}
	}

	private void clear() {
		registeredTowers.clear();
		towersByLevel.clear();
		lastExecutedTick = -1;
	}

	// ====================================================================
	// 调试用 API
	// ====================================================================

	public int getRegisteredTowerCount() {
		return registeredTowers.size();
	}

	public int getTowerCountInLevel(Level level) {
		List<WirelessPowerTowerBlockEntity> towers = towersByLevel.get(level);
		return towers == null ? 0 : towers.size();
	}
}
