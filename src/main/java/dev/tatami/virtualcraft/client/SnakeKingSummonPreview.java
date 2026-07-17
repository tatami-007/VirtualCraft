package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Lightweight in-world summon target feedback with no explanatory overlay. */
final class SnakeKingSummonPreview {
	private SnakeKingSummonPreview() {
	}

	static void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || player.tickCount % 4 != 0) {
			return;
		}
		boolean holdingKey = player.getMainHandItem().is(ModItems.SNAKE_KING_KEY.get())
				|| player.getOffhandItem().is(ModItems.SNAKE_KING_KEY.get());
		if (!holdingKey) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = minecraft.level.clip(new ClipContext(eye,
				eye.add(player.getViewVector(1.0F).scale(6.0)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		if (!minecraft.level.getBlockState(pos).is(ModBlocks.THUNDER_POOL.get())) {
			return;
		}

		double rotation = player.tickCount * 0.11;
		for (int i = 0; i < 8; i++) {
			double angle = rotation + Math.PI * 2.0 * i / 8.0;
			double radius = i % 2 == 0 ? 0.72 : 0.58;
			minecraft.level.addParticle(ModParticleTypes.SNAKE_ARC.get(),
					pos.getX() + 0.5 + Math.cos(angle) * radius,
					pos.getY() + 1.02,
					pos.getZ() + 0.5 + Math.sin(angle) * radius,
					0.0, 0.012, 0.0);
		}
		for (int i = 0; i < 5; i++) {
			double progress = i / 4.0;
			double z = pos.getZ() + 0.16 + progress * 0.68;
			double x = pos.getX() + 0.5 + Math.sin(progress * Math.PI * 2.0 + rotation) * 0.16;
			minecraft.level.addParticle(ModParticleTypes.SNAKE_ARC.get(), x,
					pos.getY() + 1.08 + Math.sin(progress * Math.PI) * 0.12, z, 0.0, 0.008, 0.0);
		}
		minecraft.level.addParticle(ModParticleTypes.SNAKE_DATA_RUNE.get(),
				pos.getX() + 0.5, pos.getY() + 1.04, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
	}
}
