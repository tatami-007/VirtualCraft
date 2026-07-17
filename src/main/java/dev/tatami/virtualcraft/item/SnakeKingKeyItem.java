package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.List;

public class SnakeKingKeyItem extends Item {
	private static final double BOSS_EXCLUSION_RADIUS = 96.0;

	public SnakeKingKeyItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.snake_king_key.use")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		return trySummon(context.getLevel(), player, context.getItemInHand(), context.getClickedPos());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (hit.getType() != HitResult.Type.BLOCK
				|| !level.getBlockState(hit.getBlockPos()).is(ModBlocks.THUNDER_POOL.get())) {
			return InteractionResultHolder.pass(stack);
		}
		InteractionResult result = trySummon(level, player, stack, hit.getBlockPos());
		return new InteractionResultHolder<>(result, stack);
	}

	private InteractionResult trySummon(Level sourceLevel, Player sourcePlayer, ItemStack key, BlockPos poolPos) {
		if (!sourceLevel.getBlockState(poolPos).is(ModBlocks.THUNDER_POOL.get())) {
			return InteractionResult.PASS;
		}
		if (sourceLevel.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!(sourceLevel instanceof ServerLevel level) || !(sourcePlayer instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		if (!level.dimension().equals(ModDimensions.VIRTUAL_WORLD_LEVEL)) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.snake_king.virtual_world_only").withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		AABB nearby = new AABB(poolPos).inflate(BOSS_EXCLUSION_RADIUS);
		if (!level.getEntitiesOfClass(VirtualSnakeKing.class, nearby, VirtualSnakeKing::isAlive).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.snake_king.already_present").withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}

		BlockPos spawnPos = poolPos.above();
		VirtualSnakeKing boss = ModEntityTypes.VIRTUAL_SNAKE_KING.get().create(level);
		if (boss == null) {
			return InteractionResult.FAIL;
		}
		boss.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 0.05, spawnPos.getZ() + 0.5,
				player.getYRot() + 180.0F, 0.0F);
		boss.setHome(poolPos);
		if (!level.noCollision(boss, boss.getBoundingBox())) {
			player.displayClientMessage(Component.translatable("message.virtual_craft.snake_king.need_space").withStyle(ChatFormatting.RED), true);
			boss.discard();
			return InteractionResult.FAIL;
		}
		if (!level.addFreshEntity(boss)) {
			return InteractionResult.FAIL;
		}
		boss.beginSummoning(poolPos, player.getUUID());
		player.getCooldowns().addCooldown(this, 100);
		if (!player.getAbilities().instabuild) {
			key.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
