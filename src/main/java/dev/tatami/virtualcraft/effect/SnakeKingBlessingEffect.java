package dev.tatami.virtualcraft.effect;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SnakeKingBlessingEffect extends MobEffect {
	public SnakeKingBlessingEffect() {
		super(MobEffectCategory.BENEFICIAL, 0xD6A83B);
		addAttributeModifier(
				Attributes.ARMOR,
				ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "snake_king_blessing_armor"),
				-0.30D,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
