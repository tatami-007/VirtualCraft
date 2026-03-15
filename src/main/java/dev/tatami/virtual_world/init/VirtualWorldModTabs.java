/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package dev.tatami.virtual_world.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import dev.tatami.virtual_world.VirtualWorldMod;

public class VirtualWorldModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirtualWorldMod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VIRTUAL_WORLD_MOD_TAB = REGISTRY.register("virtual_world_mod_tab",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.virtual_world.virtual_world_mod_tab")).icon(() -> new ItemStack(VirtualWorldModItems.ELECTRIC_BALL.get())).displayItems((parameters, tabData) -> {
				tabData.accept(VirtualWorldModBlocks.ELECTRIC_ORE.get().asItem());
				tabData.accept(VirtualWorldModBlocks.DEEPSLATE_ELECTRIC_ORE.get().asItem());
				tabData.accept(VirtualWorldModItems.ELECTRIC_BALL.get());
				tabData.accept(VirtualWorldModItems.ELECTRIC_ABSORBER.get());
			}).build());
}