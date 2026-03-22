package dev.tatami.virtualcraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.menu.CurrencyGeneratorMenu;
import dev.tatami.virtualcraft.menu.ElectricBallGeneratorMenu;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import dev.tatami.virtualcraft.menu.ElectricCondenserSideConfigMenu;
import dev.tatami.virtualcraft.menu.HolographicShopMenu;
import dev.tatami.virtualcraft.menu.PowMinerMenu;
import dev.tatami.virtualcraft.menu.WirelessPowerMenu;

public final class ModMenuTypes {
	private ModMenuTypes() {
	}

	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, VirtualCraft.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<ElectricCondenserMenu>> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser",
			() -> IMenuTypeExtension.create(ElectricCondenserMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<ElectricBallGeneratorMenu>> ELECTRIC_BALL_GENERATOR = REGISTRY.register("electric_ball_generator",
			() -> IMenuTypeExtension.create(ElectricBallGeneratorMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<ElectricCondenserSideConfigMenu>> ELECTRIC_CONDENSER_SIDE_CONFIG = REGISTRY.register("electric_condenser_side_config",
			() -> IMenuTypeExtension.create(ElectricCondenserSideConfigMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<HolographicShopMenu>> HOLOGRAPHIC_SHOP = REGISTRY.register("holographic_shop",
			() -> IMenuTypeExtension.create(HolographicShopMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CurrencyGeneratorMenu>> CURRENCY_GENERATOR = REGISTRY.register("currency_generator",
			() -> IMenuTypeExtension.create(CurrencyGeneratorMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<PowMinerMenu>> POW_MINER = REGISTRY.register("pow_miner",
			() -> IMenuTypeExtension.create(PowMinerMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<WirelessPowerMenu>> WIRELESS_POWER = REGISTRY.register("wireless_power",
			() -> IMenuTypeExtension.create(WirelessPowerMenu::new));
}


