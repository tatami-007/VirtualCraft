package dev.tatami.virtualcraft.client.overlay;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MushroomBossBarAssetsTest {
	@Test void atlasMatchesRendererAndNeverCoversHealthFill() throws Exception {
		try (var stream=getClass().getResourceAsStream("/assets/virtual_craft/textures/gui/boss/mushroom_king_bar.png")) {
			assertNotNull(stream);
			BufferedImage image=ImageIO.read(stream);
			assertEquals(256,image.getWidth());
			assertEquals(256,image.getHeight());
			for (int y=22;y<30;y++) for (int x=26;x<218;x++) {
				assertEquals(0,image.getRGB(x,y)>>>24,"Opaque decoration over health at "+x+","+y);
			}
			for (int row:new int[]{48,56,64}) for (int y=row;y<row+8;y++) for (int x=0;x<192;x++) {
				assertEquals(255,image.getRGB(x,y)>>>24,"Incomplete health strip");
			}
			for (int y=35;y<39;y++) for (int x=35;x<209;x++) {
				assertEquals(0,image.getRGB(x,y)>>>24,"Frame covers posture slot");
			}
			for (int row:new int[]{80,88,96,104}) for (int y=row;y<row+4;y++) for (int x=0;x<174;x++) {
				assertEquals(255,image.getRGB(x,y)>>>24,"Broken continuous posture strip");
			}
		}
	}
}
