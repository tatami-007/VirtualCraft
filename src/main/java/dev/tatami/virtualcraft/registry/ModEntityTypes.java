package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSheep;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
	private ModEntityTypes() {
	}

	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, VirtualCraft.MODID);

	public static final DeferredHolder<EntityType<?>, EntityType<VirtualSheep>> VIRTUAL_SHEEP = REGISTRY.register("virtual_sheep",
			() -> EntityType.Builder.of(VirtualSheep::new, MobCategory.CREATURE)
					.sized(0.9F, 1.3F)
					.eyeHeight(1.1F)
					.clientTrackingRange(10)
					.build("virtual_craft:virtual_sheep"));

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(VIRTUAL_SHEEP.get(), VirtualSheep.createAttributes().build());
	}
}
