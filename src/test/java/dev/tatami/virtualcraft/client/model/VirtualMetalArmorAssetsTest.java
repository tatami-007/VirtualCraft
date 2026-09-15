package dev.tatami.virtualcraft.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class VirtualMetalArmorAssetsTest {
	private static final String[] STYLES = {"iron", "silver", "titanium", "chromium", "snake_king"};
	private static final int[] ENERGY_COLORS = {0x36BED1, 0x75AFFF, 0x76B5FF, 0x38D5A4, 0xE7CD8D};

	@Test
	void everySetHasVanillaArmorLayersAndExpectedIconSize() throws Exception {
		for (int styleIndex = 0; styleIndex < STYLES.length; styleIndex++) {
			String style = STYLES[styleIndex];
			for (int layer = 1; layer <= 2; layer++) {
				BufferedImage armor = readImage(
						"/assets/virtual_craft/textures/models/armor/virtual_" + style + "_layer_" + layer + ".png");
				assertEquals(64, armor.getWidth());
				assertEquals(32, armor.getHeight());
				assertTrue(containsRgb(armor, ENERGY_COLORS[styleIndex]),
						style + " layer " + layer + " must share the item energy color");
			}

			for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
				BufferedImage icon = readImage("/assets/virtual_craft/textures/item/equipment/virtual_"
						+ style + "/virtual_" + style + "_" + piece + ".png");
				assertEquals(16, icon.getWidth());
				assertEquals(16, icon.getHeight());
				assertTrue(containsRgb(icon, ENERGY_COLORS[styleIndex]),
						style + " " + piece + " must share the worn energy color");
			}

			BufferedImage sword = readImage("/assets/virtual_craft/textures/item/equipment/virtual_"
					+ style + "/virtual_" + style + "_sword.png");
			assertEquals(16, sword.getWidth());
			assertEquals(16, sword.getHeight());
			for (int y = 0; y < sword.getHeight(); y++) {
				for (int x = 0; x < sword.getWidth(); x++) {
					assertEquals(sword.getRGB(x, y),
							sword.getRGB(sword.getWidth() - 1 - y, sword.getHeight() - 1 - x),
							style + " sword is not symmetric at " + x + "," + y);
				}
			}
		}
	}

	private boolean containsRgb(BufferedImage image, int expectedRgb) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) == expectedRgb) {
					return true;
				}
			}
		}
		return false;
	}

	private BufferedImage readImage(String path) throws Exception {
		try (InputStream stream = getClass().getResourceAsStream(path)) {
			assertNotNull(stream, path);
			return ImageIO.read(stream);
		}
	}
}
