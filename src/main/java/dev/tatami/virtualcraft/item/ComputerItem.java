package dev.tatami.virtualcraft.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import dev.tatami.virtualcraft.gameplay.VirtualWorldReturnPoint;
import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModDimensions;

/**
 * 移植自旧 1.16.5 工程 {@code Computer + ComputerDangYouJianDianJiKongQiShi}，并按新工程的需求做了两处升级：
 * <ol>
 *   <li><b>单方块空岛</b>：虚拟世界在数据包中是完全空白的虚空（dimension JSON 的 layers 为空数组），
 *       玩家首次进入时由本类在 {@link #ISLAND_POS} 自动放置一块草方块作为孤岛——避免数据包硬塞 1 层基岩
 *       带来的 "整层地板" 视觉。</li>
 *   <li><b>原位返回</b>：进入虚拟世界前把玩家的 (dim, x, y, z, yaw, pitch) 写入
 *       {@link ModAttachments#VIRTUAL_WORLD_RETURN_POINT}，潜行右键退出时直接回到该坐标，而不是粗暴地把玩家
 *       丢回 OVERWORLD spawn——后者会导致玩家辛苦跑到的位置丢失。</li>
 * </ol>
 * 行为契约同旧版：
 * <ul>
 *   <li>玩家在 {@link ModDimensions#VIRTUAL_WORLD} 之外、且不是其它自定义维度（即主世界 / 下界 / 末地等）
 *       的任意维度右键 → 进入虚拟世界（旧版要求穿戴绝缘装备的门槛已按用户决策废弃）。</li>
 *   <li>玩家在 {@link ModDimensions#VIRTUAL_WORLD} 中潜行右键 → 切回返回点；非潜行则给 actionbar 提示。</li>
 * </ul>
 * 旧版的 electrocution 每 tick 掉血机制已按用户要求完全删除。
 */
