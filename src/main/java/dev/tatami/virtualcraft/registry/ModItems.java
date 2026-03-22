package dev.tatami.virtualcraft.registry;

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
import dev.tatami.virtualcraft.item.ElectricAbsorberItem;
import dev.tatami.virtualcraft.item.ElectricBallItem;
import dev.tatami.virtualcraft.item.HashVoucherItem;
import dev.tatami.virtualcraft.item.HolographicTerminalItem;
import dev.tatami.virtualcraft.item.LinkCardItem;
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
	public static final DeferredItem<Item> HASH_VOUCHER = REGISTRY.register("hash_voucher", HashVoucherItem::new);
	public static final DeferredItem<Item> MINING_SURVEYOR = REGISTRY.register("mining_surveyor", MiningSurveyorItem::new);
	public static final DeferredItem<Item> SPEED_UPGRADE_CARD = REGISTRY.register("speed_upgrade_card", SpeedUpgradeCardItem::new);
	public static final DeferredItem<Item> LINK_CARD = REGISTRY.register("link_card", LinkCardItem::new);
	public static final DeferredItem<Item> HOLOGRAPHIC_SHOP = REGISTRY.register("holographic_shop", HolographicTerminalItem::new);
	public static final DeferredItem<Item> CURRENCY_GENERATOR = block(ModBlocks.CURRENCY_GENERATOR);
	public static final DeferredItem<Item> POW_MINER = block(ModBlocks.POW_MINER);
	public static final DeferredItem<Item> WIRELESS_POWER_TOWER = block(ModBlocks.WIRELESS_POWER_TOWER);
	public static final DeferredItem<Item> WIRELESS_POWER_RECEIVER = block(ModBlocks.WIRELESS_POWER_RECEIVER);
	public static final DeferredItem<Item> CRYPTO_BTC = REGISTRY.register(CryptoCurrency.BTC.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.BTC.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_ETH = REGISTRY.register(CryptoCurrency.ETH.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.ETH.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_USDT = REGISTRY.register(CryptoCurrency.USDT.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.USDT.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_SOL = REGISTRY.register(CryptoCurrency.SOL.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.SOL.stackLimit())));
	public static final DeferredItem<Item> CRYPTO_DOGE = REGISTRY.register(CryptoCurrency.DOGE.itemName(), () -> new Item(new Item.Properties().stacksTo(CryptoCurrency.DOGE.stackLimit())));
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


