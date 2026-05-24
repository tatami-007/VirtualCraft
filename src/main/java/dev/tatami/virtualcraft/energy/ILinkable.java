package dev.tatami.virtualcraft.energy;

import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * 可被光束绑定器（{@code virtual_craft:laser_binding_tool}）连接的方块实体接口。
 *
 * <p>1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的 {@code com.mebeamformer.blockentity.ILinkable}
 * 语义。绑定器交互流程依赖 {@link #addLink(BlockPos)} / {@link #removeLink(BlockPos)} /
 * {@link #getLinks()} 三个方法的精确语义，所以保持原 ABI。</p>
 *
 * <p>VirtualCraft 当前只有 {@link WirelessPowerTowerBlockEntity} 实现本接口。</p>
 */
public interface ILinkable {

	/** 添加一条到 {@code other} 的连接。已存在则视为 no-op（由实现决定）。 */
	void addLink(BlockPos other);

	/** 移除到 {@code other} 的连接。不存在则视为 no-op。 */
	void removeLink(BlockPos other);

	/** 返回所有连接目标的不可变快照。 */
	Set<BlockPos> getLinks();
}
