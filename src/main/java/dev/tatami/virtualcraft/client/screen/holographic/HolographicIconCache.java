package dev.tatami.virtualcraft.client.screen.holographic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Caches decoded GPU images for currency and item icons so that the
 * Holographic Terminal can re-use them during frequent UI rebuilds without
 * paying the cost of re-reading the resource pack every time.
 *
 * <p>The immutable {@link Image} is shared while every call receives its own
 * {@link ImageDrawable}, because bounds are mutable per view. This avoids the
 * previous hot path where cached PNG bytes were still decoded and uploaded to
 * the GPU every time a page rebuilt.</p>
 */
public final class HolographicIconCache {
	private static final ConcurrentMap<ResourceLocation, Optional<Image>> TEXTURE_IMAGES = new ConcurrentHashMap<>();
	private static final ConcurrentMap<ResourceLocation, Optional<AnimationSpec>> ANIMATION_SPECS =
			new ConcurrentHashMap<>();
	private static final ResourceLocation[] PREWARM_TEXTURES = createPrewarmTextures();
	private static volatile int prewarmIndex = PREWARM_TEXTURES.length;

	private HolographicIconCache() {
	}

	public static ImageDrawable currencyIcon(CryptoCurrency currency, int pxSize) {
		return load(currency.iconTexture(), pxSize);
	}

	public static ImageDrawable itemIcon(ResourceLocation itemId, int pxSize) {
		ImageDrawable item = resolveItemModelTexture(itemId)
				.map(texture -> load(texture, pxSize))
				.orElse(null);
		if (item == null) {
			item = load(itemTexture(itemId), pxSize);
		}
		if (item != null) {
			return item;
		}
		String path = itemId.getPath();
		for (String suffix : new String[] { "", "_side", "_front", "_top" }) {
			ResourceLocation blockTexture = ResourceLocation.fromNamespaceAndPath(
					itemId.getNamespace(),
					"textures/block/" + path + suffix + ".png");
			ImageDrawable block = load(blockTexture, pxSize);
			if (block != null) {
				return block;
			}
		}
		return null;
	}

