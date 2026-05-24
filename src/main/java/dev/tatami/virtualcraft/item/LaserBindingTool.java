package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.block.WirelessPowerTowerBlock;
import dev.tatami.virtualcraft.energy.ILinkable;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import dev.tatami.virtualcraft.energy.WirelessEnergyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * 光束绑定器（{@code virtual_craft:laser_binding_tool}）。
 *
 * <p>1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的 {@code LaserBindingTool} 交互流程，
 * 但去掉了 {@code OmniBeamFormerBlockEntity} 分支（VirtualCraft 工作区没有 AE2 / 光束成型器）。</p>
 *
 * <h3>玩家交互（与原版完全一致）</h3>
 * <ul>
 *   <li><b>Shift + 右键 塔</b>：把该塔记为"源"（{@code SourceType=tower}）。</li>
 *   <li><b>Shift + 右键 有能量能力的机器（已选源是塔）</b>：塔→机器单向绑定（toggle 加/移除）。
 *       距离限制：水平 XZ ≤ {@link WirelessEnergyNetwork#TOWER_RANGE_XZ 20}、垂直 Y ≤ {@link WirelessEnergyNetwork#TOWER_RANGE_Y 256}。</li>
 *   <li><b>普通右键 塔（已选源是塔）</b>：塔↔塔双向绑定（toggle，源和目标都加/移除对方）。</li>
 *   <li><b>Shift + 右键空气</b>：清空已选源（{@code use} 而不是 {@code useOn}，因为没有点到方块）。</li>
 * </ul>
 *
 * <h3>NBT 存储</h3>
 * <p>NeoForge 1.21.1 移除了 {@code stack.getTag()}，改用 {@link DataComponents#CUSTOM_DATA}。
 * 我们把 {@code SourcePos} / {@code SourceType} 写到 {@link CustomData}，整个 tool 状态都附在 stack 上，
 * 玩家把工具丢进物品栏/容器再拿出来后绑定状态保留。</p>
 */
public class LaserBindingTool extends Item {

	static final String TAG_SOURCE = "SourcePos";
	static final String TAG_SOURCE_TYPE = "SourceType";
	static final String TYPE_TOWER = "tower";

	/**
	 * 批量绑定锁。{@link dev.tatami.virtualcraft.client.binding.LaserBindingClientHandler} 在检测到
	 * Ctrl+Shift+右键机器后会发 {@link dev.tatami.virtualcraft.network.LaserBatchBindPayload}，同时 {@code cancel}
	 * 客户端事件。但客户端 cancel <b>不</b>能阻止服务端独立触发的 {@code useOn}——服务端那边会接着对
	 * {@code start} 机器再 toggle 一次，把批量绑定刚刚干的活对冲掉。
	 *
	 * <p>修法：{@code bulkBindNeighbors} 处理完后在 ItemStack 上写一个一次性锁，{@code useOn} 服务端
	 * 入口检测到锁就 early return 并消费掉。NeoForge 的 packet 处理是 TCP 保序的，且两者都走
	 * {@code enqueueWork}（主线程任务队列），所以 lock 写入一定发生在 {@code useOn} 运行之前。</p>
	 */
	static final String TAG_BATCH_LOCK = "BatchLock";

	/** 1:1 沿用原版 NeoForge 工具签名：{@code Properties} 由调用方提供（一般 stacksTo(1)）。 */
	public LaserBindingTool(Properties props) {
		super(props);
	}

	// ====================================================================
	// 工具方法
	// ====================================================================

	/**
	 * 给玩家点到的塔任何一段，返回其底部位置。塔的 BE 只在底部，所以所有 {@code addLink/removeLink} 都得用底部坐标。
	 * <p>静态方法以便 {@link dev.tatami.virtualcraft.network.LaserBatchBindPayload} 等其他位置复用。</p>
	 */
	static BlockPos getTowerBasePos(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof WirelessPowerTowerBlock) {
			int part = state.getValue(WirelessPowerTowerBlock.PART);
			return pos.below(part);
		}
		return pos;
	}

	static WirelessPowerTowerBlockEntity getTowerBlockEntity(Level level, BlockPos pos) {
		BlockPos basePos = getTowerBasePos(level, pos);
		BlockEntity be = level.getBlockEntity(basePos);
		return be instanceof WirelessPowerTowerBlockEntity tower ? tower : null;
	}

	/** 目标 BE 是否有 NeoForge 能量能力（任意一面）。 */
	static boolean hasEnergyCapability(BlockEntity be) {
		if (be == null || be.getLevel() == null) {
			return false;
		}
		Level lvl = be.getLevel();
		for (Direction dir : Direction.values()) {
			if (lvl.getCapability(Capabilities.EnergyStorage.BLOCK,
					be.getBlockPos(), be.getBlockState(), be, dir) != null) {
				return true;
			}
		}
		return false;
	}

	static CompoundTag readTag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	static void writeTag(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	static void msg(Player player, String key, Object... args) {
		if (player != null) {
			player.displayClientMessage(Component.translatable(key, args), true);
		}
	}

	static boolean isInTowerRange(BlockPos source, BlockPos target) {
		int dx = Math.abs(target.getX() - source.getX());
		int dy = Math.abs(target.getY() - source.getY());
		int dz = Math.abs(target.getZ() - source.getZ());
		return dx <= WirelessEnergyNetwork.TOWER_RANGE_XZ
				&& dz <= WirelessEnergyNetwork.TOWER_RANGE_XZ
				&& dy <= WirelessEnergyNetwork.TOWER_RANGE_Y;
	}

	// ====================================================================
	// 主交互入口
	// ====================================================================

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockPos clickedPos = ctx.getClickedPos();
		ItemStack stack = ctx.getItemInHand();
		Player player = ctx.getPlayer();

		// === 批量锁检测 ===
		// LaserBatchBindPayload.handle 中在 bulkBindNeighbors 后设了锁。如果现在走到 useOn，说明
		// 是客户端 Ctrl+Shift+右键同时发的 vanilla 右键包跟后过来了——跳过本次处理，避免把刚刚
		// 批量 toggle 过的 start 机器反转。
		{
			CompoundTag toolTag = readTag(stack);
			if (toolTag.getBoolean(TAG_BATCH_LOCK)) {
				toolTag.remove(TAG_BATCH_LOCK);
				writeTag(stack, toolTag);
				return InteractionResult.SUCCESS;
			}
		}

		BlockPos pos = getTowerBasePos(level, clickedPos);
		BlockEntity be = level.getBlockEntity(pos);

		BlockState clickedState = level.getBlockState(clickedPos);
		boolean isClickedTower = clickedState.getBlock() instanceof WirelessPowerTowerBlock;

		CompoundTag tag = readTag(stack);
		boolean hasSource = tag.contains(TAG_SOURCE);
		boolean isLinkable = be instanceof ILinkable;
		boolean hasEnergy = hasEnergyCapability(be);
		// Ctrl+Shift+右键 机器 会被客户端 hook 在服务端调用之前发出 LaserBatchBindPayload
		// 并取消 useOn。这里不需要额外处理，单点 toggle 逻辑不受影响。

		if (player != null && player.isShiftKeyDown()) {
			// ============================================================
			// Shift + 右键
			// ============================================================
			if (hasSource) {
				String sourceType = tag.getString(TAG_SOURCE_TYPE);
				if (TYPE_TOWER.equals(sourceType)) {
					// 源是塔：Shift+右键只用于绑定有能量能力的机器
					if (isClickedTower) {
						msg(player, "tooltip.virtual_craft.binding.tower_to_tower_needs_normal_click");
						return InteractionResult.SUCCESS;
					} else if (isLinkable) {
						// 目标是其他 ILinkable —— 落到下面"选新源"逻辑
					} else if (hasEnergy) {
						return handleTowerToMachine(level, tag, player, pos);
					} else {
						return InteractionResult.PASS;
					}
				}
				// 源不是塔（理论上 VirtualCraft 不会进这分支，因为塔是唯一 ILinkable），落到下面
			}

			// 选定新源
			if (!isLinkable) {
				return InteractionResult.PASS;
			}
			CompoundTag posTag = new CompoundTag();
			posTag.putInt("x", pos.getX());
			posTag.putInt("y", pos.getY());
			posTag.putInt("z", pos.getZ());
			tag.put(TAG_SOURCE, posTag);
			tag.putString(TAG_SOURCE_TYPE, TYPE_TOWER);
			writeTag(stack, tag);
			msg(player, "tooltip.virtual_craft.binding.set_tower", pos.getX(), pos.getY(), pos.getZ());
			return InteractionResult.CONSUME;
		}

		// ================================================================
		// 普通右键
		// ================================================================
		if (!hasSource) {
			if (isLinkable) {
				msg(player, "tooltip.virtual_craft.binding.no_source");
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		String sourceType = tag.getString(TAG_SOURCE_TYPE);
		if (TYPE_TOWER.equals(sourceType)) {
			if (!isClickedTower) {
				msg(player, "tooltip.virtual_craft.binding.tower_needs_shift");
				return InteractionResult.SUCCESS;
			}
			return handleTowerToTower(level, tag, player, pos);
		}

		// VirtualCraft 当前没有其他 source type；fallback 提示
		msg(player, "tooltip.virtual_craft.binding.invalid");
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide && player.isShiftKeyDown()) {
			CompoundTag tag = readTag(stack);
			if (tag.contains(TAG_SOURCE)) {
				tag.remove(TAG_SOURCE);
				tag.remove(TAG_SOURCE_TYPE);
				writeTag(stack, tag);
				msg(player, "tooltip.virtual_craft.binding.cleared");
				return InteractionResultHolder.consume(stack);
			}
		}
		return super.use(level, player, hand);
	}

	// ====================================================================
	// 绑定子流程
	// ====================================================================

	/** 塔 → 机器（单向）绑定 toggle。 */
	private InteractionResult handleTowerToMachine(Level level, CompoundTag tag, Player player, BlockPos targetPos) {
		CompoundTag posTag = tag.getCompound(TAG_SOURCE);
		BlockPos source = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));

		if (source.equals(targetPos)) {
			msg(player, "tooltip.virtual_craft.binding.self_link");
			return InteractionResult.SUCCESS;
		}

		WirelessPowerTowerBlockEntity sourceEntity = getTowerBlockEntity(level, source);
		if (sourceEntity == null) {
			tag.remove(TAG_SOURCE);
			tag.remove(TAG_SOURCE_TYPE);
			msg(player, "tooltip.virtual_craft.binding.invalid");
			return InteractionResult.SUCCESS;
		}

		if (!isInTowerRange(source, targetPos)) {
			msg(player, "tooltip.virtual_craft.binding.tower_out_of_range");
			return InteractionResult.SUCCESS;
		}

		if (sourceEntity.getLinks().contains(targetPos)) {
			sourceEntity.removeLink(targetPos);
			msg(player, "tooltip.virtual_craft.binding.tower_unlinked",
					source.getX(), source.getY(), source.getZ(),
					targetPos.getX(), targetPos.getY(), targetPos.getZ());
		} else {
			sourceEntity.addLink(targetPos);
			msg(player, "tooltip.virtual_craft.binding.tower_linked",
					source.getX(), source.getY(), source.getZ(),
					targetPos.getX(), targetPos.getY(), targetPos.getZ());
		}
		return InteractionResult.SUCCESS;
	}

	/** 塔 ↔ 塔（双向）绑定 toggle。 */
	private InteractionResult handleTowerToTower(Level level, CompoundTag tag, Player player, BlockPos targetPos) {
		CompoundTag posTag = tag.getCompound(TAG_SOURCE);
		BlockPos source = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));

		if (source.equals(targetPos)) {
			msg(player, "tooltip.virtual_craft.binding.self_link");
			return InteractionResult.SUCCESS;
		}

		if (!isInTowerRange(source, targetPos)) {
			msg(player, "tooltip.virtual_craft.binding.tower_out_of_range");
			return InteractionResult.SUCCESS;
		}

		WirelessPowerTowerBlockEntity sourceEntity = getTowerBlockEntity(level, source);
		WirelessPowerTowerBlockEntity targetEntity = getTowerBlockEntity(level, targetPos);
		if (sourceEntity == null || targetEntity == null) {
			tag.remove(TAG_SOURCE);
			tag.remove(TAG_SOURCE_TYPE);
			msg(player, "tooltip.virtual_craft.binding.invalid");
			return InteractionResult.SUCCESS;
		}

		boolean isLinked = sourceEntity.getLinks().contains(targetPos);
		if (isLinked) {
			sourceEntity.removeLink(targetPos);
			targetEntity.removeLink(source);
			msg(player, "tooltip.virtual_craft.binding.tower_to_tower_unlinked",
					source.getX(), source.getY(), source.getZ(),
					targetPos.getX(), targetPos.getY(), targetPos.getZ());
		} else {
			sourceEntity.addLink(targetPos);
			targetEntity.addLink(source);
			msg(player, "tooltip.virtual_craft.binding.tower_to_tower_linked",
					source.getX(), source.getY(), source.getZ(),
					targetPos.getX(), targetPos.getY(), targetPos.getZ());
		}
		return InteractionResult.SUCCESS;
	}

}
