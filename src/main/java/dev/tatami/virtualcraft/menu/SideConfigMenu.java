package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.machine.IConfigurableSidedMachine;
import dev.tatami.virtualcraft.machine.SideMode;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * <b>v15 通用面配置 Menu — 所有实现 {@link IConfigurableSidedMachine} 的机器共用。</b>
 *
 * <p>参考 AE2 Crystal Science 的 SideConfigMenu 设计：客户端只通过 BlockPos 找到 BE，
 * 通过 {@link IConfigurableSidedMachine} 接口操作 6 面 mode + autoImport/Export，
 * 主菜单回跳通过 {@link IConfigurableSidedMachine#createMainMenuProvider()} 即可。</p>
 *
 * <h3>按钮 ID 协议</h3>
 * <ul>
 *   <li>0..5    — 6 面循环切换（顺序 TOP/BOTTOM/LEFT/RIGHT/FRONT/BACK，相对机器朝向）</li>
 *   <li>100     — AUTO_IMPORT toggle</li>
 *   <li>101     — AUTO_EXPORT toggle</li>
 *   <li>102     — CLEAR_ALL（v16 — 6 面全部重置为 NONE）</li>
 *   <li>120     — BACK_TO_MAIN（返回机器主菜单）</li>
 * </ul>
 *
 * <h3>ContainerData 协议（8 项 — 由 BE 的 {@link IConfigurableSidedMachine#getSideConfigData()} 提供）</h3>
 * <ul>
 *   <li>0..5 — 6 面 SideMode ordinal（顺序 UP/DOWN/NORTH/SOUTH/WEST/EAST 世界方向）</li>
 *   <li>6    — autoImport 开关</li>
 *   <li>7    — autoExport 开关</li>
 * </ul>
 */
@SuppressWarnings("this-escape") // addSlot() / addDataSlots() in the constructor is the required AbstractContainerMenu pattern.
public class SideConfigMenu extends AbstractContainerMenu {
	public static final int TOP_BUTTON = 0;
	public static final int BOTTOM_BUTTON = 1;
	public static final int LEFT_BUTTON = 2;
	public static final int RIGHT_BUTTON = 3;
	public static final int FRONT_BUTTON = 4;
	public static final int BACK_BUTTON = 5;
	public static final int AUTO_IMPORT_BUTTON = 100;
	public static final int AUTO_EXPORT_BUTTON = 101;
	public static final int CLEAR_ALL_BUTTON = 102;
	public static final int BACK_TO_MAIN_BUTTON = 120;

	private final BlockEntity machineBe;
	private final IConfigurableSidedMachine machine;
	private final Level level;
	private final ContainerData data;

	public SideConfigMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getMachine(playerInventory, extraData.readBlockPos()));
	}

	private SideConfigMenu(int containerId, Inventory playerInventory, BlockEntity machineBe) {
		super(ModMenuTypes.SIDE_CONFIG.get(), containerId);
		if (!(machineBe instanceof IConfigurableSidedMachine machine)) {
			throw new IllegalStateException(
					"BlockEntity at " + machineBe.getBlockPos() + " does not implement IConfigurableSidedMachine: "
							+ machineBe.getClass().getName());
		}
		this.machineBe = machineBe;
		this.machine = machine;
		this.level = playerInventory.player.level();
		this.data = machine.getSideConfigData();
		// SideConfig 是纯配置副界面（无玩家背包槽，无机器槽位），addDataSlots 同步 8 项配置。
		// quickMoveStack 不会被触发，因为没有 slot 可 shift+click。
		addDataSlots(data);
	}

	public SideConfigMenu(int containerId, Inventory playerInventory, BlockEntity machineBe, ContainerData data) {
		super(ModMenuTypes.SIDE_CONFIG.get(), containerId);
		if (!(machineBe instanceof IConfigurableSidedMachine machine)) {
			throw new IllegalStateException("Not a configurable sided machine BE");
		}
		this.machineBe = machineBe;
		this.machine = machine;
		this.level = playerInventory.player.level();
		this.data = data;
		addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id >= TOP_BUTTON && id <= BACK_BUTTON) {
			machine.cycleSideMode(getDirectionForButton(id));
			return true;
		}
		if (id == AUTO_IMPORT_BUTTON) {
			machine.toggleAutoImport();
			return true;
		}
		if (id == AUTO_EXPORT_BUTTON) {
			machine.toggleAutoExport();
			return true;
		}
		if (id == CLEAR_ALL_BUTTON) {
			machine.clearAllSides();
			return true;
		}
		if (id == BACK_TO_MAIN_BUTTON && player instanceof ServerPlayer serverPlayer) {
			// 复用 BE 的 MenuProvider 工厂，玩家直接跳转回主菜单
			serverPlayer.openMenu(machine.createMainMenuProvider(),
					buffer -> buffer.writeBlockPos(machineBe.getBlockPos()));
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	/** 获取某个按钮 id 对应面的当前 SideMode（客户端 Screen 用此实时同步 SideFaceButton 颜色）。 */
	public SideMode getMode(int buttonId) {
		Direction direction = getDirectionForButton(buttonId);
		return SideMode.values()[data.get(getDataIndex(direction))];
	}

	public ItemStack getAdjacentBlockItem(int buttonId) {
		BlockPos adjacentPos = machineBe.getBlockPos().relative(getDirectionForButton(buttonId));
		if (!level.isLoaded(adjacentPos)) {
			return ItemStack.EMPTY;
		}
		var state = level.getBlockState(adjacentPos);
		if (state.isAir()) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(state.getBlock().asItem());
	}

	public boolean isAutoImportEnabled() {
		return data.get(6) != 0;
	}

	public boolean isAutoExportEnabled() {
		return data.get(7) != 0;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(ContainerLevelAccess.create(level, machineBe.getBlockPos()), player, machine.getMachineBlock());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		// SideConfig 没有任何 slot，shift+click 不触发该方法。仅为接口一致性返回 EMPTY。
		return ItemStack.EMPTY;
	}

	private Direction getDirectionForButton(int buttonId) {
		Direction facing = machine.getMachineFacing();
		return switch (buttonId) {
			case TOP_BUTTON -> Direction.UP;
			case BOTTOM_BUTTON -> Direction.DOWN;
			case LEFT_BUTTON -> facing.getCounterClockWise();
			case RIGHT_BUTTON -> facing.getClockWise();
			case FRONT_BUTTON -> facing;
			case BACK_BUTTON -> facing.getOpposite();
			default -> facing;
		};
	}

	private static int getDataIndex(Direction direction) {
		return switch (direction) {
			case UP -> 0;
			case DOWN -> 1;
			case NORTH -> 2;
			case SOUTH -> 3;
			case WEST -> 4;
			case EAST -> 5;
		};
	}

	private static BlockEntity getMachine(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof IConfigurableSidedMachine) {
			return blockEntity;
		}
		throw new IllegalStateException("Expected IConfigurableSidedMachine BlockEntity at " + blockPos
				+ ", got " + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
	}

	/**
	 * 服务端打开 SideConfig 的辅助方法 — BE 主菜单的 SIDE_CONFIG 按钮直接调用即可。
	 *
	 * @param player  发起请求的玩家（必须是 ServerPlayer）
	 * @param machine 机器 BE（必须 implements {@link IConfigurableSidedMachine}）
	 */
	public static void openFor(ServerPlayer player, IConfigurableSidedMachine machine) {
		if (!(machine instanceof BlockEntity be)) {
			return;
		}
		player.openMenu(
				new net.minecraft.world.SimpleMenuProvider(
						(containerId, inv, ignored) -> new SideConfigMenu(containerId, inv, be, machine.getSideConfigData()),
						net.minecraft.network.chat.Component.translatable("screen.virtual_craft.side_config.title")),
				buffer -> buffer.writeBlockPos(be.getBlockPos()));
	}
}