	private static Optional<ResourceLocation> resolveItemModelTexture(ResourceLocation itemId) {
		ResourceLocation model = ResourceLocation.fromNamespaceAndPath(
				itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json");
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(model);
			if (resource.isEmpty()) {
				return Optional.empty();
			}
			try (var stream = resource.get().open();
					var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonObject textures = root.getAsJsonObject("textures");
				if (textures == null || !textures.has("layer0")) {
					return Optional.empty();
				}
				ResourceLocation layer = ResourceLocation.parse(textures.get("layer0").getAsString());
				return Optional.of(ResourceLocation.fromNamespaceAndPath(
						layer.getNamespace(), "textures/" + layer.getPath() + ".png"));
			}
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	public static void invalidate() {
		// Do not close shared images here: an already-open screen may still own
		// drawables referencing them during F3+T. Clearing the cache lets those
		// views keep rendering safely; Image's Cleaner releases the texture after
		// the old view tree is collected.
		TEXTURE_IMAGES.clear();
		ANIMATION_SPECS.clear();
		prewarmIndex = PREWARM_TEXTURES.length;
	}

	/**
	 * Starts a staggered render-thread prewarm. One icon is decoded and uploaded
	 * per client tick, avoiding both login spikes and first-open spikes.
	 */
	public static void scheduleCurrencyPrewarm() {
		prewarmIndex = 0;
	}

	public static void tickCurrencyPrewarm() {
		int index = prewarmIndex;
		if (index < 0 || index >= PREWARM_TEXTURES.length) {
			return;
		}
		TEXTURE_IMAGES.computeIfAbsent(PREWARM_TEXTURES[index], HolographicIconCache::decodeImage);
		prewarmIndex = index + 1;
	}

	@SuppressWarnings("deprecation")
	private static ImageDrawable load(ResourceLocation texture, int pxSize) {
		Optional<Image> image = TEXTURE_IMAGES.computeIfAbsent(texture, HolographicIconCache::decodeImage);
		if (image.isEmpty() || image.get().isClosed()) {
			return null;
		}
		try {
			ImageDrawable drawable = ANIMATION_SPECS
					.computeIfAbsent(texture, key -> decodeAnimationSpec(key, image.get()))
					.<ImageDrawable>map(spec -> new AnimatedSpriteDrawable(image.get(), spec))
					.orElseGet(() -> new ImageDrawable(image.get()));
			// nearest-neighbor 采样：与 vanilla 物品栏的像素艺术渲染风格一致——
			// 16x16 物品贴图保留清晰锐利的像素边缘，绝不出现 bilinear 双线性
			// 插值带来的"糊"。代价是非整数倍缩放（如 16→20）时像素行宽略不均，
			// 但视觉上仍属"原版物品风格"，比 setFilter(true) 的"晕开"观感好得多。
			drawable.setFilter(false);
			drawable.setBounds(0, 0, pxSize, pxSize);
			return drawable;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Optional<AnimationSpec> decodeAnimationSpec(ResourceLocation texture, Image image) {
		if (image.getWidth() <= 0 || image.getHeight() <= image.getWidth()
				|| image.getHeight() % image.getWidth() != 0) {
			return Optional.empty();
		}
		ResourceLocation metadata = ResourceLocation.fromNamespaceAndPath(
				texture.getNamespace(), texture.getPath() + ".mcmeta");
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(metadata);
			if (resource.isEmpty()) {
				return Optional.empty();
			}
			try (var stream = resource.get().open();
					var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonObject animation = root.getAsJsonObject("animation");
				if (animation == null) {
					return Optional.empty();
				}
				int frameCount = image.getHeight() / image.getWidth();
				int defaultTime = Math.max(1, getInt(animation, "frametime", 1));
				List<Integer> frames = new ArrayList<>();
				List<Integer> times = new ArrayList<>();
				JsonArray configuredFrames = animation.getAsJsonArray("frames");
				if (configuredFrames == null || configuredFrames.isEmpty()) {
					for (int frame = 0; frame < frameCount; frame++) {
						frames.add(frame);
						times.add(defaultTime);
					}
				} else {
					for (JsonElement element : configuredFrames) {
						int frame;
						int time = defaultTime;
						if (element.isJsonObject()) {
							JsonObject entry = element.getAsJsonObject();
							frame = getInt(entry, "index", -1);
							time = Math.max(1, getInt(entry, "time", defaultTime));
						} else {
							frame = element.getAsInt();
						}
						if (frame >= 0 && frame < frameCount) {
							frames.add(frame);
							times.add(time);
						}
					}
				}
				if (frames.isEmpty()) {
					return Optional.empty();
				}
				return Optional.of(new AnimationSpec(image.getWidth(),
						frames.stream().mapToInt(Integer::intValue).toArray(),
						times.stream().mapToInt(Integer::intValue).toArray()));
			}
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
				? element.getAsInt() : fallback;
	}

	@SuppressWarnings("deprecation")
	private static Optional<Image> decodeImage(ResourceLocation texture) {
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
			if (resource.isEmpty()) {
				return Optional.empty();
			}
			try (var stream = resource.get().open()) {
				Image image = new ImageDrawable(stream).getImage();
				return Optional.ofNullable(image);
			}
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}

	private static ResourceLocation itemTexture(ResourceLocation itemId) {
		return ResourceLocation.fromNamespaceAndPath(
				itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
	}

	private static ResourceLocation[] createPrewarmTextures() {
		List<ResourceLocation> textures = new ArrayList<>();
		for (CryptoCurrency currency : CryptoCurrency.values()) {
			textures.add(currency.iconTexture());
		}
		for (String item : new String[]{
				"minecraft:emerald", "minecraft:spyglass", "minecraft:ender_pearl",
				"minecraft:writable_book", "minecraft:paper", "minecraft:gold_ingot",
				"virtual_craft:encrypted_hash_voucher"}) {
			textures.add(itemTexture(ResourceLocation.parse(item)));
		}
		return textures.stream().distinct().toArray(ResourceLocation[]::new);
	}

	private record AnimationSpec(int frameSize, int[] frames, int[] frameTimes) {
		private int totalTicks() {
			int total = 0;
			for (int time : frameTimes) {
				total += time;
			}
			return Math.max(1, total);
		}

		private int frameAt(long animationTick) {
			int cursor = (int)Math.floorMod(animationTick, totalTicks());
			for (int index = 0; index < frames.length; index++) {
				cursor -= frameTimes[index];
				if (cursor < 0) {
					return frames[index];
				}
			}
			return frames[frames.length - 1];
		}
	}

	private static final class AnimatedSpriteDrawable extends ImageDrawable {
		private static final long TICK_NANOS = 50_000_000L;
		private final AnimationSpec spec;
		private final long startedAt = System.nanoTime();
		private final Runnable requestNextFrame = this::invalidateSelf;

		private AnimatedSpriteDrawable(Image image, AnimationSpec spec) {
			super(image);
			this.spec = spec;
			updateSourceFrame(0L);
		}

		@Override
		public void draw(Canvas canvas) {
			long animationTick = (System.nanoTime() - startedAt) / TICK_NANOS;
			updateSourceFrame(animationTick);
			super.draw(canvas);
			unscheduleSelf(requestNextFrame);
			scheduleSelf(requestNextFrame, Core.timeMillis() + 50L);
		}

		@Override
		public boolean setVisible(boolean visible, boolean restart) {
			boolean changed = super.setVisible(visible, restart);
			if (!visible) {
				unscheduleSelf(requestNextFrame);
			} else if (restart || changed) {
				invalidateSelf();
			}
			return changed;
		}

		@Override
		public int getIntrinsicWidth() {
			return spec.frameSize();
		}

		@Override
		public int getIntrinsicHeight() {
			return spec.frameSize();
		}

		private void updateSourceFrame(long animationTick) {
			int top = spec.frameAt(animationTick) * spec.frameSize();
			setSrcRect(0, top, spec.frameSize(), top + spec.frameSize());
		}
	}

	/**
	 * 注册资源重载监听器：F3+T 重新加载客户端资源时自动 invalidate 本 cache。
	 * 否则开发期改了 PNG，玩家在游戏内按 F3+T 不会看到新贴图——cache 仍是旧字节。
	 *
	 * <p>{@link RegisterClientReloadListenersEvent} 在 mod bus 上触发；这个内部类用
	 * {@link EventBusSubscriber} 自动订阅，{@link ResourceManagerReloadListener#onResourceManagerReload}
	 * 在每次 {@code Minecraft.reloadResourcePacks()} 时被回调。</p>
	 */
	@EventBusSubscriber(modid = VirtualCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static final class ReloadHook {
		private ReloadHook() {
		}

		@SubscribeEvent
		public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
			event.registerReloadListener((ResourceManagerReloadListener) rm -> HolographicIconCache.invalidate());
		}
	}
}
