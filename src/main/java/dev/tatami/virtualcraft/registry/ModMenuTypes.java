package dev.tatami.virtualcraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.menu.ElectricBallGeneratorMenu;
import dev.tatami.virtualcraft.menu.ElectricCondenserMenu;
import dev.tatami.virtualcraft.menu.SideConfigMenu;
import dev.tatami.virtualcraft.menu.PowMinerMenu;
import dev.tatami.virtualcraft.menu.ComputeRackMenu;
import dev.tatami.virtualcraft.menu.LightningResonatorMenu;
import dev.tatami.virtualcraft.menu.QuantumSimulationChamberMenu;

public final class ModMenuTypes {
	private ModMenuTypes() {
	}

	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, VirtualCraft.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<ElectricCondenserMenu>> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser",
			() -> IMenuTypeExtension.create(ElectricCondenserMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<ElectricBallGeneratorMenu>> ELECTRIC_BALL_GENERATOR = REGISTRY.register("electric_ball_generator",
			() -> IMenuTypeExtension.create(ElectricBallGeneratorMenu::new));
	/**
	 * v15 通用面配置 MenuType — 所有 implements {@code IConfigurableSidedMachine} 的 BE 共用。
	 * 替代了 v14 之前的 ELECTRIC_CONDENSER_SIDE_CONFIG。未来新机器接入时无需新增 MenuType。
	 */
	public static final DeferredHolder<MenuType<?>, MenuType<SideConfigMenu>> SIDE_CONFIG = REGISTRY.register("side_config",
			() -> IMenuTypeExtension.create(SideConfigMenu::new));
	// HOLOGRAPHIC_SHOP MenuType 已被移除：HolographicShop GUI 不再走 menu 系统。
	// 客户端通过 OpenHolographicTerminalPayload + MuiModApi.openScreen 打开 ModernUI SimpleScreen
	// （非 AbstractContainerScreen），从根本上避免 JEI 误识别为容器 GUI 而画 Ingredient Overlay。
	// 服务端 sessionId 由 HolographicTerminalSession 管理，替代旧的 MenuType.containerId。
	public static final DeferredHolder<MenuType<?>, MenuType<PowMinerMenu>> POW_MINER = REGISTRY.register("pow_miner",
			() -> IMenuTypeExtension.create(PowMinerMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<ComputeRackMenu>> COMPUTE_RACK =
			REGISTRY.register("compute_rack", () -> IMenuTypeExtension.create(ComputeRackMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<LightningResonatorMenu>> LIGHTNING_RESONATOR =
			REGISTRY.register("lightning_resonator", () -> IMenuTypeExtension.create(LightningResonatorMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<QuantumSimulationChamberMenu>> QUANTUM_SIMULATION_CHAMBER =
			REGISTRY.register("quantum_simulation_chamber",
					() -> IMenuTypeExtension.create(QuantumSimulationChamberMenu::new));
	// WirelessPowerTower 重做后无 GUI — LinkCard 直接 toggleBinding 完成所有交互
}

