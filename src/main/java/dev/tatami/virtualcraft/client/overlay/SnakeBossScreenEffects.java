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
		if (snake.getSummonTicks() > 0 && snake.getSummonTicks() <= 7) {
			summonIntensity = 1.0F - snake.getSummonTicks() / 7.0F;
		}
		if (summonIntensity > 0.0F) {
			drawSummonPulse(event.getGuiGraphics(), Mth.clamp(summonIntensity, 0.0F, 1.0F));
		}
		float combatIntensity = SnakeBossCombatFeedback.flashIntensity();
		if (combatIntensity > 0.0F) {
			drawCombatPulse(event.getGuiGraphics(), Mth.clamp(combatIntensity, 0.0F, 1.0F),
					SnakeBossCombatFeedback.feedbackKind(), SnakeBossCombatFeedback.lateralDirection());
		}
		float tutorialIntensity = SnakeBossCombatFeedback.comboTutorialIntensity();
		if (tutorialIntensity > 0.0F && snake.getAttackState() == VirtualSnakeKing.ATTACK_DOUBLE_HUNT) {
			drawComboTutorial(event.getGuiGraphics(), snake, tutorialIntensity,
					SnakeBossCombatFeedback.comboTutorialKind());
		}
	}

	private static void drawComboTutorial(GuiGraphics graphics, VirtualSnakeKing snake,
			float intensity, int kind) {
		Minecraft minecraft = Minecraft.getInstance();
		int centerX = graphics.guiWidth() / 2;
		int top = Math.min(graphics.guiHeight() - 74, graphics.guiHeight() / 2 + 54);
		int alpha = Math.round(218.0F * intensity);
		int panel = Math.round(154.0F * intensity) << 24 | 0x090C12;
		int border = alpha << 24 | 0xC9E8F2;
		int width = 246;
		graphics.fill(centerX - width / 2, top, centerX + width / 2, top + 48, panel);
		graphics.fill(centerX - width / 2, top, centerX + width / 2, top + 1, border);
		graphics.fill(centerX - width / 2, top + 47, centerX + width / 2, top + 48, border);

		String suffix = kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_BITE_CUE ? "bbb"
				: kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE ? "bbt" : "btb";
		int text = alpha << 24 | 0xF4F7F8;
		int muted = Math.round(alpha * 0.82F) << 24 | 0xB9C8CE;
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
		int[] offsets = kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_BITE_CUE
				? new int[]{-28, 0, 28} : kind == SnakeCombatFeedbackPayload.COMBO_BITE_BITE_TAIL_CUE
				? new int[]{-32, -12, 30} : new int[]{-32, 10, 32};
		for (int i = 0; i < 3; i++) {
			int x = centerX + offsets[i];
			int rgb = tail[i] ? 0xE7A93A : 0xF8F2D0;
			int pipAlpha = i == segment ? alpha : Math.round(alpha * 0.42F);
			int color = pipAlpha << 24 | rgb;
			int size = i == segment ? 4 : 3;
			graphics.fill(x - size, top + 36, x + size + 1, top + 38, color);
			graphics.fill(x - 1, top + 33, x + 2, top + 41, color);
			if (tail[i]) {
				graphics.fill(x - 7, top + 40, x + 8, top + 42, color);
			}
		}
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
			case SnakeCombatFeedbackPayload.HIGH_DODGE -> 0x72D8FF;
			case SnakeCombatFeedbackPayload.LOW_DODGE -> 0xFF5A45;
			case SnakeCombatFeedbackPayload.SHIELD_BREAK -> 0xC57B56;
			case SnakeCombatFeedbackPayload.POSTURE_BREAK -> 0xE7C65A;
			default -> 0xF2D2B7;
		};
		int alpha = Math.round((kind == SnakeCombatFeedbackPayload.PARRY ? 190.0F : 122.0F) * intensity);
		int color = alpha << 24 | rgb;
		int pale = Math.round(alpha * 0.68F) << 24 | 0xFFFFFF;
		int radius = Math.max(5, Math.round(15.0F * intensity));
		int thickness = kind == SnakeCombatFeedbackPayload.PARRY ? 2 : 1;

		graphics.fill(centerX - radius, centerY - thickness, centerX - 3, centerY + thickness, color);
		graphics.fill(centerX + 3, centerY - thickness, centerX + radius, centerY + thickness, color);
		graphics.fill(centerX - thickness, centerY - radius, centerX + thickness, centerY - 3, color);
		graphics.fill(centerX - thickness, centerY + 3, centerX + thickness, centerY + radius, color);
		graphics.fill(centerX - radius / 2, centerY - radius / 2 - 1,
				centerX + radius / 2, centerY - radius / 2, pale);
		graphics.fill(centerX - radius / 2, centerY + radius / 2,
				centerX + radius / 2, centerY + radius / 2 + 1, pale);

		if (kind == SnakeCombatFeedbackPayload.PARRY
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
				: kind == SnakeCombatFeedbackPayload.LOW_CUE ? 0xFF4438 : 0xFFF2C2;
		int alpha = Math.round(210.0F * Math.min(1.0F, intensity * 1.45F));
		int color = alpha << 24 | rgb;
		int core = Math.min(255, alpha + 35) << 24 | 0xFFFFFF;
		int spread = 12 + Math.round((1.0F - intensity) * 18.0F);
		if (kind == SnakeCombatFeedbackPayload.HIGH_CUE) {
			graphics.fill(centerX - spread, centerY - 15, centerX + spread, centerY - 12, color);
			graphics.fill(centerX - 8, centerY - 8, centerX + 9, centerY - 5, core);
			graphics.fill(centerX - 5, centerY - 5, centerX + 6, centerY - 2, core);
			graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 2, core);
		} else if (kind == SnakeCombatFeedbackPayload.LOW_CUE) {
			graphics.fill(centerX - spread, centerY + 12, centerX + spread, centerY + 15, color);
			graphics.fill(centerX - 2, centerY - 3, centerX + 3, centerY + 8, core);
			graphics.fill(centerX - 5, centerY - 6, centerX + 6, centerY - 3, core);
			graphics.fill(centerX - 8, centerY - 9, centerX + 9, centerY - 6, core);
		} else {
			graphics.fill(centerX - spread, centerY - 10, centerX - spread + 3, centerY + 11, color);
			graphics.fill(centerX + spread - 3, centerY - 10, centerX + spread, centerY + 11, color);
			graphics.fill(centerX - spread, centerY - 10, centerX - spread + 9, centerY - 7, color);
			graphics.fill(centerX + spread - 9, centerY - 10, centerX + spread, centerY - 7, color);
			graphics.fill(centerX - 1, centerY - 7, centerX + 2, centerY + 8, core);
		}
	}
}
