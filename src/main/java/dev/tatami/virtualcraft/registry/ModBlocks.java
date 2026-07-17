package dev.tatami.virtualcraft.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.Block;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.block.DeepslateElectricOreBlock;
import dev.tatami.virtualcraft.block.ElectricBallGeneratorBlock;
import dev.tatami.virtualcraft.block.ElectricCondenserBlock;
import dev.tatami.virtualcraft.block.ElectricOreBlock;
import dev.tatami.virtualcraft.block.PowMinerBlock;
import dev.tatami.virtualcraft.block.EnergyPipeBlock;
import dev.tatami.virtualcraft.block.WirelessPowerTowerBlock;
import dev.tatami.virtualcraft.block.ThunderPoolBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	private ModBlocks() {
	}

	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(VirtualCraft.MODID);
	public static final DeferredBlock<Block> ELECTRIC_ORE = REGISTRY.register("electric_ore", ElectricOreBlock::new);
	public static final DeferredBlock<Block> DEEPSLATE_ELECTRIC_ORE = REGISTRY.register("deepslate_electric_ore", DeepslateElectricOreBlock::new);
	public static final DeferredBlock<Block> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser", ElectricCondenserBlock::new);
	public static final DeferredBlock<Block> ELECTRIC_BALL_GENERATOR = REGISTRY.register("electric_ball_generator", ElectricBallGeneratorBlock::new);
	public static final DeferredBlock<Block> POW_MINER = REGISTRY.register("pow_miner", PowMinerBlock::new);
	/**
	 * 无线供电塔 1:1 沿用 ME-Beam-Former 1.21.1 的 properties：
	 * 金属硬度 1.5、金属音、需要镐挖、noOcclusion 防视觉穿模。
	 */
	public static final DeferredBlock<Block> WIRELESS_POWER_TOWER = REGISTRY.register("wireless_power_tower",
			() -> new WirelessPowerTowerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(1.5f)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops()
					.noOcclusion()));
	public static final DeferredBlock<Block> ENERGY_PIPE = REGISTRY.register("energy_pipe", EnergyPipeBlock::new);
	public static final DeferredBlock<Block> LIGHTNING_LOG = REGISTRY.register("lightning_log",
			() -> new RotatedPillarBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(2.0F)
					.sound(SoundType.WOOD)));
	public static final DeferredBlock<Block> LIGHTNING_LEAVES = REGISTRY.register("lightning_leaves",
			() -> new LeavesBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(0.2F)
					.randomTicks()
					.sound(SoundType.GRASS)
					.noOcclusion()));
	public static final DeferredBlock<ThunderPoolBlock> THUNDER_POOL = REGISTRY.register("thunder_pool",
			() -> new ThunderPoolBlock((net.minecraft.world.level.material.FlowingFluid) ModFluids.THUNDER_POOL.get(),
					BlockBehaviour.Properties.of()
							.mapColor(MapColor.COLOR_PURPLE)
							.replaceable()
							.noCollission()
							.strength(100.0F)
							.lightLevel(state -> 8)
							.noLootTable()
							.liquid()));
	// === 虚拟世界地表方块(暂用旧版贴图) ===
	public static final DeferredBlock<Block> VIRTUAL_GRASS = REGISTRY.register("virtual_grass",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(0.6f)
					.sound(SoundType.GRASS)));
	public static final DeferredBlock<Block> VIRTUAL_DIRT = REGISTRY.register("virtual_dirt",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.DIRT)
					.strength(0.5f)
					.sound(SoundType.GRAVEL)));
	public static final DeferredBlock<Block> VIRTUAL_STONE = REGISTRY.register("virtual_stone",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(1.5f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_COBBLESTONE = REGISTRY.register("virtual_cobblestone",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(2.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_IRON_ORE = REGISTRY.register("virtual_iron_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_SILVER_ORE = REGISTRY.register("virtual_silver_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_ALUMINUM_ORE = REGISTRY.register("virtual_aluminum_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_GOLD_ORE = REGISTRY.register("virtual_gold_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_TITANIUM_ORE = REGISTRY.register("virtual_titanium_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VIRTUAL_CHROMIUM_ORE = REGISTRY.register("virtual_chromium_ore",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.sound(SoundType.STONE)
					.requiresCorrectToolForDrops()));
}
