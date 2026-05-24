package dev.tatami.virtualcraft.client.binding;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import dev.tatami.virtualcraft.blockentity.WirelessPowerTowerBlockEntity;
import dev.tatami.virtualcraft.client.render.BeamRenderHelper;
import dev.tatami.virtualcraft.item.LaserBindingTool;

import java.util.ArrayList;
import java.util.List;

/**
 * 无线供电塔的客户端独立渲染器。玩家手持 {@link LaserBindingTool} 时，对附近所有塔渲染：
 * <ol>
 *   <li>塔顶 → 每个绑定目标的红色光束（复用 {@link BeamRenderHelper}）</li>
 *   <li>每个绑定机器中心的红色半透明小立方体 —— 光束看起来"打在小立方体上"</li>
 * </ol>
 *
 * <h3>为什么不用 BlockEntityRenderer</h3>
 * <p>vanilla 1.21.1 {@code LevelRenderer.renderBlockEntities} 按 chunk section 遍历 BE、
 * section 不在视锥内就跳过该 section 内所有 BE，BER 不被调用。
 * {@code shouldRenderOffScreen} / {@code getViewDistance} / {@code getRenderBoundingBox}
 * 都在 BER 被调用之后才生效 ——塔本身的 1×1×1 方块离开视锥 → 整台塔的光束消失
 * （“光束在屏幕边缘消失”的根因）。
 *
 * <p>改走 {@link net.neoforged.neoforge.client.event.RenderLevelStageEvent#AFTER_TRANSLUCENT_BLOCKS}
 * 自己遍历附近塔。1）不受 chunk section culling 影响 2）与机器高亮可以敢同一
 * pose stack pipeline 一起画，避免两个 renderer 之间的同步问题。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>只画机器，不画塔</b>：塔之间的连接通过塔顶发出/接收的光束已能看清。</li>
 *   <li><b>扫描范围 = 玩家附近 64 格</b>：足够覆盖玩家视野内的所有塔。</li>
 *   <li><b>渲染时机 = AFTER_TRANSLUCENT_BLOCKS</b>：保证高亮小方块混色到场景里。</li>
 * </ul>
 */
public final class BoundMachineHighlighter {

	/** 半透明高亮立方体的边长。 */
	private static final float CUBE_SIZE = 0.5f;
	private static final float CUBE_HALF = CUBE_SIZE * 0.5f;

	/** 玩家附近扫描半径（块）。 */
	private static final int SCAN_RADIUS = 64;

	/** 光束颜色与粗细（与原 BER 一致）。 */
	private static final float BEAM_R = 1.0f;
	private static final float BEAM_G = 0.0f;
	private static final float BEAM_B = 0.0f;
	private static final float BEAM_THICKNESS = 0.04f;

	/**
	 * 高亮小方块颜色 —— 与光束颜色完全一致（纯红、不加粉混色）。
	 * <p>透明度迭代：0.55（初版偏淡）→ 0.75（用户要求显眼）→ 0.50（用户反馈太"实"，
	 * 立方体完全遮住下方机器外壳；降到 0.50 让机器轮廓能从立方体后透出，立方体仍清晰可见）。</p>
	 */
	private static final float HL_R = BEAM_R;
	private static final float HL_G = BEAM_G;
	private static final float HL_B = BEAM_B;
	private static final float HL_A = 0.50f;

	/** 多帧复用的 scratch buffer，避免每帧重新分配 ArrayList。 */
	private final List<WirelessPowerTowerBlockEntity> scratchTowers = new ArrayList<>();

	// 不再用自定义 RenderType / sharedBuffer 路径。会话开始前的 work 版本能显示，多次改 RT 配置都失败，
	// 怀疑是 sharedBuffer 在 BEAM_NO_DEPTH 和 HIGHLIGHT_CUBE 之间共享时存在状态机交互。最务实的做法：
	// 用 vanilla 内部所有 debug 渲染（hit outline、selection box）使用的 immediate-mode 路径——
	// Tesselator.begin → addChainedFilledBoxVertices → BufferBuilder.build → BufferUploader.drawWithShader。
	// 这条路径完全独立于 BufferSource sharedBuffer，渲染 state 由 RenderSystem 直接控制（disableDepth /
	// enableBlend / disableCull），不会被光束的 sharedBuffer 影响，也不会被 vanilla 后续 RT pipeline 影响。

	public void renderInGame(PoseStack poseStack) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Level level = mc.level;
		if (player == null || level == null) {
			return;
		}

