/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package dev.tatami.virtual_world.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.world.level.block.Block;

import dev.tatami.virtual_world.block.ElectricOreBlock;
import dev.tatami.virtual_world.block.DeepslateElectricOreBlock;
import dev.tatami.virtual_world.VirtualWorldMod;

public class VirtualWorldModBlocks {
	public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(VirtualWorldMod.MODID);
	public static final DeferredBlock<Block> ELECTRIC_ORE = REGISTRY.register("electric_ore", ElectricOreBlock::new);
	public static final DeferredBlock<Block> DEEPSLATE_ELECTRIC_ORE = REGISTRY.register("deepslate_electric_ore", DeepslateElectricOreBlock::new);
	// Start of user code block custom blocks
	// End of user code block custom blocks
}