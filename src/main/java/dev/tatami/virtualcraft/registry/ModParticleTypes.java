package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticleTypes {
	private ModParticleTypes() {
	}

	public static final DeferredRegister<ParticleType<?>> REGISTRY =
			DeferredRegister.create(Registries.PARTICLE_TYPE, VirtualCraft.MODID);

	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_DATA_RUNE = register("snake_data_rune");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_ARC = register("snake_arc");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_SHARD = register("snake_shard");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_TRAIL = register("snake_trail");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_HIGH_WARNING = register("snake_high_warning");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_MID_WARNING = register("snake_mid_warning");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_LOW_WARNING = register("snake_low_warning");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_POISON_RED = register("snake_poison_red");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_POISON_BLUE = register("snake_poison_blue");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNAKE_PARRY_FLASH = register("snake_parry_flash");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUSHROOM_SPORE_GLOW =
			register("mushroom_spore_glow");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUSHROOM_ROOT_SHARD =
			register("mushroom_root_shard");
	public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUSHROOM_CAP_FLASH =
			register("mushroom_cap_flash");

	private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
		return REGISTRY.register(name, () -> new SimpleParticleType(false));
	}
}
