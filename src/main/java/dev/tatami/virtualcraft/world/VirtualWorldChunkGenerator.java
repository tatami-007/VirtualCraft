package dev.tatami.virtualcraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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

	public VirtualWorldChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
		super(biomeSource, settings);
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
		super.buildSurface(region, structureManager, randomState, chunk);
		replaceVanillaBlocks(chunk);
	}

	private void replaceVanillaBlocks(ChunkAccess chunk) {
		BlockState grass = ModBlocks.VIRTUAL_GRASS.get().defaultBlockState();
		BlockState dirt = ModBlocks.VIRTUAL_DIRT.get().defaultBlockState();
		BlockState stone = ModBlocks.VIRTUAL_STONE.get().defaultBlockState();
		int minY = chunk.getMinBuildHeight();
		int maxY = chunk.getMaxBuildHeight();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int depth = -1;
				boolean underWater = false;
				for (int y = maxY - 1; y >= minY; y--) {
					pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
					BlockState state = chunk.getBlockState(pos);
					if (state.isAir()) {
						depth = -1;
						underWater = false;
						continue;
					}
					if (!state.getFluidState().isEmpty()) {
						depth = -1;
						underWater = true;
						continue;
					}
					Block block = state.getBlock();
					if (block == Blocks.BEDROCK || !"minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
						depth = depth < 0 ? 4 : depth + 1;
						continue;
					}
					depth = depth < 0 ? 0 : depth + 1;
					if (depth == 0) {
						chunk.setBlockState(pos, underWater ? dirt : grass, false);
					} else if (depth <= 3) {
						chunk.setBlockState(pos, dirt, false);
					} else {
						chunk.setBlockState(pos, stone, false);
					}
				}
			}
		}
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}
}
