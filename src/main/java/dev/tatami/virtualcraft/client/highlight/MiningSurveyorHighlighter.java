package dev.tatami.virtualcraft.client.highlight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import dev.tatami.virtualcraft.util.ShapeMerger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class MiningSurveyorHighlighter {
	private static final int MAX_RENDERED_ORES = 128;
	private static final double WORLD_HIGHLIGHT_MAX_DISTANCE = 160.0D;
	private static final double EDGE_ARROW_MIN_DOT = 0.35D;
	private static final int ARROW_MARGIN = 18;
	private static final RenderType SURVEY_LINES = RenderType.create(
			"virtual_craft_survey_no_depth_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES,
			1536,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
					.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5D)))
					.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
					.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	private BlockPos cachedOrigin;
	private MiningSurveyTarget cachedTarget;
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

		Optional<BlockPos> targetPos = MiningSurveyTargetData.target(stack).or(MiningSurveyorClientState::targetPos);
		if (targetPos.isEmpty()) {
			clearCache();
			return;
		}

		BlockPos origin = targetPos.get();
		if (!shouldRenderWorldHighlight(player, origin)) {
			return;
		}

		MiningSurveyTarget target = MiningSurveyorClientState.target().orElse(MiningSurveyTargetData.lockedTarget(stack));
		if (!origin.equals(cachedOrigin) || !target.equals(cachedTarget)) {
			cachedOrigin = origin.immutable();
			cachedTarget = target;
			cachedCluster = OreClusterFinder.collectConnectedOres(level, cachedOrigin, MAX_RENDERED_ORES, target);
			cachedEdges = buildEdges(cachedCluster);
		}
		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

		VertexConsumer lineBuffer = bufferSource.getBuffer(SURVEY_LINES);
		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Matrix4f matrix = poseStack.last().pose();
		if (!cachedEdges.isEmpty()) {
			for (CachedEdge edge : cachedEdges) {
				lineBuffer.addVertex(matrix, edge.x1(), edge.y1(), edge.z1()).setColor(0.96f, 0.82f, 0.35f, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
				lineBuffer.addVertex(matrix, edge.x2(), edge.y2(), edge.z2()).setColor(0.96f, 0.82f, 0.35f, 1.0f).setNormal(edge.xn(), edge.yn(), edge.zn());
			}
		}
		renderTargetMarker(lineBuffer, matrix, player, origin);
		poseStack.popPose();
		bufferSource.endBatch(SURVEY_LINES);
	}

	public void renderGui(RenderGuiLayerEvent.Post event) {
		if (!VanillaGuiLayers.OVERLAY_MESSAGE.equals(event.getName())) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (minecraft.options.hideGui || player == null || minecraft.level == null) {
			return;
		}

		ItemStack stack = resolveSurveyor(player);
		if (stack.isEmpty()) {
			return;
		}
		Optional<BlockPos> targetPos = MiningSurveyTargetData.target(stack).or(MiningSurveyorClientState::targetPos);
		if (targetPos.isEmpty() || shouldHideEdgeArrow(player, targetPos.get())) {
			return;
		}

		drawEdgeArrow(event.getGuiGraphics(), player, targetPos.get());
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

	private boolean shouldRenderWorldHighlight(LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		Vec3 toTarget = Vec3.atCenterOf(target).subtract(eye);
		double distance = toTarget.length();
		return distance <= WORLD_HIGHLIGHT_MAX_DISTANCE && distance > 0.001D;
	}

	private boolean shouldHideEdgeArrow(LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		Vec3 toTarget = Vec3.atCenterOf(target).subtract(eye);
		double distance = toTarget.length();
		if (distance <= 0.001D || distance > WORLD_HIGHLIGHT_MAX_DISTANCE) {
			return false;
		}
		return player.getViewVector(1.0F).normalize().dot(toTarget.normalize()) >= EDGE_ARROW_MIN_DOT;
	}

	private void drawEdgeArrow(GuiGraphics guiGraphics, LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		Vec3 toTarget = Vec3.atCenterOf(target).subtract(eye);
		double distance = toTarget.length();
		if (distance <= 0.001D) {
			return;
		}
		Vec3 direction = toTarget.normalize();
		Vec3 look = player.getViewVector(1.0F).normalize();
		Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = look.cross(worldUp);
		if (right.lengthSqr() < 0.0001D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(look).normalize();

		double dot = look.dot(direction);
		double xDot = right.dot(direction);
		double yDot = up.dot(direction);
		double scale = Math.max(0.22D, dot + 0.45D);
		int centerX = guiGraphics.guiWidth() / 2;
		int centerY = guiGraphics.guiHeight() / 2;
		int x = centerX + (int) Math.round((xDot / scale) * (guiGraphics.guiWidth() * 0.42D));
		int y = centerY - (int) Math.round((yDot / scale) * (guiGraphics.guiHeight() * 0.42D));
		x = Mth.clamp(x, ARROW_MARGIN, guiGraphics.guiWidth() - ARROW_MARGIN);
		y = Mth.clamp(y, ARROW_MARGIN, guiGraphics.guiHeight() - ARROW_MARGIN);

		String arrow = arrowGlyph(x - centerX, y - centerY);
		String meters = Math.round(distance) + "m";
		guiGraphics.drawCenteredString(Minecraft.getInstance().font, arrow, x + 1, y - 13, 0xAA000000);
		guiGraphics.drawCenteredString(Minecraft.getInstance().font, arrow, x, y - 14, 0xFF8FEAFF);
		guiGraphics.drawCenteredString(Minecraft.getInstance().font, meters, x + 1, y + 2, 0xAA000000);
		guiGraphics.drawCenteredString(Minecraft.getInstance().font, meters, x, y + 1, 0xFFFFFFFF);
	}

	private String arrowGlyph(int dx, int dy) {
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx >= 0 ? ">" : "<";
		}
		return dy >= 0 ? "v" : "^";
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

	private void renderTargetMarker(VertexConsumer lineBuffer, Matrix4f matrix, LocalPlayer player, BlockPos target) {
		Vec3 center = Vec3.atCenterOf(target);
		double distance = player.getEyePosition().distanceTo(center);
		float radius = (float) Math.max(0.65D, Math.min(2.5D, distance / 24.0D));
		float x = (float) center.x;
		float y = (float) center.y;
		float z = (float) center.z;
		emitLine(lineBuffer, matrix, x - radius, y, z, x + radius, y, z);
		emitLine(lineBuffer, matrix, x, y - radius, z, x, y + radius, z);
		emitLine(lineBuffer, matrix, x, y, z - radius, x, y, z + radius);
		emitLine(lineBuffer, matrix, x - radius, y, z - radius, x + radius, y, z + radius);
		emitLine(lineBuffer, matrix, x - radius, y, z + radius, x + radius, y, z - radius);
	}

	private static void emitLine(VertexConsumer lineBuffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2) {
		lineBuffer.addVertex(matrix, x1, y1, z1).setColor(0.15f, 0.78f, 1.0f, 1.0f).setNormal(0.0f, 1.0f, 0.0f);
		lineBuffer.addVertex(matrix, x2, y2, z2).setColor(0.15f, 0.78f, 1.0f, 1.0f).setNormal(0.0f, 1.0f, 0.0f);
	}

	private void clearCache() {
		cachedOrigin = null;
		cachedTarget = null;
		cachedCluster = List.of();
		cachedEdges = List.of();
	}
}
