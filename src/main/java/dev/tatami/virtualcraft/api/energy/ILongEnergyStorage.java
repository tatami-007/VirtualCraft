package dev.tatami.virtualcraft.api.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 支持 long 级能量的存储接口，兼容 {@link IEnergyStorage} 语义。
 *
 * 使第三方互操作（Flux Networks 通过反射桥接、其他塔实现）的方法解析路径完全等价，
 * 无需在每个调用点做 {@code Method.getMethod(...)} 字符串映射。</p>
 *
 * <p>方法名的 {@code L} 后缀避免与 Forge {@link IEnergyStorage#receiveEnergy(int, boolean)}
 * 重载 ABI 冲突，让一个 BlockEntity 可以同时 implements 两个接口。</p>
 */
public interface ILongEnergyStorage {

	long receiveEnergyL(long maxReceive, boolean simulate);

	long extractEnergyL(long maxExtract, boolean simulate);

	long getEnergyStoredL();

	long getMaxEnergyStoredL();

	boolean canExtract();

	boolean canReceive();
}
