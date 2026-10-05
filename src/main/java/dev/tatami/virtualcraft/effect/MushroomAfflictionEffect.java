package dev.tatami.virtualcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visual/status representation; the encounter handler owns exact strength and expiry. */
public final class MushroomAfflictionEffect extends MobEffect {
    public MushroomAfflictionEffect(int color) { super(MobEffectCategory.HARMFUL, color); }
}
