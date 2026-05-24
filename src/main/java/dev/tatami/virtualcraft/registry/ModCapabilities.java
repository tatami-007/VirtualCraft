package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.api.energy.ILongEnergyStorage;
import dev.tatami.virtualcraft.blockentity.ElectricBallGeneratorBlockEntity;
import dev.tatami.virtualcraft.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualcraft.blockentity.PowMinerBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
	private ModCapabilities() {
	}

	/**
	 * 1:1 沿用 ME-Beam-Former 1.21.1 (LGPL-3.0) 的 {@code MEBFCapabilities.LONG_ENERGY_STORAGE}。
	 *
	 * <p>无线塔之间走这个 cap 直接 long→long 传输，突破 NeoForge {@code IEnergyStorage}
	 * 21 亿 FE/t 单次上限。第三方 mod 若想让自己的设备成为塔的 long 能量源/接收者，
	 * 实现 {@link ILongEnergyStorage} 并在 {@code RegisterCapabilitiesEvent} 注册到本 cap 即可。</p>
	 */
	public static final BlockCapability<ILongEnergyStorage, Direction> LONG_ENERGY_STORAGE = BlockCapability.createSided(
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "long_energy_storage"),
			ILongEnergyStorage.class);

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.ELECTRIC_CONDENSER.get(),
				(ElectricCondenserBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getItemHandler(side));
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.ELECTRIC_BALL_GENERATOR.get(),
				(ElectricBallGeneratorBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getItemHandler(side));
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.POW_MINER.get(),
				(PowMinerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getItemHandler(side));
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.ELECTRIC_CONDENSER.get(),
				(ElectricCondenserBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.ELECTRIC_BALL_GENERATOR.get(),
				(ElectricBallGeneratorBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.POW_MINER.get(),
				(PowMinerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		// WirelessPowerTowerBlockEntity 自身实现 IEnergyStorage（int 接口，兼容标准 FE 机器）
		// 与 ILongEnergyStorage（long 接口，给塔↔塔直通用）。两个 cap 都直接返回 BE 本身。
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.WIRELESS_POWER_TOWER.get(),
				(WirelessPowerTowerBlockEntity blockEntity, Direction side) -> blockEntity);
		event.registerBlockEntity(
				LONG_ENERGY_STORAGE,
				ModBlockEntities.WIRELESS_POWER_TOWER.get(),
				(WirelessPowerTowerBlockEntity blockEntity, Direction side) -> blockEntity);
	}
}


