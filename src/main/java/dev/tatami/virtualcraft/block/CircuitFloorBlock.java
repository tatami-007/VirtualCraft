package dev.tatami.virtualcraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class CircuitFloorBlock extends Block {
	public CircuitFloorBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_CYAN)
				.strength(50.0f, 1200.0f)
				.sound(SoundType.METAL)
				.lightLevel(state -> 3)
				.requiresCorrectToolForDrops());
	}
}
