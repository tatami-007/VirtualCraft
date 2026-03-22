package dev.tatami.virtualcraft.registry;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;

public final class ModCreativeTabs {
	private ModCreativeTabs() {
	}

	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirtualCraft.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = REGISTRY.register("virtual_craft_mod_tab",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.virtual_craft.virtual_craft_mod_tab")).icon(() -> new ItemStack(ModItems.ELECTRIC_BALL.get())).displayItems((parameters, tabData) -> {
				tabData.accept(ModBlocks.ELECTRIC_ORE.get().asItem());
				tabData.accept(ModBlocks.DEEPSLATE_ELECTRIC_ORE.get().asItem());
				tabData.accept(ModBlocks.ELECTRIC_CONDENSER.get().asItem());
				tabData.accept(ModBlocks.ELECTRIC_BALL_GENERATOR.get().asItem());
				tabData.accept(ModItems.HOLOGRAPHIC_SHOP.get());
				tabData.accept(ModBlocks.POW_MINER.get().asItem());
				tabData.accept(ModBlocks.WIRELESS_POWER_TOWER.get().asItem());
				tabData.accept(ModBlocks.WIRELESS_POWER_RECEIVER.get().asItem());
				tabData.accept(ModItems.ELECTRIC_BALL.get());
				tabData.accept(ModItems.ELECTRIC_INGOT.get());
				tabData.accept(ModItems.ELECTRIC_ABSORBER.get());
				tabData.accept(ModItems.HASH_VOUCHER.get());
				tabData.accept(ModItems.MINING_SURVEYOR.get());
				tabData.accept(ModItems.SPEED_UPGRADE_CARD.get());
				tabData.accept(ModItems.LINK_CARD.get());
				tabData.accept(ModItems.CRYPTO_BTC.get());
				tabData.accept(ModItems.CRYPTO_ETH.get());
				tabData.accept(ModItems.CRYPTO_USDT.get());
				tabData.accept(ModItems.CRYPTO_SOL.get());
				tabData.accept(ModItems.CRYPTO_DOGE.get());
			}).build());
}


