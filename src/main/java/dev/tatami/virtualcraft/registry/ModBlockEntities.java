package dev.tatami.virtualcraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.blockentity.ElectricBallGeneratorBlockEntity;
import dev.tatami.virtualcraft.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualcraft.blockentity.EnergyPipeBlockEntity;
import dev.tatami.virtualcraft.blockentity.PowMinerBlockEntity;
import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;

public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VirtualCraft.MODID);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricCondenserBlockEntity>> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser",
			() -> BlockEntityType.Builder.of(ElectricCondenserBlockEntity::new, ModBlocks.ELECTRIC_CONDENSER.get()).build(null));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyPipeBlockEntity>> ENERGY_PIPE = REGISTRY.register("energy_pipe",
			() -> BlockEntityType.Builder.of(EnergyPipeBlockEntity::new, ModBlocks.ENERGY_PIPE.get()).build(null));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricBallGeneratorBlockEntity>> ELECTRIC_BALL_GENERATOR = REGISTRY.register("electric_ball_generator",
			() -> BlockEntityType.Builder.of(ElectricBallGeneratorBlockEntity::new, ModBlocks.ELECTRIC_BALL_GENERATOR.get()).build(null));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowMinerBlockEntity>> POW_MINER = REGISTRY.register("pow_miner",
			() -> BlockEntityType.Builder.of(PowMinerBlockEntity::new, ModBlocks.POW_MINER.get()).build(null));
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WirelessPowerTowerBlockEntity>> WIRELESS_POWER_TOWER = REGISTRY.register("wireless_power_tower",
			() -> BlockEntityType.Builder.of(WirelessPowerTowerBlockEntity::new, ModBlocks.WIRELESS_POWER_TOWER.get()).build(null));
}


