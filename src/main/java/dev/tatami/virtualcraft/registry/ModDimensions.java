package dev.tatami.virtualcraft.registry;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.world.VirtualWorldChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDimensions {
	private ModDimensions() {
	}

	public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
			DeferredRegister.create(Registries.CHUNK_GENERATOR, VirtualCraft.MODID);

	public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<VirtualWorldChunkGenerator>> VIRTUAL_WORLD_GENERATOR =
			CHUNK_GENERATORS.register("virtual_world", () -> VirtualWorldChunkGenerator.CODEC);

	public static final ResourceKey<DimensionType> VIRTUAL_WORLD_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));

	public static final ResourceKey<Level> VIRTUAL_WORLD_LEVEL = ResourceKey.create(Registries.DIMENSION,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));
}
