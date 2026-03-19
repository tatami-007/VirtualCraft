/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package dev.tatami.virtualworld.core;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.core.definitions.VWBlocks;
import dev.tatami.virtualworld.core.definitions.VWItems;

public final class VWTabs {
	private VWTabs() {
	}

	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirtualWorld.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VIRTUAL_WORLD_MOD_TAB = REGISTRY.register("virtual_world_mod_tab",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.virtual_world.virtual_world_mod_tab")).icon(() -> new ItemStack(VWItems.ELECTRIC_BALL.get())).displayItems((parameters, tabData) -> {
				tabData.accept(VWBlocks.ELECTRIC_ORE.get().asItem());
				tabData.accept(VWBlocks.DEEPSLATE_ELECTRIC_ORE.get().asItem());
				tabData.accept(VWBlocks.ELECTRIC_CONDENSER.get().asItem());
				tabData.accept(VWItems.ELECTRIC_BALL.get());
				tabData.accept(VWItems.ELECTRIC_INGOT.get());
				tabData.accept(VWItems.ELECTRIC_ABSORBER.get());
			}).build());
}
