package dev.tatami.virtualcraft.client.screen.holographic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
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
	private static final ResourceLocation[] PREWARM_TEXTURES = createPrewarmTextures();
	private static volatile int prewarmIndex = PREWARM_TEXTURES.length;

	private HolographicIconCache() {
	}

	public static ImageDrawable currencyIcon(CryptoCurrency currency, int pxSize) {
		return load(currency.iconTexture(), pxSize);
	}

	public static ImageDrawable itemIcon(ResourceLocation itemId, int pxSize) {
		ResourceLocation texture = itemTexture(itemId);
		ImageDrawable item = load(texture, pxSize);
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

	public static void invalidate() {
		// Do not close shared images here: an already-open screen may still own
		// drawables referencing them during F3+T. Clearing the cache lets those
		// views keep rendering safely; Image's Cleaner releases the texture after
		// the old view tree is collected.
		TEXTURE_IMAGES.clear();
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
			ImageDrawable drawable = new ImageDrawable(image.get());
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
