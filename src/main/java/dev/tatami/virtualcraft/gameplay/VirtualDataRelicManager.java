package dev.tatami.virtualcraft.gameplay;

import java.util.List;

import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 「数据矿脉下潜」的建造与流程中枢(取代旧的固定房间/点方块谜题)。
 *
 * <p>核心循环:进入维度=开新一局(随机种子,落到第 1 层) → 每层是
 * {@link DataMineFloor} 程序化生成的有机矿洞(石/深板岩/黑石主题随深度切换,
 * 矿脉越深越密) → 踩下行井深入下一层(更险更肥) → 潜行右键计算机撤离,
 * 把本局「算力袋」按最深层数折算成碎片 + 分级算力券。阵亡丢本局算力袋。</p>
 *
 * <p>同一局内每层由 {@code (本局种子, 层号)} 确定性生成;不同局重新洗牌。
 * 楼层在玩家专属 sector 内用两个槽位双缓冲(depth 的奇偶),永不在玩家脚下重建。</p>
 */
public final class VirtualDataRelicManager {
	private static final int BASE_Y = 100;
	private static final int SLOT_GAP = 64;
	private static final int W = DataMineFloor.WIDTH;
	private static final int D = DataMineFloor.DEPTH;
	private static final int INTERIOR = DataMineFloor.INTERIOR_HEIGHT;

	private VirtualDataRelicManager() {
	}

