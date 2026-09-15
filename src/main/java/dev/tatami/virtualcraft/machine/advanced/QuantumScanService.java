package dev.tatami.virtualcraft.machine.advanced;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import dev.tatami.virtualcraft.world.LightningForestEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public final class QuantumScanService {
	private static final int MAX_SCANNED_CHUNKS = 49;
	private static final TagKey<Block> RELIC_CORES = TagKey.create(
			Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_data_relic_cores"));

	private QuantumScanService() {
	}

	public static ScanResult scan(ServerLevel sourceLevel, BlockPos sourcePos, LockedMachineProcess process) {
		return switch (process.program()) {
			case DEEP_ORE -> scanOre(sourceLevel, sourcePos, process);
			case RELIC_TOPOLOGY -> scanRelic(sourceLevel.getServer(), sourcePos, process.rangeChunks());
			case STORM_PATH -> scanStorm(sourceLevel.getServer(), sourcePos, process.rangeChunks());
		};
	}

	private static ScanResult scanOre(ServerLevel level, BlockPos center, LockedMachineProcess process) {
		MiningSurveyTarget target = MiningSurveyTarget.byKey(process.targetKey())
				.orElse(MiningSurveyTarget.defaultTarget());
		Set<BlockPos> visited = new HashSet<>();
		List<OreClusterFinder.SurveyResult> results = new ArrayList<>();

		for (LevelChunk chunk : loadedChunks(level, center, process.rangeChunks())) {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			int minX = chunk.getPos().getMinBlockX();
			int minZ = chunk.getPos().getMinBlockZ();
			LevelChunkSection[] sections = chunk.getSections();
			for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
				LevelChunkSection section = sections[sectionIndex];
				if (section.hasOnlyAir() || !section.maybeHas(target::matches)) {
					continue;
				}
				int minY = SectionPos.sectionToBlockCoord(level.getMinSection() + sectionIndex);
				for (int y = minY; y < minY + 16; y++) {
					for (int x = minX; x < minX + 16; x++) {
						for (int z = minZ; z < minZ + 16; z++) {
						cursor.set(x, y, z);
						if (visited.contains(cursor) || !target.matches(chunk.getBlockState(cursor))) {
							continue;
						}
						BlockPos seed = cursor.immutable();
						List<BlockPos> cluster = OreClusterFinder.collectConnectedOres(level, seed, 128, target);
						visited.addAll(cluster);
						if (cluster.isEmpty()) {
							continue;
						}
						BlockPos nearest = cluster.stream()
								.min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
								.orElse(seed);
						double distance = nearest.distSqr(center);
						results.add(new OreClusterFinder.SurveyResult(nearest.immutable(), cluster.size(), distance));
						}
					}
				}
			}
		}
		results.sort(Comparator.comparingInt(OreClusterFinder.SurveyResult::clusterSize).reversed()
				.thenComparingDouble(OreClusterFinder.SurveyResult::distanceSquared));
		int resultLimit = Math.min(8, Math.max(3, process.rangeChunks() / 2));
		List<ScanPoint> points = results.stream()
				.limit(resultLimit)
				.map(result -> new ScanPoint(result.anchor(), result.clusterSize()))
				.toList();
		return points.isEmpty()
				? ScanResult.missing(QuantumProgram.DEEP_ORE, level.dimension())
				: new ScanResult(QuantumProgram.DEEP_ORE, level.dimension(), points, target.key());
	}

	private static ScanResult scanRelic(MinecraftServer server, BlockPos fallbackCenter, int rangeChunks) {
		ServerLevel level = server.getLevel(ModDimensions.VIRTUAL_WORLD_LEVEL);
		if (level == null) {
			return ScanResult.missing(QuantumProgram.RELIC_TOPOLOGY, ModDimensions.VIRTUAL_WORLD_LEVEL);
		}
		for (LevelChunk chunk : loadedChunks(level, fallbackCenter, rangeChunks)) {
			for (BlockPos pos : chunk.getBlockEntitiesPos()) {
				if (chunk.getBlockState(pos).is(RELIC_CORES)) {
					return ScanResult.single(QuantumProgram.RELIC_TOPOLOGY, level.dimension(), pos, 1, "");
				}
			}
		}
		return ScanResult.missing(QuantumProgram.RELIC_TOPOLOGY, level.dimension());
	}

	private static ScanResult scanStorm(MinecraftServer server, BlockPos fallbackCenter, int rangeChunks) {
		ServerLevel level = server.getLevel(ModDimensions.VIRTUAL_WORLD_LEVEL);
		if (level == null) {
			return ScanResult.missing(QuantumProgram.STORM_PATH, ModDimensions.VIRTUAL_WORLD_LEVEL);
		}
		BlockPos bestBiome = null;
		for (LevelChunk chunk : loadedChunks(level, fallbackCenter, rangeChunks)) {
			BlockPos center = new BlockPos(chunk.getPos().getMiddleBlockX(), 64, chunk.getPos().getMiddleBlockZ());
			if (!level.getBiome(center).is(LightningForestEffects.LIGHTNING_FOREST)) {
				continue;
			}
			BlockPos pool = findSurfacePool(level, chunk);
			if (pool != null) {
				return ScanResult.single(QuantumProgram.STORM_PATH, level.dimension(), pool, 1, "");
			}
			if (bestBiome == null) {
				bestBiome = center;
			}
		}
		return bestBiome == null
				? ScanResult.missing(QuantumProgram.STORM_PATH, level.dimension())
				: ScanResult.single(QuantumProgram.STORM_PATH, level.dimension(), bestBiome, 0, "");
	}

	private static BlockPos findSurfacePool(ServerLevel level, LevelChunk chunk) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = chunk.getPos().getMinBlockX() + localX;
				int z = chunk.getPos().getMinBlockZ() + localZ;
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ);
				for (int y = Math.max(level.getMinBuildHeight(), top - 10);
						y <= Math.min(level.getMaxBuildHeight() - 1, top + 2); y++) {
					cursor.set(x, y, z);
					if (chunk.getBlockState(cursor).is(ModBlocks.THUNDER_POOL.get())) {
						return cursor.immutable();
					}
				}
			}
		}
		return null;
	}

	private static List<LevelChunk> loadedChunks(ServerLevel level, BlockPos center, int rangeChunks) {
		ChunkPos centerChunk = new ChunkPos(center);
		List<ChunkPos> candidates = new ArrayList<>();
		int boundedRange = Math.min(16, Math.max(1, rangeChunks));
		for (int dx = -boundedRange; dx <= boundedRange; dx++) {
			for (int dz = -boundedRange; dz <= boundedRange; dz++) {
				candidates.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
			}
		}
		candidates.sort(Comparator.comparingInt(pos ->
				Math.abs(pos.x - centerChunk.x) + Math.abs(pos.z - centerChunk.z)));
		List<LevelChunk> loaded = new ArrayList<>();
		for (ChunkPos pos : candidates) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
			if (chunk != null) {
				loaded.add(chunk);
				if (loaded.size() >= MAX_SCANNED_CHUNKS) {
					break;
				}
			}
		}
		return loaded;
	}

	public record ScanPoint(BlockPos pos, int count) {
		public ScanPoint {
			pos = pos.immutable();
			count = Math.max(0, count);
		}
	}

	public record ScanResult(QuantumProgram program, ResourceKey<Level> dimension, List<ScanPoint> points,
			String targetKey) {
		public ScanResult {
			points = List.copyOf(points);
		}

		public BlockPos pos() {
			return points.isEmpty() ? BlockPos.ZERO : points.getFirst().pos();
		}

		public int count() {
			return points.isEmpty() ? 0 : points.getFirst().count();
		}

		public boolean found() {
			return !points.isEmpty();
		}

		public static ScanResult single(QuantumProgram program, ResourceKey<Level> dimension, BlockPos pos,
				int count, String targetKey) {
			return new ScanResult(program, dimension, List.of(new ScanPoint(pos, count)), targetKey);
		}

		public static ScanResult missing(QuantumProgram program, ResourceKey<Level> dimension) {
			return new ScanResult(program, dimension, List.of(), "");
		}
	}
}
