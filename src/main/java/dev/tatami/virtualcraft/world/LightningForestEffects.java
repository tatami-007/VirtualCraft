package dev.tatami.virtualcraft.world;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/** Applies the lightning forest's accumulating environmental damage. */
public final class LightningForestEffects {
	public static final ResourceKey<Biome> LIGHTNING_FOREST = ResourceKey.create(Registries.BIOME,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "lightning_forest"));

	private LightningForestEffects() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().getBiome(player.blockPosition()).is(LIGHTNING_FOREST)) {
				player.hurt(player.damageSources().lightningBolt(), 2.0F);
			}
		}
	}
}
