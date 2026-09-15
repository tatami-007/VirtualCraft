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
import dev.tatami.virtualcraft.block.VirtualMushroomBlock;
import dev.tatami.virtualcraft.block.BossAltarBlock;
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
	public static final DeferredBlock<Block> COMPUTE_RACK = REGISTRY.register("compute_rack",
			dev.tatami.virtualcraft.block.ComputeRackBlock::new);
	public static final DeferredBlock<Block> LIGHTNING_RESONATOR = REGISTRY.register("lightning_resonator",
			dev.tatami.virtualcraft.block.LightningResonatorBlock::new);
	public static final DeferredBlock<Block> QUANTUM_SIMULATION_CHAMBER = REGISTRY.register(
			"quantum_simulation_chamber", dev.tatami.virtualcraft.block.QuantumSimulationChamberBlock::new);
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
	public static final DeferredBlock<VirtualMushroomBlock> BLUE_MUSHROOM = mushroom(
			"blue_mushroom", MapColor.COLOR_BLUE);
	public static final DeferredBlock<VirtualMushroomBlock> POISON_MUSHROOM = mushroom(
			"poison_mushroom", MapColor.COLOR_PURPLE);
	public static final DeferredBlock<VirtualMushroomBlock> NITRIFYING_PARASOL = mushroom(
			"nitrifying_parasol", MapColor.COLOR_PINK);
	public static final DeferredBlock<VirtualMushroomBlock> PARALYSIS_MUSHROOM = mushroom(
			"paralysis_mushroom", MapColor.COLOR_YELLOW);
	public static final DeferredBlock<VirtualMushroomBlock> GHOST_NITRIFYING_PARASOL = mushroom(
			"ghost_nitrifying_parasol", MapColor.COLOR_RED);
	public static final DeferredBlock<VirtualMushroomBlock> MANDRAKE_MUSHROOM = mushroom(
			"mandrake_mushroom", MapColor.TERRACOTTA_BROWN, 0.5F);
	public static final DeferredBlock<VirtualMushroomBlock> HEARTBEAT_MUSHROOM = mushroom(
			"heartbeat_mushroom", MapColor.COLOR_GREEN);
	public static final DeferredBlock<Block> SNAKE_KING_ALTAR = REGISTRY.register("snake_king_altar",
			() -> new BossAltarBlock(BossAltarBlock.Boss.SNAKE, altarProperties(MapColor.COLOR_BLACK)));
	public static final DeferredBlock<Block> MUSHROOM_KING_ALTAR = REGISTRY.register("mushroom_king_altar",
			() -> new BossAltarBlock(BossAltarBlock.Boss.MUSHROOM, altarProperties(MapColor.COLOR_PURPLE)));
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
	// === 虚拟世界地形方块 ===
	public static final DeferredBlock<Block> VIRTUAL_GRASS_BLOCK = REGISTRY.register("virtual_grass_block",
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
	public static final DeferredBlock<Block> CHARGED_GRASS_BLOCK = REGISTRY.register("charged_grass_block",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(0.7F)
					.sound(SoundType.GRASS)));
	public static final DeferredBlock<Block> CHARGED_DIRT = REGISTRY.register("charged_dirt",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_PURPLE)
					.strength(0.6F)
					.sound(SoundType.ROOTED_DIRT)));
	public static final DeferredBlock<Block> LIGHTNING_STONE = REGISTRY.register("lightning_stone",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(2.2F, 7.0F)
					.sound(SoundType.DEEPSLATE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> FULGURITE = REGISTRY.register("fulgurite",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(1.8F, 5.0F)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> 10)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> TOXIC_GRASS_BLOCK = REGISTRY.register("toxic_grass_block",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(0.7F)
					.sound(SoundType.GRASS)));
	public static final DeferredBlock<Block> TOXIC_DIRT = REGISTRY.register("toxic_dirt",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(0.6F)
					.sound(SoundType.ROOTED_DIRT)));
	public static final DeferredBlock<Block> SERPENT_STONE = REGISTRY.register("serpent_stone",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(2.2F, 7.0F)
					.sound(SoundType.DEEPSLATE)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> VENOM_CRYSTAL = REGISTRY.register("venom_crystal",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GREEN)
					.strength(1.8F, 5.0F)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> 11)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> SERPENT_STONE_BRICKS = REGISTRY.register("serpent_stone_bricks",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(2.8F, 8.0F)
					.sound(SoundType.DEEPSLATE_BRICKS)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> CHISELED_SERPENT_STONE_BRICKS = REGISTRY.register("chiseled_serpent_stone_bricks",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.GOLD)
					.strength(2.8F, 8.0F)
					.sound(SoundType.DEEPSLATE_BRICKS)
					.lightLevel(state -> 5)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> SERPENT_FLOOR_TILES = REGISTRY.register("serpent_floor_tiles",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(2.8F, 8.0F)
					.sound(SoundType.DEEPSLATE_TILES)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> SERPENT_LATTICE = REGISTRY.register("serpent_lattice",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(2.8F, 8.0F)
					.sound(SoundType.DEEPSLATE_BRICKS)
					.lightLevel(state -> 3)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> MUSHROOM_GRASS_BLOCK = REGISTRY.register("mushroom_grass_block",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_MAGENTA)
					.strength(0.7F)
					.sound(SoundType.GRASS)));
	public static final DeferredBlock<Block> MUSHROOM_DIRT = REGISTRY.register("mushroom_dirt",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_PURPLE)
					.strength(0.6F)
					.sound(SoundType.ROOTED_DIRT)));
	public static final DeferredBlock<Block> FUNGAL_STONE = REGISTRY.register("fungal_stone",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_PURPLE)
					.strength(2.0F, 6.0F)
					.sound(SoundType.TUFF)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> SPORE_LAMP = REGISTRY.register("spore_lamp",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PINK)
					.strength(1.2F, 3.0F)
					.sound(SoundType.SHROOMLIGHT)
					.lightLevel(state -> 12)));
	public static final DeferredBlock<Block> FUNGAL_STONE_BRICKS = REGISTRY.register("fungal_stone_bricks",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_PURPLE)
					.strength(2.5F, 7.0F)
					.sound(SoundType.TUFF_BRICKS)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> CHISELED_FUNGAL_STONE_BRICKS = REGISTRY.register("chiseled_fungal_stone_bricks",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_WHITE)
					.strength(2.2F, 6.0F)
					.sound(SoundType.TUFF_BRICKS)
					.lightLevel(state -> 4)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> FUNGAL_CAP_TILES = REGISTRY.register("fungal_cap_tiles",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_PURPLE)
					.strength(2.5F, 7.0F)
					.sound(SoundType.TUFF_BRICKS)
					.requiresCorrectToolForDrops()));
	public static final DeferredBlock<Block> FUNGAL_GILL_TILES = REGISTRY.register("fungal_gill_tiles",
			() -> new Block(BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_WHITE)
					.strength(2.2F, 6.0F)
					.sound(SoundType.TUFF_BRICKS)
					.lightLevel(state -> 2)
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

	private static DeferredBlock<VirtualMushroomBlock> mushroom(String name, MapColor color) {
		return mushroom(name, color, 1.0F);
	}

	private static DeferredBlock<VirtualMushroomBlock> mushroom(String name, MapColor color, float spreadRate) {
		return REGISTRY.register(name, () -> new VirtualMushroomBlock(BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollission()
				.instabreak()
				.randomTicks()
				.sound(SoundType.GRASS)
				.offsetType(BlockBehaviour.OffsetType.XZ)
				.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY), spreadRate));
	}

	private static BlockBehaviour.Properties altarProperties(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(-1.0F, 3_600_000.0F)
				.lightLevel(state -> 7)
				.noLootTable();
	}
}
