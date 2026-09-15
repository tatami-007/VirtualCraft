package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Summons the Mushroom King from a flat 3x3 virtual-world footprint. */
public class MushroomKingSealItem extends Item {
	private static final double BOSS_EXCLUSION_RADIUS = 128.0;

	public MushroomKingSealItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.mushroom_king_seal.use")
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level sourceLevel = context.getLevel();
		Player sourcePlayer = context.getPlayer();
		BlockPos clickedPos = context.getClickedPos();
		if (sourcePlayer == null) {
			return InteractionResult.PASS;
		}
		if (sourceLevel.getBlockState(clickedPos).is(ModBlocks.MUSHROOM_KING_ALTAR.get())) {
			return InteractionResult.PASS;
		}
		if (sourceLevel.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (!(sourceLevel instanceof ServerLevel level) || !(sourcePlayer instanceof ServerPlayer player)
				|| !level.dimension().equals(ModDimensions.VIRTUAL_WORLD_LEVEL)) {
			if (!sourceLevel.isClientSide) {
				sourcePlayer.displayClientMessage(Component.translatable(
						"message.virtual_craft.mushroom_king.virtual_world_only")
						.withStyle(ChatFormatting.RED), true);
			}
			return InteractionResult.FAIL;
		}
		BlockPos center = findSummonCenter(level, clickedPos);
		if (center == null) {
			player.displayClientMessage(Component.translatable(
					"message.virtual_craft.mushroom_king.need_base")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		if (!level.getEntitiesOfClass(VirtualMushroomKing.class,
				new AABB(center).inflate(BOSS_EXCLUSION_RADIUS), VirtualMushroomKing::isAlive).isEmpty()) {
			player.displayClientMessage(Component.translatable(
					"message.virtual_craft.mushroom_king.already_present")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}
		if (!hasSummonSpace(level, center)) {
			player.displayClientMessage(Component.translatable(
					"message.virtual_craft.mushroom_king.need_space")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.FAIL;
		}

		VirtualMushroomKing boss = ModEntityTypes.VIRTUAL_MUSHROOM_KING.get().create(level);
		if (boss == null) {
			return InteractionResult.FAIL;
		}
		boss.moveTo(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
				player.getYRot() + 180.0F, 0.0F);
		if (!level.noCollision(boss, boss.getBoundingBox())) {
			boss.discard();
			return InteractionResult.FAIL;
		}
		if (!level.addFreshEntity(boss)) {
			return InteractionResult.FAIL;
		}
		boss.beginSummoning(center, player.getUUID());
		player.getCooldowns().addCooldown(this, 100);
		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	public static boolean isValidThroneBase(Level level, BlockPos center) {
		var centerState = level.getBlockState(center);
		if (!centerState.is(ModBlocks.VIRTUAL_GRASS_BLOCK.get())
				&& !centerState.is(ModBlocks.VIRTUAL_DIRT.get())
				&& !centerState.is(ModBlocks.VIRTUAL_STONE.get())) {
			return false;
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				BlockPos pos = center.offset(x, 0, z);
				if (!isValidThroneCell(level, pos, x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	public static BlockPos findSummonCenter(Level level, BlockPos clickedPos) {
		if (isValidThroneBase(level, clickedPos)) {
			return clickedPos;
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				BlockPos candidate = clickedPos.offset(x, 0, z);
				if (isValidThroneBase(level, candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	public static boolean isValidThroneCell(Level level, BlockPos pos, int xOffset, int zOffset) {
		var state = level.getBlockState(pos);
		if (state.is(ModBlocks.VIRTUAL_GRASS_BLOCK.get())
				|| state.is(ModBlocks.VIRTUAL_DIRT.get())) {
			return true;
		}
		boolean corner = Math.abs(xOffset) == 1 && Math.abs(zOffset) == 1;
		return state.is(ModBlocks.VIRTUAL_STONE.get())
				|| corner && (state.is(ModBlocks.VIRTUAL_COBBLESTONE.get())
						|| state.is(Blocks.COBBLESTONE));
	}

	private static boolean hasSummonSpace(ServerLevel level, BlockPos center) {
		for (int y = 1; y <= 6; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					var state = level.getBlockState(center.offset(x, y, z));
					if (!state.isAir() && !state.canBeReplaced()) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
