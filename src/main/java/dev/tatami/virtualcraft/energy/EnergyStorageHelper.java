package dev.tatami.virtualcraft.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 与目标方块进行能量进/出操作的统一入口。
 *
 * <p>语义 1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的 {@code EnergyStorageHelper}：
 * 返回 {@code -1} 表示"目标根本没有这个能力"（让调用方知道继续向后回退试别的能力），
 * 返回 {@code >=0} 表示"有能力但实际可传量是 N"（即使 N=0 也表示能力存在但满了/空了）。</p>
 *
 * <p>VirtualCraft 版本相比原版的差异：移除了 Flux Networks / GregTech CEu 的反射软依赖，
 * 因为本工作区没有打这两个 mod 包。如果以后要补，照着原版方法签名加回 {@code tryExtractFluxEnergy}
 * 与 {@code tryInsertGTEnergy} 即可，本类把"先试 Flux/GT、再回退标准"的优先级做成了显式的串行：
 * 当前实现只剩下"标准 NeoForge {@link IEnergyStorage}"一条路径，所以无需层级分发。</p>
 */
public final class EnergyStorageHelper {

	private EnergyStorageHelper() {
	}

	/**
	 * 从 {@code be} 的 {@code side} 面提取最多 {@code maxExtract} FE 的能量。
	 *
	 * @return 实际/模拟提取量；目标在 {@code side} 没有 {@link IEnergyStorage} 能力时返回 {@code -1}
	 */
	public static long extractEnergy(BlockEntity be, @Nullable Direction side, long maxExtract, boolean simulate) {
		IEnergyStorage cap = getEnergyStorage(be, side);
		if (cap != null && cap.canExtract()) {
			int maxInt = (int) Math.min(maxExtract, Integer.MAX_VALUE);
			return cap.extractEnergy(maxInt, simulate);
		}
		return -1;
	}

	/**
	 * 向 {@code be} 的 {@code side} 面注入最多 {@code maxInsert} FE 的能量。
	 *
	 * @return 实际/模拟注入量；目标在 {@code side} 没有 {@link IEnergyStorage} 能力时返回 {@code -1}
	 */
	public static long insertEnergy(BlockEntity be, @Nullable Direction side, long maxInsert, boolean simulate) {
		IEnergyStorage cap = getEnergyStorage(be, side);
		if (cap != null && cap.canReceive()) {
			int maxInt = (int) Math.min(maxInsert, Integer.MAX_VALUE);
			return cap.receiveEnergy(maxInt, simulate);
		}
		return -1;
	}

	public static boolean canExtract(BlockEntity be, @Nullable Direction side) {
		IEnergyStorage cap = getEnergyStorage(be, side);
		return cap != null && cap.canExtract();
	}

	public static boolean canReceive(BlockEntity be, @Nullable Direction side) {
		IEnergyStorage cap = getEnergyStorage(be, side);
		return cap != null && cap.canReceive();
	}

	public static long getEnergyStored(BlockEntity be, @Nullable Direction side) {
		IEnergyStorage cap = getEnergyStorage(be, side);
		return cap != null ? cap.getEnergyStored() : 0;
	}

	@Nullable
	private static IEnergyStorage getEnergyStorage(BlockEntity be, @Nullable Direction side) {
		if (be == null || be.isRemoved()) {
			return null;
		}
		Level level = be.getLevel();
		if (level == null) {
			return null;
		}

		return level.getCapability(
				Capabilities.EnergyStorage.BLOCK,
				be.getBlockPos(),
				be.getBlockState(),
				be,
				side);
	}
}
