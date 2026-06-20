package dev.tatami.virtualcraft.gameplay;

/**
 * 纯函数：把本局「算力袋」(挖到的矿脉单位数) 与「最深到达层数」折算为撤离结算。
 * 无 Minecraft 依赖，便于单测奖励曲线的单调性与边界。
 *
 * <p>设计意图：越深越肥（同样的矿，深层结算更多碎片、更高级券概率），里程碑
 * 额外奖励，鼓励「再赌一层」与「见好就收」的取舍。</p>
 */
public final class DataMineRewards {
	/** 券档位：0=普通 1=探索者 2=加密 3=量子 4=创世。 */
	public static final int VOUCHER_TIERS = 5;

	private DataMineRewards() {
	}

	/**
	 * 撤离时获得的数据碎片数。与矿量线性、与最深层数递增。空袋则为 0。
	 */
	public static int fragments(int oreUnits, int deepestDepth) {
		if (oreUnits <= 0) {
			return 0;
		}
		int depth = Math.max(1, deepestDepth);
		int perOre = 2 + depth; // 深度越大，每块矿折算越多碎片
		int milestone = milestoneBonus(depth);
		return oreUnits * perOre + milestone;
	}

	/**
	 * 指定档位券的掉落概率 [0,1]。普通券基础概率最高，越高档越稀有，但都随深度提升。
	 */
	public static float voucherChance(int tier, int deepestDepth) {
		if (tier < 0 || tier >= VOUCHER_TIERS) {
			return 0f;
		}
		int depth = Math.max(1, deepestDepth);
		// 基础概率随档位递减，随深度线性提升，最后夹到 [0, 0.95]。
		float base = 0.45f - tier * 0.09f;
		float growth = depth * (0.02f - tier * 0.0025f);
		return clamp(base + growth, 0f, 0.95f);
	}

	/** 期望掉落份数（袋越大、越深，结算的券越多）。用于建造层决定 roll 次数。 */
	public static int voucherRolls(int oreUnits, int deepestDepth) {
		if (oreUnits <= 0) {
			return 0;
		}
		int depth = Math.max(1, deepestDepth);
		return 1 + (oreUnits / 6) + (depth / 4);
	}

	/** 到达里程碑层（每 5 层）给的额外碎片。 */
	public static int milestoneBonus(int deepestDepth) {
		int depth = Math.max(0, deepestDepth);
		return (depth / 5) * 25;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
