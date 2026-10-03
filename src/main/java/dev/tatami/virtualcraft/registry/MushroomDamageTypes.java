package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Data-driven damage registry entries; both preserve the vanilla reduction pipeline. */
public final class MushroomDamageTypes {
    public static final ResourceKey<DamageType> STRIKE = key("mushroom_strike");
    public static final ResourceKey<DamageType> ULTIMATE = key("mushroom_ultimate");
    private MushroomDamageTypes() {}
    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, name));
    }
}
