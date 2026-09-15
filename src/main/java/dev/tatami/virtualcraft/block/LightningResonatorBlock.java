package dev.tatami.virtualcraft.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.blockentity.LightningResonatorBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LightningResonatorBlock extends BaseEntityBlock {
	public static final MapCodec<LightningResonatorBlock> CODEC = simpleCodec(LightningResonatorBlock::new);

	private LightningResonatorBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	public LightningResonatorBlock() {
		this(BlockBehaviour.Properties.of().strength(4.0F, 8.0F).requiresCorrectToolForDrops().lightLevel(state -> 6));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hitResult) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
				&& level.getBlockEntity(pos) instanceof LightningResonatorBlockEntity resonator) {
			serverPlayer.openMenu(resonator, buffer -> buffer.writeBlockPos(pos));
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LightningResonatorBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, ModBlockEntities.LIGHTNING_RESONATOR.get(),
				LightningResonatorBlockEntity::tick);
	}
}
