package dev.tatami.virtualcraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class EnergyPylonBlock extends Block {
	public EnergyPylonBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_BLUE)
				.strength(50.0f, 1200.0f)
				.sound(SoundType.AMETHYST)
				.lightLevel(state -> 15)
				.requiresCorrectToolForDrops()
				.noOcclusion());
	}
}
