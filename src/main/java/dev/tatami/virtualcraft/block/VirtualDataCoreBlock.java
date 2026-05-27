package dev.tatami.virtualcraft.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.blockentity.VirtualDataCoreBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlockEntities;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VirtualDataCoreBlock extends BaseEntityBlock {
	public static final MapCodec<VirtualDataCoreBlock> CODEC = simpleCodec(VirtualDataCoreBlock::new);

	public VirtualDataCoreBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	public VirtualDataCoreBlock() {
		this(BlockBehaviour.Properties.of()
				.strength(-1.0F, 3_600_000.0F)
				.lightLevel(state -> 10)
				.noLootTable());
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
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof VirtualDataCoreBlockEntity core) {
			core.showStatus(player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (stack.is(ModItems.ELECTRIC_BALL.get())) {
			if (!level.isClientSide && level.getBlockEntity(pos) instanceof VirtualDataCoreBlockEntity core) {
				core.tryStart(player, stack);
			}
			return ItemInteractionResult.sidedSuccess(level.isClientSide);
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VirtualDataCoreBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, ModBlockEntities.VIRTUAL_DATA_CORE.get(), VirtualDataCoreBlockEntity::tick);
	}
}
