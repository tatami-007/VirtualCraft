package dev.tatami.virtualcraft.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;

/**
 * <b>v15 通用面配置接口</b>：任何带"6 面 I/O 模式 + 自动进出"配置的 BlockEntity 实现此接口，
 * 即可自动接入通用 {@code SideConfigMenu / SideConfigScreen}。
 *
 * <h3>设计目标（参考 AE2 Crystal Science）</h3>
 * <p>所有机器共用同一个 SideConfigScreen 与 side_config_menu.png 白板贴图。
 * 机器只需实现本接口提供 6 面状态/切换/主菜单工厂，无需为每个机器单独写 Menu/Screen 类。</p>
 *
 * <h3>新机器接入步骤</h3>
 * <ol>
 *   <li>BlockEntity {@code implements IConfigurableSidedMachine}</li>
 *   <li>实现 6 个 abstract 方法（getSideMode / cycleSideMode / toggleAutoImport/Export / getSideConfigData / getMachineFacing / getMachineBlock / createMainMenuProvider）</li>
 *   <li>主菜单加 BACK_TO_SIDE_CONFIG button（id 通常用 200）触发 {@code openSideConfig(serverPlayer, this)}</li>
 *   <li>注册：无需新增 MenuType（共用 {@link dev.tatami.virtualcraft.registry.ModMenuTypes#SIDE_CONFIG}）</li>
 * </ol>
 *
 * <h3>ContainerData 协议（8 个 int）</h3>
 * <ul>
 *   <li>index 0..5 — 6 面 SideMode ordinal（顺序 UP/DOWN/NORTH/SOUTH/WEST/EAST）</li>
 *   <li>index 6  — autoImport 开关（0/1）</li>
 *   <li>index 7  — autoExport 开关（0/1）</li>
 * </ul>
 */
public interface IConfigurableSidedMachine {

	/** 取某面的当前 SideMode（NONE/INPUT/OUTPUT/BOTH）。 */
	SideMode getSideMode(Direction direction);

	/** 把某面的 SideMode 推进到下一档（用于 SideFaceButton 点击切换）。 */
	void cycleSideMode(Direction direction);

	/** 切换 autoImport 开关（"全部面 INPUT" 工具按钮回调）。 */
	void toggleAutoImport();

	/** 切换 autoExport 开关（"全部面 OUTPUT" 工具按钮回调）。 */
	void toggleAutoExport();

	/**
	 * 清空 6 面 SideMode 配置：所有面重置为 NONE（禁用），同时关闭 autoImport / autoExport。
	 *
	 * <p>v16 起对应 SideConfigScreen 右侧"Clear All"工具按钮（X 红图标）。
	 * 默认实现：循环 6 个 Direction，反复 cycleSideMode 直到当前 mode 为 NONE。
	 * 性能不重要（玩家偶发点击），子类如需更高效可 override。</p>
	 */
	default void clearAllSides() {
		for (Direction direction : Direction.values()) {
			// 反复 cycleSideMode 直到当前 mode 落到 NONE（最多 SideMode.values().length 次）
			int safety = SideMode.values().length;
			while (getSideMode(direction) != SideMode.NONE && safety-- > 0) {
				cycleSideMode(direction);
			}
		}
		// 注：autoImport/autoExport 状态保留 — 玩家偏好，不应被 Clear 统一干掉。
		// 如果机器需要 Clear 也清 auto toggle，可以 override 此默认实现。
	}

	/**
	 * 返回 SideConfig 同步用的 ContainerData（8 个 int）。
	 * 客户端通过此 data 读取 6 面 mode + autoImport/Export 状态进行渲染。
	 */
	ContainerData getSideConfigData();

	/**
	 * 返回机器的水平朝向（HorizontalDirectionalBlock.FACING property 取出来的值）。
	 * SideConfigMenu 用此 facing 计算 LEFT/RIGHT/FRONT/BACK 这 4 个相对面对应的世界 Direction。
	 */
	Direction getMachineFacing();

	/**
	 * 返回机器对应的 Block（用于 stillValid 验证 BE 仍在原位置且未被替换为其他机器）。
	 */
	Block getMachineBlock();

	/**
	 * 工厂方法：创建机器主菜单的 MenuProvider，供 SideConfigMenu 的 BACK 按钮把玩家送回主菜单。
	 *
	 * <p>典型实现：
	 * <pre>
	 * return new SimpleMenuProvider(
	 *     (containerId, inv, ignored) -&gt; new XxxMenu(containerId, inv, this, this.getData()),
	 *     Component.translatable("block.virtual_craft.xxx"));
	 * </pre>
	 */
	MenuProvider createMainMenuProvider();
}
