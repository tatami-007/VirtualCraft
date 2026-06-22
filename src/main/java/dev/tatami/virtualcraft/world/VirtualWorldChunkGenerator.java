package dev.tatami.virtualcraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.biome.BiomeSource;

import java.util.Set;

/**
 * Wraps the vanilla noise-based chunk generator and replaces surface blocks
 * with sci-fi themed blocks after terrain generation.
 */
public class VirtualWorldChunkGenerator extends NoiseBasedChunkGenerator {

	public static final MapCodec<VirtualWorldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
					NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings)
			).apply(instance, VirtualWorldChunkGenerator::new));

	private static final Set<Block> SURFACE_REPLACEABLE = Set.of(
			Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
			Blocks.MYCELIUM, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
			Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.MUD,
			Blocks.DIRT_PATH, Blocks.FARMLAND, Blocks.CLAY,
			Blocks.SANDSTONE, Blocks.RED_SANDSTONE);

	public VirtualWorldChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
		super(biomeSource, settings);
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		super.buildSurface(region, structureManager, randomState, chunk);
		replaceSurfaceBlocks(chunk);
	}

	private void replaceSurfaceBlocks(ChunkAccess chunk) {
		BlockState circuitFloor = ModBlocks.CIRCUIT_FLOOR.get().defaultBlockState();
		int minY = chunk.getMinBuildHeight();
		int maxY = chunk.getMaxBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = maxY - 1; y >= minY; y--) {
					pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
					BlockState state = chunk.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						continue;
					}
					if (SURFACE_REPLACEABLE.contains(state.getBlock())) {
						chunk.setBlockState(pos, circuitFloor, false);
					}
					break;
				}
			}
		}
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}
}
