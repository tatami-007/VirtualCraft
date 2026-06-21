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
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import dev.tatami.virtualcraft.item.ComputerItem;
import dev.tatami.virtualcraft.item.ElectricAbsorberItem;
import dev.tatami.virtualcraft.item.ElectricBallItem;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.item.HolographicTerminalItem;
import dev.tatami.virtualcraft.item.LaserBindingTool;
import dev.tatami.virtualcraft.item.MiningSurveyorItem;
import dev.tatami.virtualcraft.item.SpeedUpgradeCardItem;
import dev.tatami.virtualcraft.gameplay.ElectricAbsorberChargeHelper;

public final class ModItems {
	private ModItems() {
	}

	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(VirtualCraft.MODID);
	public static final DeferredItem<Item> ELECTRIC_ORE = block(ModBlocks.ELECTRIC_ORE);
	public static final DeferredItem<Item> DEEPSLATE_ELECTRIC_ORE = block(ModBlocks.DEEPSLATE_ELECTRIC_ORE);
	public static final DeferredItem<Item> ELECTRIC_CONDENSER = block(ModBlocks.ELECTRIC_CONDENSER);
	public static final DeferredItem<Item> ELECTRIC_BALL_GENERATOR = block(ModBlocks.ELECTRIC_BALL_GENERATOR);
	public static final DeferredItem<Item> ELECTRIC_BALL = REGISTRY.register("electric_ball", ElectricBallItem::new);
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
	/**
	 * 光束绑定器 — 1:1 还原 ME-Beam-Former 1.21.1 的 LaserBindingTool。
	 * 交互：Shift+右键塔选源 / Shift+右键机器绑机器 / 右键塔绑塔 / Shift+右键空气清空。
	 */
	public static final DeferredItem<Item> LASER_BINDING_TOOL = REGISTRY.register("laser_binding_tool",
			() -> new LaserBindingTool(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> HOLOGRAPHIC_SHOP = REGISTRY.register("holographic_shop", HolographicTerminalItem::new);
	public static final DeferredItem<Item> POW_MINER = block(ModBlocks.POW_MINER);
	public static final DeferredItem<Item> WIRELESS_POWER_TOWER = block(ModBlocks.WIRELESS_POWER_TOWER);
	public static final DeferredItem<Item> DATA_FRAGMENT = REGISTRY.register("data_fragment", () -> new Item(new Item.Properties()));
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
	public static final DeferredItem<Item> COMPUTER = REGISTRY.register("computer", () -> new Item(new Item.Properties().stacksTo(1)));

	public static List<Item> hashVoucherItems() {
		return List.of(
				HASH_VOUCHER.get(),
				EXPLORER_HASH_VOUCHER.get(),
				ENCRYPTED_HASH_VOUCHER.get(),
				QUANTUM_HASH_VOUCHER.get(),
				GENESIS_HASH_VOUCHER.get());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block) {
		return block(block, new Item.Properties());
	}

	private static DeferredItem<Item> block(DeferredHolder<Block, Block> block, Item.Properties properties) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties));
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


