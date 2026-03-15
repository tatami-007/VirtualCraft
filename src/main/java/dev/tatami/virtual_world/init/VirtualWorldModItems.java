/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package dev.tatami.virtual_world.init;

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

import dev.tatami.virtual_world.procedures.ElectricAbsorberUseTimeProcedure;
import dev.tatami.virtual_world.item.ElectricBallItem;
import dev.tatami.virtual_world.item.ElectricAbsorberItem;
import dev.tatami.virtual_world.VirtualWorldMod;

public class VirtualWorldModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(VirtualWorldMod.MODID);
	public static final DeferredItem<Item> ELECTRIC_ORE = block(VirtualWorldModBlocks.ELECTRIC_ORE);
	public static final DeferredItem<Item> DEEPSLATE_ELECTRIC_ORE = block(VirtualWorldModBlocks.DEEPSLATE_ELECTRIC_ORE);
	public static final DeferredItem<Item> ELECTRIC_BALL = REGISTRY.register("electric_ball", ElectricBallItem::new);
	public static final DeferredItem<Item> ELECTRIC_ABSORBER = REGISTRY.register("electric_absorber", ElectricAbsorberItem::new);

	// Start of user code block custom items
	// End of user code block custom items
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
				ItemProperties.register(ELECTRIC_ABSORBER.get(), ResourceLocation.parse("virtual_world:electric_absorber_time"),
						(itemStackToRender, clientWorld, entity, itemEntityId) -> (float) ElectricAbsorberUseTimeProcedure.execute(entity, itemStackToRender));
			});
		}
	}
}
