package dev.tatami.virtualcraft.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class VirtualDataCacheBlock extends VirtualDataRoomNodeBlock {
	public static final MapCodec<VirtualDataCacheBlock> CODEC = simpleCodec(VirtualDataCacheBlock::new);

	public VirtualDataCacheBlock() {
		super(NodeType.CACHE);
	}

	public VirtualDataCacheBlock(BlockBehaviour.Properties properties) {
		super(properties, NodeType.CACHE);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
}