		// 仅在玩家手持光束绑定器时显示，避免杀机各玩家看到不相干的光束
		boolean holding = player.getMainHandItem().getItem() instanceof LaserBindingTool
				|| player.getOffhandItem().getItem() instanceof LaserBindingTool;
		if (!holding) {
			return;
		}

		scratchTowers.clear();
		collectNearbyTowers(level, player.blockPosition(), scratchTowers);
		if (scratchTowers.isEmpty()) {
			return;
		}

		Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

		// === 1. 光束渲染（写入 BEAM_NO_DEPTH，sharedBuffer 路径，与 vanilla beaconBeam 同款）===
		for (WirelessPowerTowerBlockEntity tower : scratchTowers) {
			renderBeamsFromTower(poseStack, bufferSource, level, tower, cameraPos);
		}
		// 光束写完立刻 flush（vanilla 不带参 endBatch flush 全部 pending RT），让 sharedBuffer 完全 reset。
		// 这之后 cube 用 immediate-mode 独立路径绘制，与 sharedBuffer 完全解耦。
		bufferSource.endBatch();

		// === 2. 高亮立方体渲染（immediate-mode，独立 BufferBuilder，不依赖 sharedBuffer）===
		renderAllHighlightCubes(poseStack, level, cameraPos);
		scratchTowers.clear();
	}

	/**
	 * Immediate-mode 批量绘制所有高亮立方体。一次 begin/build/draw 处理全部 cube，减少 GL draw call。
	 *
	 * <p>vanilla 内部所有调试渲染（hit outline、debug crosshair、F3+B 视图等）都用这条路径：
	 * {@link Tesselator#begin} 拿独立 BufferBuilder → 写顶点 → {@link BufferBuilder#build} →
	 * {@link BufferUploader#drawWithShader} 直接提交 GPU。完全不经过 BufferSource sharedBuffer，
	 * 不会被光束的 RT 状态、vanilla 后续 RT pipeline 影响。RenderSystem 状态在前后手动设置/恢复。</p>
	 */
	private void renderAllHighlightCubes(PoseStack poseStack, Level level, Vec3 cameraPos) {
		poseStack.pushPose();
		poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

		// GL 状态：禁用深度测试让 cube 透机器外壳可见；启用 blend 让 alpha=0.55 半透明混色；
		// 禁用 cull face 防意外的 winding-order 问题；setShader 选 POSITION_COLOR 跟顶点格式匹配。
		RenderSystem.disableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);

		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
		boolean wroteAny = false;
		for (WirelessPowerTowerBlockEntity tower : scratchTowers) {
			List<BlockPos> links = tower.getClientLinks();
			if (links == null || links.isEmpty()) {
				continue;
			}
			for (BlockPos target : links) {
				// 用户要求："塔不用高亮，光束和机器小方块连上就行"——塔本身不画 cube。
				if (level.getBlockEntity(target) instanceof WirelessPowerTowerBlockEntity) {
					continue;
				}
				double x0 = target.getX() + 0.5d - CUBE_HALF;
				double y0 = target.getY() + 0.5d - CUBE_HALF;
				double z0 = target.getZ() + 0.5d - CUBE_HALF;
				double x1 = target.getX() + 0.5d + CUBE_HALF;
				double y1 = target.getY() + 0.5d + CUBE_HALF;
				double z1 = target.getZ() + 0.5d + CUBE_HALF;
				LevelRenderer.addChainedFilledBoxVertices(poseStack, buffer, x0, y0, z0, x1, y1, z1, HL_R, HL_G, HL_B, HL_A);
				wroteAny = true;
			}
		}

		if (wroteAny) {
			MeshData mesh = buffer.build();
			if (mesh != null) {
				BufferUploader.drawWithShader(mesh);
			}
		} else {
			// 没写顶点也要把 BufferBuilder 关掉，否则 Tesselator 留在 begin 状态，下一帧 begin 抛错。
			MeshData mesh = buffer.build();
			if (mesh != null) {
				mesh.close();
			}
		}

		// 恢复 GL 状态——绝对不能漏，否则会污染后续 vanilla pipeline 的渲染（天空、UI 都会出问题）。
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.enableDepthTest();

		poseStack.popPose();
	}

	/**
	 * 从一个塔发出所有光束。 1:1 沉用原 BER 代码的几何计算，只是把 PoseStack 的入口位置
	 * 从"BER 提供的塔本体坐标"换成"塔本体世界坐标 - 相机"（创造出与 BER 一致的上下文）。
	 */
	private static void renderBeamsFromTower(
			PoseStack poseStack, MultiBufferSource buffers, Level level,
			WirelessPowerTowerBlockEntity tower, Vec3 cameraPos) {
		List<BlockPos> links = tower.getClientLinks();
		if (links == null || links.isEmpty()) {
			return;
		}

		BlockPos towerBase = tower.getBlockPos();
		// 塔顶中心（塔高 3 格，底部 + 2.5 是顶部中点，+0.25 微抬避免穿模）
		float towerTopX = towerBase.getX() + 0.5f;
		float towerTopY = towerBase.getY() + 2.75f;
		float towerTopZ = towerBase.getZ() + 0.5f;

		for (BlockPos targetPos : links) {
			BlockEntity targetBE = level.getBlockEntity(targetPos);
			boolean isTargetTower = targetBE instanceof WirelessPowerTowerBlockEntity;

			float targetX = targetPos.getX() + 0.5f;
			float targetY = isTargetTower ? targetPos.getY() + 2.75f : targetPos.getY() + 0.5f;
			float targetZ = targetPos.getZ() + 0.5f;

			float vx = targetX - towerTopX;
			float vy = targetY - towerTopY;
			float vz = targetZ - towerTopZ;
			double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (length <= 0.1) {
				continue;
			}

			float invLen = (float) (1.0 / length);
			float nx = vx * invLen, ny = vy * invLen, nz = vz * invLen;

			float startOX = nx * 0.25f, startOY = ny * 0.25f, startOZ = nz * 0.25f;
			// 机器目标：让光束端点穿入 cube 中心（用户要求"光束和立方体连在一起"）。
			// 推导：BeamRenderHelper 内部有 BLOCK_BEAM_SHIFT=-0.25*n 的 endpoint 回退。原代码机器可用 endOffset=0，
			// 结果光束端点落在 target-0.25*n（刚好 cube 表面）——视觉上"贴着但未穿入"。
			// 现在设 endOffset=-0.5*n，让 endpoint 增加 0.5*n ⋯刚好抵消后退 + 穿过 cube 表面达到中心。
			float endOX = isTargetTower ? -nx * 0.25f : -nx * 0.5f;
			float endOY = isTargetTower ? -ny * 0.25f : -ny * 0.5f;
			float endOZ = isTargetTower ? -nz * 0.25f : -nz * 0.5f;

			float adjVx = vx - startOX - endOX;
			float adjVy = vy - startOY - endOY;
			float adjVz = vz - startOZ - endOZ;

			poseStack.pushPose();
			// 原 BER 入口时 PoseStack 位于 (塔底世界坐标 - 相机)，然后 BER 调
			// translate(startOffsetX, 2.25 + startOffsetY, startOffsetZ)。
			// 这里两步合为一步：direct translate 到 (塔顶世界坐标 + startOffset - 相机)。
			poseStack.translate(
					towerBase.getX() - cameraPos.x + startOX,
					towerBase.getY() - cameraPos.y + 2.25 + startOY,
					towerBase.getZ() - cameraPos.z + startOZ);

			// BeamRenderHelper 内部 translate(0.5, 0.5, 0.5) 到方块中心—— 与 BER 调用路径一致
			BeamRenderHelper.renderColoredBeamVector(
					poseStack, buffers,
					adjVx, adjVy, adjVz,
					BEAM_R, BEAM_G, BEAM_B,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, BEAM_THICKNESS);
			poseStack.popPose();
		}
	}

	/**
	 * 扫玩家附近 chunk 找出所有在扫描范围内的塔 BE。
	 * <p>{@link net.minecraft.world.level.chunk.LevelChunk#getBlockEntities()}
	 * 直接返回 chunk 内所有 BE 的 map，O(BE 数) 遍历，不用扫方块。</p>
	 */
	private static void collectNearbyTowers(
			Level level, BlockPos playerPos, List<WirelessPowerTowerBlockEntity> out) {
		int chunkRadius = (SCAN_RADIUS >> 4) + 1;
		int playerCX = playerPos.getX() >> 4;
		int playerCZ = playerPos.getZ() >> 4;
		for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
			for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof WirelessPowerTowerBlockEntity tower)) {
						continue;
					}
					BlockPos tp = tower.getBlockPos();
					if (Math.abs(tp.getX() - playerPos.getX()) <= SCAN_RADIUS
							&& Math.abs(tp.getZ() - playerPos.getZ()) <= SCAN_RADIUS) {
						out.add(tower);
					}
				}
			}
		}
	}
}
