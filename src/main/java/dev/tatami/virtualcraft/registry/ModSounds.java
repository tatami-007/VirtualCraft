package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
	private ModSounds() {
	}

	public static final DeferredRegister<SoundEvent> REGISTRY =
			DeferredRegister.create(Registries.SOUND_EVENT, VirtualCraft.MODID);

	public static final DeferredHolder<SoundEvent, SoundEvent> MUSHROOM_DODGE_SUCCESS = register("mushroom_king.dodge_success");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_SUMMON = register("snake_king.summon");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_ROAR = register("snake_king.roar");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_BITE = register("snake_king.bite");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_TAIL = register("snake_king.tail");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_CAST = register("snake_king.cast");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_CAST_PULSE =
			register("snake_king.cast_pulse");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_CAST_LOCK =
			register("snake_king.cast_lock");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_CAST_RELEASE =
			register("snake_king.cast_release");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_FANG = register("snake_king.fang");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_CHAIN = register("snake_king.chain");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_DASH = register("snake_king.dash");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_IMPACT = register("snake_king.impact");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_PARRY = register("snake_king.parry");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_DODGE = register("snake_king.dodge");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_HIGH_DODGE =
			register("snake_king.high_dodge");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_LOW_DODGE =
			register("snake_king.low_dodge");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_JUDGMENT = register("snake_king.judgment");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_DEATH = register("snake_king.death");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_MUSIC = register("snake_king.music");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_BITE_WINDUP = register("snake_king.bite_windup");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_BITE_HIT = register("snake_king.bite_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_TAIL_WINDUP = register("snake_king.tail_windup");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_TAIL_HIT = register("snake_king.tail_hit");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_POISON_GROUND_CHARGE = register("snake_king.poison_ground_charge");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_POISON_GROUND_RELEASE = register("snake_king.poison_ground_release");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_POISON_HEAD_CHARGE = register("snake_king.poison_head_charge");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_POISON_HEAD_RELEASE = register("snake_king.poison_head_release");
	public static final DeferredHolder<SoundEvent, SoundEvent> SNAKE_KING_SHIELD_BREAK = register("snake_king.shield_break");


	private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, name);
		return REGISTRY.register(name, () -> SoundEvent.createVariableRangeEvent(id));
	}
}
