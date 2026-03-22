package dev.tatami.virtualcraft.shop;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;

public final class ShopCatalogLoader {
	private static final ResourceLocation DEFAULT_CATALOG = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "shop_catalog/default.json");

	private ShopCatalogLoader() {
	}

	public static List<ShopOffer> load(MinecraftServer server) {
		List<ShopOffer> offers = new ArrayList<>();
		Optional<Resource> resource = server.getResourceManager().getResource(DEFAULT_CATALOG);
		if (resource.isEmpty()) {
			return offers;
		}

		try (Reader reader = resource.get().openAsReader()) {
			JsonObject root = GsonHelper.parse(reader);
			JsonArray entries = GsonHelper.getAsJsonArray(root, "offers", new JsonArray());
			for (int index = 0; index < entries.size(); index++) {
				if (!(entries.get(index).isJsonObject())) {
					continue;
				}
				JsonObject json = entries.get(index).getAsJsonObject();
				OptionalLong priceUsdt = readUsdtMinor(json, "priceUsdt", "buyPrice");
				OptionalLong sellUsdt = readUsdtMinor(json, "sellUsdt", "sellPrice");
				Optional<ShopOffer> offer = ShopOffer.create(
						GsonHelper.getAsString(json, "id", "offer_" + index),
						GsonHelper.getAsString(json, "item"),
						GsonHelper.getAsInt(json, "count", 1),
						priceUsdt.orElse(0L),
						sellUsdt.orElse(0L),
						GsonHelper.getAsBoolean(json, "enabled", true));
				offer.ifPresent(offers::add);
			}
		} catch (Exception exception) {
			VirtualCraft.LOGGER.error("Failed to load shop catalog {}", DEFAULT_CATALOG, exception);
		}
		return offers;
	}

	private static OptionalLong readUsdtMinor(JsonObject json, String preferredKey, String legacyKey) {
		if (json.has(preferredKey)) {
			return ShopOffer.parseUsdtMinor(json.get(preferredKey).getAsString());
		}
		if (json.has(legacyKey)) {
			return OptionalLong.of(GsonHelper.getAsLong(json, legacyKey, 0L) * CryptoCurrency.USDT.wholeUnitMinor());
		}
		return OptionalLong.empty();
	}
}
