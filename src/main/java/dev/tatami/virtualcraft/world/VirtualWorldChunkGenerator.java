package dev.tatami.virtualcraft.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

public class VirtualWorldChunkGenerator extends ChunkGenerator {
	public static final MapCodec<VirtualWorldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
			).apply(instance, VirtualWorldChunkGenerator::new));

	private static final int SURFACE_Y = 64;

	public VirtualWorldChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public void applyCarvers(WorldGenRegion level, long seed, RandomState random,
			BiomeManager biomeManager, StructureManager structureManager,
			ChunkAccess chunk, GenerationStep.Carving step) {
	}

	@Override
	public void buildSurface(WorldGenRegion level, StructureManager structureManager,
			RandomState random, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion level) {
	}

	@Override
	public int getGenDepth() {
		return 256;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
			StructureManager structureManager, ChunkAccess chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState floor = ModBlocks.CIRCUIT_FLOOR.get().defaultBlockState();

		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < SURFACE_Y; y++) {
					pos.set(minX + x, y, minZ + z);
					BlockState state;
					if (y == 0) {
						state = bedrock;
					} else if (y < 32) {
						state = deepslate;
					} else if (y < 63) {
						state = stone;
					} else {
						state = floor;
					}
					chunk.setBlockState(pos, state, false);
				}
			}
		}

		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getSeaLevel() {
		return -1;
	}

	@Override
	public int getMinY() {
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
			RandomState random) {
		return SURFACE_Y;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
		BlockState[] states = new BlockState[SURFACE_Y];
		states[0] = Blocks.BEDROCK.defaultBlockState();
		for (int i = 1; i < 32; i++) {
			states[i] = Blocks.DEEPSLATE.defaultBlockState();
		}
		for (int i = 32; i < 63; i++) {
			states[i] = Blocks.STONE.defaultBlockState();
		}
		states[63] = ModBlocks.CIRCUIT_FLOOR.get().defaultBlockState();
		return new NoiseColumn(0, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
	}
}
