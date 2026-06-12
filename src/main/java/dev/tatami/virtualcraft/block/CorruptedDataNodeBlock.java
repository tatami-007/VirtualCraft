package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CorruptedDataNodeBlock extends VirtualDataRoomNodeBlock {
	public static final MapCodec<CorruptedDataNodeBlock> CODEC = simpleCodec(CorruptedDataNodeBlock::new);

	public CorruptedDataNodeBlock() {
		super(NodeType.CORRUPTED);
	}

	public CorruptedDataNodeBlock(BlockBehaviour.Properties properties) {
		super(properties, NodeType.CORRUPTED);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
}
