package dev.tatami.virtualcraft.currency;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.annotation.Nullable;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public enum CryptoCurrency {
	BTC("btc", "crypto_btc", 64, "bitcoin", 6, false, 2400, 16_384),
	ETH("eth", "crypto_eth", 64, "ethereum", 6, false, 1800, 8_192),
	USDT("usdt", "crypto_usdt", 64, "tether", 2, true, 1200, 2_048),
	SOL("sol", "crypto_sol", 64, "solana", 6, false, 900, 4_096),
	DOGE("doge", "crypto_doge", 64, "dogecoin", 6, false, 300, 6_144);

	private static final EnumMap<CryptoCurrency, Long> MINOR_FACTORS = new EnumMap<>(CryptoCurrency.class);

	private final String id;
	private final String itemName;
	private final int stackLimit;
	private final String marketSymbol;
	private final int scale;
	private final boolean usdtPegged;
	private final int burnTimeTicks;
	private final int energyPerTick;
	private final ResourceLocation iconTexture;

	static {
		for (CryptoCurrency currency : values()) {
			long factor = 1L;
			for (int index = 0; index < currency.scale; index++) {
				factor *= 10L;
			}
			MINOR_FACTORS.put(currency, factor);
		}
	}

	CryptoCurrency(String id, String itemName, int stackLimit, String marketSymbol, int scale, boolean usdtPegged, int burnTimeTicks, int energyPerTick) {
		this.id = id;
		this.itemName = itemName;
		this.stackLimit = stackLimit;
		this.marketSymbol = marketSymbol;
		this.scale = scale;
		this.usdtPegged = usdtPegged;
		this.burnTimeTicks = burnTimeTicks;
		this.energyPerTick = energyPerTick;
		this.iconTexture = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "textures/item/" + itemName + ".png");
	}

	public String id() {
		return id;
	}

	public String itemName() {
		return itemName;
	}

	public String ticker() {
		return name();
	}

	public int stackLimit() {
		return stackLimit;
	}

	public String marketSymbol() {
		return marketSymbol;
	}

	public int scale() {
		return scale;
	}

	public boolean isUsdtPegged() {
		return usdtPegged;
	}

	public ResourceLocation iconTexture() {
		return iconTexture;
	}

	public long minorFactor() {
		return MINOR_FACTORS.get(this);
	}

	public long wholeUnitMinor() {
		return minorFactor();
	}

	public int burnTimeTicks() {
		return burnTimeTicks;
	}

	public int energyPerTick() {
		return energyPerTick;
	}

	public int totalEnergyPerItem() {
		return burnTimeTicks * energyPerTick;
	}

	public String translationKey() {
		return "item.virtual_craft." + itemName;
	}

	public Component displayName() {
		return Component.translatable(translationKey());
	}

	public Item item() {
		return switch (this) {
			case BTC -> ModItems.CRYPTO_BTC.get();
			case ETH -> ModItems.CRYPTO_ETH.get();
			case USDT -> ModItems.CRYPTO_USDT.get();
			case SOL -> ModItems.CRYPTO_SOL.get();
			case DOGE -> ModItems.CRYPTO_DOGE.get();
		};
	}

	public ItemStack stack(int count) {
		return new ItemStack(item(), count);
	}

	public BigDecimal toDecimal(long minorUnits) {
		return BigDecimal.valueOf(minorUnits, scale);
	}

	public long toMinorUnits(BigDecimal amount, RoundingMode roundingMode) {
		BigDecimal scaled = amount.max(BigDecimal.ZERO).setScale(scale, roundingMode);
		return scaled.unscaledValue().longValueExact();
	}

	public OptionalLong parse(String text) {
		if (text == null || text.isBlank()) {
			return OptionalLong.empty();
		}
		try {
			BigDecimal parsed = new BigDecimal(text.trim());
			if (parsed.signum() < 0) {
				return OptionalLong.empty();
			}
			BigDecimal stripped = parsed.stripTrailingZeros();
			int effectiveScale = Math.max(0, stripped.scale());
			if (effectiveScale > scale) {
				return OptionalLong.empty();
			}
			return OptionalLong.of(toMinorUnits(parsed, RoundingMode.DOWN));
		} catch (NumberFormatException exception) {
			return OptionalLong.empty();
		}
	}

	public String formatFixed(long minorUnits) {
		return toDecimal(minorUnits).setScale(scale, RoundingMode.DOWN).toPlainString();
	}

	public String formatCompact(long minorUnits) {
		BigDecimal amount = toDecimal(minorUnits).setScale(scale, RoundingMode.DOWN).stripTrailingZeros();
		if (amount.scale() < 0) {
			amount = amount.setScale(0, RoundingMode.DOWN);
		}
		return amount.toPlainString();
	}

	public boolean matches(ItemStack stack) {
		return stack.is(item());
	}

	public static Optional<CryptoCurrency> byId(String id) {
		return Arrays.stream(values())
				.filter(currency -> currency.id.equals(id.toLowerCase(Locale.ROOT)))
				.findFirst();
	}

	@Nullable
	public static CryptoCurrency byItem(ItemStack stack) {
		for (CryptoCurrency currency : values()) {
			if (currency.matches(stack)) {
				return currency;
			}
		}
		return null;
	}
}
