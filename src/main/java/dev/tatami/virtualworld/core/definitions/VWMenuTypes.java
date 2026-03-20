package dev.tatami.virtualworld.core.definitions;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.menu.ElectricCondenserMenu;
import dev.tatami.virtualworld.menu.ElectricCondenserSideConfigMenu;

public final class VWMenuTypes {
	private VWMenuTypes() {
	}

	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, VirtualWorld.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<ElectricCondenserMenu>> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser",
			() -> IMenuTypeExtension.create(ElectricCondenserMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<ElectricCondenserSideConfigMenu>> ELECTRIC_CONDENSER_SIDE_CONFIG = REGISTRY.register("electric_condenser_side_config",
			() -> IMenuTypeExtension.create(ElectricCondenserSideConfigMenu::new));
}

