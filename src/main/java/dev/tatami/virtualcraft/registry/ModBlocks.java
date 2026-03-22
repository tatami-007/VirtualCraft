package dev.tatami.virtualcraft.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.Block;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.block.CurrencyGeneratorBlock;
import dev.tatami.virtualcraft.block.DeepslateElectricOreBlock;
import dev.tatami.virtualcraft.block.ElectricBallGeneratorBlock;
import dev.tatami.virtualcraft.block.ElectricCondenserBlock;
import dev.tatami.virtualcraft.block.ElectricOreBlock;
import dev.tatami.virtualcraft.block.HolographicShopBlock;
import dev.tatami.virtualcraft.block.PowMinerBlock;
import dev.tatami.virtualcraft.block.WirelessPowerReceiverBlock;
import dev.tatami.virtualcraft.block.WirelessPowerTowerBlock;

public final class ModBlocks {
	private ModBlocks() {
	}

	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(VirtualCraft.MODID);
	public static final DeferredBlock<Block> ELECTRIC_ORE = REGISTRY.register("electric_ore", ElectricOreBlock::new);
	public static final DeferredBlock<Block> DEEPSLATE_ELECTRIC_ORE = REGISTRY.register("deepslate_electric_ore", DeepslateElectricOreBlock::new);
	public static final DeferredBlock<Block> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser", ElectricCondenserBlock::new);
	public static final DeferredBlock<Block> ELECTRIC_BALL_GENERATOR = REGISTRY.register("electric_ball_generator", ElectricBallGeneratorBlock::new);
	public static final DeferredBlock<Block> HOLOGRAPHIC_SHOP = REGISTRY.register("holographic_shop", HolographicShopBlock::new);
	public static final DeferredBlock<Block> CURRENCY_GENERATOR = REGISTRY.register("currency_generator", CurrencyGeneratorBlock::new);
	public static final DeferredBlock<Block> POW_MINER = REGISTRY.register("pow_miner", PowMinerBlock::new);
	public static final DeferredBlock<Block> WIRELESS_POWER_TOWER = REGISTRY.register("wireless_power_tower", WirelessPowerTowerBlock::new);
	public static final DeferredBlock<Block> WIRELESS_POWER_RECEIVER = REGISTRY.register("wireless_power_receiver", WirelessPowerReceiverBlock::new);
}


