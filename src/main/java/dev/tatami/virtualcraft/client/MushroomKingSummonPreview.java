package dev.tatami.virtualcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tatami.virtualcraft.item.MushroomKingSealItem;
import dev.tatami.virtualcraft.registry.ModDimensions;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Compact, text-free guide for the Mushroom King's 3x3 summon footprint. */
final class MushroomKingSummonPreview {
	private static final float VALID_R = 0.30F;
	private static final float VALID_G = 0.92F;
	private static final float VALID_B = 1.00F;
	private static final float INVALID_R = 1.00F;
	private static final float INVALID_G = 0.48F;
	private static final float INVALID_B = 0.16F;

	private MushroomKingSummonPreview() {
	}

	static void render(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null
				|| !minecraft.level.dimension().equals(ModDimensions.VIRTUAL_WORLD_LEVEL)
				|| !isHoldingSeal(player)) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = minecraft.level.clip(new ClipContext(eye,
				eye.add(player.getViewVector(1.0F).scale(7.0D)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos clickedPos = hit.getBlockPos();
		BlockPos foundCenter = MushroomKingSealItem.findSummonCenter(minecraft.level, clickedPos);
		BlockPos center = foundCenter != null ? foundCenter : clickedPos;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		VertexConsumer lines = buffers.getBuffer(RenderType.lines());
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		Matrix4f matrix = poseStack.last().pose();

		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				BlockPos pos = center.offset(x, 0, z);
				boolean valid = MushroomKingSealItem.isValidThroneCell(minecraft.level, pos, x, z);
				float red = valid ? VALID_R : INVALID_R;
				float green = valid ? VALID_G : INVALID_G;
				float blue = valid ? VALID_B : INVALID_B;
				drawBox(lines, matrix, new AABB(pos).inflate(0.004D), red, green, blue);
			}
		}

		poseStack.popPose();
		buffers.endBatch(RenderType.lines());
	}

	private static boolean isHoldingSeal(LocalPlayer player) {
		return player.getMainHandItem().is(ModItems.MUSHROOM_KING_SEAL.get())
				|| player.getOffhandItem().is(ModItems.MUSHROOM_KING_SEAL.get());
	}

	private static void drawBox(VertexConsumer vertices, Matrix4f matrix, AABB box,
			float red, float green, float blue) {
		edge(vertices, matrix, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, red, green, blue);
		edge(vertices, matrix, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue);
		edge(vertices, matrix, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue);
		edge(vertices, matrix, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, red, green, blue);
		edge(vertices, matrix, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue);
	}

	private static void edge(VertexConsumer vertices, Matrix4f matrix,
			double x1, double y1, double z1, double x2, double y2, double z2,
			float red, float green, float blue) {
		float normalX = (float) (x2 - x1);
		float normalY = (float) (y2 - y1);
		float normalZ = (float) (z2 - z1);
		vertices.addVertex(matrix, (float) x1, (float) y1, (float) z1)
				.setColor(red, green, blue, 1.0F).setNormal(normalX, normalY, normalZ);
		vertices.addVertex(matrix, (float) x2, (float) y2, (float) z2)
				.setColor(red, green, blue, 1.0F).setNormal(normalX, normalY, normalZ);
	}
}
