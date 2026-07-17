package dev.tatami.virtualcraft.boss;

import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;

public interface PostureBoss {
	int getPosture();

	int getMaxPosture();

	int getStaggerTicks();

	BossAttackDirection getAttackDirection();

	void damagePosture(int amount, @Nullable Player source);

	default boolean isStaggered() {
		return getStaggerTicks() > 0;
	}
}
