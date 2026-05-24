package dev.tatami.virtualcraft.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.tatami.virtualcraft.blockentity.HolographicShopBlockEntity;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.network.HolographicShopSyncDispatcher;
import dev.tatami.virtualcraft.network.OpenHolographicTerminalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

@SuppressWarnings("this-escape") // registerDefaultState() in the constructor is the canonical Mojang Block pattern.
public class HolographicShopBlock extends BaseEntityBlock {
	public static final MapCodec<HolographicShopBlock> CODEC = simpleCodec(HolographicShopBlock::new);
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	private HolographicShopBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	public HolographicShopBlock() {
		this(BlockBehaviour.Properties.of().strength(3.5F, 6.0F).requiresCorrectToolForDrops());
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		// Avoid deprecated BlockState.rotate(Rotation); delegate to our own rotate() impl.
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			// 与 HolographicTerminalItem 同一路径：发 Open payload 到客户端，客户端用
			// MuiModApi.openScreen 打开 SimpleScreen（非 AbstractContainerScreen）——JEI 默认不识别。
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof HolographicShopBlockEntity) {
				int sessionId = HolographicTerminalSession.open(serverPlayer);
				PacketDistributor.sendToPlayer(serverPlayer, new OpenHolographicTerminalPayload(sessionId, /*itemMode=*/ false, pos));
				HolographicShopSyncDispatcher.pushInitialSnapshot(serverPlayer);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HolographicShopBlockEntity(pos, state);
	}
}
