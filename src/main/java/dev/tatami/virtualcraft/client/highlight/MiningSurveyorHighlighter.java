package dev.tatami.virtualcraft.client.highlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import dev.tatami.virtualcraft.util.ShapeMerger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public final class MiningSurveyorHighlighter {
	private static final int MAX_RENDERED_ORES = 128;
	private static final double WORLD_HIGHLIGHT_MAX_DISTANCE = 160.0D;
	private static final double EDGE_ARROW_MIN_DOT = 0.35D;
	private static final int ARROW_MARGIN = 18;

	// v25 refined aesthetic. Old style (#02C7FF wireframe + center axis cross +
	// alpha pulse) read as debug rendering: too saturated, too busy, the pulse
	// drew the eye away from the world. New style follows the project's existing
	// in-world highlight language (see BoundMachineHighlighter): a low-alpha
	// CYAN-tinted shell so the cluster reads as "a glowing volume in the rock",
	// plus a clean 1-px outline on top so the silhouette stays crisp. No pulse,
	// no axis crosses — players keep their attention on the terrain.
	private static final float FILL_R = 0.30f;
	private static final float FILL_G = 0.78f;
	private static final float FILL_B = 0.96f;
	private static final float FILL_A = 0.20f;
	private static final float OUTLINE_R = 0.36f;
	private static final float OUTLINE_G = 0.86f;
	private static final float OUTLINE_B = 1.00f;
	private static final float OUTLINE_A = 0.95f;

	private static final RenderType SURVEY_LINES = RenderType.create(
			"virtual_craft_survey_box_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES,
			1536,
			RenderType.CompositeState.builder()
					.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
					.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0D)))
					.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
					.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
					.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(RenderStateShard.COLOR_WRITE)
					.setCullState(RenderStateShard.NO_CULL)
					.createCompositeState(false));

	private static final int RESCAN_INTERVAL_TICKS = 20;

	private BlockPos cachedOrigin;
	private MiningSurveyTarget cachedTarget;
	private List<BlockPos> cachedCluster = List.of();
	private List<AABB> cachedBoxes = List.of();
	private List<CachedEdge> cachedEdges = List.of();
	private long lastRescanTick;

	public void renderInGame(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Level level = minecraft.level;
		if (player == null || level == null) {
			clearCache();
			return;
		}

		ItemStack stack = resolveSurveyor(player);
		Optional<BlockPos> targetPos = MiningSurveyorClientState.targetPos().or(() -> stack.isEmpty()
				? Optional.empty()
				: MiningSurveyTargetData.target(stack));
		if (targetPos.isEmpty()) {
			clearCache();
			return;
		}

		BlockPos origin = targetPos.get();
		if (!shouldRenderWorldHighlight(player, origin)) {
			return;
		}

		MiningSurveyTarget target = MiningSurveyorClientState.target().orElseGet(() -> stack.isEmpty()
				? MiningSurveyTarget.defaultTarget()
				: MiningSurveyTargetData.lockedTarget(stack));
		long currentTick = level.getGameTime();
		boolean targetChanged = !origin.equals(cachedOrigin) || !target.equals(cachedTarget);
		boolean rescanDue = (currentTick - lastRescanTick) >= RESCAN_INTERVAL_TICKS;
		if (targetChanged || rescanDue) {
			cachedOrigin = origin.immutable();
			cachedTarget = target;
			lastRescanTick = currentTick;
			List<BlockPos> freshCluster = OreClusterFinder.collectConnectedOres(level, cachedOrigin, MAX_RENDERED_ORES, target);
			if (targetChanged || freshCluster.size() != cachedCluster.size()) {
				cachedCluster = freshCluster;
				rebuildGeometry(cachedCluster.isEmpty() ? List.of(cachedOrigin) : cachedCluster);
			}
		}
		if (cachedBoxes.isEmpty()) {
			return;
		}
		Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
		// Two-pass render: filled translucent shell first (depth-tested off so it
		// shows through stone), then crisp outline on top so the silhouette stays
		// readable even when the fill blends with bright terrain.
		renderFilledShell(poseStack, cameraPos, cachedBoxes);
		renderOutline(poseStack, cameraPos, cachedEdges);
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
		Optional<BlockPos> targetPos = MiningSurveyorClientState.targetPos().or(() -> stack.isEmpty()
				? Optional.empty()
				: MiningSurveyTargetData.target(stack));
		if (targetPos.isEmpty()) {
			return;
		}

		BlockPos pos = targetPos.get();
		MiningSurveyTarget surveyTarget = MiningSurveyorClientState.target().orElseGet(() -> stack.isEmpty()
				? MiningSurveyTarget.defaultTarget()
				: MiningSurveyTargetData.lockedTarget(stack));
		int cluster = MiningSurveyorClientState.clusterSize();
		if (cluster <= 0 && !stack.isEmpty()) {
			cluster = MiningSurveyTargetData.clusterSize(stack);
		}
		// Always-on locator chip (name · cluster · distance · vertical hint) so the
		// player keeps the lock-on readout even when staring straight at the vein
		// through stone; the edge arrow only kicks in once it leaves the view cone.
		drawLocatorChip(event.getGuiGraphics(), player, pos, surveyTarget, cluster);
		if (!shouldHideEdgeArrow(player, pos)) {
			drawEdgeArrow(event.getGuiGraphics(), player, pos);
		}
	}

	private void drawLocatorChip(GuiGraphics guiGraphics, LocalPlayer player, BlockPos pos, MiningSurveyTarget surveyTarget, int cluster) {
		double distance = Vec3.atCenterOf(pos).subtract(player.getEyePosition()).length();
		int meters = (int) Math.round(distance);
		int dy = pos.getY() - Mth.floor(player.getY());
		var font = Minecraft.getInstance().font;
		int x = 6;
		int y = guiGraphics.guiHeight() - 50;

		String name = surveyTarget.name().getString();
		String countStr = cluster > 0 ? " \u00d7" + cluster : "";
		String distStr = " " + meters + "m";
		String vertStr = dy > 2 ? " \u2191" + dy : dy < -2 ? " \u2193" + (-dy) : "";
		String fullText = "\u25c6 " + name + countStr + "  " + distStr + vertStr;

		int textWidth = font.width(fullText);
		guiGraphics.fill(x - 3, y - 3, x + textWidth + 3, y + font.lineHeight + 2, 0x60000000);

		int cx = x;
		cx = drawSegment(guiGraphics, font, "\u25c6 ", cx, y, 0xFF4DD8F0);
		cx = drawSegment(guiGraphics, font, name, cx, y, 0xFFCCEEFF);
		if (!countStr.isEmpty()) {
			cx = drawSegment(guiGraphics, font, countStr, cx, y, 0xFF66DDAA);
		}
		cx = drawSegment(guiGraphics, font, "  " + distStr, cx, y, 0xFFAABBCC);
		if (!vertStr.isEmpty()) {
			int vertColor = dy > 0 ? 0xFFEEAA44 : 0xFF66DDAA;
			drawSegment(guiGraphics, font, vertStr, cx, y, vertColor);
		}
	}

	private int drawSegment(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
		guiGraphics.drawString(font, text, x, y, color, false);
		return x + font.width(text);
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
		var font = Minecraft.getInstance().font;
		guiGraphics.drawCenteredString(font, arrow, x + 1, y - 13, 0x66000000);
		guiGraphics.drawCenteredString(font, arrow, x, y - 14, 0xFF8FEAFF);
		guiGraphics.drawCenteredString(font, meters, x + 1, y + 2, 0x66000000);
		guiGraphics.drawCenteredString(font, meters, x, y + 1, 0xFFFFFFFF);
	}

	private String arrowGlyph(int dx, int dy) {
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx >= 0 ? "\u25b6" : "\u25c0";
		}
		return dy >= 0 ? "\u25bc" : "\u25b2";
	}

	private void rebuildGeometry(List<BlockPos> positions) {
		if (positions.isEmpty()) {
			cachedBoxes = List.of();
			cachedEdges = List.of();
			return;
		}
		List<AABB> boxes = new ArrayList<>();
		for (AABB box : ShapeMerger.merge(positions, BlockPos.ZERO)) {
			// Inflate a hair so adjacent boxes don't z-fight at their shared face.
			boxes.add(box.inflate(0.006D));
		}
		List<CachedEdge> edges = new ArrayList<>(boxes.size() * 12);
		for (AABB box : boxes) {
			addBoxEdges(edges, box);
		}
		cachedBoxes = List.copyOf(boxes);
		cachedEdges = List.copyOf(edges);
	}

	private void addBoxEdges(List<CachedEdge> edges, AABB box) {
		// 12 box edges only — no axis cross through the center any more.
		// The translucent shell already conveys "this is a volume", so the
		// cross is redundant clutter that made the highlight feel like debug.
		edges.add(CachedEdge.fromDoubles(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ));
		edges.add(CachedEdge.fromDoubles(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ));
		edges.add(CachedEdge.fromDoubles(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ));
	}

	/**
	 * Renders translucent filled boxes behind the outline so the cluster reads
	 * as a glowing volume even when it's occluded by stone or terrain. Uses
	 * vanilla's immediate-mode tesselator path (same as {@code BoundMachineHighlighter})
	 * rather than a custom {@link RenderType} because we need full control over
	 * depth-test / blend state without leaking it into the surrounding
	 * {@code AFTER_TRANSLUCENT_BLOCKS} stage pipeline.
	 */
	private void renderFilledShell(PoseStack poseStack, Vec3 cameraPos, List<AABB> boxes) {
		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		RenderSystem.disableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);

		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
		boolean wroteAny = false;
		for (AABB box : boxes) {
			LevelRenderer.addChainedFilledBoxVertices(poseStack, buffer,
					box.minX, box.minY, box.minZ,
					box.maxX, box.maxY, box.maxZ,
					FILL_R, FILL_G, FILL_B, FILL_A);
			wroteAny = true;
		}
		MeshData mesh = buffer.build();
		if (mesh != null) {
			if (wroteAny) {
				BufferUploader.drawWithShader(mesh);
			} else {
				mesh.close();
			}
		}

		// Restore — leaking these would corrupt vanilla's subsequent passes
		// (sky, UI, the outline pass below).
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.enableDepthTest();

		poseStack.popPose();
	}

	private void renderOutline(PoseStack poseStack, Vec3 cameraPos, List<CachedEdge> edges) {
		if (edges.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
		VertexConsumer lineBuffer = bufferSource.getBuffer(SURVEY_LINES);

		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
		Matrix4f matrix = poseStack.last().pose();

		for (CachedEdge edge : edges) {
			lineBuffer.addVertex(matrix, edge.x1(), edge.y1(), edge.z1())
					.setColor(OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_A)
					.setNormal(edge.xn(), edge.yn(), edge.zn());
			lineBuffer.addVertex(matrix, edge.x2(), edge.y2(), edge.z2())
					.setColor(OUTLINE_R, OUTLINE_G, OUTLINE_B, OUTLINE_A)
					.setNormal(edge.xn(), edge.yn(), edge.zn());
		}
		bufferSource.endBatch(SURVEY_LINES);

		poseStack.popPose();
	}

	private void clearCache() {
		cachedOrigin = null;
		cachedTarget = null;
		cachedCluster = List.of();
		cachedBoxes = List.of();
		cachedEdges = List.of();
		lastRescanTick = 0;
	}
}
