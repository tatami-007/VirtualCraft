package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.machine.advanced.QuantumSimulationRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeTypes {
	private ModRecipeTypes() {
	}

	public static final DeferredRegister<RecipeType<?>> TYPES =
			DeferredRegister.create(Registries.RECIPE_TYPE, VirtualCraft.MODID);
	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, VirtualCraft.MODID);

	public static final DeferredHolder<RecipeType<?>, RecipeType<QuantumSimulationRecipe>> QUANTUM_SIMULATION_TYPE =
			TYPES.register("quantum_simulation", () -> new RecipeType<>() {
				@Override
				public String toString() {
					return VirtualCraft.MODID + ":quantum_simulation";
				}
			});
	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<QuantumSimulationRecipe>> QUANTUM_SIMULATION_SERIALIZER =
			SERIALIZERS.register("quantum_simulation", QuantumSimulationRecipe.Serializer::new);
}
