package dev.tatami.virtualcraft.gameplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 把「数据矿脉下潜」接进世界事件:
 * <ul>
 *   <li>破坏维度内的矿脉方块 → 进本局算力袋(不掉原矿,撤离才结算)。</li>
 *   <li>维度内阵亡 → 提示本局算力袋已丢失(状态非 copyOnDeath,重生自动清零)。</li>
 *   <li>每 tick 探测玩家是否踩在下行井上 → 深入下一层;站在撤离台上给提示。</li>
 * </ul>
 */
public final class DataMineEvents {
	private static final double SHAFT_RADIUS_SQ = 2.2D * 2.2D;
	private static final double PAD_RADIUS_SQ = 2.2D * 2.2D;
	private static final long DESCEND_COOLDOWN_TICKS = 20L;
	private static final Map<UUID, Long> DESCEND_COOLDOWN = new HashMap<>();

	private DataMineEvents() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(DataMineEvents::onBlockBreak);
		bus.addListener(DataMineEvents::onDeath);
		bus.addListener(DataMineEvents::onServerTick);
	}

	private static void onBlockBreak(BlockEvent.BreakEvent event) {
		LevelAccessor accessor = event.getLevel();
		if (!(accessor instanceof ServerLevel level) || !ModDimensions.VIRTUAL_WORLD.equals(level.dimension())) {
			return;
		}
		if (!(event.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		BlockState state = event.getState();
		if (!state.is(ModBlocks.ELECTRIC_ORE.get()) && !state.is(ModBlocks.DEEPSLATE_ELECTRIC_ORE.get())) {
			return;
		}
		DataMineRunState run = player.getData(ModAttachments.DATA_MINE_RUN);
		if (!run.isActive()) {
			return;
		}
		// 不掉原矿,统一进算力袋;手动移除以确保无掉落且即时同步。
		event.setCanceled(true);
		level.removeBlock(event.getPos(), false);
		run.addOre(1);
		player.setData(ModAttachments.DATA_MINE_RUN, run);
		player.displayClientMessage(
				Component.translatable("message.virtual_craft.data_mine.mined", 1, run.oreUnits())
						.withStyle(ChatFormatting.AQUA),
				true);
	}

	private static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!ModDimensions.VIRTUAL_WORLD.equals(player.level().dimension())) {
			return;
		}
		DataMineRunState run = player.getData(ModAttachments.DATA_MINE_RUN);
		if (!run.isActive() || run.oreUnits() <= 0) {
			return;
		}
		// 状态非 copyOnDeath:重生后自动得到空状态,这里只负责提示损失。
		player.displayClientMessage(
				Component.translatable("message.virtual_craft.data_mine.death", run.oreUnits())
						.withStyle(ChatFormatting.RED),
				false);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(ModDimensions.VIRTUAL_WORLD);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : level.players()) {
			DataMineRunState run = player.getData(ModAttachments.DATA_MINE_RUN);
			if (!run.isActive()) {
				continue;
			}
			if (run.hasShaft() && onMarker(player, run.shaftX(), run.shaftY(), run.shaftZ(), SHAFT_RADIUS_SQ)) {
				Long until = DESCEND_COOLDOWN.get(player.getUUID());
				if (until == null || now >= until) {
					DESCEND_COOLDOWN.put(player.getUUID(), now + DESCEND_COOLDOWN_TICKS);
					VirtualDataRelicManager.descend(player, level);
				}
				continue;
			}
			if (run.hasPad() && onMarker(player, run.padX(), run.padY(), run.padZ(), PAD_RADIUS_SQ) && now % 40L == 0L) {
				player.displayClientMessage(
						Component.translatable("message.virtual_craft.data_mine.extract_hint", run.oreUnits())
								.withStyle(ChatFormatting.YELLOW),
						true);
			}
		}
	}

	private static boolean onMarker(ServerPlayer player, int x, int y, int z, double radiusSq) {
		double dx = player.getX() - (x + 0.5D);
		double dz = player.getZ() - (z + 0.5D);
		double dy = player.getY() - y;
		return Math.abs(dy) <= 3.0D && (dx * dx + dz * dz) <= radiusSq;
	}

	/** 维度卸载等场景的内存清理,避免 cooldown map 无限增长。 */
	public static void forget(UUID playerId) {
		DESCEND_COOLDOWN.remove(playerId);
	}
}
