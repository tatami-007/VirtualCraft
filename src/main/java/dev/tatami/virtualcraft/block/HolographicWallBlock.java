package dev.tatami.virtualcraft.block;

import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class HolographicWallBlock extends HalfTransparentBlock {
	public HolographicWallBlock() {
		super(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_BLUE)
				.strength(50.0f, 1200.0f)
				.sound(SoundType.GLASS)
				.lightLevel(state -> 7)
				.noOcclusion()
				.requiresCorrectToolForDrops());
	}
}
