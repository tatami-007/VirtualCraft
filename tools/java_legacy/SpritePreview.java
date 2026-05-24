import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 把 widgets.png 里 (248, 0, 8, 64) 的 MC-pixel-perfect 垂直 fill sprite
 * 裁剪下来并 10 倍放大（80×640），方便和 tools/mc_pixel_perfect.png (160×640) 并排对比。
 */
public class SpritePreview {
	public static void main(String[] args) throws Exception {
		BufferedImage src = ImageIO.read(new File("src/main/resources/assets/virtual_craft/textures/gui/widgets.png"));
		// v7：sprite 尺寸 16×64，位置 x=240..255
		int cropW = 16, cropH = 64;
		int spriteSrcX = 240;
		int scale = 10;
		int destW = cropW * scale;
		int destH = cropH * scale;
		BufferedImage out = new BufferedImage(destW, destH, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < cropH; y++) {
			for (int x = 0; x < cropW; x++) {
				int rgb = src.getRGB(spriteSrcX + x, y);
				for (int sy = 0; sy < scale; sy++) {
					for (int sx = 0; sx < scale; sx++) {
						out.setRGB(x * scale + sx, y * scale + sy, rgb);
					}
				}
			}
		}
		File dest = new File("tools/mc_pixel_perfect_generated_preview.png");
		ImageIO.write(out, "png", dest);
		System.out.println("Wrote " + dest.getAbsolutePath() + " (" + destW + "x" + destH + ")");
	}
}
