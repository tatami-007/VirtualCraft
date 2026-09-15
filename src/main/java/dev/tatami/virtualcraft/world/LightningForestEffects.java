package dev.tatami.virtualcraft.world;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.item.VirtualArmorProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/** Applies the lightning forest's accumulating environmental damage. */
public final class LightningForestEffects {
	private static final String EXPOSURE_TAG = VirtualCraft.MODID + ":lightning_forest_exposure";
	private static final int MAX_EXPOSURE = 8;

	public static final ResourceKey<Biome> LIGHTNING_FOREST = ResourceKey.create(Registries.BIOME,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "lightning_forest"));

	private LightningForestEffects() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean inForest = player.level().getBiome(player.blockPosition()).is(LIGHTNING_FOREST);
			CompoundTag data = player.getPersistentData();
			if (player.isCreative() || player.isSpectator() || !inForest
					|| VirtualArmorProtection.isFullyInsulated(player)) {
				data.remove(EXPOSURE_TAG);
				continue;
			}

			int exposure = Math.min(MAX_EXPOSURE, data.getInt(EXPOSURE_TAG) + 1);
			data.putInt(EXPOSURE_TAG, exposure);
			float baseDamage = 2.5F + exposure * 0.5F;
			player.hurt(player.damageSources().lightningBolt(),
					VirtualArmorProtection.scaleLightningDamage(player, baseDamage));
			VirtualArmorProtection.applyConductivePenalty(player, exposure >= 5 ? 1 : 0);
		}
	}
}