	/**
	 * Computer 进入维度时调用:开新一局并建好第 1 层,返回落点。
	 */
	public static BlockPos ensureSector(ServerPlayer player, ServerLevel level) {
		if (!ModDimensions.VIRTUAL_WORLD.equals(level.dimension())) {
			throw new IllegalArgumentException("Data mine floors can only be generated in the virtual world");
		}
		DataMineRunState state = player.getData(ModAttachments.DATA_MINE_RUN);
		long seed = player.getUUID().getMostSignificantBits()
				^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 19)
				^ (level.getGameTime() * 0x9E3779B97F4A7C15L)
				^ level.getRandom().nextLong();
		state.startRun(seed);
		BlockPos landing = buildFloor(player, level, state, 1);
		player.setData(ModAttachments.DATA_MINE_RUN, state);
		player.displayClientMessage(
				Component.translatable("message.virtual_craft.data_mine.enter", 1).withStyle(ChatFormatting.AQUA),
				true);
		return landing;
	}

	/** tick 探测到玩家踩在下行井上时调用:深入下一层并传送。 */
	public static void descend(ServerPlayer player, ServerLevel level) {
		DataMineRunState state = player.getData(ModAttachments.DATA_MINE_RUN);
		if (!state.isActive()) {
			return;
		}
		int next = state.depth() + 1;
		state.setDepth(next);
		BlockPos landing = buildFloor(player, level, state, next);
		player.setData(ModAttachments.DATA_MINE_RUN, state);
		player.teleportTo(level, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
				player.getYRot(), player.getXRot());
		level.playSound(null, landing, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.7F);
		player.displayClientMessage(
				Component.translatable("message.virtual_craft.data_mine.descend", next).withStyle(ChatFormatting.GOLD),
				true);
	}

	/**
	 * 撤离结算:把本局算力袋按最深层数折算成碎片 + 分级算力券发给玩家,然后清空本局。
	 * 由 Computer 退出路径在传送回主世界之前调用。
	 */
	public static void settleRun(ServerPlayer player) {
		DataMineRunState state = player.getData(ModAttachments.DATA_MINE_RUN);
		if (!state.isActive() || state.oreUnits() <= 0) {
			int cleared = state.reset();
			player.setData(ModAttachments.DATA_MINE_RUN, state);
			if (cleared <= 0) {
				player.displayClientMessage(
						Component.translatable("message.virtual_craft.data_mine.settle_empty")
								.withStyle(ChatFormatting.GRAY),
						false);
			}
			return;
		}

		int ore = state.oreUnits();
		int deepest = state.deepest();
		int fragments = DataMineRewards.fragments(ore, deepest);
		int rolls = DataMineRewards.voucherRolls(ore, deepest);
		RandomSource rng = player.level().getRandom();

		int[] voucherByTier = new int[DataMineRewards.VOUCHER_TIERS];
		int voucherTotal = 0;
		for (int i = 0; i < rolls; i++) {
			// 从最高档往下试,命中即止:高档稀有、低档常见。
			for (int tier = DataMineRewards.VOUCHER_TIERS - 1; tier >= 0; tier--) {
				if (rng.nextFloat() < DataMineRewards.voucherChance(tier, deepest)) {
					voucherByTier[tier]++;
					voucherTotal++;
					break;
				}
			}
		}

		give(player, new ItemStack(ModItems.DATA_FRAGMENT.get()), fragments);
		giveVouchers(player, voucherByTier);

		state.reset();
		player.setData(ModAttachments.DATA_MINE_RUN, state);
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F,
				1.2F);
		player.displayClientMessage(
				Component.translatable("message.virtual_craft.data_mine.settle", deepest, fragments, voucherTotal)
						.withStyle(ChatFormatting.GREEN),
				false);
	}

	private static void giveVouchers(ServerPlayer player, int[] voucherByTier) {
		give(player, ModItems.HASH_VOUCHER.get().getDefaultInstance(), voucherByTier[0]);
		give(player, ModItems.EXPLORER_HASH_VOUCHER.get().getDefaultInstance(), voucherByTier[1]);
		give(player, ModItems.ENCRYPTED_HASH_VOUCHER.get().getDefaultInstance(), voucherByTier[2]);
		give(player, ModItems.QUANTUM_HASH_VOUCHER.get().getDefaultInstance(), voucherByTier[3]);
		give(player, ModItems.GENESIS_HASH_VOUCHER.get().getDefaultInstance(), voucherByTier[4]);
	}

	private static void give(ServerPlayer player, ItemStack template, int count) {
		int remaining = count;
		int max = template.getMaxStackSize();
		while (remaining > 0) {
			int give = Math.min(max, remaining);
			ItemStack stack = template.copy();
			stack.setCount(give);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= give;
		}
	}

	/**
	 * 建造第 {@code depth} 层(全量覆写槽位 footprint),刷怪、放矿脉/下行井/撤离台,
	 * 把下行井与撤离台坐标写进 run 状态,返回入口落点。
	 */
	private static BlockPos buildFloor(ServerPlayer player, ServerLevel level, DataMineRunState state, int depth) {
		DataMineFloor floor = DataMineFloor.generate(state.seed(), depth);
		Theme theme = themeFor(depth);
		int ox = sectorX(player) + (depth & 1) * SLOT_GAP;
		int oz = sectorZ(player);
		int slabY = BASE_Y - 1;
		int ceilY = BASE_Y + INTERIOR;

		// 先清掉该槽位里残留的旧怪(楼层方块会被覆写,但实体不会)。
		AABB slotBox = new AABB(ox - 1, slabY - 1, oz - 1, ox + W + 1, ceilY + 2, oz + D + 1);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, slotBox)) {
			monster.discard();
		}

		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				int wx = ox + x;
				int wz = oz + z;
				setBlock(level, wx, slabY, wz, theme.floor);
				setBlock(level, wx, ceilY, wz, theme.ceiling);
				boolean open = floor.isOpen(x, z);
				for (int y = BASE_Y; y < ceilY; y++) {
					setBlock(level, wx, y, wz, open ? air : theme.wall);
				}
				// 顶部稀疏布光,免得纯黑看不见。
				if (open && (x % 6 == 0) && (z % 6 == 0)) {
					setBlock(level, wx, ceilY - 1, wz, Blocks.SEA_LANTERN.defaultBlockState());
				}
			}
		}

		for (int[] vein : floor.oreVeins()) {
			placeVein(level, ox + vein[0], oz + vein[1], theme);
		}

		// 下行井:发光台 + 中心标柱,踩上去深入下一层。
		int sx = ox + floor.shaftX();
		int sz = oz + floor.shaftZ();
		placeShaft(level, sx, sz);
		state.setShaft(sx, BASE_Y, sz);

		// 撤离台:入口处金台,提示「潜行右键计算机撤离结算」。
		int px = ox + floor.padX();
		int pz = oz + floor.padZ();
		placePad(level, px, pz);
		state.setPad(px, BASE_Y, pz);

		spawnMobs(level, floor, ox, oz, depth);

		return new BlockPos(ox + floor.entryX(), BASE_Y, oz + floor.entryZ());
	}

	private static void placeVein(ServerLevel level, int cx, int cz, Theme theme) {
		BlockState ore = theme.ore;
		setBlock(level, cx, BASE_Y, cz, ore);
		setBlock(level, cx, BASE_Y + 1, cz, ore);
		setBlock(level, cx, BASE_Y + 2, cz, ore);
		setBlock(level, cx + 1, BASE_Y, cz, ore);
		setBlock(level, cx - 1, BASE_Y, cz, ore);
		setBlock(level, cx, BASE_Y, cz + 1, ore);
		setBlock(level, cx, BASE_Y, cz - 1, ore);
		if (theme.corrupted) {
			setBlock(level, cx + 1, BASE_Y + 1, cz, ModBlocks.CORRUPTED_DATA_NODE.get().defaultBlockState());
		}
	}

	private static void placeShaft(ServerLevel level, int sx, int sz) {
		BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				setBlock(level, sx + dx, BASE_Y - 1, sz + dz, lantern);
			}
		}
		setBlock(level, sx, BASE_Y, sz, Blocks.AIR.defaultBlockState());
		setBlock(level, sx, BASE_Y + 1, sz, Blocks.AIR.defaultBlockState());
		setBlock(level, sx, BASE_Y + 3, sz, Blocks.GLOWSTONE.defaultBlockState());
		// 四角标柱,远处可见。
		setBlock(level, sx + 1, BASE_Y, sz + 1, Blocks.LODESTONE.defaultBlockState());
		setBlock(level, sx - 1, BASE_Y, sz - 1, Blocks.LODESTONE.defaultBlockState());
		setBlock(level, sx + 1, BASE_Y, sz - 1, Blocks.LODESTONE.defaultBlockState());
		setBlock(level, sx - 1, BASE_Y, sz + 1, Blocks.LODESTONE.defaultBlockState());
	}

	private static void placePad(ServerLevel level, int px, int pz) {
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				setBlock(level, px + dx, BASE_Y - 1, pz + dz, gold);
			}
		}
		setBlock(level, px, BASE_Y + 3, pz, Blocks.SEA_LANTERN.defaultBlockState());
	}

	private static void spawnMobs(ServerLevel level, DataMineFloor floor, int ox, int oz, int depth) {
		List<int[]> spawns = floor.mobSpawns();
		RandomSource rng = level.getRandom();
		for (int[] spot : spawns) {
			int wx = ox + spot[0];
			int wz = oz + spot[1];
			EntityType<?> type = pickMob(depth, rng);
			Entity entity = type.create(level);
			if (!(entity instanceof Mob mob)) {
				continue;
			}
			BlockPos pos = new BlockPos(wx, BASE_Y, wz);
			mob.moveTo(wx + 0.5D, BASE_Y, wz + 0.5D, rng.nextFloat() * 360F, 0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
		}
	}

	private static EntityType<?> pickMob(int depth, RandomSource rng) {
		EntityType<?>[] pool;
		if (depth <= 3) {
			pool = new EntityType<?>[] { EntityType.ZOMBIE, EntityType.SPIDER };
		} else if (depth <= 7) {
			pool = new EntityType<?>[] { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK };
		} else {
			pool = new EntityType<?>[] { EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.SPIDER,
					EntityType.STRAY, EntityType.ZOMBIE };
		}
		return pool[rng.nextInt(pool.length)];
	}

	private static Theme themeFor(int depth) {
		if (depth <= 3) {
			return new Theme(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
					Blocks.STONE.defaultBlockState(), ModBlocks.ELECTRIC_ORE.get().defaultBlockState(), false);
		}
		if (depth <= 7) {
			return new Theme(Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
					Blocks.DEEPSLATE.defaultBlockState(), ModBlocks.DEEPSLATE_ELECTRIC_ORE.get().defaultBlockState(),
					false);
		}
		return new Theme(Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
				Blocks.BLACKSTONE.defaultBlockState(), ModBlocks.DEEPSLATE_ELECTRIC_ORE.get().defaultBlockState(),
				true);
	}

	private static void setBlock(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}

	private static int sectorX(ServerPlayer player) {
		return VirtualDataSectorMath.originFor(player.getUUID()).x();
	}

	private static int sectorZ(ServerPlayer player) {
		return VirtualDataSectorMath.originFor(player.getUUID()).z();
	}

	/** 一层的方块主题。 */
	private record Theme(BlockState floor, BlockState wall, BlockState ceiling, BlockState ore, boolean corrupted) {
	}
}
