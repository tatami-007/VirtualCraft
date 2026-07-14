package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.api.energy.ILongEnergyStorage;
import dev.tatami.virtualcraft.energy.EnergyStorageHelper;
import dev.tatami.virtualcraft.energy.ILinkable;
import dev.tatami.virtualcraft.energy.WirelessEnergyNetwork;
import dev.tatami.virtualcraft.energy.WirelessPowerReceiver;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 无线能源感应塔的方块实体。
 *
 * <p>1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的
 * {@code com.mebeamformer.blockentity.WirelessEnergyTowerBlockEntity} 行为，
 * 但去掉了 AE2 父类（{@code AENetworkedBlockEntity}）和 AE2 网络相关字段——
 * VirtualCraft 工作区不依赖 AE2，所以本类直接继承 {@link BlockEntity}，
 * 同时实现 {@link ILinkable}、{@link IEnergyStorage}（int 接口兼容旧机器）、
 * {@link ILongEnergyStorage}（long 接口给塔间直通缓冲用）。</p>
 *
 * <h3>核心字段</h3>
 * <ul>
 *   <li>{@link #links}：服务端权威的连接目标集合（持久化到 NBT，存于 {@code "links"} 标签）。</li>
 *   <li>{@link #clientLinks}：客户端只读快照，由 {@link ClientboundBlockEntityDataPacket} 同步过来用于渲染。</li>
 *   <li>{@link #lastSyncedLinks}：上一次推送给客户端的 {@code links} 快照，用于变化检测避免无意义同步。</li>
 * </ul>
 *
 * <h3>行为对照原版</h3>
 * <ul>
 *   <li>{@link #pushEnergyToTarget(BlockEntity)}：tick 时由 {@link WirelessEnergyNetwork} 调用，
 *       为某个绑定的目标推送能量。目标若是塔则走 {@link #pushEnergyToTower}（BFS 网络中继），
 *       否则从邻居能量源直接拉电送给目标。</li>
 *   <li>{@link #distributeEnergyInNetwork(long, boolean, Set)}：BFS 遍历当前塔可达的整个塔网络，
 *       优先把能量塞给每个塔的相邻设备和绑定设备。{@code visited} 集合防止 A→B→A 循环。</li>
 *   <li>{@link #receiveEnergyL(long, boolean)}：作为 {@link ILongEnergyStorage} 的输入端点 ——
 *       当外部"推电进塔"时，塔把电量分配给所有 {@link #links} 中的目标（递归塔/直接灌机器）。</li>
 *   <li>{@link #extractEnergyL(long, boolean)}：作为 {@link ILongEnergyStorage} 的输出端点 ——
 *       当外部"从塔抽电"时，塔从邻居能量源直接拉电交付。</li>
 * </ul>
 *
 * <h3>网络同步</h3>
 * <p>NeoForge 1.21.1 的 BE 同步用 {@link #getUpdatePacket} + {@link #getUpdateTag} 双路径：
 * 区块加载时走 {@code getUpdateTag}，runtime 增量同步走 {@code getUpdatePacket}。
 * 都通过 {@link #saveToUpdateTag} 把 {@link #lastSyncedLinks} 序列化到客户端，
 * {@link #handleUpdateTag} 在客户端反序列化到 {@link #clientLinks}。</p>
 */
public class WirelessPowerTowerBlockEntity extends BlockEntity implements ILinkable, IEnergyStorage, ILongEnergyStorage {

	/** 单 tick 单次塔→塔传输的上限。原版用 {@link Long#MAX_VALUE}，本实现一致。 */
	private static final long MAX_TRANSFER = Long.MAX_VALUE;
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final int THUNDER_POOL_RADIUS = 8;
	private static final int THUNDER_POOL_VERTICAL_RADIUS = 4;
	private static final int THUNDER_POOL_SCAN_INTERVAL = 40;

	/** 服务端权威的连接目标集合。客户端这个字段为空，渲染只读 {@link #clientLinks}。 */
	private final Set<BlockPos> links = new HashSet<>();

	/** 客户端连接快照（同步包过来，仅用于 BER 渲染红色光束）。 */
	private List<BlockPos> clientLinks = Collections.emptyList();

	/** 上一次推给客户端的 links 副本，diff 它就知道是否需要 markForUpdate。 */
	private final Set<BlockPos> lastSyncedLinks = new HashSet<>();
	private long lastThunderPoolScan = Long.MIN_VALUE;
	private boolean thunderPoolNearby;

	public WirelessPowerTowerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WIRELESS_POWER_TOWER.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		if (level != null && !level.isClientSide) {
			WirelessEnergyNetwork.getInstance().registerTower(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && !level.isClientSide) {
			WirelessEnergyNetwork.getInstance().unregisterTower(this);
		}
	}

	// ====================================================================
	// 网络管理器 (WirelessEnergyNetwork) 调用的钩子
	// ====================================================================

	public Set<BlockPos> getLastSyncedLinks() {
		return Collections.unmodifiableSet(lastSyncedLinks);
	}

	public void updateSyncedLinks(Set<BlockPos> validLinks) {
		this.lastSyncedLinks.clear();
		this.lastSyncedLinks.addAll(validLinks);
		markForUpdate();
	}

	/**
	 * 给指定目标推送能量。1:1 还原：先尝试从邻居能量源（任意一面有 IEnergyStorage 且 canExtract）
	 * 提取，再推给目标的任意一面。两步都成功才真正从源扣电（先 simulate 决定数量，再 actual）。
	 *
	 * <p>目标若是塔则走 {@link #pushEnergyToTower} 做 BFS 网络中继。</p>
	 */
	public void pushEnergyToTarget(BlockEntity target) {
		if (level == null || target == null || !isOperational()) {
			return;
		}

		if (target instanceof WirelessPowerTowerBlockEntity targetTower) {
			if (targetTower.isOperational()) {
				pushEnergyToTower(targetTower);
			}
			return;
		}
		if (!canPowerTarget(target)) {
			return;
		}

		// 遍历自身 6 面找一个能"输出"的邻居
		for (Direction sourceDir : DIRECTIONS) {
			BlockPos neighborPos = worldPosition.relative(sourceDir);
			BlockEntity sourceBE = level.getBlockEntity(neighborPos);
			if (sourceBE == null || sourceBE instanceof WirelessPowerTowerBlockEntity) {
				continue;
			}

			long extracted = EnergyStorageHelper.extractEnergy(sourceBE, sourceDir.getOpposite(), MAX_TRANSFER, true);
			if (extracted > 0) {
				long inserted = pushToTargetAllSides(target, extracted, true);
				if (inserted > 0) {
					long actualExtracted = EnergyStorageHelper.extractEnergy(sourceBE, sourceDir.getOpposite(), inserted, false);
					if (actualExtracted > 0) {
						pushToTargetAllSides(target, actualExtracted, false);
						return;
					}
				}
			}
		}
	}

	/** 试目标的 6 面任一可注入面，第一个返回非零的就用。 */
	private long pushToTargetAllSides(BlockEntity target, long amount, boolean simulate) {
		if (!canPowerTarget(target)) {
			return 0;
		}
		for (Direction targetDir : DIRECTIONS) {
			long inserted = EnergyStorageHelper.insertEnergy(target, targetDir, amount, simulate);
			if (inserted > 0) {
				return inserted;
			}
		}
		return 0;
	}

	/** 塔→塔：用 BFS 在整个目标塔可达的网络中分配能量。 */
	private void pushEnergyToTower(WirelessPowerTowerBlockEntity targetTower) {
		if (level == null || !isOperational() || !targetTower.isOperational()) {
			return;
		}

		for (Direction sourceDir : DIRECTIONS) {
			BlockPos neighborPos = worldPosition.relative(sourceDir);
			BlockEntity sourceBE = level.getBlockEntity(neighborPos);
			if (sourceBE == null || sourceBE instanceof WirelessPowerTowerBlockEntity) {
				continue;
			}

			long extracted = EnergyStorageHelper.extractEnergy(sourceBE, sourceDir.getOpposite(), MAX_TRANSFER, true);
			if (extracted > 0) {
				// 模拟阶段：决定能塞多少进去
				Set<BlockPos> visitedSimulate = new HashSet<>();
				visitedSimulate.add(this.worldPosition);
				long distributed = targetTower.distributeEnergyInNetwork(extracted, true, visitedSimulate);

				if (distributed > 0) {
					// 实际阶段：用新的 visited 重新跑一遍，避免把模拟阶段的访问标记带过来
					Set<BlockPos> visitedActual = new HashSet<>();
					visitedActual.add(this.worldPosition);
					long actualExtracted = EnergyStorageHelper.extractEnergy(sourceBE, sourceDir.getOpposite(), distributed, false);
					targetTower.distributeEnergyInNetwork(actualExtracted, false, visitedActual);
					return;
				}
			}
		}
	}

	/**
	 * BFS 遍历塔网络分配能量。原版关键算法 1:1 移植——
	 * 对每个塔依次：(1) 灌它的邻居设备 (2) 灌它的绑定设备 (3) 把它连接的其他塔加入队列。
	 *
	 * @param visited 已访问的塔位置集合（防止 A→B→A 循环）
	 * @return 实际/模拟分配出去的能量总量
	 */
	private long distributeEnergyInNetwork(long amount, boolean simulate, Set<BlockPos> visited) {
		if (level == null || amount <= 0) {
			return 0;
		}

		ArrayDeque<WirelessPowerTowerBlockEntity> towerQueue = new ArrayDeque<>();
		towerQueue.add(this);
		visited.add(this.worldPosition);

		long totalInserted = 0;

		while (!towerQueue.isEmpty() && totalInserted < amount) {
			WirelessPowerTowerBlockEntity currentTower = towerQueue.poll();
			if (!currentTower.isOperational()) {
				continue;
			}

			// 1. 当前塔的邻居（非塔）
			for (Direction dir : DIRECTIONS) {
				if (totalInserted >= amount) {
					break;
				}
				BlockPos neighborPos = currentTower.worldPosition.relative(dir);
				BlockEntity neighborBE = level.getBlockEntity(neighborPos);
				if (neighborBE != null && !(neighborBE instanceof WirelessPowerTowerBlockEntity)) {
					long neighborRemaining = amount - totalInserted;
					long inserted = pushToTargetAllSides(neighborBE, neighborRemaining, simulate);
					totalInserted += inserted;
				}
			}

			// 2. 当前塔的绑定设备（非塔）
			if (totalInserted < amount && !currentTower.links.isEmpty()) {
				for (BlockPos targetPos : currentTower.links) {
					if (totalInserted >= amount) {
						break;
					}
					BlockEntity targetBE = level.getBlockEntity(targetPos);
					if (targetBE == null || targetBE instanceof WirelessPowerTowerBlockEntity) {
						continue;
					}
					long targetRemaining = amount - totalInserted;
					long inserted = pushToTargetAllSides(targetBE, targetRemaining, simulate);
					totalInserted += inserted;
				}
			}

			// 3. BFS 扩展：把连接的其他塔入队
			if (totalInserted < amount && !currentTower.links.isEmpty()) {
				for (BlockPos targetPos : currentTower.links) {
					if (visited.contains(targetPos)) {
						continue;
					}
					BlockEntity targetBE = level.getBlockEntity(targetPos);
					if (targetBE instanceof WirelessPowerTowerBlockEntity targetTower) {
						visited.add(targetPos);
						towerQueue.add(targetTower);
					}
				}
			}
		}
		return totalInserted;
	}

	// ====================================================================
	// ILinkable
	// ====================================================================

	@Override
	public void addLink(BlockPos other) {
		if (other.equals(this.getBlockPos())) {
			return;
		}
		if (this.links.add(other)) {
			// markForUpdate 会 setChanged() + sendBlockUpdated ——后者立即推 getUpdateTag 到客户端
			// 避免玩家绑定后需要等下一个 {@link WirelessEnergyNetwork} tick 临幸才能看到光束 / 高亮
			markForUpdate();
		}
	}

	@Override
	public void removeLink(BlockPos other) {
		if (this.links.remove(other)) {
			markForUpdate();
		}
	}

	@Override
	public Set<BlockPos> getLinks() {
		return Collections.unmodifiableSet(links);
	}

	public boolean hasTargets() {
		return !links.isEmpty();
	}

	public boolean isOperational() {
		if (level == null || level.isClientSide) {
			return false;
		}
		long gameTime = level.getGameTime();
		if (gameTime - lastThunderPoolScan < THUNDER_POOL_SCAN_INTERVAL && lastThunderPoolScan != Long.MIN_VALUE) {
			return thunderPoolNearby;
		}
		lastThunderPoolScan = gameTime;
		thunderPoolNearby = scanForThunderPool();
		return thunderPoolNearby;
	}

	private boolean scanForThunderPool() {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = -THUNDER_POOL_VERTICAL_RADIUS; y <= THUNDER_POOL_VERTICAL_RADIUS; y++) {
			for (int x = -THUNDER_POOL_RADIUS; x <= THUNDER_POOL_RADIUS; x++) {
				for (int z = -THUNDER_POOL_RADIUS; z <= THUNDER_POOL_RADIUS; z++) {
					if (x * x + z * z > THUNDER_POOL_RADIUS * THUNDER_POOL_RADIUS) {
						continue;
					}
					cursor.setWithOffset(worldPosition, x, y, z);
					if (level.getBlockState(cursor).is(ModBlocks.THUNDER_POOL.get())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean canPowerTarget(BlockEntity target) {
		return target instanceof WirelessPowerReceiver receiver && receiver.canReceiveWirelessPower();
	}

	public List<BlockPos> getClientLinks() {
		return clientLinks;
	}

	// ====================================================================
	// 持久化 + 客户端同步
	// ====================================================================

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (BlockPos p : this.links) {
			CompoundTag t = new CompoundTag();
			t.putInt("x", p.getX());
			t.putInt("y", p.getY());
			t.putInt("z", p.getZ());
			list.add(t);
		}
		tag.put("links", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.links.clear();
		if (tag.contains("links", Tag.TAG_LIST)) {
			ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompound(i);
				this.links.add(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
			}
		}
	}

	/**
	 * 区块初次加载时把状态打包给客户端 —— 让 BER 立刻能拿到 {@link #clientLinks} 渲染光束，
	 * 而不是要等下一次 {@link #markForUpdate} 触发增量同步。
	 */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.getUpdateTag(registries);
		ListTag list = new ListTag();
		// 用服务端权威的 {@link #links} 而不是 {@link #lastSyncedLinks} ——
		// lastSyncedLinks 要等网络管理器 tick 后才更新，玩家绑定后需要等几秒才同步。
		// 用 links 则在 addLink/removeLink 之后的下一帧就能同步到客户端。
		for (BlockPos p : this.links) {
			CompoundTag t = new CompoundTag();
			t.putInt("x", p.getX());
			t.putInt("y", p.getY());
			t.putInt("z", p.getZ());
			list.add(t);
		}
		tag.put("client_links", list);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		super.handleUpdateTag(tag, registries);
		readClientLinks(tag);
	}

	@Nullable
	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		// ClientboundBlockEntityDataPacket 自动调用 saveCustomOnly(getUpdateTag) 走 getUpdateTag 路径
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
		// 1.21.1：onDataPacket 默认调 handleUpdateTag，但显式重写更清晰
		CompoundTag tag = pkt.getTag();
		if (tag != null) {
			readClientLinks(tag);
		}
	}

	private void readClientLinks(CompoundTag tag) {
		if (!tag.contains("client_links", Tag.TAG_LIST)) {
			return;
		}
		ListTag list = tag.getList("client_links", Tag.TAG_COMPOUND);
		List<BlockPos> next = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			next.add(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
		}
		this.clientLinks = next;
	}

	private void markForUpdate() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	// ====================================================================
	// 渲染包围盒（必须包含所有连接目标，否则远距离光束会被剔除）
	// ====================================================================

	@OnlyIn(Dist.CLIENT)
	public AABB getRenderBoundingBox() {
		if (clientLinks == null || clientLinks.isEmpty()) {
			BlockPos pos = getBlockPos();
			return new AABB(pos.getX() - 5, pos.getY() - 5, pos.getZ() - 5,
					pos.getX() + 6, pos.getY() + 8, pos.getZ() + 6);
		}

		BlockPos pos = getBlockPos();
		double minX = pos.getX();
		double minY = pos.getY();
		double minZ = pos.getZ();
		double maxX = pos.getX() + 1;
		double maxY = pos.getY() + 3; // 塔高 3 格
		double maxZ = pos.getZ() + 1;

		for (BlockPos target : clientLinks) {
			minX = Math.min(minX, target.getX());
			minY = Math.min(minY, target.getY());
			minZ = Math.min(minZ, target.getZ());
			maxX = Math.max(maxX, target.getX() + 1);
			maxY = Math.max(maxY, target.getY() + 1);
			maxZ = Math.max(maxZ, target.getZ() + 1);
		}

		double expansion = 5.0;
		return new AABB(minX - expansion, minY - expansion, minZ - expansion,
				maxX + expansion, maxY + expansion, maxZ + expansion);
	}

	// ====================================================================
	// IEnergyStorage（int 接口，兼容 NeoForge 标准 API；clamp 到 Integer.MAX_VALUE）
	// ====================================================================

	@Override
	public int receiveEnergy(int maxReceive, boolean simulate) {
		return (int) Math.min(receiveEnergyL(maxReceive, simulate), Integer.MAX_VALUE);
	}

	@Override
	public int extractEnergy(int maxExtract, boolean simulate) {
		return (int) Math.min(extractEnergyL(maxExtract, simulate), Integer.MAX_VALUE);
	}

	@Override
	public int getEnergyStored() {
		return (int) Math.min(getEnergyStoredL(), Integer.MAX_VALUE);
	}

	@Override
	public int getMaxEnergyStored() {
		return (int) Math.min(getMaxEnergyStoredL(), Integer.MAX_VALUE);
	}

	@Override
	public boolean canExtract() {
		if (level == null || !isOperational()) {
			return false;
		}
		for (Direction dir : DIRECTIONS) {
			BlockPos neighborPos = worldPosition.relative(dir);
			BlockEntity neighborBE = level.getBlockEntity(neighborPos);
			if (neighborBE != null && !(neighborBE instanceof WirelessPowerTowerBlockEntity)) {
				if (EnergyStorageHelper.canExtract(neighborBE, dir.getOpposite())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean canReceive() {
		return level != null && isOperational() && !links.isEmpty();
	}

	// ====================================================================
	// ILongEnergyStorage（long 接口；外部可以 long→long 灌进塔，由塔分配给 links）
	// ====================================================================

	@Override
	public long receiveEnergyL(long maxReceive, boolean simulate) {
		if (level == null || maxReceive <= 0 || links.isEmpty() || !isOperational()) {
			return 0;
		}

		long totalInserted = 0;
		for (BlockPos targetPos : links) {
			if (totalInserted >= maxReceive) {
				break;
			}
			BlockEntity targetBE = level.getBlockEntity(targetPos);
			if (targetBE == null) {
				continue;
			}

			long remaining = maxReceive - totalInserted;

			// 目标是塔：用 BFS 遍历整个塔网络（防循环）
			if (targetBE instanceof WirelessPowerTowerBlockEntity targetTower) {
				Set<BlockPos> visited = new HashSet<>();
				visited.add(this.worldPosition);
				long inserted = targetTower.distributeEnergyInNetwork(remaining, simulate, visited);
				totalInserted += inserted;
			} else {
				// 目标是设备：先试 null side，再 6 面
				long inserted = EnergyStorageHelper.insertEnergy(targetBE, null, remaining, simulate);
				if (inserted <= 0) {
					for (Direction dir : DIRECTIONS) {
						inserted = EnergyStorageHelper.insertEnergy(targetBE, dir, remaining, simulate);
						if (inserted > 0) {
							break;
						}
					}
				}
				if (inserted > 0) {
					totalInserted += inserted;
				}
			}
		}
		return totalInserted;
	}

	@Override
	public long extractEnergyL(long maxExtract, boolean simulate) {
		if (level == null || maxExtract <= 0 || !isOperational()) {
			return 0;
		}
		// 从邻居能量源直接抽（第一个能输出的方向就用）
		for (Direction dir : DIRECTIONS) {
			BlockPos neighborPos = worldPosition.relative(dir);
			BlockEntity neighborBE = level.getBlockEntity(neighborPos);
			if (neighborBE == null || neighborBE instanceof WirelessPowerTowerBlockEntity) {
				continue;
			}
			long extracted = EnergyStorageHelper.extractEnergy(neighborBE, dir.getOpposite(), maxExtract, simulate);
			if (extracted > 0) {
				return extracted;
			}
		}
		return 0;
	}

	@Override
	public long getEnergyStoredL() {
		if (level == null || !isOperational()) {
			return 0;
		}
		long total = 0;
		for (Direction dir : DIRECTIONS) {
			BlockPos neighborPos = worldPosition.relative(dir);
			BlockEntity neighborBE = level.getBlockEntity(neighborPos);
			if (neighborBE != null && !(neighborBE instanceof WirelessPowerTowerBlockEntity)) {
				total += EnergyStorageHelper.getEnergyStored(neighborBE, dir.getOpposite());
			}
		}
		return total;
	}

	@Override
	public long getMaxEnergyStoredL() {
		// 塔本身没有内部缓冲 —— 所有传输都是从邻居源直通到目标。返回大值表示"可处理 long 级"。
		return Long.MAX_VALUE;
	}
}
