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

/** Fungal sovereign frame, integrated chitin armour and a continuous mycelium gauge. */
public final class MushroomBossOverlay {
	private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/gui/boss/mushroom_king_bar.png");
	private static final int WIDTH = 244;
	private static final int HEALTH_X = 26;
	private static final int HEALTH_Y = 22;
	private static final int HEALTH_WIDTH = 192;
	private static final int HEALTH_HEIGHT = 8;
	private static final int ATLAS_HEIGHT = 256;
	private static final int POSTURE_WIDTH = 174;
	private static final Map<UUID, Integer> BOSS_ENTITY_IDS = new HashMap<>();
	private static final Map<UUID, BossHealthTrail> DAMAGE_MEMORY = new HashMap<>();

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
		event.setIncrement(58);
		GuiGraphics graphics = event.getGuiGraphics();
		int x = (graphics.guiWidth() - WIDTH) / 2;
		int y = event.getY() + 3;
		float health = event.getBossEvent().getProgress();
		long tick = boss.level().getGameTime();
		float memory = DAMAGE_MEMORY.computeIfAbsent(boss.getUUID(),
				id -> new BossHealthTrail(health, tick)).update(health, tick);

		drawHealth(graphics, x, y, health, memory);
		drawPosture(graphics, x, y + 35, boss);
		graphics.blit(ATLAS, x, y, 0, 0, WIDTH, 42, 256, ATLAS_HEIGHT);
		drawArmourNodes(graphics, x, y, boss);
		drawDirection(graphics, x + WIDTH / 2, y + 2, boss);

		Component name = event.getBossEvent().getName();
		int nameX = graphics.guiWidth() / 2 - Minecraft.getInstance().font.width(name) / 2;
		graphics.drawString(Minecraft.getInstance().font, name, nameX, y - 10,
				0xFFEADFD5, true);
	}

	private static void drawHealth(GuiGraphics graphics, int x, int y, float health, float memory) {
		int hx = x + HEALTH_X;
		int hy = y + HEALTH_Y;
		graphics.blit(ATLAS, hx, hy, 0, 48, HEALTH_WIDTH, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		int memoryWidth = Math.round(HEALTH_WIDTH * memory);
		if (memoryWidth > 0) {
			graphics.blit(ATLAS, hx, hy, 0, 56, memoryWidth, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		}
		int healthWidth = Math.round(HEALTH_WIDTH * health);
		if (healthWidth > 0) {
			graphics.blit(ATLAS, hx, hy, 0, 64, healthWidth, HEALTH_HEIGHT, 256, ATLAS_HEIGHT);
		}
	}

	private static void drawPosture(GuiGraphics graphics, int x, int y,
			VirtualMushroomKing boss) {
		float posture = boss.getPosture() / (float)Math.max(1, boss.getMaxPosture());
		boolean parryFlash = SnakeBossCombatFeedback.feedbackBossEntityId() == boss.getId()
				&& SnakeBossCombatFeedback.feedbackKind() == SnakeCombatFeedbackPayload.PARRY
				&& SnakeBossCombatFeedback.flashIntensity() > 0.0F;
		int start = x + (WIDTH - POSTURE_WIDTH) / 2;
		graphics.blit(ATLAS, start, y, 0, 80, POSTURE_WIDTH, 4, 256, ATLAS_HEIGHT);
		boolean stagger = boss.getStaggerTicks() > 0;
		int fillWidth = stagger ? POSTURE_WIDTH : Math.round(POSTURE_WIDTH * Math.clamp(posture, 0, 1));
		boolean flash = parryFlash || (stagger && boss.getStaggerTicks() % 12 < 4);
		int row = flash ? 104 : stagger || posture >= 0.75F ? 96 : 88;
		if (fillWidth > 0) graphics.blit(ATLAS, start, y, 0, row, fillWidth, 4, 256, ATLAS_HEIGHT);
	}

	private static void drawArmourNodes(GuiGraphics graphics, int x, int y,
			VirtualMushroomKing boss) {
		drawArmourNode(graphics, x + 3, y + 12, boss.getLeftShoulderArmor(),
				VirtualMushroomKing.LEFT_SHOULDER_MAX, boss.getBreakingPlate() == 0, false);
		drawArmourNode(graphics, x + 5, y + 26, boss.getChestArmor(),
				VirtualMushroomKing.CHEST_MAX, boss.getBreakingPlate() == 2, false);
		drawArmourNode(graphics, x + 219, y + 26, boss.getBackArmor(),
				VirtualMushroomKing.BACK_MAX, boss.getBreakingPlate() == 3, true);
		drawArmourNode(graphics, x + 221, y + 12, boss.getRightShoulderArmor(),
				VirtualMushroomKing.RIGHT_SHOULDER_MAX, boss.getBreakingPlate() == 1, true);
	}

	private static void drawArmourNode(GuiGraphics graphics, int x, int y, int value, int max,
			boolean breaking, boolean right) {
		float ratio = value / (float)Math.max(1, max);
		int state = value <= 0 ? 2 : ratio < 0.35F || breaking ? 1 : 0;
		graphics.blit(ATLAS, x, y, state * 24, right ? 140 : 120, 20, 14, 256, ATLAS_HEIGHT);
	}

	private static void drawDirection(GuiGraphics graphics, int centerX, int y,
			VirtualMushroomKing boss) {
		BossAttackDirection direction = boss.getAttackDirection();
		int sourceX = boss.getAttackState() == 0
				|| MushroomKingAttackRules.isUnavoidableAttack(boss.getAttackState()) ? 0 : switch (direction) {
			case HIGH -> 48;
			case MID -> 96;
			case LOW -> 144;
		};
		graphics.blit(ATLAS, centerX - 20, y, sourceX, 176, 40, 18, 256, ATLAS_HEIGHT);
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
