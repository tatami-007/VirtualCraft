package dev.tatami.virtualcraft.client.overlay;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.BossAttackDirection;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public final class SnakeBossOverlay {
	private static final int WIDTH = 256;
	private static final int BAR_X = 22;
	private static final int BAR_Y = 11;
	private static final int BAR_WIDTH = 212;
	private static final int BAR_HEIGHT = 7;
	private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(
			VirtualCraft.MODID, "textures/gui/snake_boss_bar.png");
	private static final Map<UUID, Float> DAMAGE_MEMORY = new HashMap<>();
	private static final Map<UUID, Integer> BOSS_ENTITY_IDS = new HashMap<>();

	private SnakeBossOverlay() {
	}

	public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
		VirtualSnakeKing snake = findSnake(event.getBossEvent().getId(), event.getBossEvent().getName(),
				event.getBossEvent().getProgress());
		if (snake == null) {
			return;
		}
		event.setCanceled(true);
		event.setIncrement(52);
		GuiGraphics graphics = event.getGuiGraphics();
		int x = (graphics.guiWidth() - WIDTH) / 2;
		int y = event.getY() + 5;
		float health = event.getBossEvent().getProgress();
		float memory = DAMAGE_MEMORY.getOrDefault(snake.getUUID(), health);
		memory = health >= memory ? health : Math.max(health, memory - 0.0045F);
		DAMAGE_MEMORY.put(snake.getUUID(), memory);

		drawFrame(graphics, x, y, health, memory, snake);
		Component name = event.getBossEvent().getName();
		int nameX = graphics.guiWidth() / 2 - Minecraft.getInstance().font.width(name) / 2;
		graphics.drawString(Minecraft.getInstance().font, name, nameX, y - 10, 0xFFF5D778, true);
	}

	private static void drawFrame(GuiGraphics graphics, int x, int y, float health, float memory,
			VirtualSnakeKing snake) {
		int barX = x + BAR_X;
		int barY = y + BAR_Y;
		graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF100F12);
		graphics.fill(barX, barY + 1, barX + Math.round(BAR_WIDTH * memory), barY + BAR_HEIGHT - 1,
				0xFFD6C58D);
		int healthWidth = Math.round(BAR_WIDTH * health);
		int healthColor = health <= 0.25F ? 0xFFC84A2B : 0xFFD69A19;
		int highlightColor = health <= 0.25F ? 0xFFFF9A68 : 0xFFFFE27A;
		graphics.fill(barX, barY + 1, barX + healthWidth, barY + BAR_HEIGHT - 1, healthColor);
		graphics.fill(barX, barY + 1, barX + healthWidth, barY + 2, highlightColor);
		graphics.blit(FRAME, x, y, 0, 0, WIDTH, 32, WIDTH, 32);
		drawPosture(graphics, barX, y + 30, snake);

		int attack = snake.getAttackState();
		int cx = x + WIDTH / 2;
		if (attack != VirtualSnakeKing.ATTACK_NONE) {
			int speed = health <= 0.25F ? 1 : 3;
			int pulse = (Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount) / speed & 1;
			BossAttackDirection direction = snake.getAttackDirection();
			int color = directionColor(direction, pulse);
			drawDirectionGlyph(graphics, cx, y + 15, direction, color);
			float charge = attackCharge(snake, attack, snake.getAttackTick());
			graphics.fill(cx - 34, y + 40, cx + 34, y + 43, 0xE008090D);
			graphics.fill(cx - 33, y + 41, cx - 33 + Math.round(66 * charge), y + 42, color);
		}
		if (health <= 0.25F && Minecraft.getInstance().player != null
				&& (Minecraft.getInstance().player.tickCount & 3) == 0) {
			int spark = (Minecraft.getInstance().player.tickCount * 17) % 196;
			graphics.fill(barX + spark, barY - 1, barX + spark + 5, barY, 0xFFDDF8FF);
		}
	}

	private static void drawPosture(GuiGraphics graphics, int x, int y, VirtualSnakeKing snake) {
		int posture = snake.getPosture();
		int pulse = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
		graphics.fill(x - 2, y + 2, x + BAR_WIDTH + 2, y + 5, 0xD906070A);
		for (int segment = 0; segment < 5; segment++) {
			int sx = x + segment * 43;
			int width = 40;
			float fill = Math.max(0.0F, Math.min(1.0F, (posture - segment * 20) / 20.0F));
			drawPostureSegment(graphics, sx, y, width, fill, snake.isStaggered(), pulse);
		}
		int breakAge = 80 - snake.getStaggerTicks();
		if (breakAge >= 0 && breakAge < 12) {
			drawPostureBreak(graphics, x, y, breakAge);
		}
	}

	private static void drawPostureSegment(GuiGraphics graphics, int x, int y, int width,
			float fill, boolean staggered, int pulse) {
		int rim = staggered && (pulse & 2) == 0 ? 0xFF9B7A38 : 0xFF665126;
		int empty = staggered && (pulse & 2) == 0 ? 0xFF44334F : 0xFF1B1721;

		graphics.fill(x + 3, y, x + width - 3, y + 1, 0xFF07080B);
		graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF07080B);
		graphics.fill(x, y + 2, x + width, y + 5, 0xFF07080B);
		graphics.fill(x + 1, y + 5, x + width - 1, y + 6, 0xFF07080B);
		graphics.fill(x + 3, y + 6, x + width - 3, y + 7, 0xFF07080B);

		graphics.fill(x + 3, y + 1, x + width - 3, y + 2, rim);
		graphics.fill(x + 2, y + 2, x + width - 2, y + 5, empty);
		graphics.fill(x + 3, y + 5, x + width - 3, y + 6, 0xFF352A1C);
		graphics.fill(x + 3, y + 2, x + width - 3, y + 3, 0xFF342B3D);

		if (fill <= 0.0F) {
			return;
		}
		int usable = width - 4;
		int filled = Math.max(1, Math.round(usable * fill));
		fillClipped(graphics, x + 3, y + 2, x + 3 + filled, y + 3,
				x + width - 3, 0xFFFFEDA0);
		fillClipped(graphics, x + 2, y + 3, x + 2 + filled, y + 5,
				x + width - 2, 0xFFD3A52D);
		fillClipped(graphics, x + 3, y + 5, x + 3 + filled, y + 6,
				x + width - 3, 0xFF795116);
	}

	private static void fillClipped(GuiGraphics graphics, int left, int top, int right,
			int bottom, int maximumRight, int color) {
		int clipped = Math.min(right, maximumRight);
		if (clipped > left) {
			graphics.fill(left, top, clipped, bottom, color);
		}
	}

	private static void drawPostureBreak(GuiGraphics graphics, int x, int y, int age) {
		int spread = 3 + age * 2;
		int alpha = Math.max(0, 230 - age * 18);
		int pale = alpha << 24 | 0xFFF4C7;
		int gold = Math.max(0, alpha - 42) << 24 | 0xD5A937;
		for (int shard = 0; shard < 9; shard++) {
			int anchor = x + 11 + shard * 24;
			int direction = (shard & 1) == 0 ? -1 : 1;
			int sx = anchor + direction * spread;
			int sy = y + 3 + ((shard * 7) % 5 - 2) * (1 + age / 4);
			graphics.fill(sx, sy, sx + 3, sy + 1, pale);
			graphics.fill(sx + direction, sy - 1, sx + direction + 1, sy + 2, gold);
		}
	}

	private static int directionColor(BossAttackDirection direction, int pulse) {
		return switch (direction) {
			case HIGH -> pulse == 0 ? 0xFFBDEEFF : 0xFF3FA9FF;
			case MID -> pulse == 0 ? 0xFFFFFFFF : 0xFFDDE8F1;
			case LOW -> pulse == 0 ? 0xFFFFB09C : 0xFFFF4938;
		};
	}

	private static float attackCharge(VirtualSnakeKing snake, int attack, int tick) {
		int windup = switch (attack) {
			case VirtualSnakeKing.ATTACK_DOUBLE_HUNT -> 20;
			case VirtualSnakeKing.ATTACK_DATA_FANGS -> 20;
			case VirtualSnakeKing.ATTACK_RUNE_SIGIL -> 20;
			case VirtualSnakeKing.ATTACK_VOID_DASH -> tick < 30 ? 16 : 41;
			case VirtualSnakeKing.ATTACK_CROWN_JUDGMENT -> 62;
			case VirtualSnakeKing.ATTACK_OVERHEAD_FANG -> 22;
			default -> 1;
		};
		int start = attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT
				? tick - Math.floorMod(tick, 20)
				: attack == VirtualSnakeKing.ATTACK_VOID_DASH && tick >= 30 ? 25 : 0;
		if (attack == VirtualSnakeKing.ATTACK_DOUBLE_HUNT) {
			windup = start + (snake.isComboTailStage() ? 19 : 11);
		}
		return Math.min(1.0F, Math.max(0.0F, (tick - start) / (float)Math.max(1, windup - start)));
	}

	private static void drawDirectionGlyph(GuiGraphics graphics, int cx, int cy,
			BossAttackDirection direction, int color) {
		switch (direction) {
			case HIGH -> {
				graphics.fill(cx - 1, cy - 5, cx + 2, cy + 4, color);
				graphics.fill(cx - 4, cy - 2, cx + 5, cy, color);
				graphics.fill(cx - 3, cy - 3, cx + 4, cy - 1, color);
			}
			case MID -> {
				graphics.fill(cx - 5, cy - 3, cx - 2, cy + 1, color);
				graphics.fill(cx + 2, cy - 3, cx + 5, cy + 1, color);
				graphics.fill(cx - 3, cy, cx, cy + 4, color);
				graphics.fill(cx + 1, cy, cx + 4, cy + 4, color);
			}
			case LOW -> {
				graphics.fill(cx - 6, cy + 2, cx + 7, cy + 4, color);
				graphics.fill(cx - 4, cy, cx - 1, cy + 2, color);
				graphics.fill(cx + 2, cy, cx + 5, cy + 2, color);
			}
		}
	}

	private static VirtualSnakeKing findSnake(UUID bossEventId, Component name, float progress) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return null;
		}
		Integer mappedId = BOSS_ENTITY_IDS.get(bossEventId);
		if (mappedId != null && minecraft.level.getEntity(mappedId) instanceof VirtualSnakeKing mapped
				&& (mapped.isAlive() || mapped.deathTime > 0)) {
			return mapped;
		}
		VirtualSnakeKing best = null;
		double bestScore = Double.MAX_VALUE;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof VirtualSnakeKing snake)
					|| (!snake.isAlive() && snake.deathTime <= 0)
					|| !snake.getDisplayName().getString().equals(name.getString())) {
				continue;
			}
			double healthDifference = Math.abs(snake.getHealth() / snake.getMaxHealth() - progress);
			double distance = minecraft.player == null ? 0.0 : snake.distanceToSqr(minecraft.player) * 0.00001;
			double score = healthDifference * 100.0 + distance;
			if (score < bestScore) {
				best = snake;
				bestScore = score;
			}
		}
		if (best != null) {
			BOSS_ENTITY_IDS.put(bossEventId, best.getId());
		}
		return best;
	}
}
