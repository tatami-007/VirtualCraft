package dev.tatami.virtualcraft.world;

import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generates a rare, sealed thunder pool without crossing chunk borders. */
public final class ThunderPoolFeature extends Feature<NoneFeatureConfiguration> {
	private static final int RADIUS = 7;
	private static final int MAX_SURFACE_VARIATION = 2;

	public ThunderPoolFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		int centerX = (origin.getX() >> 4 << 4) + 7 + random.nextInt(2);
		int centerZ = (origin.getZ() >> 4 << 4) + 7 + random.nextInt(2);
		int shape = random.nextInt(4);
		int depth = 1 + random.nextInt(3);
		long shapeSeed = random.nextLong();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minimumSurfaceY = Integer.MAX_VALUE;
		int maximumSurfaceY = Integer.MIN_VALUE;

		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (!insidePool(shape, shapeSeed, dx, dz)) {
					continue;
				}
				int x = centerX + dx;
				int z = centerZ + dz;
				int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				cursor.set(x, surfaceY - 1, z);
				if (!level.getBiome(cursor).is(LightningForestEffects.LIGHTNING_FOREST)
						|| !level.getBlockState(cursor).is(ModBlocks.VIRTUAL_GRASS.get())) {
					return false;
				}
				minimumSurfaceY = Math.min(minimumSurfaceY, surfaceY);
				maximumSurfaceY = Math.max(maximumSurfaceY, surfaceY);
			}
		}
		if (minimumSurfaceY == Integer.MAX_VALUE
				|| maximumSurfaceY - minimumSurfaceY > MAX_SURFACE_VARIATION) {
			return false;
		}

		int waterSurfaceY = minimumSurfaceY - 1;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (!insidePool(shape, shapeSeed, dx, dz) || isBank(shape, shapeSeed, dx, dz)) {
					continue;
				}
				int x = centerX + dx;
				int z = centerZ + dz;
				int columnSurfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				setBlock(level, cursor.set(x, waterSurfaceY - depth, z),
						ModBlocks.VIRTUAL_STONE.get().defaultBlockState());
				for (int y = waterSurfaceY - depth + 1; y <= waterSurfaceY; y++) {
					setBlock(level, cursor.set(x, y, z), ModBlocks.THUNDER_POOL.get().defaultBlockState());
				}
				for (int y = waterSurfaceY + 1; y < columnSurfaceY; y++) {
					setBlock(level, cursor.set(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		return true;
	}

	private static boolean isBank(int shape, long seed, int x, int z) {
		return !insidePool(shape, seed, x - 1, z)
				|| !insidePool(shape, seed, x + 1, z)
				|| !insidePool(shape, seed, x, z - 1)
				|| !insidePool(shape, seed, x, z + 1);
	}

	private static boolean insidePool(int shape, long seed, int x, int z) {
		double distance = switch (shape) {
			case 0 -> ellipseDistance(x, z, 7.0, 5.0);
			case 1 -> Math.min(ellipseDistance(x + 2.0, z, 5.0, 5.5), ellipseDistance(x - 2.0, z, 5.0, 4.0));
			case 2 -> Math.min(ellipseDistance(x, z + 2.0, 5.5, 5.0), ellipseDistance(x, z - 2.0, 4.0, 5.0));
			default -> ellipseDistance(x + z * 0.28, z, 6.5, 5.2);
		};
		return distance <= 1.0 + edgeVariation(seed, x, z);
	}

	private static double ellipseDistance(double dx, double dz, double radiusX, double radiusZ) {
		double x = dx / radiusX;
		double z = dz / radiusZ;
		return Math.sqrt(x * x + z * z);
	}

	private static double edgeVariation(long seed, int x, int z) {
		long mixed = seed ^ (x * 341873128712L) ^ (z * 132897987541L);
		mixed ^= mixed >>> 33;
		mixed *= 0xff51afd7ed558ccdL;
		mixed ^= mixed >>> 33;
		return ((mixed & 0xFFL) / 255.0 - 0.5) * 0.12;
	}
}
