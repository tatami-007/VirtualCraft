package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public final class ModDimensions {
	private ModDimensions() {
	}

	public static final ResourceKey<DimensionType> VIRTUAL_WORLD_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));

	public static final ResourceKey<Level> VIRTUAL_WORLD_LEVEL = ResourceKey.create(Registries.DIMENSION,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));
}
