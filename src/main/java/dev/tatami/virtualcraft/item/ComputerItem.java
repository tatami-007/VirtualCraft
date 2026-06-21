package dev.tatami.virtualcraft.item;

import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import dev.tatami.virtualcraft.gameplay.VirtualWorldReturnPoint;
import dev.tatami.virtualcraft.registry.ModAttachments;
import dev.tatami.virtualcraft.registry.ModDimensions;

public class ComputerItem extends Item {
	private static final int COOLDOWN_TICKS = 40;

	public ComputerItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
		}

		MinecraftServer server = sp.getServer();
		if (server == null) {
			return InteractionResultHolder.fail(stack);
		}

		ResourceKey<Level> current = level.dimension();

		if (current == ModDimensions.VIRTUAL_WORLD_LEVEL) {
			VirtualWorldReturnPoint rp = sp.getData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT);
			ServerLevel target = server.getLevel(rp.dimension());
			if (target == null) {
				target = server.overworld();
			}
			sp.teleportTo(target, rp.x(), rp.y(), rp.z(), Set.of(), rp.yaw(), rp.pitch());
			sp.displayClientMessage(
					Component.translatable("message.virtual_craft.computer.disconnect").withStyle(ChatFormatting.AQUA),
					true);
		} else {
			ServerLevel vw = server.getLevel(ModDimensions.VIRTUAL_WORLD_LEVEL);
			if (vw == null) {
				sp.displayClientMessage(
						Component.translatable("message.virtual_craft.computer.unavailable")
								.withStyle(ChatFormatting.RED),
						true);
				return InteractionResultHolder.fail(stack);
			}
			sp.setData(ModAttachments.VIRTUAL_WORLD_RETURN_POINT, new VirtualWorldReturnPoint(current, sp.getX(),
					sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()));
			sp.teleportTo(vw, 0.5, 64, 0.5, Set.of(), sp.getYRot(), sp.getXRot());
			sp.displayClientMessage(
					Component.translatable("message.virtual_craft.computer.connect").withStyle(ChatFormatting.GREEN),
					true);
		}

		sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
			TooltipFlag tooltipFlag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.computer").withStyle(ChatFormatting.GRAY));
	}
}
