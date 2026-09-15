package dev.tatami.virtualcraft.client.overlay;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.client.SnakeBossCombatFeedback;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/** Compact fungal crown bar with six posture plates and four embedded armour nodes. */
public final class MushroomBossOverlay {
	private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/gui/boss/mushroom_king_bar.png");
	private static final int WIDTH = 244;
	private static final int HEALTH_X = 17;
	private static final int HEALTH_Y = 9;
	private static final int HEALTH_WIDTH = 210;
	private static final int HEALTH_HEIGHT = 6;
	private static final int ATLAS_HEIGHT = 80;
	private static final int POSTURE_SEGMENTS = 6;
	private static final int POSTURE_WIDTH = 32;
	private static final int POSTURE_GAP = 3;
	private static final Map<UUID, Integer> BOSS_ENTITY_IDS = new HashMap<>();
	private static final Map<UUID, Float> DAMAGE_MEMORY = new HashMap<>();

	private MushroomBossOverlay() {
	}

	public static void clear() {
		BOSS_ENTITY_IDS.clear();
		DAMAGE_MEMORY.clear();
	}

	public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
		VirtualMushroomKing boss = findMushroomKing(event.getBossEvent().getId(),
				event.getBossEvent().getName(), event.getBossEvent().getProgress());
		if (boss == null) {
			return;
		}
		event.setCanceled(true);
		event.setIncrement(38);
		GuiGraphics graphics = event.getGuiGraphics();
		int x = (graphics.guiWidth() - WIDTH) / 2;
		int y = event.getY() + 3;
		float health = event.getBossEvent().getProgress();
		float memory = DAMAGE_MEMORY.getOrDefault(boss.getUUID(), health);
		memory = health >= memory ? health : Math.max(health, memory - 0.0035F);
		DAMAGE_MEMORY.put(boss.getUUID(), memory);

		drawHealth(graphics, x, y, health, memory);
		graphics.blit(ATLAS, x, y, 0, 0, WIDTH, 22, 256, ATLAS_HEIGHT);
		drawArmourNodes(graphics, x, y, boss);
		drawDirection(graphics, x + WIDTH / 2, y + 5, boss);
		drawPosture(graphics, x, y + 23, boss);

