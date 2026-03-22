package dev.tatami.virtualcraft.shop;

import java.util.OptionalLong;

import java.util.Optional;

import dev.tatami.virtualcraft.currency.CryptoCurrency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record ShopOffer(String id, ResourceLocation itemId, int count, long priceUsdtMinor, long sellUsdtMinor, boolean enabled) {
	public Item item() {
		return BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
	}

	public ItemStack createStack(int multiplier) {
		return new ItemStack(item(), count * multiplier);
	}

	public boolean isValidItem() {
		return item() != Items.AIR;
	}

	public Component displayName() {
		return createStack(1).getHoverName();
	}

	public static Optional<ShopOffer> create(String id, String itemId, int count, long priceUsdtMinor, long sellUsdtMinor, boolean enabled) {
		ResourceLocation location = ResourceLocation.tryParse(itemId);
		if (location == null || count <= 0) {
			return Optional.empty();
		}
		ShopOffer offer = new ShopOffer(id, location, count, Math.max(0L, priceUsdtMinor), Math.max(0L, sellUsdtMinor), enabled);
		return offer.isValidItem() ? Optional.of(offer) : Optional.empty();
	}

	public static OptionalLong parseUsdtMinor(String value) {
		return CryptoCurrency.USDT.parse(value);
	}
}
