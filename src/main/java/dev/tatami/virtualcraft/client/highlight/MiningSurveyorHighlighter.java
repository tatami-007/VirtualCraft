package dev.tatami.virtualcraft.client.highlight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import dev.tatami.virtualcraft.util.ShapeMerger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MiningSurveyorHighlighter {
	private static final int MAX_RENDERED_ORES = 128;

	private BlockPos cachedOrigin;
	private List<BlockPos> cachedCluster = List.of();
	private List<CachedEdge> cachedEdges = List.of();

	public void renderInGame(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Level level = minecraft.level;
		if (player == null || level == null) {
			clearCache();
			return;
		}

		ItemStack stack = resolveSurveyor(player);
		if (stack.isEmpty()) {
			clearCache();
			return;
		}

		Optional<BlockPos> target = MiningSurveyTargetData.target(stack);
		if (target.isEmpty()) {
			clearCache();
			return;
		}

		BlockPos origin = target.get();
		if (!origin.equals(cachedOrigin)) {
			cachedOrigin = origin.immutable();
			cachedCluster = OreClusterFinder.collectConnectedOres(level, cachedOrigin, MAX_RENDERED_ORES);
			cachedEdges = buildEdges(cachedCluster);
		}
		if (cachedEdges.isEmpty()) {
			return;
		}

		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Matrix4f matrix = poseStack.last().pose();
		for (CachedEdge edge : cachedEdges) {
			lineBuffer.addVertex(matrix, edge.x1(), edge.y1(), edge.z1()).setColor(0.96f, 0.82f, 0.35f, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
			lineBuffer.addVertex(matrix, edge.x2(), edge.y2(), edge.z2()).setColor(0.96f, 0.82f, 0.35f, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
		}
		poseStack.popPose();
		bufferSource.endBatch(RenderType.lines());
	}

	private ItemStack resolveSurveyor(LocalPlayer player) {
		if (player.getMainHandItem().is(ModItems.MINING_SURVEYOR.get())) {
			return player.getMainHandItem();
		}
		if (player.getOffhandItem().is(ModItems.MINING_SURVEYOR.get())) {
			return player.getOffhandItem();
		}
		return ItemStack.EMPTY;
	}

	private List<CachedEdge> buildEdges(List<BlockPos> positions) {
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
		cachedOrigin = null;
		cachedCluster = List.of();
		cachedEdges = List.of();
	}
}
