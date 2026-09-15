package dev.tatami.virtualcraft.world;

import dev.tatami.virtualcraft.machine.advanced.ResonanceFieldService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class LightningResonanceEvents {
	private LightningResonanceEvents() {
	}

	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getEntity() instanceof LightningBolt lightning && event.getLevel() instanceof ServerLevel level) {
			ResonanceFieldService.receiveLightning(level, lightning.blockPosition());
		}
	}
}
