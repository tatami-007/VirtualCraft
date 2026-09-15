package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.effect.SnakeKingBlessingEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMobEffects {
	private ModMobEffects() {
	}

	public static final DeferredRegister<MobEffect> REGISTRY =
			DeferredRegister.create(Registries.MOB_EFFECT, VirtualCraft.MODID);

	public static final DeferredHolder<MobEffect, SnakeKingBlessingEffect> SNAKE_KING_BLESSING =
			REGISTRY.register("snake_king_blessing", SnakeKingBlessingEffect::new);
}
