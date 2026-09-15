package dev.tatami.virtualcraft.item;

import java.util.List;

import dev.tatami.virtualcraft.registry.ModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Virtual-metal sword with a short right-click parry stance. */
public final class VirtualSwordItem extends SwordItem {
	public enum Trait {
		NONE,
		SNAKE_KING,
		MUSHROOM_KING
	}

	public static final float SNAKE_AFFLICTION_CHANCE = 0.30F;
	public static final float MUSHROOM_SPORE_CHANCE = 0.35F;
	private static final int POISON_TICKS = 120;
	private static final int WITHER_TICKS = 100;
	private static final float FIRE_SECONDS = 6.0F;

	private final Trait trait;

	public VirtualSwordItem(Tier tier, Item.Properties properties, Trait trait) {
		super(tier, properties);
		this.trait = trait;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.CUSTOM;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack,
			boolean slotChanged) {
		return slotChanged || oldStack.getItem() != newStack.getItem();
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (trait == Trait.SNAKE_KING && !attacker.level().isClientSide
				&& attacker.getRandom().nextFloat() < SNAKE_AFFLICTION_CHANCE) {
			switch (attacker.getRandom().nextInt(3)) {
				case 0 -> target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 1), attacker);
				case 1 -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0), attacker);
				default -> target.igniteForSeconds(FIRE_SECONDS);
			}
		}
		if (trait == Trait.MUSHROOM_KING && !attacker.level().isClientSide
				&& attacker.getRandom().nextFloat() < MUSHROOM_SPORE_CHANCE) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker);
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), attacker);
			if (target.level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(),
						target.getX(), target.getY() + target.getBbHeight() * 0.62, target.getZ(),
						8, 0.32, 0.38, 0.32, 0.018);
			}
		}
		return super.hurtEnemy(stack, target, attacker);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		if (trait == Trait.SNAKE_KING) {
			tooltip.add(Component.translatable("tooltip.virtual_craft.weapon.snake_king_affliction"));
		} else if (trait == Trait.MUSHROOM_KING) {
			tooltip.add(Component.translatable("tooltip.virtual_craft.weapon.mushroom_king_spores"));
		}
	}
}
