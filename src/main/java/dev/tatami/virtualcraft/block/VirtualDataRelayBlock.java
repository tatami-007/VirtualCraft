package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class VirtualDataRelayBlock extends VirtualDataRoomNodeBlock {
	public static final MapCodec<VirtualDataRelayBlock> CODEC = simpleCodec(VirtualDataRelayBlock::new);

	public VirtualDataRelayBlock() {
		super(NodeType.RELAY);
	}

	public VirtualDataRelayBlock(BlockBehaviour.Properties properties) {
		super(properties, NodeType.RELAY);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
}
