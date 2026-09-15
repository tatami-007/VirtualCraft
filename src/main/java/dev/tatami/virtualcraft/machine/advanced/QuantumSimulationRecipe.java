package dev.tatami.virtualcraft.machine.advanced;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.tatami.virtualcraft.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record QuantumSimulationRecipe(
		QuantumProgram program,
		int durationTicks,
		long totalEnergy,
		int resonanceCost,
		int minimumCompute,
		int baseRangeChunks) implements Recipe<QuantumSimulationRecipeInput> {

	private static final Codec<Integer> POSITIVE_INT = Codec.INT.validate(value ->
			value > 0
					? com.mojang.serialization.DataResult.success(value)
					: com.mojang.serialization.DataResult.error(() -> "value must be positive"));
	private static final Codec<Long> POSITIVE_LONG = Codec.LONG.validate(value ->
			value > 0
					? com.mojang.serialization.DataResult.success(value)
					: com.mojang.serialization.DataResult.error(() -> "value must be positive"));

	@Override
	public boolean matches(QuantumSimulationRecipeInput input, Level level) {
		return true;
	}

	@Override
	public ItemStack assemble(QuantumSimulationRecipeInput input, HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return ItemStack.EMPTY;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeTypes.QUANTUM_SIMULATION_SERIALIZER.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ModRecipeTypes.QUANTUM_SIMULATION_TYPE.get();
	}

	public static final class Serializer implements RecipeSerializer<QuantumSimulationRecipe> {
		private static final MapCodec<QuantumSimulationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
				instance.group(
						QuantumProgram.CODEC.fieldOf("program").forGetter(QuantumSimulationRecipe::program),
						POSITIVE_INT.fieldOf("duration_ticks").forGetter(QuantumSimulationRecipe::durationTicks),
						POSITIVE_LONG.fieldOf("total_energy").forGetter(QuantumSimulationRecipe::totalEnergy),
						POSITIVE_INT.fieldOf("resonance_cost").forGetter(QuantumSimulationRecipe::resonanceCost),
						POSITIVE_INT.fieldOf("minimum_compute").forGetter(QuantumSimulationRecipe::minimumCompute),
						POSITIVE_INT.fieldOf("base_range_chunks").forGetter(QuantumSimulationRecipe::baseRangeChunks))
						.apply(instance, QuantumSimulationRecipe::new));

		private static final StreamCodec<RegistryFriendlyByteBuf, QuantumSimulationRecipe> STREAM_CODEC =
				StreamCodec.composite(
						ByteBufCodecs.idMapper(
								index -> QuantumProgram.byIndex(index),
								QuantumProgram::ordinal),
						QuantumSimulationRecipe::program,
						ByteBufCodecs.VAR_INT,
						QuantumSimulationRecipe::durationTicks,
						ByteBufCodecs.VAR_LONG,
						QuantumSimulationRecipe::totalEnergy,
						ByteBufCodecs.VAR_INT,
						QuantumSimulationRecipe::resonanceCost,
						ByteBufCodecs.VAR_INT,
						QuantumSimulationRecipe::minimumCompute,
						ByteBufCodecs.VAR_INT,
						QuantumSimulationRecipe::baseRangeChunks,
						QuantumSimulationRecipe::new);

		@Override
		public MapCodec<QuantumSimulationRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, QuantumSimulationRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
