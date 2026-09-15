package dev.tatami.virtualcraft.machine.advanced;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public final class QuantumSimulationRecipeInput implements RecipeInput {
	public static final QuantumSimulationRecipeInput INSTANCE = new QuantumSimulationRecipeInput();

	private QuantumSimulationRecipeInput() {
	}

	@Override
	public ItemStack getItem(int index) {
		throw new IndexOutOfBoundsException(index);
	}

	@Override
	public int size() {
		return 0;
	}
}
