import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import javax.imageio.ImageIO;

/**
 * 像素采样器：读取 AE2 公模真实贴图 PNG 文件，
 * 输出每个区域的颜色直方图（按出现次数降序），
 * 用于校准 GuiTextureGenerator 的 Palette 常量。
 *
 * Usage:
 *   java -cp tools ColorSampler tools/refs/inscriber.png
 */
public class ColorSampler {

	public static void main(String[] args) throws Exception {
		for (String path : args) {
			BufferedImage img = ImageIO.read(new File(path));
			System.out.println("=== " + path + " (" + img.getWidth() + "x" + img.getHeight() + ") ===");
			Map<Integer, Integer> hist = new HashMap<>();
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int rgb = img.getRGB(x, y);
					if ((rgb >>> 24) < 128) continue; // 跳过透明
					int rgb24 = rgb & 0xFFFFFF;
					hist.merge(rgb24, 1, Integer::sum);
				}
			}
			// 按频次降序排序，输出前 30 个
			LinkedHashMap<Integer, Integer> sorted = new LinkedHashMap<>();
			hist.entrySet().stream()
				.sorted(Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getValue).reversed())
				.limit(30)
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
			int total = hist.values().stream().mapToInt(Integer::intValue).sum();
			System.out.printf("  Total non-transparent: %d, unique colors: %d%n", total, hist.size());
			System.out.println("  Top 30 by frequency:");
			for (Map.Entry<Integer, Integer> e : sorted.entrySet()) {
				int rgb = e.getKey();
				int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				double pct = 100.0 * e.getValue() / total;
				System.out.printf("    #%06X  rgb(%3d,%3d,%3d)  %6d px  %5.2f%%%n", rgb, r, g, b, e.getValue(), pct);
			}

			// 采样几个特定坐标（AE2 通用 176×166 公模常见位置）
			System.out.println("  Specific pixel samples:");
			String[][] samples = {
				{"1,1",     "outer-tl"},
				{"4,4",     "border-area"},
				{"8,8",     "inner-bg"},
				{"50,30",   "mid-bg"},
				{"80,30",   "near-arrow"},
				{"100,50",  "far-bg"},
				{"7,82",    "above-inv"},
				{"7,84",    "inv-frame-tl"},
				{"8,85",    "inv-slot-outer-tl"},
				{"9,86",    "inv-slot-fill"},
				{"15,85",   "inv-slot-bottom"},
			};
			for (String[] s : samples) {
				String[] xy = s[0].split(",");
				int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
				if (x >= img.getWidth() || y >= img.getHeight()) continue;
				int rgb = img.getRGB(x, y) & 0xFFFFFF;
				int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
				System.out.printf("    (%3d,%3d) [%s]  #%06X  rgb(%3d,%3d,%3d)%n", x, y, s[1], rgb, r, g, b);
			}
		}
	}
}
