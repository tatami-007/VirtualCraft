package dev.tatami.virtualcraft.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 纯逻辑：单层「数据矿洞」的程序化布局。无 Minecraft 依赖，可离线单测。
 *
 * <p>每层是一座有机洞穴（细胞自动机生成的开放区域），而非方正房间——这是
 * 上一版「数据迷宫」被嫌弃的根因（房间太规整、没有探索感）。为保证肉鸽下潜
 * 永远可通关，生成器在 CA 洞穴之上**强制开凿** L 形隧道，把入口、撤离台、
 * 下行井、每处矿脉都连到入口，因此 BFS 一定连通。</p>
 *
 * <p>坐标系：以层原点为 (0,0) 的 {@code WIDTH × DEPTH} 网格，单位为方块。
 * y 由建造层补足（在开放格上向上掏 {@link #INTERIOR_HEIGHT} 高）。</p>
 */
public final class DataMineFloor {
	public static final int WIDTH = 48;
	public static final int DEPTH = 48;
	public static final int INTERIOR_HEIGHT = 8;

	private final int depth;
	private final boolean[][] open;
	private final int entryX;
	private final int entryZ;
	private final int shaftX;
	private final int shaftZ;
	private final List<int[]> oreVeins;
	private final List<int[]> mobSpawns;

	private DataMineFloor(int depth, boolean[][] open, int entryX, int entryZ, int shaftX, int shaftZ,
			List<int[]> oreVeins, List<int[]> mobSpawns) {
		this.depth = depth;
		this.open = open;
		this.entryX = entryX;
		this.entryZ = entryZ;
		this.shaftX = shaftX;
		this.shaftZ = shaftZ;
		this.oreVeins = oreVeins;
		this.mobSpawns = mobSpawns;
	}

	/**
	 * 生成第 {@code depth} 层（depth ≥ 1）的布局。{@code seed} 通常由玩家本局 run
	 * 种子混合而来，使同一 run 内的同一层稳定、不同 run 重新洗牌。
	 */
	public static DataMineFloor generate(long seed, int depth) {
		if (depth < 1) {
			throw new IllegalArgumentException("floor depth must be >= 1");
		}
		Random rng = new Random(mix(seed + 0x9E3779B97F4A7C15L * (long) (depth + 1)));
		boolean[][] wall = new boolean[WIDTH][DEPTH];
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				wall[x][z] = isBorder(x, z) || rng.nextInt(100) < 46;
			}
		}
		for (int iteration = 0; iteration < 4; iteration++) {
			boolean[][] next = new boolean[WIDTH][DEPTH];
			for (int x = 0; x < WIDTH; x++) {
				for (int z = 0; z < DEPTH; z++) {
					next[x][z] = isBorder(x, z) || wallNeighbors(wall, x, z) >= 5;
				}
			}
			wall = next;
		}

		int entryX = WIDTH / 2;
		int entryZ = 3;
		int shaftX = WIDTH / 2;
		int shaftZ = DEPTH - 4;
		carveDisk(wall, entryX, entryZ, 3);
		carveDisk(wall, shaftX, shaftZ, 2);

		int veinCount = 3 + depth;
		List<int[]> oreVeins = new ArrayList<>();
		for (int i = 0; i < veinCount; i++) {
			int vx = 4 + rng.nextInt(WIDTH - 8);
			int vz = 4 + rng.nextInt(DEPTH - 8);
			carveDisk(wall, vx, vz, 1);
			oreVeins.add(new int[] { vx, vz });
		}

		// 强制连通：入口 → 下行井、入口 → 每处矿脉。CA 洞穴提供有机外观，
		// 这些隧道保证「永远走得到」。
		carveTunnel(wall, entryX, entryZ, shaftX, shaftZ);
		for (int[] vein : oreVeins) {
			carveTunnel(wall, entryX, entryZ, vein[0], vein[1]);
		}

		boolean[][] openMask = new boolean[WIDTH][DEPTH];
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				openMask[x][z] = !wall[x][z];
			}
		}

		int mobCount = 2 + depth;
		List<int[]> mobSpawns = new ArrayList<>();
		int guard = 0;
		while (mobSpawns.size() < mobCount && guard++ < 4000) {
			int mx = 2 + rng.nextInt(WIDTH - 4);
			int mz = 2 + rng.nextInt(DEPTH - 4);
			if (!openMask[mx][mz]) {
				continue;
			}
			// 不在入口安全区刷怪，给玩家落地缓冲。
			if (manhattan(mx, mz, entryX, entryZ) < 8) {
				continue;
			}
			mobSpawns.add(new int[] { mx, mz });
		}

		return new DataMineFloor(depth, openMask, entryX, entryZ, shaftX, shaftZ, oreVeins, mobSpawns);
	}

	public int depth() {
		return depth;
	}

	public boolean isOpen(int x, int z) {
		return inBounds(x, z) && open[x][z];
	}

	public int entryX() {
		return entryX;
	}

	public int entryZ() {
		return entryZ;
	}

	public int shaftX() {
		return shaftX;
	}

	public int shaftZ() {
		return shaftZ;
	}

	/** 撤离台与入口同处，落地即可撤离结算。 */
	public int padX() {
		return entryX;
	}

	public int padZ() {
		return entryZ;
	}

	/** 矿脉中心格列表（建造层在其周围嵌入矿脉方块）。 */
	public List<int[]> oreVeins() {
		return oreVeins;
	}

	/** 刷怪格列表（远离入口）。 */
	public List<int[]> mobSpawns() {
		return mobSpawns;
	}

	private static boolean isBorder(int x, int z) {
		return x == 0 || z == 0 || x == WIDTH - 1 || z == DEPTH - 1;
	}

	private static boolean inBounds(int x, int z) {
		return x >= 0 && z >= 0 && x < WIDTH && z < DEPTH;
	}

	private static int wallNeighbors(boolean[][] wall, int x, int z) {
		int count = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int nx = x + dx;
				int nz = z + dz;
				if (!inBounds(nx, nz) || wall[nx][nz]) {
					count++;
				}
			}
		}
		return count;
	}

	private static void carveDisk(boolean[][] wall, int cx, int cz, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				if (inBounds(x, z) && !isBorder(x, z)) {
					wall[x][z] = false;
				}
			}
		}
	}

	private static void carveTunnel(boolean[][] wall, int fromX, int fromZ, int toX, int toZ) {
		int x = fromX;
		int z = fromZ;
		while (x != toX) {
			x += Integer.signum(toX - x);
			carveDisk(wall, x, z, 1);
		}
		while (z != toZ) {
			z += Integer.signum(toZ - z);
			carveDisk(wall, x, z, 1);
		}
	}

	private static int manhattan(int ax, int az, int bx, int bz) {
		return Math.abs(ax - bx) + Math.abs(az - bz);
	}

	private static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}
}
