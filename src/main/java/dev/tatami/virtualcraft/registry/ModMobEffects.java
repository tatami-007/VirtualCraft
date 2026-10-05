package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.effect.SnakeKingBlessingEffect;
import dev.tatami.virtualcraft.effect.MushroomAfflictionEffect;
import dev.tatami.virtualcraft.boss.MushroomAfflictionType;
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

	public static final DeferredHolder<MobEffect, MushroomAfflictionEffect> SPORE_DRAG =
			REGISTRY.register("spore_drag", () -> new MushroomAfflictionEffect(0xA7AA53));
	public static final DeferredHolder<MobEffect, MushroomAfflictionEffect> MYCELIAL_SNARE =
			REGISTRY.register("mycelial_snare", () -> new MushroomAfflictionEffect(0x9B8054));
	public static final DeferredHolder<MobEffect, MushroomAfflictionEffect> SPORE_MISDIRECTION =
			REGISTRY.register("spore_misdirection", () -> new MushroomAfflictionEffect(0xA16EC5));
	public static final DeferredHolder<MobEffect, MushroomAfflictionEffect> SPORE_REVERSAL =
			REGISTRY.register("spore_reversal", () -> new MushroomAfflictionEffect(0xCC6E9C));

	public static DeferredHolder<MobEffect, MushroomAfflictionEffect> mushroom(MushroomAfflictionType type) {
		return switch (type) {
			case SPORE_DRAG -> SPORE_DRAG;
			case MYCELIAL_SNARE -> MYCELIAL_SNARE;
			case SPORE_MISDIRECTION -> SPORE_MISDIRECTION;
			case SPORE_REVERSAL -> SPORE_REVERSAL;
		};
	}
}
