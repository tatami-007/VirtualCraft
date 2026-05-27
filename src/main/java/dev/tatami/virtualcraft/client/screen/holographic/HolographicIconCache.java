package dev.tatami.virtualcraft.client.screen.holographic;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * Caches decoded PNG bytes for currency and item icons so that the
 * Holographic Terminal can re-use them during frequent UI rebuilds without
 * paying the cost of re-reading the resource pack every time.
 *
 * <p>We intentionally cache the raw bytes instead of the {@link ImageDrawable}
 * itself because {@code Drawable#setBounds} is per-instance state; sharing a
 * single drawable between multiple {@code ImageView}s would cause them to
 * fight over sizing. A new {@code ImageDrawable} is cheap once the PNG is
 * already decoded into memory.</p>
 */
public final class HolographicIconCache {
	private static final ConcurrentMap<ResourceLocation, Optional<byte[]>> TEXTURE_BYTES = new ConcurrentHashMap<>();
	private static volatile int currencyPrewarmIndex = CryptoCurrency.values().length;

	private HolographicIconCache() {
	}

	public static ImageDrawable currencyIcon(CryptoCurrency currency, int pxSize) {
		return load(currency.iconTexture(), pxSize);
	}

	public static ImageDrawable itemIcon(ResourceLocation itemId, int pxSize) {
		ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
				itemId.getNamespace(),
				"textures/item/" + itemId.getPath() + ".png");
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
		TEXTURE_BYTES.clear();
		currencyPrewarmIndex = CryptoCurrency.values().length;
	}

	/**
	 * Starts a tiny staggered icon prewarm instead of reading every currency
	 * PNG during the login event. This keeps world entry responsive while
	 * still making the terminal cheap to open a few ticks later.
	 *
	 * <p>Decoding the PNG into a {@code ImageDrawable} is intentionally left
	 * to the actual UI build pass: that step requires an OpenGL/render-thread
	 * context and depends on the dp() of the call site, while the byte array
	 * is identical regardless of size.</p>
	 */
	public static void scheduleCurrencyPrewarm() {
		currencyPrewarmIndex = 0;
	}

	public static void tickCurrencyPrewarm() {
		CryptoCurrency[] currencies = CryptoCurrency.values();
		int index = currencyPrewarmIndex;
		if (index < 0 || index >= currencies.length) {
			return;
		}
		TEXTURE_BYTES.computeIfAbsent(currencies[index].iconTexture(), HolographicIconCache::readBytes);
		currencyPrewarmIndex = index + 1;
	}

	@SuppressWarnings("deprecation")
	private static ImageDrawable load(ResourceLocation texture, int pxSize) {
		Optional<byte[]> bytes = TEXTURE_BYTES.computeIfAbsent(texture, HolographicIconCache::readBytes);
		if (bytes.isEmpty()) {
			return null;
		}
		try {
			ImageDrawable drawable = new ImageDrawable(new ByteArrayInputStream(bytes.get()));
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

	private static Optional<byte[]> readBytes(ResourceLocation texture) {
		try {
			var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
			if (resource.isEmpty()) {
				return Optional.empty();
			}
			try (var stream = resource.get().open()) {
				return Optional.of(stream.readAllBytes());
			}
		} catch (Exception ignored) {
			return Optional.empty();
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
