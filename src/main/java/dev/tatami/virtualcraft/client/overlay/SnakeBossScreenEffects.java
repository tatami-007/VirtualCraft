package dev.tatami.virtualcraft.client.overlay;

import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.client.SnakeBossCombatFeedback;
import dev.tatami.virtualcraft.network.SnakeCombatFeedbackPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Restrained full-screen pulses used only at summon completion and judgment impact. */
public final class SnakeBossScreenEffects {
	private SnakeBossScreenEffects() {
	}

	public static void render(RenderGuiLayerEvent.Post event) {
		if (!VanillaGuiLayers.OVERLAY_MESSAGE.equals(event.getName())) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
			return;
		}
		VirtualSnakeKing snake = null;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof VirtualSnakeKing candidate && candidate.isAlive()
					&& candidate.distanceToSqr(minecraft.player) < 96.0 * 96.0) {
				snake = candidate;
				break;
			}
		}
		if (snake == null) {
			return;
		}

		float summonIntensity = 0.0F;
		if (snake != null && snake.getSummonTicks() > 0 && snake.getSummonTicks() <= 7) {
			summonIntensity = 1.0F - snake.getSummonTicks() / 7.0F;
		}
		if (summonIntensity > 0.0F) {
			drawSummonPulse(event.getGuiGraphics(), Mth.clamp(summonIntensity, 0.0F, 1.0F));
		}
		float combatIntensity = SnakeBossCombatFeedback.flashIntensity();
		if (combatIntensity > 0.0F) {
			int feedbackKind = SnakeBossCombatFeedback.feedbackKind();
			drawCombatPulse(event.getGuiGraphics(), Mth.clamp(combatIntensity, 0.0F, 1.0F),
					feedbackKind, SnakeBossCombatFeedback.lateralDirection());
		}
		float tutorialIntensity = SnakeBossCombatFeedback.comboTutorialIntensity();
		if (tutorialIntensity > 0.0F && snake != null
				&& snake.getAttackState() == VirtualSnakeKing.ATTACK_DOUBLE_HUNT) {
			drawComboTutorial(event.getGuiGraphics(), snake, tutorialIntensity,
					SnakeBossCombatFeedback.comboTutorialKind());
		}
	}

	private static void drawComboTutorial(GuiGraphics graphics, VirtualSnakeKing snake,
			float intensity, int kind) {
		Minecraft minecraft = Minecraft.getInstance();
		int centerX = graphics.guiWidth() / 2;
		int top = Math.min(graphics.guiHeight() - 76, graphics.guiHeight() / 2 + 48);
		int alpha = Math.round(224.0F * intensity);
		int panel = Math.round(188.0F * intensity) << 24 | 0x080B11;
		int border = alpha << 24 | 0xB89038;
		int accent = alpha << 24 | 0xF4D982;
		int width = Math.min(222, graphics.guiWidth() - 18);
		int left = centerX - width / 2;
		int right = centerX + width / 2;
		fillCutCornerPanel(graphics, left, top, right, top + 54, panel, border, accent);

		String suffix = kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_BITE_CUE ? "bbb"
				: kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE ? "bbt" : "btb";
		int text = alpha << 24 | 0xF4F7F8;
		int muted = Math.round(alpha * 0.82F) << 24 | 0xB8C3C8;
		graphics.fill(left + 9, top + 8, left + 12, top + 17, accent);
		graphics.fill(left + 6, top + 11, left + 15, top + 14, accent);
		graphics.drawCenteredString(minecraft.font,
				Component.translatable("message.virtual_craft.snake_king.combo." + suffix + ".title"),
				centerX, top + 6, text);
		graphics.drawCenteredString(minecraft.font,
				Component.translatable("message.virtual_craft.snake_king.combo." + suffix + ".hint"),
				centerX, top + 19, muted);

		int segment = Mth.clamp(snake.getComboSegment(), 0, 2);
		boolean[] tail = switch (kind) {
			case SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE -> new boolean[]{false, false, true};
			case SnakeCombatFeedbackPayload.COMBO_BITE_TAIL_BITE_CUE -> new boolean[]{false, true, false};
			default -> new boolean[]{false, false, false};
		};
		int chipWidth = 34;
		int gap = 7;
		int rowWidth = chipWidth * 3 + gap * 2;
		int rowLeft = centerX - rowWidth / 2;
		for (int i = 0; i < 3; i++) {
			int chipLeft = rowLeft + i * (chipWidth + gap);
			boolean active = i == segment;
			int chipRgb = tail[i] ? 0xD99635 : 0xD9E8EA;
			int chipAlpha = active ? alpha : Math.round(alpha * 0.48F);
			int chipBorder = chipAlpha << 24 | chipRgb;
			int chipPanel = Math.round((active ? 112.0F : 62.0F) * intensity) << 24
					| (tail[i] ? 0x3A2413 : 0x172126);
			fillCutCornerPanel(graphics, chipLeft, top + 34, chipLeft + chipWidth, top + 48,
					chipPanel, chipBorder, chipBorder);
			drawComboMoveGlyph(graphics, chipLeft + chipWidth / 2, top + 41,
					tail[i], chipBorder);
		}
	}

	private static void drawComboMoveGlyph(GuiGraphics graphics, int centerX, int centerY,
			boolean tail, int color) {
		if (tail) {
			// A broad hook reads as a delayed sweep without adding another word.
			graphics.fill(centerX - 7, centerY - 3, centerX + 4, centerY - 2, color);
			graphics.fill(centerX + 3, centerY - 2, centerX + 7, centerY, color);
			graphics.fill(centerX + 5, centerY, centerX + 7, centerY + 3, color);
			graphics.fill(centerX - 5, centerY + 2, centerX + 6, centerY + 3, color);
			graphics.fill(centerX - 7, centerY, centerX - 5, centerY + 3, color);
			return;
		}
		// Opposed fangs close toward the center at the bite timing.
		graphics.fill(centerX - 7, centerY - 4, centerX - 3, centerY - 3, color);
		graphics.fill(centerX - 5, centerY - 3, centerX - 2, centerY, color);
		graphics.fill(centerX + 3, centerY - 4, centerX + 7, centerY - 3, color);
		graphics.fill(centerX + 2, centerY - 3, centerX + 5, centerY, color);
		graphics.fill(centerX - 5, centerY + 2, centerX - 2, centerY + 5, color);
		graphics.fill(centerX - 7, centerY + 5, centerX - 3, centerY + 6, color);
		graphics.fill(centerX + 2, centerY + 2, centerX + 5, centerY + 5, color);
		graphics.fill(centerX + 3, centerY + 5, centerX + 7, centerY + 6, color);
	}

	private static void drawSummonPulse(GuiGraphics graphics, float intensity) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int alpha = Math.round(54.0F * intensity);
		int gold = alpha << 24 | 0xE8B82A;
		int pale = Math.round(34.0F * intensity) << 24 | 0xFFF3BD;
		int edge = Math.max(1, Math.round(7.0F * intensity));
		graphics.fill(0, 0, width, edge, gold);
		graphics.fill(0, height - edge, width, height, gold);
		graphics.fill(0, edge, edge, height - edge, pale);
		graphics.fill(width - edge, edge, width, height - edge, pale);
		for (int ring = 0; ring < 3; ring++) {
			int inset = Math.round((18 + ring * 13) * intensity);
			int lineAlpha = Math.max(0, alpha - ring * 12);
			int color = lineAlpha << 24 | (ring == 1 ? 0xFFF7CC : 0xD39A17);
			graphics.fill(inset, inset, width - inset, inset + 1, color);
			graphics.fill(inset, height - inset - 1, width - inset, height - inset, color);
		}
	}

	private static void drawCombatPulse(GuiGraphics graphics, float intensity, int kind, float lateral) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int centerX = width / 2 + Math.round(lateral * 12.0F);
		int centerY = height / 2;
		if (kind == SnakeCombatFeedbackPayload.HIGH_CUE
				|| kind == SnakeCombatFeedbackPayload.MID_CUE
				|| kind == SnakeCombatFeedbackPayload.LOW_CUE) {
			drawTeachingCue(graphics, centerX, centerY, intensity, kind);
			return;
		}
		int rgb = switch (kind) {
			case SnakeCombatFeedbackPayload.PARRY -> 0xFFF9D8;
			case SnakeCombatFeedbackPayload.WEAPON_PARRY -> 0xDDFBFF;
			case SnakeCombatFeedbackPayload.HIGH_DODGE -> 0x72D8FF;
			case SnakeCombatFeedbackPayload.LOW_DODGE -> 0xFFC247;
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> 0xC57B56;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 0xE7C65A;
			default -> 0xF2D2B7;
		};
		int alpha = Math.round((kind == SnakeCombatFeedbackPayload.PARRY
				|| kind == SnakeCombatFeedbackPayload.WEAPON_PARRY ? 190.0F : 122.0F) * intensity);
		int color = alpha << 24 | rgb;
		int pale = Math.round(alpha * 0.68F) << 24 | 0xFFFFFF;
		int radius = Math.max(5, Math.round(15.0F * intensity));
		int thickness = kind == SnakeCombatFeedbackPayload.PARRY
				|| kind == SnakeCombatFeedbackPayload.WEAPON_PARRY ? 2 : 1;

		graphics.fill(centerX - radius, centerY - thickness, centerX - 3, centerY + thickness, color);
		graphics.fill(centerX + 3, centerY - thickness, centerX + radius, centerY + thickness, color);
		graphics.fill(centerX - thickness, centerY - radius, centerX + thickness, centerY - 3, color);
		graphics.fill(centerX - thickness, centerY + 3, centerX + thickness, centerY + radius, color);
		graphics.fill(centerX - radius / 2, centerY - radius / 2 - 1,
				centerX + radius / 2, centerY - radius / 2, pale);
		graphics.fill(centerX - radius / 2, centerY + radius / 2,
				centerX + radius / 2, centerY + radius / 2 + 1, pale);

		if (kind == SnakeCombatFeedbackPayload.PARRY
				|| kind == SnakeCombatFeedbackPayload.WEAPON_PARRY
				|| kind == SnakeCombatFeedbackPayload.POSTURE_BREAK) {
			int edgeAlpha = Math.round(68.0F * intensity);
			int edgeColor = edgeAlpha << 24 | rgb;
			int edge = Math.max(1, Math.round(3.0F * intensity));
			graphics.fill(0, 0, width, edge, edgeColor);
			graphics.fill(0, height - edge, width, height, edgeColor);
		}
	}

	private static void drawTeachingCue(GuiGraphics graphics, int centerX, int centerY,
			float intensity, int kind) {
		int rgb = kind == SnakeCombatFeedbackPayload.HIGH_CUE ? 0x38BFFF
				: kind == SnakeCombatFeedbackPayload.LOW_CUE ? 0xFFC247 : 0xFFF2C2;
		int alpha = Math.round(190.0F * Math.min(1.0F, intensity * 1.45F));
		int color = alpha << 24 | rgb;
		int core = Math.min(255, alpha + 35) << 24 | 0xFFFFFF;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int spread = 22 + Math.round((1.0F - intensity) * 22.0F);
		if (kind == SnakeCombatFeedbackPayload.HIGH_CUE) {
			int y = 12;
			graphics.fill(centerX - spread, y, centerX + spread, y + 2, color);
			graphics.fill(centerX - 14, y + 4, centerX + 15, y + 6, core);
			graphics.fill(centerX - 8, y + 7, centerX + 9, y + 9, core);
			graphics.fill(centerX - 2, y + 10, centerX + 3, y + 13, core);
		} else if (kind == SnakeCombatFeedbackPayload.LOW_CUE) {
			int y = height - 14;
			graphics.fill(centerX - spread, y, centerX + spread, y + 2, color);
			graphics.fill(centerX - 14, y - 4, centerX + 15, y - 2, core);
			graphics.fill(centerX - 8, y - 7, centerX + 9, y - 5, core);
			graphics.fill(centerX - 2, y - 11, centerX + 3, y - 8, core);
		} else {
			int inset = Math.max(12, Math.min(28, width / 18));
			int halfHeight = 20;
			graphics.fill(inset, centerY - halfHeight, inset + 2, centerY + halfHeight, color);
			graphics.fill(width - inset - 2, centerY - halfHeight, width - inset, centerY + halfHeight, color);
			graphics.fill(inset + 3, centerY - 7, inset + 7, centerY + 8, core);
			graphics.fill(width - inset - 7, centerY - 7, width - inset - 3, centerY + 8, core);
			drawParryShield(graphics, centerX, centerY - 18, color, core);
		}
		if (kind == SnakeCombatFeedbackPayload.MID_CUE) {
			drawTeachingCard(graphics, centerX, centerY + 30, intensity, kind, rgb);
		}
	}

	private static void drawParryShield(GuiGraphics graphics, int centerX, int centerY,
			int border, int spark) {
		int panel = 0xD4080B11;
		graphics.fill(centerX - 7, centerY - 6, centerX + 8, centerY - 4, border);
		graphics.fill(centerX - 7, centerY - 4, centerX - 5, centerY + 1, border);
		graphics.fill(centerX + 6, centerY - 4, centerX + 8, centerY + 1, border);
		graphics.fill(centerX - 6, centerY + 1, centerX - 4, centerY + 4, border);
		graphics.fill(centerX + 5, centerY + 1, centerX + 7, centerY + 4, border);
		graphics.fill(centerX - 4, centerY + 4, centerX - 2, centerY + 6, border);
		graphics.fill(centerX + 3, centerY + 4, centerX + 5, centerY + 6, border);
		graphics.fill(centerX - 2, centerY + 6, centerX + 3, centerY + 8, border);
		graphics.fill(centerX - 5, centerY - 4, centerX + 6, centerY + 1, panel);
		graphics.fill(centerX - 4, centerY + 1, centerX + 5, centerY + 4, panel);
		graphics.fill(centerX - 2, centerY + 4, centerX + 3, centerY + 6, panel);
		graphics.fill(centerX - 1, centerY - 3, centerX + 2, centerY + 5, spark);
		graphics.fill(centerX - 4, centerY, centerX + 5, centerY + 2, spark);
	}

	private static void drawTeachingCard(GuiGraphics graphics, int centerX, int top,
			float intensity, int kind, int rgb) {
		Minecraft minecraft = Minecraft.getInstance();
		String suffix = kind == SnakeCombatFeedbackPayload.HIGH_CUE ? "high"
				: kind == SnakeCombatFeedbackPayload.LOW_CUE ? "low" : "mid";
		Component direction = Component.translatable(
				"message.virtual_craft.snake_king.cue." + suffix + ".direction");
		Component action = Component.translatable(
				"message.virtual_craft.snake_king.cue." + suffix + ".action");
		Component hint = Component.translatable(
				"message.virtual_craft.snake_king.cue." + suffix + ".hint");
		int cardWidth = Math.min(204, graphics.guiWidth() - 16);
		int left = centerX - cardWidth / 2;
		int right = centerX + cardWidth / 2;
		int alpha = Math.round(232.0F * Math.min(1.0F, intensity * 1.7F));
		int panel = Math.round(190.0F * Math.min(1.0F, intensity * 1.7F)) << 24 | 0x080B11;
		int border = alpha << 24 | rgb;
		int pale = alpha << 24 | 0xF7FAF8;
		int muted = Math.round(alpha * 0.78F) << 24 | 0xC0C9CD;
		fillCutCornerPanel(graphics, left, top, right, top + 37, panel, border, border);

		int tagWidth = Math.max(34, minecraft.font.width(direction) + 12);
		graphics.fill(left + 7, top + 7, left + 7 + tagWidth, top + 19,
				Math.round(alpha * 0.34F) << 24 | rgb);
		graphics.fill(left + 7, top + 7, left + 9, top + 19, border);
		graphics.drawCenteredString(minecraft.font, direction,
				left + 7 + tagWidth / 2, top + 9, pale);
		graphics.drawString(minecraft.font, action, left + 15 + tagWidth, top + 9, pale, false);
		graphics.drawCenteredString(minecraft.font, hint, centerX, top + 23, muted);
	}

	private static void fillCutCornerPanel(GuiGraphics graphics, int left, int top,
			int right, int bottom, int panel, int border, int accent) {
		graphics.fill(left + 2, top, right - 2, bottom, panel);
		graphics.fill(left, top + 2, right, bottom - 2, panel);
		graphics.fill(left + 3, top, right - 3, top + 1, border);
		graphics.fill(left + 3, bottom - 1, right - 3, bottom, border);
		graphics.fill(left, top + 3, left + 1, bottom - 3, border);
		graphics.fill(right - 1, top + 3, right, bottom - 3, border);
		graphics.fill(left + 1, top + 1, left + 3, top + 2, border);
		graphics.fill(right - 3, top + 1, right - 1, top + 2, border);
		graphics.fill(left + 1, bottom - 2, left + 3, bottom - 1, border);
		graphics.fill(right - 3, bottom - 2, right - 1, bottom - 1, border);
		graphics.fill(left + 1, top + 4, left + 3, bottom - 4, accent);
	}
}
