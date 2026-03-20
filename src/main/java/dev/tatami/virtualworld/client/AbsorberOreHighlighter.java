package dev.tatami.virtualworld.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import dev.tatami.virtualworld.core.definitions.VWItems;
import dev.tatami.virtualworld.logic.AbsorberReleaseProcedure;
import dev.tatami.virtualworld.util.OreClusterFinder;
import dev.tatami.virtualworld.util.ShapeMerger;

public final class AbsorberOreHighlighter {
	private static final int MAX_RENDERED_ORES = 128;

	private long cachedGameTime = Long.MIN_VALUE;
	private List<BlockPos> cachedCluster = List.of();
	private BlockPos cachedOrigin;
	private List<CachedEdge> cachedEdges = List.of();

	public void renderInGame(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Level level = minecraft.level;
		if (player == null || level == null || player.getUseItem().getItem() != VWItems.ELECTRIC_ABSORBER.get()) {
			clearCache();
			return;
		}

		BlockPos targetPos = OreClusterFinder.findTargetedOre(level, player, AbsorberReleaseProcedure.getOreScanRange());
		if (targetPos == null) {
			clearCache();
			return;
		}

		long gameTime = level.getGameTime();
		if (gameTime != cachedGameTime || !targetPos.equals(cachedOrigin)) {
			cachedGameTime = gameTime;
			cachedOrigin = targetPos.immutable();
			cachedCluster = OreClusterFinder.collectConnectedOres(level, cachedOrigin, MAX_RENDERED_ORES);
			cachedEdges = buildEdges(cachedCluster, cachedOrigin);
		}

		if (cachedEdges.isEmpty() || cachedOrigin == null) {
			return;
		}

		int absorptionCapacity = AbsorberReleaseProcedure.getAbsorptionCapacity(player.getTicksUsingItem());
		float red = absorptionCapacity >= cachedCluster.size() ? 0.25f : 0.95f;
		float green = absorptionCapacity >= cachedCluster.size() ? 0.95f : 0.75f;
		float blue = 1.0f;

		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Matrix4f matrix = poseStack.last().pose();
		for (CachedEdge edge : cachedEdges) {
			lineBuffer.addVertex(matrix, edge.x1(), edge.y1(), edge.z1()).setColor(red, green, blue, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
			lineBuffer.addVertex(matrix, edge.x2(), edge.y2(), edge.z2()).setColor(red, green, blue, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
		}
		poseStack.popPose();
		bufferSource.endBatch(RenderType.lines());
	}

	private List<CachedEdge> buildEdges(List<BlockPos> positions, BlockPos origin) {
		if (positions.isEmpty()) {
			return List.of();
		}

		Collection<VoxelShape> shapes = new HashSet<>();
		for (AABB box : ShapeMerger.merge(positions, BlockPos.ZERO)) {
			shapes.add(Shapes.create(box.inflate(0.002D)));
		}

		VoxelShape combinedShape = Shapes.empty();
		for (VoxelShape shape : shapes) {
			combinedShape = Shapes.joinUnoptimized(combinedShape, shape, BooleanOp.OR);
		}

		List<CachedEdge> edges = new ArrayList<>();
		combinedShape.forAllEdges((x1, y1, z1, x2, y2, z2) -> edges.add(CachedEdge.fromDoubles(x1, y1, z1, x2, y2, z2)));
		return edges;
	}

	private void clearCache() {
		cachedGameTime = Long.MIN_VALUE;
		cachedOrigin = null;
		cachedCluster = List.of();
		cachedEdges = List.of();
	}
}

