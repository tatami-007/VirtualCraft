package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.world.ThunderPoolFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
	private ModFeatures() {
	}

	public static final DeferredRegister<Feature<?>> REGISTRY =
			DeferredRegister.create(Registries.FEATURE, VirtualCraft.MODID);

	public static final DeferredHolder<Feature<?>, ThunderPoolFeature> THUNDER_POOL =
			REGISTRY.register("thunder_pool", ThunderPoolFeature::new);
}
