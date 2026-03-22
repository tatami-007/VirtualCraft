package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.blockentity.CurrencyGeneratorBlockEntity;
import dev.tatami.virtualcraft.blockentity.ElectricBallGeneratorBlockEntity;
import dev.tatami.virtualcraft.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualcraft.blockentity.PowMinerBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerReceiverBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {
	private ModCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.ELECTRIC_CONDENSER.get(),
				(ElectricCondenserBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getItemHandler(side));
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.ELECTRIC_BALL_GENERATOR.get(),
				(ElectricBallGeneratorBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getInventory());
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.CURRENCY_GENERATOR.get(),
				(CurrencyGeneratorBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getInventory());
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.POW_MINER.get(),
				(PowMinerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getInventory());
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
				ModBlockEntities.CURRENCY_GENERATOR.get(),
				(CurrencyGeneratorBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.POW_MINER.get(),
				(PowMinerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.WIRELESS_POWER_TOWER.get(),
				(WirelessPowerTowerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getInventory());
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				ModBlockEntities.WIRELESS_POWER_RECEIVER.get(),
				(WirelessPowerReceiverBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getInventory());
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.WIRELESS_POWER_TOWER.get(),
				(WirelessPowerTowerBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
		event.registerBlockEntity(
				Capabilities.EnergyStorage.BLOCK,
				ModBlockEntities.WIRELESS_POWER_RECEIVER.get(),
				(WirelessPowerReceiverBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getEnergyStorage(side));
	}
}


