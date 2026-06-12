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
				tabData.accept(ModItems.ELECTRIC_BALL.get());
				tabData.accept(ModItems.ELECTRIC_INGOT.get());
				tabData.accept(ModItems.ELECTRIC_ABSORBER.get());
				tabData.accept(ModItems.HASH_VOUCHER.get());
				tabData.accept(ModItems.EXPLORER_HASH_VOUCHER.get());
				tabData.accept(ModItems.ENCRYPTED_HASH_VOUCHER.get());
				tabData.accept(ModItems.QUANTUM_HASH_VOUCHER.get());
				tabData.accept(ModItems.GENESIS_HASH_VOUCHER.get());
				tabData.accept(ModItems.DATA_FRAGMENT.get());
				tabData.accept(ModItems.MINING_SURVEYOR.get());
				tabData.accept(ModItems.SPEED_UPGRADE_CARD.get());
				// 1:1 ME-Beam-Former 风格的光束绑定器（替换了 v20 的 LinkCard）
				tabData.accept(ModItems.LASER_BINDING_TOOL.get());
				tabData.accept(ModItems.CRYPTO_BTC.get());
				tabData.accept(ModItems.CRYPTO_ETH.get());
				tabData.accept(ModItems.CRYPTO_USDT.get());
				tabData.accept(ModItems.CRYPTO_SOL.get());
				tabData.accept(ModItems.CRYPTO_TRX.get());
				tabData.accept(ModItems.CRYPTO_DOGE.get());
				// === 电脑 + 虚拟世界（移植自 1.16.5 旧工程） ===
				// 顺序按合成依赖：原料 → 子部件 → 主机 → 电脑（玩家最常拿，放最后便于热键定位）。
				tabData.accept(ModItems.MOUSE_WHEEL.get());
				tabData.accept(ModItems.COPPER_WIRE.get());
				tabData.accept(ModItems.SILICON.get());
				tabData.accept(ModItems.SILICON_WAFER.get());
				tabData.accept(ModItems.CIRCUIT_BOARD.get());
				tabData.accept(ModItems.CPU.get());
				tabData.accept(ModItems.GPU.get());
				tabData.accept(ModItems.MEMORY_STICK.get());
				tabData.accept(ModItems.POWER_SUPPLY.get());
				tabData.accept(ModItems.HARD_DRIVE.get());
				tabData.accept(ModItems.DISPLAY.get());
				tabData.accept(ModItems.KEYBOARD.get());
				tabData.accept(ModItems.MOUSE.get());
				tabData.accept(ModItems.COMPUTER_MAINFRAME.get());
				tabData.accept(ModItems.COMPUTER.get());
				tabData.accept(ModBlocks.VIRTUAL_DATA_CORE.get().asItem());
				tabData.accept(ModBlocks.VIRTUAL_DATA_ANCHOR.get().asItem());
				tabData.accept(ModBlocks.VIRTUAL_DATA_RELAY.get().asItem());
				tabData.accept(ModBlocks.CORRUPTED_DATA_NODE.get().asItem());
				tabData.accept(ModBlocks.VIRTUAL_DATA_CACHE.get().asItem());
				tabData.accept(ModBlocks.VIRTUAL_DATA_GATE.get().asItem());
				tabData.accept(ModItems.VIRTUAL_SHEEP_SPAWN_EGG.get());
			}).build());
}


