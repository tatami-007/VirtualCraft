package dev.tatami.virtualcraft.client.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MushroomKingAssetsTest {
	private static final String[] ATTACK_ANIMATIONS = {
			"attack_mid_root_cadence",
			"attack_mid_stem_hammer",
			"attack_mid_spore_needle_volley",
			"attack_mid_cap_clap",
			"attack_high_canopy_closure",
			"attack_high_spore_breath",
			"attack_low_mycelial_surge",
			"attack_low_root_convergence"
	};
	private static final String[] COMBO_STEP_ANIMATIONS = {
			"combo_mid_step_left",
			"combo_mid_step_right",
			"combo_mid_step_clap",
			"combo_mid_step_slam",
			"combo_mid_step_drive",
			"combo_low_step_left",
			"combo_low_step_right",
			"combo_low_step_surge",
			"combo_high_step_finish",
			"combo_high_step_breath"
	};
	private static final String[] SOUNDS = {
			"summon", "roar", "windup", "strike", "root_cadence", "stem_hammer",
			"spore_needle", "cap_clap", "canopy_closure", "spore_breath", "mycelial_surge",
			"root_convergence", "plate_break", "stagger", "death", "shield_break"
	};

	@Test
	void everyCombatAnimationExistsInTheGeckoAsset() throws Exception {
		String json = readText("/assets/virtual_craft/animations/virtual_mushroom_king.animation.json");
		for (String animation : ATTACK_ANIMATIONS) {
			assertTrue(json.contains("\"animation.virtual_mushroom_king." + animation + "\""),
					animation);
		}
		for (String animation : COMBO_STEP_ANIMATIONS) {
			assertTrue(json.contains("\"animation.virtual_mushroom_king." + animation + "\""),
					animation);
		}
	}

	@Test
	void animationAssetDoesNotUseTrailingCommasRejectedByGeckoLib() throws Exception {
		String json = readText("/assets/virtual_craft/animations/virtual_mushroom_king.animation.json");
		assertTrue(!Pattern.compile(",\\s*[}\\]]").matcher(json).find(),
				"GeckoLib rejects trailing commas and silently drops the entire animation asset");
	}

	@Test
	void everyCombatAnimationCoversItsServerAttackTimeline() throws Exception {
		String json = readText("/assets/virtual_craft/animations/virtual_mushroom_king.animation.json");
		for (int attack = 1; attack <= MushroomKingAttackRules.ATTACK_COUNT; attack++) {
			String key = "animation.virtual_mushroom_king." + ATTACK_ANIMATIONS[attack - 1];
			Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
					+ "\"\\s*:\\s*\\{[^}]*?\"animation_length\"\\s*:\\s*([0-9.]+)",
					Pattern.DOTALL).matcher(json);
			assertTrue(matcher.find(), "Missing animation_length for " + key);
			float seconds = Float.parseFloat(matcher.group(1));
			float requiredSeconds = MushroomKingAttackRules.endTick(attack) / 20.0F;
			assertTrue(seconds >= requiredSeconds,
					key + " ends at " + seconds + "s, before server timeline " + requiredSeconds + "s");
		}
	}

	@Test
	void everyRegisteredMushroomSoundHasAnOggResource() throws Exception {
		String json = readText("/assets/virtual_craft/sounds.json");
		for (String sound : SOUNDS) {
			assertTrue(json.contains("\"mushroom_king." + sound + "\""), sound);
			String path = "/assets/virtual_craft/sounds/mushroom_king/" + sound + ".ogg";
			try (InputStream stream = getClass().getResourceAsStream(path)) {
				assertNotNull(stream, path);
				assertArrayEquals(new byte[]{'O', 'g', 'g', 'S'}, stream.readNBytes(4), path);
			}
		}
	}

	private String readText(String path) throws Exception {
		try (InputStream stream = getClass().getResourceAsStream(path)) {
			assertNotNull(stream, path);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
