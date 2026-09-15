package dev.tatami.virtualcraft.block;

import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** Structure-only altar that consumes the matching boss summoning item. */
public final class BossAltarBlock extends Block {
	private static final double BOSS_EXCLUSION_RADIUS = 128.0D;

	public enum Boss {
		SNAKE,
		MUSHROOM
	}

	private final Boss boss;

	public BossAltarBlock(Boss boss, BlockBehaviour.Properties properties) {
		super(properties);
		this.boss = boss;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (!isActivationItem(stack)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return ItemInteractionResult.FAIL;
		}
		if (!serverLevel.dimension().equals(ModDimensions.VIRTUAL_WORLD_LEVEL)) {
			serverPlayer.displayClientMessage(Component.translatable(dimensionErrorKey())
					.withStyle(ChatFormatting.RED), true);
			return ItemInteractionResult.FAIL;
		}
		if (hasNearbyBoss(serverLevel, pos)) {
			serverPlayer.displayClientMessage(Component.translatable(alreadyPresentKey())
					.withStyle(ChatFormatting.RED), true);
			return ItemInteractionResult.FAIL;
		}
		if (!spawnBoss(serverLevel, serverPlayer, pos)) {
			serverPlayer.displayClientMessage(Component.translatable(spaceErrorKey())
					.withStyle(ChatFormatting.RED), true);
			return ItemInteractionResult.FAIL;
		}

		serverPlayer.getCooldowns().addCooldown(stack.getItem(), 100);
		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return ItemInteractionResult.SUCCESS;
	}

	private boolean isActivationItem(ItemStack stack) {
		return boss == Boss.SNAKE
				? stack.is(ModItems.SNAKE_KING_KEY.get())
				: stack.is(ModItems.MUSHROOM_KING_SEAL.get());
	}

	private boolean hasNearbyBoss(ServerLevel level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(BOSS_EXCLUSION_RADIUS);
		return boss == Boss.SNAKE
				? !level.getEntitiesOfClass(VirtualSnakeKing.class, area, VirtualSnakeKing::isAlive).isEmpty()
				: !level.getEntitiesOfClass(VirtualMushroomKing.class, area, VirtualMushroomKing::isAlive).isEmpty();
	}

	private boolean spawnBoss(ServerLevel level, ServerPlayer player, BlockPos altarPos) {
		if (boss == Boss.SNAKE) {
			VirtualSnakeKing snake = ModEntityTypes.VIRTUAL_SNAKE_KING.get().create(level);
			if (snake == null) {
				return false;
			}
			snake.moveTo(altarPos.getX() + 0.5D, altarPos.getY() + 1.05D, altarPos.getZ() + 0.5D,
					player.getYRot() + 180.0F, 0.0F);
			snake.setHome(altarPos);
			if (!level.addFreshEntity(snake)) {
				snake.discard();
				return false;
			}
			snake.beginSummoning(altarPos, player.getUUID());
			return true;
		}

		VirtualMushroomKing mushroom = ModEntityTypes.VIRTUAL_MUSHROOM_KING.get().create(level);
		if (mushroom == null) {
			return false;
		}
		mushroom.moveTo(altarPos.getX() + 0.5D, altarPos.getY() + 1.0D, altarPos.getZ() + 0.5D,
				player.getYRot() + 180.0F, 0.0F);
		if (!level.addFreshEntity(mushroom)) {
			mushroom.discard();
			return false;
		}
		mushroom.beginSummoning(altarPos, player.getUUID());
		return true;
	}

	private String dimensionErrorKey() {
		return boss == Boss.SNAKE
				? "message.virtual_craft.snake_king.virtual_world_only"
				: "message.virtual_craft.mushroom_king.virtual_world_only";
	}

	private String alreadyPresentKey() {
		return boss == Boss.SNAKE
				? "message.virtual_craft.snake_king.already_present"
				: "message.virtual_craft.mushroom_king.already_present";
	}

	private String spaceErrorKey() {
		return boss == Boss.SNAKE
				? "message.virtual_craft.snake_king.need_space"
				: "message.virtual_craft.mushroom_king.need_space";
	}
}
