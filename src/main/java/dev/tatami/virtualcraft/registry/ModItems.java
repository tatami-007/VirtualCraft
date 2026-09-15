package dev.tatami.virtualcraft.registry;

import java.util.List;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.item.ComputerItem;
import dev.tatami.virtualcraft.item.ElectricAbsorberItem;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.item.HolographicTerminalItem;
import dev.tatami.virtualcraft.item.LaserBindingTool;
import dev.tatami.virtualcraft.item.MiningSurveyorItem;
import dev.tatami.virtualcraft.item.PowerCellItem;
import dev.tatami.virtualcraft.item.SpeedUpgradeCardItem;
import dev.tatami.virtualcraft.item.SnakeKingKeyItem;
import dev.tatami.virtualcraft.item.MushroomKingSealItem;
import dev.tatami.virtualcraft.item.MedicinalMushroomItem;
import dev.tatami.virtualcraft.item.MedicinalMushroomItem.Medicine;
import dev.tatami.virtualcraft.item.VirtualMetalArmorItem;
import dev.tatami.virtualcraft.item.VirtualSwordItem;
import dev.tatami.virtualcraft.gameplay.ElectricAbsorberChargeHelper;

public final class ModItems {
	private ModItems() {
	}

	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(VirtualCraft.MODID);
	public static final DeferredItem<Item> ELECTRIC_ORE = block(ModBlocks.ELECTRIC_ORE);
	public static final DeferredItem<Item> DEEPSLATE_ELECTRIC_ORE = block(ModBlocks.DEEPSLATE_ELECTRIC_ORE);
	public static final DeferredItem<Item> ELECTRIC_CONDENSER = block(ModBlocks.ELECTRIC_CONDENSER);
	public static final DeferredItem<Item> ELECTRIC_BALL_GENERATOR = block(ModBlocks.ELECTRIC_BALL_GENERATOR);
	public static final DeferredItem<Item> ELECTRIC_BALL = REGISTRY.register("electric_ball", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> ELECTRIC_INGOT = REGISTRY.register("electric_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> ELECTRIC_ABSORBER = REGISTRY.register("electric_absorber", ElectricAbsorberItem::new);
	public static final DeferredItem<Item> HASH_VOUCHER = REGISTRY.register("hash_voucher", () -> new HashVoucherItem());
	public static final DeferredItem<Item> EXPLORER_HASH_VOUCHER = REGISTRY.register("explorer_hash_voucher",
			() -> new HashVoucherItem(HashVoucherItem.EXPLORER_BTC_PAYOUT_MINOR));
	public static final DeferredItem<Item> ENCRYPTED_HASH_VOUCHER = REGISTRY.register("encrypted_hash_voucher",
			() -> new HashVoucherItem(HashVoucherItem.ENCRYPTED_BTC_PAYOUT_MINOR));
	public static final DeferredItem<Item> QUANTUM_HASH_VOUCHER = REGISTRY.register("quantum_hash_voucher",
			() -> new HashVoucherItem(HashVoucherItem.QUANTUM_BTC_PAYOUT_MINOR));
	public static final DeferredItem<Item> GENESIS_HASH_VOUCHER = REGISTRY.register("genesis_hash_voucher",
			() -> new HashVoucherItem(HashVoucherItem.GENESIS_BTC_PAYOUT_MINOR));
	public static final DeferredItem<Item> MINING_SURVEYOR = REGISTRY.register("mining_surveyor", MiningSurveyorItem::new);
	public static final DeferredItem<Item> SPEED_UPGRADE_CARD = REGISTRY.register("speed_upgrade_card", SpeedUpgradeCardItem::new);
	public static final DeferredItem<Item> BASIC_POWER_CELL = REGISTRY.register("basic_power_cell",
			() -> new PowerCellItem(PowerCellItem.Tier.BASIC));
	public static final DeferredItem<Item> ADVANCED_POWER_CELL = REGISTRY.register("advanced_power_cell",
			() -> new PowerCellItem(PowerCellItem.Tier.ADVANCED));
	public static final DeferredItem<Item> QUANTUM_POWER_CELL = REGISTRY.register("quantum_power_cell",
			() -> new PowerCellItem(PowerCellItem.Tier.QUANTUM));
	public static final DeferredItem<Item> SNAKE_KING_KEY = REGISTRY.register("snake_king_key", SnakeKingKeyItem::new);
	public static final DeferredItem<Item> SNAKE_SCALE = REGISTRY.register("snake_scale",
			() -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
	public static final DeferredItem<Item> VIRTUAL_SNAKE_KING_SMITHING_TEMPLATE = REGISTRY.register(
			"virtual_snake_king_smithing_template",
			() -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
	public static final DeferredItem<Item> MUSHROOM_KING_SEAL = REGISTRY.register("mushroom_king_seal", MushroomKingSealItem::new);
	public static final DeferredItem<Item> MUSHROOM_KING_CORE = REGISTRY.register("mushroom_king_core",
			() -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
	public static final DeferredItem<Item> MUSHROOM_KING_SPORE = REGISTRY.register("mushroom_king_spore",
			() -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE)));
	public static final DeferredItem<Item> VIRTUAL_MUSHROOM_KING_SMITHING_TEMPLATE = REGISTRY.register(
			"virtual_mushroom_king_smithing_template",
			() -> new Item(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
	/**
	 * 光束绑定器 — 1:1 还原 ME-Beam-Former 1.21.1 的 LaserBindingTool。
	 * 交互：Shift+右键塔选源 / Shift+右键机器绑机器 / 右键塔绑塔 / Shift+右键空气清空。
	 */
	public static final DeferredItem<Item> LASER_BINDING_TOOL = REGISTRY.register("laser_binding_tool",
			() -> new LaserBindingTool(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> HOLOGRAPHIC_SHOP = REGISTRY.register("holographic_shop", HolographicTerminalItem::new);
	public static final DeferredItem<Item> POW_MINER = block(ModBlocks.POW_MINER);
	public static final DeferredItem<Item> WIRELESS_POWER_TOWER = block(ModBlocks.WIRELESS_POWER_TOWER);
	public static final DeferredItem<Item> VIRTUAL_GRASS_BLOCK = block(ModBlocks.VIRTUAL_GRASS_BLOCK);
	public static final DeferredItem<Item> VIRTUAL_DIRT = block(ModBlocks.VIRTUAL_DIRT);
	public static final DeferredItem<Item> VIRTUAL_STONE = block(ModBlocks.VIRTUAL_STONE);
	public static final DeferredItem<Item> CHARGED_GRASS_BLOCK = block(ModBlocks.CHARGED_GRASS_BLOCK);
	public static final DeferredItem<Item> CHARGED_DIRT = block(ModBlocks.CHARGED_DIRT);
	public static final DeferredItem<Item> LIGHTNING_STONE = block(ModBlocks.LIGHTNING_STONE);
	public static final DeferredItem<Item> FULGURITE = block(ModBlocks.FULGURITE);
	public static final DeferredItem<Item> TOXIC_GRASS_BLOCK = block(ModBlocks.TOXIC_GRASS_BLOCK);
	public static final DeferredItem<Item> TOXIC_DIRT = block(ModBlocks.TOXIC_DIRT);
	public static final DeferredItem<Item> SERPENT_STONE = block(ModBlocks.SERPENT_STONE);
	public static final DeferredItem<Item> VENOM_CRYSTAL = block(ModBlocks.VENOM_CRYSTAL);
	public static final DeferredItem<Item> SERPENT_STONE_BRICKS = block(ModBlocks.SERPENT_STONE_BRICKS);
	public static final DeferredItem<Item> CHISELED_SERPENT_STONE_BRICKS = block(ModBlocks.CHISELED_SERPENT_STONE_BRICKS);
	public static final DeferredItem<Item> SERPENT_FLOOR_TILES = block(ModBlocks.SERPENT_FLOOR_TILES);
	public static final DeferredItem<Item> SERPENT_LATTICE = block(ModBlocks.SERPENT_LATTICE);
	public static final DeferredItem<Item> MUSHROOM_GRASS_BLOCK = block(ModBlocks.MUSHROOM_GRASS_BLOCK);
	public static final DeferredItem<Item> MUSHROOM_DIRT = block(ModBlocks.MUSHROOM_DIRT);
	public static final DeferredItem<Item> FUNGAL_STONE = block(ModBlocks.FUNGAL_STONE);
	public static final DeferredItem<Item> SPORE_LAMP = block(ModBlocks.SPORE_LAMP);
	public static final DeferredItem<Item> FUNGAL_STONE_BRICKS = block(ModBlocks.FUNGAL_STONE_BRICKS);
	public static final DeferredItem<Item> CHISELED_FUNGAL_STONE_BRICKS = block(ModBlocks.CHISELED_FUNGAL_STONE_BRICKS);
	public static final DeferredItem<Item> FUNGAL_CAP_TILES = block(ModBlocks.FUNGAL_CAP_TILES);
	public static final DeferredItem<Item> FUNGAL_GILL_TILES = block(ModBlocks.FUNGAL_GILL_TILES);
	public static final DeferredItem<Item> VIRTUAL_IRON_ORE = block(ModBlocks.VIRTUAL_IRON_ORE);
	public static final DeferredItem<Item> VIRTUAL_SILVER_ORE = block(ModBlocks.VIRTUAL_SILVER_ORE);
	public static final DeferredItem<Item> VIRTUAL_ALUMINUM_ORE = block(ModBlocks.VIRTUAL_ALUMINUM_ORE);
	public static final DeferredItem<Item> VIRTUAL_GOLD_ORE = block(ModBlocks.VIRTUAL_GOLD_ORE);
	public static final DeferredItem<Item> VIRTUAL_TITANIUM_ORE = block(ModBlocks.VIRTUAL_TITANIUM_ORE);
	public static final DeferredItem<Item> VIRTUAL_CHROMIUM_ORE = block(ModBlocks.VIRTUAL_CHROMIUM_ORE);
	public static final DeferredItem<Item> RAW_VIRTUAL_IRON = REGISTRY.register("raw_virtual_iron", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> RAW_VIRTUAL_ALUMINUM = REGISTRY.register("raw_virtual_aluminum", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> RAW_VIRTUAL_SILVER = REGISTRY.register("raw_virtual_silver", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> RAW_VIRTUAL_GOLD = REGISTRY.register("raw_virtual_gold", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> RAW_VIRTUAL_TITANIUM = REGISTRY.register("raw_virtual_titanium", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> RAW_VIRTUAL_CHROMIUM = REGISTRY.register("raw_virtual_chromium", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> VIRTUAL_IRON_INGOT = REGISTRY.register("virtual_iron_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<ArmorItem> VIRTUAL_IRON_HELMET = virtualArmor(
			"virtual_iron_helmet", ModArmorMaterials.VIRTUAL_IRON, ModArmorMaterials.VIRTUAL_IRON_PROFILE,
			ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_IRON_CHESTPLATE = virtualArmor(
			"virtual_iron_chestplate", ModArmorMaterials.VIRTUAL_IRON, ModArmorMaterials.VIRTUAL_IRON_PROFILE,
			ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_IRON_LEGGINGS = virtualArmor(
			"virtual_iron_leggings", ModArmorMaterials.VIRTUAL_IRON, ModArmorMaterials.VIRTUAL_IRON_PROFILE,
			ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_IRON_BOOTS = virtualArmor(
			"virtual_iron_boots", ModArmorMaterials.VIRTUAL_IRON, ModArmorMaterials.VIRTUAL_IRON_PROFILE,
			ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_IRON_SWORD = virtualSword(
			"virtual_iron_sword", ModToolTiers.VIRTUAL_IRON, 4.0F, -2.35F);
	public static final DeferredItem<Item> VIRTUAL_ALUMINUM_INGOT = REGISTRY.register("virtual_aluminum_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> VIRTUAL_SILVER_INGOT = REGISTRY.register("virtual_silver_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<ArmorItem> VIRTUAL_SILVER_HELMET = virtualArmor(
			"virtual_silver_helmet", ModArmorMaterials.VIRTUAL_SILVER, ModArmorMaterials.VIRTUAL_SILVER_PROFILE,
			ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_SILVER_CHESTPLATE = virtualArmor(
			"virtual_silver_chestplate", ModArmorMaterials.VIRTUAL_SILVER, ModArmorMaterials.VIRTUAL_SILVER_PROFILE,
			ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_SILVER_LEGGINGS = virtualArmor(
			"virtual_silver_leggings", ModArmorMaterials.VIRTUAL_SILVER, ModArmorMaterials.VIRTUAL_SILVER_PROFILE,
			ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_SILVER_BOOTS = virtualArmor(
			"virtual_silver_boots", ModArmorMaterials.VIRTUAL_SILVER, ModArmorMaterials.VIRTUAL_SILVER_PROFILE,
			ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_SILVER_SWORD = virtualSword(
			"virtual_silver_sword", ModToolTiers.VIRTUAL_SILVER, 4.25F, -2.32F);
	public static final DeferredItem<Item> VIRTUAL_GOLD_INGOT = REGISTRY.register("virtual_gold_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> VIRTUAL_TITANIUM_INGOT = REGISTRY.register("virtual_titanium_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<ArmorItem> VIRTUAL_TITANIUM_HELMET = virtualArmor(
			"virtual_titanium_helmet", ModArmorMaterials.VIRTUAL_TITANIUM, ModArmorMaterials.VIRTUAL_TITANIUM_PROFILE,
			ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_TITANIUM_CHESTPLATE = virtualArmor(
			"virtual_titanium_chestplate", ModArmorMaterials.VIRTUAL_TITANIUM, ModArmorMaterials.VIRTUAL_TITANIUM_PROFILE,
			ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_TITANIUM_LEGGINGS = virtualArmor(
			"virtual_titanium_leggings", ModArmorMaterials.VIRTUAL_TITANIUM, ModArmorMaterials.VIRTUAL_TITANIUM_PROFILE,
			ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_TITANIUM_BOOTS = virtualArmor(
			"virtual_titanium_boots", ModArmorMaterials.VIRTUAL_TITANIUM, ModArmorMaterials.VIRTUAL_TITANIUM_PROFILE,
			ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_TITANIUM_SWORD = virtualSword(
			"virtual_titanium_sword", ModToolTiers.VIRTUAL_TITANIUM, 4.5F, -2.30F);
	public static final DeferredItem<Item> VIRTUAL_CHROMIUM_INGOT = REGISTRY.register("virtual_chromium_ingot", () -> new Item(new Item.Properties()));
	public static final DeferredItem<ArmorItem> VIRTUAL_CHROMIUM_HELMET = virtualArmor(
			"virtual_chromium_helmet", ModArmorMaterials.VIRTUAL_CHROMIUM, ModArmorMaterials.VIRTUAL_CHROMIUM_PROFILE,
			ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_CHROMIUM_CHESTPLATE = virtualArmor(
			"virtual_chromium_chestplate", ModArmorMaterials.VIRTUAL_CHROMIUM, ModArmorMaterials.VIRTUAL_CHROMIUM_PROFILE,
			ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_CHROMIUM_LEGGINGS = virtualArmor(
			"virtual_chromium_leggings", ModArmorMaterials.VIRTUAL_CHROMIUM, ModArmorMaterials.VIRTUAL_CHROMIUM_PROFILE,
			ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_CHROMIUM_BOOTS = virtualArmor(
			"virtual_chromium_boots", ModArmorMaterials.VIRTUAL_CHROMIUM, ModArmorMaterials.VIRTUAL_CHROMIUM_PROFILE,
			ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_CHROMIUM_SWORD = virtualSword(
			"virtual_chromium_sword", ModToolTiers.VIRTUAL_CHROMIUM, 5.0F, -2.20F);
	public static final DeferredItem<ArmorItem> VIRTUAL_SNAKE_KING_HELMET = virtualArmor(
			"virtual_snake_king_helmet", ModArmorMaterials.VIRTUAL_SNAKE_KING,
			ModArmorMaterials.VIRTUAL_SNAKE_KING_PROFILE, ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_SNAKE_KING_CHESTPLATE = virtualArmor(
			"virtual_snake_king_chestplate", ModArmorMaterials.VIRTUAL_SNAKE_KING,
			ModArmorMaterials.VIRTUAL_SNAKE_KING_PROFILE, ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_SNAKE_KING_LEGGINGS = virtualArmor(
			"virtual_snake_king_leggings", ModArmorMaterials.VIRTUAL_SNAKE_KING,
			ModArmorMaterials.VIRTUAL_SNAKE_KING_PROFILE, ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_SNAKE_KING_BOOTS = virtualArmor(
			"virtual_snake_king_boots", ModArmorMaterials.VIRTUAL_SNAKE_KING,
			ModArmorMaterials.VIRTUAL_SNAKE_KING_PROFILE, ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_SNAKE_KING_SWORD = snakeKingSword(
			"virtual_snake_king_sword", ModToolTiers.VIRTUAL_SNAKE_KING, 5.0F, -2.20F);
	public static final DeferredItem<ArmorItem> VIRTUAL_MUSHROOM_KING_HELMET = virtualArmor(
			"virtual_mushroom_king_helmet", ModArmorMaterials.VIRTUAL_MUSHROOM_KING,
			ModArmorMaterials.VIRTUAL_MUSHROOM_KING_PROFILE, ArmorItem.Type.HELMET);
	public static final DeferredItem<ArmorItem> VIRTUAL_MUSHROOM_KING_CHESTPLATE = virtualArmor(
			"virtual_mushroom_king_chestplate", ModArmorMaterials.VIRTUAL_MUSHROOM_KING,
			ModArmorMaterials.VIRTUAL_MUSHROOM_KING_PROFILE, ArmorItem.Type.CHESTPLATE);
	public static final DeferredItem<ArmorItem> VIRTUAL_MUSHROOM_KING_LEGGINGS = virtualArmor(
			"virtual_mushroom_king_leggings", ModArmorMaterials.VIRTUAL_MUSHROOM_KING,
			ModArmorMaterials.VIRTUAL_MUSHROOM_KING_PROFILE, ArmorItem.Type.LEGGINGS);
	public static final DeferredItem<ArmorItem> VIRTUAL_MUSHROOM_KING_BOOTS = virtualArmor(
			"virtual_mushroom_king_boots", ModArmorMaterials.VIRTUAL_MUSHROOM_KING,
			ModArmorMaterials.VIRTUAL_MUSHROOM_KING_PROFILE, ArmorItem.Type.BOOTS);
	public static final DeferredItem<SwordItem> VIRTUAL_MUSHROOM_KING_SWORD = mushroomKingSword(
			"virtual_mushroom_king_sword", ModToolTiers.VIRTUAL_MUSHROOM_KING, 5.0F, -2.20F);
	public static final DeferredItem<Item> VIRTUAL_COBBLESTONE = block(ModBlocks.VIRTUAL_COBBLESTONE);
	public static final DeferredItem<Item> ENERGY_PIPE = block(ModBlocks.ENERGY_PIPE);
	public static final DeferredItem<Item> COMPUTE_RACK = block(ModBlocks.COMPUTE_RACK);
	public static final DeferredItem<Item> LIGHTNING_RESONATOR = block(ModBlocks.LIGHTNING_RESONATOR);
	public static final DeferredItem<Item> QUANTUM_SIMULATION_CHAMBER = block(ModBlocks.QUANTUM_SIMULATION_CHAMBER);
	public static final DeferredItem<Item> LIGHTNING_LOG = block(ModBlocks.LIGHTNING_LOG);
	public static final DeferredItem<Item> LIGHTNING_LEAVES = block(ModBlocks.LIGHTNING_LEAVES);
	public static final DeferredItem<Item> BLUE_MUSHROOM = medicinalMushroom(ModBlocks.BLUE_MUSHROOM, Medicine.BLUE);
	public static final DeferredItem<Item> POISON_MUSHROOM = medicinalMushroom(ModBlocks.POISON_MUSHROOM, Medicine.POISON);
	public static final DeferredItem<Item> NITRIFYING_PARASOL = medicinalMushroom(
			ModBlocks.NITRIFYING_PARASOL, Medicine.NITRIFYING);
	public static final DeferredItem<Item> PARALYSIS_MUSHROOM = medicinalMushroom(
			ModBlocks.PARALYSIS_MUSHROOM, Medicine.PARALYSIS);
	public static final DeferredItem<Item> GHOST_NITRIFYING_PARASOL = medicinalMushroom(
			ModBlocks.GHOST_NITRIFYING_PARASOL, Medicine.GHOST_NITRIFYING);
	public static final DeferredItem<Item> MANDRAKE_MUSHROOM = medicinalMushroom(
			ModBlocks.MANDRAKE_MUSHROOM, Medicine.MANDRAKE);
	public static final DeferredItem<Item> HEARTBEAT_MUSHROOM = medicinalMushroom(
			ModBlocks.HEARTBEAT_MUSHROOM, Medicine.HEARTBEAT);
	public static final DeferredItem<Item> SNAKE_KING_ALTAR = block(ModBlocks.SNAKE_KING_ALTAR);
	public static final DeferredItem<Item> MUSHROOM_KING_ALTAR = block(ModBlocks.MUSHROOM_KING_ALTAR);
	public static final DeferredItem<Item> THUNDER_POOL_BUCKET = REGISTRY.register("thunder_pool_bucket",
			() -> new BucketItem(ModFluids.THUNDER_POOL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
	public static final DeferredItem<Item> CRYPTO_BTC = REGISTRY.register(CryptoCurrency.BTC.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.BTC.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_ETH = REGISTRY.register(CryptoCurrency.ETH.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.ETH.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_USDT = REGISTRY.register(CryptoCurrency.USDT.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.USDT.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_SOL = REGISTRY.register(CryptoCurrency.SOL.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.SOL.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_TRX = REGISTRY.register(CryptoCurrency.TRX.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.TRX.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_DOGE = REGISTRY.register(CryptoCurrency.DOGE.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.DOGE.stackLimit())));

	// === "电脑 + 进入虚拟世界" 流程物品（移植自 1.16.5 旧工程） =================
	// 合成依赖链：vanilla button + mouse_wheel → mouse；PC parts → computer_mainframe
	//             computer_mainframe + display + keyboard + mouse → computer
	// 旧版的 InsulatedElectricWire 在新工程被红石替代（绝缘装备体系已移除）。
	public static final DeferredItem<Item> MOUSE_WHEEL = REGISTRY.register("mouse_wheel", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> COPPER_WIRE = REGISTRY.register("copper_wire", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> SILICON = REGISTRY.register("silicon", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> SILICON_WAFER = REGISTRY.register("silicon_wafer", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> CIRCUIT_BOARD = REGISTRY.register("circuit_board", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> CPU = REGISTRY.register("cpu", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> GPU = REGISTRY.register("gpu", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> MEMORY_STICK = REGISTRY.register("memory_stick", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> POWER_SUPPLY = REGISTRY.register("power_supply", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> HARD_DRIVE = REGISTRY.register("hard_drive", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> DISPLAY = REGISTRY.register("display", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> KEYBOARD = REGISTRY.register("keyboard", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> MOUSE = REGISTRY.register("mouse", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> COMPUTER_MAINFRAME = REGISTRY.register("computer_mainframe", () -> new Item(new Item.Properties()));
	/** 电脑：右键空气在主世界 ↔ 虚拟世界之间切换。 */
	public static final DeferredItem<Item> COMPUTER = REGISTRY.register("computer", ComputerItem::new);

	public static List<Item> hashVoucherItems() {
		return List.of(
				HASH_VOUCHER.get(),
				EXPLORER_HASH_VOUCHER.get(),
				ENCRYPTED_HASH_VOUCHER.get(),
				QUANTUM_HASH_VOUCHER.get(),
				GENESIS_HASH_VOUCHER.get());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, ? extends Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, ? extends Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
	}

	private static DeferredItem<Item> medicinalMushroom(
			DeferredHolder<Block, ? extends Block> block, Medicine medicine) {
		return REGISTRY.register(block.getId().getPath(),
				() -> new MedicinalMushroomItem(block.get(), medicine));
	}

	private static DeferredItem<ArmorItem> virtualArmor(String name, Holder<ArmorMaterial> material,
			ModArmorMaterials.ArmorSetProfile profile, ArmorItem.Type type) {
		return REGISTRY.register(name, () -> new VirtualMetalArmorItem(
				material, type,
				new Item.Properties().durability(type.getDurability(profile.durabilityMultiplier())),
				VirtualMetalArmorItem.Style.fromItemName(name)));
	}

	private static DeferredItem<SwordItem> virtualSword(String name, Tier tier, float attackDamage,
			float attackSpeed) {
		return REGISTRY.register(name, () -> new VirtualSwordItem(tier, new Item.Properties()
				.attributes(SwordItem.createAttributes(tier, attackDamage, attackSpeed)),
				VirtualSwordItem.Trait.NONE));
	}

	private static DeferredItem<SwordItem> snakeKingSword(String name, Tier tier, float attackDamage,
			float attackSpeed) {
		return REGISTRY.register(name, () -> new VirtualSwordItem(tier, new Item.Properties()
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.attributes(SwordItem.createAttributes(tier, attackDamage, attackSpeed)),
				VirtualSwordItem.Trait.SNAKE_KING));
	}

	private static DeferredItem<SwordItem> mushroomKingSword(String name, Tier tier,
			float attackDamage, float attackSpeed) {
		return REGISTRY.register(name, () -> new VirtualSwordItem(tier, new Item.Properties()
				.rarity(net.minecraft.world.item.Rarity.EPIC)
				.attributes(SwordItem.createAttributes(tier, attackDamage, attackSpeed)),
				VirtualSwordItem.Trait.MUSHROOM_KING));
	}

	@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class ItemsClientSideHandler {
		@SubscribeEvent
		@OnlyIn(Dist.CLIENT)
		public static void clientLoad(FMLClientSetupEvent event) {
			event.enqueueWork(() -> {
				ItemProperties.register(ELECTRIC_ABSORBER.get(), ResourceLocation.parse("virtual_craft:electric_absorber_time"),
						(itemStackToRender, clientWorld, entity, itemEntityId) -> (float) ElectricAbsorberChargeHelper.getModelChargeValue(entity, itemStackToRender));
			});
		}
	}
}