public class ComputerItem extends Item {
	/**
	 * 全服共享的孤岛坐标。挑 (0, 64, 0) 是因为：
	 * <ul>
	 *   <li>x/z 为 0 让玩家容易找回原点；</li>
	 *   <li>y=64 在维度类型 {@code min_y=0, height=256} 的中段，往上往下都有足够空间，
	 *       即便玩家失误掉下去也有重力缓冲，方便用电脑再切一次回来。</li>
	 * </ul>
	 */
	private static final BlockPos ISLAND_POS = new BlockPos(0, 64, 0);
	public ComputerItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		// 切维度逻辑全部在服务端处理，客户端只回 sidedSuccess 触发挥手动画。
		if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}
		MinecraftServer server = serverPlayer.getServer();
		if (server == null) {
			return InteractionResultHolder.fail(stack);
		}

		ResourceKey<Level> currentDim = serverPlayer.level().dimension();
		if (ModDimensions.VIRTUAL_WORLD.equals(currentDim)) {
			// 在虚拟世界：潜行右键 → 退出到记录的返回点；否则提示。
			if (serverPlayer.isShiftKeyDown()) {
				exitVirtualWorld(serverPlayer, server);
			} else {
				serverPlayer.displayClientMessage(
						Component.translatable("message.virtual_craft.computer.exit_hint")
								.withStyle(ChatFormatting.GRAY),
						true);
			}
		} else {
			// 任何非虚拟世界的维度都允许进入——旧版只允许 OVERWORLD，现在用户决策为
			// 无条件进入，所以下界/末地/其它 mod 维度也直接放行。返回点会忠实记录
			// 来源维度，潜行退出后能回到原位。
			ServerLevel target = server.getLevel(ModDimensions.VIRTUAL_WORLD);
			if (target != null) {
				enterVirtualWorld(serverPlayer, target);
			} else {
				// 维度 JSON 缺失或加载失败的兜底提示，避免静默无响应。
				serverPlayer.displayClientMessage(
						Component.translatable("message.virtual_craft.computer.dimension_missing")
								.withStyle(ChatFormatting.RED),
						true);
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/**
	 * 进入虚拟世界：先把玩家当前位置写入返回点 attachment，再确保孤岛方块存在，最后做
	 * {@link DimensionTransition} 切换。三步顺序很重要——返回点必须在 changeDimension 之前
	 * 捕获，否则取到的是已经在虚拟世界的坐标，潜行退出会自己把自己再传回来。
	 */
	private static void enterVirtualWorld(ServerPlayer player, ServerLevel target) {
		VirtualWorldReturnPoint rp = player.getData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT);
		rp.capture(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
		// AttachmentType.serializable 的实例已经是同一引用，但显式 setData 一次以触发
		// NeoForge 的同步/标记脏位，确保关服时落盘。
		player.setData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT, rp);

		ensureIslandBlock(target);

		// 站在岛屿方块顶面 + 半格中心，避免卡在方块内或半空起跳。
		Vec3 landingPos = new Vec3(
				ISLAND_POS.getX() + 0.5D,
				ISLAND_POS.getY() + 1.0D,
				ISLAND_POS.getZ() + 0.5D);
		DimensionTransition transition = new DimensionTransition(
				target,
				landingPos,
				Vec3.ZERO,
				player.getYRot(),
				player.getXRot(),
				DimensionTransition.DO_NOTHING);
		player.changeDimension(transition);
	}

	/**
	 * 退出虚拟世界：优先回到 attachment 中记录的返回点（含原维度、yaw、pitch）；
	 * 只有在 attachment 从未被捕获过（如玩家原本就出生在虚拟世界、或存档迁移）的极端情况下，
	 * 才退而求其次回到主世界 shared spawn——避免静默 "传错地方" 的体验。
	 */
	private static void exitVirtualWorld(ServerPlayer player, MinecraftServer server) {
		VirtualWorldReturnPoint rp = player.getData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT);
		ServerLevel target;
		Vec3 pos;
		float yaw;
		float pitch;
		if (rp.isValid()) {
			target = server.getLevel(rp.dimension());
			if (target == null) {
				// 来源维度被卸载/删除（自定义维度的 mod 被卸了等），fallback overworld。
				target = server.getLevel(Level.OVERWORLD);
			}
			pos = rp.position();
			yaw = rp.yRot();
			pitch = rp.xRot();
		} else {
			target = server.getLevel(Level.OVERWORLD);
			if (target == null) {
				return; // 主世界都没有的服务器是异常状态，让玩家留在虚拟世界更安全。
			}
			BlockPos spawn = target.getSharedSpawnPos();
			pos = new Vec3(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		DimensionTransition transition = new DimensionTransition(
				target,
				pos,
				Vec3.ZERO,
				yaw,
				pitch,
				DimensionTransition.DO_NOTHING);
		player.changeDimension(transition);
		// 消费返回点，避免下一次进入又把自己 "链" 回更早的位置。下一次 enterVirtualWorld
		// 必然会重新捕获，安全无副作用。
		rp.clear();
		player.setData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT, rp);
	}

	/**
	 * 单方块空岛的懒生成：第一次有玩家进入虚拟世界时才落子。如果玩家自己破坏了岛，下次再
	 * 进入会自动补回——这是有意为之，让虚拟世界永远 "至少有一个落脚点"。
	 */
	private static void ensureIslandBlock(ServerLevel target) {
		BlockState existing = target.getBlockState(ISLAND_POS);
		if (existing.isAir()) {
			// 选 grass_block 而非 bedrock：草方块顶面贴图友好、玩家可以挖走自由扩建，
			// 同时旁边的虚空 biome 让它在视觉上仍然像一片孤立岛。
			target.setBlockAndUpdate(ISLAND_POS, Blocks.GRASS_BLOCK.defaultBlockState());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.computer.use").withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("tooltip.virtual_craft.computer.exit").withStyle(ChatFormatting.GRAY));
	}
}