		Component name = event.getBossEvent().getName();
		int nameX = graphics.guiWidth() / 2 - Minecraft.getInstance().font.width(name) / 2;
		graphics.drawString(Minecraft.getInstance().font, name, nameX, y - 10,
				0xFFEADFD5, true);
	}

	private static void drawHealth(GuiGraphics graphics, int x, int y, float health, float memory) {
		int hx = x + HEALTH_X;
		int hy = y + HEALTH_Y;
		graphics.blit(ATLAS, hx, hy, 0, 52, HEALTH_WIDTH, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		int memoryWidth = Math.round(HEALTH_WIDTH * memory);
		if (memoryWidth > 0) {
			graphics.blit(ATLAS, hx, hy, 0, 60, memoryWidth, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		}
		int healthWidth = Math.round(HEALTH_WIDTH * health);
		if (healthWidth > 0) {
			graphics.blit(ATLAS, hx, hy, 0, 68, healthWidth, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		}
	}

	private static void drawPosture(GuiGraphics graphics, int x, int y,
			VirtualMushroomKing boss) {
		float posture = boss.getPosture() / (float)Math.max(1, boss.getMaxPosture());
		boolean parryFlash = SnakeBossCombatFeedback.feedbackBossEntityId() == boss.getId()
				&& SnakeBossCombatFeedback.feedbackKind() == SnakeCombatFeedbackPayload.PARRY
				&& SnakeBossCombatFeedback.flashIntensity() > 0.0F;
		int total = POSTURE_SEGMENTS * POSTURE_WIDTH + (POSTURE_SEGMENTS - 1) * POSTURE_GAP;
		int start = x + (WIDTH - total) / 2;
		for (int segment = 0; segment < POSTURE_SEGMENTS; segment++) {
			int sx = start + segment * (POSTURE_WIDTH + POSTURE_GAP);
			graphics.blit(ATLAS, sx, y, 0, 24, POSTURE_WIDTH, 8, 256, ATLAS_HEIGHT);
			float local = Math.max(0.0F, Math.min(1.0F,
					posture * POSTURE_SEGMENTS - segment));
			if (local <= 0.0F) {
				continue;
			}
			int fillWidth = Math.max(1, Math.round(POSTURE_WIDTH * local));
			int sourceX = boss.getStaggerTicks() > 0 || parryFlash ? 68 : 34;
			graphics.blit(ATLAS, sx, y, sourceX, 24, fillWidth, 8, 256, ATLAS_HEIGHT);
		}
	}

	private static void drawArmourNodes(GuiGraphics graphics, int x, int y,
			VirtualMushroomKing boss) {
		drawArmourNode(graphics, x + 2, y + 1, boss.getLeftShoulderArmor(),
				VirtualMushroomKing.LEFT_SHOULDER_MAX, boss.getBreakingPlate() == 0);
		drawArmourNode(graphics, x + 15, y + 7, boss.getChestArmor(),
				VirtualMushroomKing.CHEST_MAX, boss.getBreakingPlate() == 2);
		drawArmourNode(graphics, x + WIDTH - 27, y + 7, boss.getBackArmor(),
				VirtualMushroomKing.BACK_MAX, boss.getBreakingPlate() == 3);
		drawArmourNode(graphics, x + WIDTH - 14, y + 1, boss.getRightShoulderArmor(),
				VirtualMushroomKing.RIGHT_SHOULDER_MAX, boss.getBreakingPlate() == 1);
	}

	private static void drawArmourNode(GuiGraphics graphics, int x, int y, int value, int max,
			boolean breaking) {
		float ratio = value / (float)Math.max(1, max);
		int state = value <= 0 ? 2 : ratio < 0.35F || breaking ? 1 : 0;
		graphics.blit(ATLAS, x, y, state * 14, 36, 12, 11, 256, ATLAS_HEIGHT);
	}

	private static void drawDirection(GuiGraphics graphics, int centerX, int y,
			VirtualMushroomKing boss) {
		if (boss.getAttackState() == 0
				|| MushroomKingAttackRules.isUnavoidableAttack(boss.getAttackState())) {
			return;
		}
		BossAttackDirection direction = boss.getAttackDirection();
		int sourceX = switch (direction) {
			case HIGH -> 48;
			case MID -> 64;
			case LOW -> 80;
		};
		graphics.blit(ATLAS, centerX - 7, y - 7, sourceX, 36, 14, 14, 256, ATLAS_HEIGHT);
	}

	private static VirtualMushroomKing findMushroomKing(UUID bossEventId, Component name,
			float progress) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		Integer mappedId = BOSS_ENTITY_IDS.get(bossEventId);
		if (mappedId != null && minecraft.level.getEntity(mappedId)
				instanceof VirtualMushroomKing mapped && (mapped.isAlive() || mapped.deathTime > 0)) {
			return mapped;
		}
		VirtualMushroomKing best = null;
		double bestScore = Double.MAX_VALUE;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualMushroomKing boss)
					|| (!boss.isAlive() && boss.deathTime <= 0)
					|| !boss.getDisplayName().getString().equals(name.getString())) {
				continue;
			}
			double healthDifference = Math.abs(boss.getHealth() / boss.getMaxHealth() - progress);
			double distance = minecraft.player == null ? 0.0
					: boss.distanceToSqr(minecraft.player) * 0.00001;
			double score = healthDifference * 100.0 + distance;
			if (score < bestScore) {
				best = boss;
				bestScore = score;
			}
		}
		if (best != null) {
			BOSS_ENTITY_IDS.put(bossEventId, best.getId());
		}
		return best;
	}
}
