package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import dev.tatami.virtualcraft.item.PowerCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

@SuppressWarnings("this-escape") // addSlot() in the constructor is the required AbstractContainerMenu pattern.
public class ElectricCondenserMenu extends AbstractContainerMenu {
	private static final int MACHINE_SLOTS = 7;
	private static final int PLAYER_INV_START = MACHINE_SLOTS;
	private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
	private static final int HOTBAR_START = PLAYER_INV_END;
	private static final int HOTBAR_END = HOTBAR_START + 9;
	private static final int OPEN_SIDE_CONFIG_BUTTON = 120;
	// ============================================================
	// v20.2 槽位坐标：以画面中轴 X=88 严格对称排列上半区 widget
	// imageWidth=176, imageHeight=178
	// ============================================================
	// v20.1 旧布局：全部 X +13对齐背包中心，但能量条/UPGRADE 偏内、INPUT 双格贴在一起
	// v20.2 调整：能量条与 UPGRADE 各自往最外偏（离中轴 72）、INPUT 双格加 gap=4 分开、严格左右对称
	//   ENERGY     (8, 25)   16×52 中点 (16, 51)  — 离中轴 72
	//   INGOT_SLOT (47, 31)  18×18 中点 (56, 40)
	//   BALL_SLOT  (47, 53)  18×18 中点 (56, 62)  — 双格中线 (56, 51)，gap=4 分开
	//   ARROW      (77, 43)  22×16 中点 (88, 51)  — 正中
	//   OUTPUT     (111, 42) 18×18 中点 (120, 51) — 离中轴 32
	//   UPGRADE    (152, 24/42/60) 1×3 组中点 (160, 51) — 离中轴 72
	//   玩家背包 (8, 96)、Hotbar (8, 154)、齿轮按钮 (152, 4)
	// v20.13 widget 整体上移 2px 让 ENERGY 上下边距接近对称（与 GuiTextureGenerator v20.13 同步）
	private static final int INGOT_SLOT_X = 47;
	private static final int INGOT_SLOT_Y = 29;          // v20.13 31→29
	private static final int BALL_SLOT_X = 47;
	private static final int BALL_SLOT_Y = 51;          // v20.13 53→51
	private static final int OUTPUT_SLOT_X = 111;
	private static final int OUTPUT_SLOT_Y = 40;       // v20.13 42→40
	// UPGRADE 3 槽垂直堆叠 @ (152, 24/42/60)，组中点 (160, 51)
	// 与齿轮按钮 (152, 4) 18×18：X 完全重叠、Y 不重（齿轮 4-22 vs slot 24-78）
	// UPGRADE 顶 24 > 标题区底 22 （1px 间距）、底 78 < 玩家背包标签 84 （6px 间距）
	private static final int UPGRADE_ONE_X = 152;
	private static final int UPGRADE_TWO_X = 152;
	private static final int UPGRADE_THREE_X = 152;
	private static final int UPGRADE_ONE_Y = 28;
	private static final int UPGRADE_TWO_Y = 46;
	private static final int UPGRADE_THREE_Y = 64;
	private static final int PLAYER_INVENTORY_X = 8;
	private static final int PLAYER_INVENTORY_Y = 96;
	private static final int HOTBAR_X = 8;
	private static final int HOTBAR_Y = 154;

	private final ElectricCondenserBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(5));
	}

	public ElectricCondenserMenu(int containerId, Inventory playerInventory, ElectricCondenserBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.ELECTRIC_CONDENSER.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;

		ItemStackHandler inventory = blockEntity.getInventory();
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.INGOT_SLOT, INGOT_SLOT_X, INGOT_SLOT_Y));
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.BALL_SLOT, BALL_SLOT_X, BALL_SLOT_Y));
		addSlot(new SlotItemHandler(inventory, ElectricCondenserBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addUpgradeSlot(inventory, 3, UPGRADE_ONE_X, UPGRADE_ONE_Y);
		addUpgradeSlot(inventory, 4, UPGRADE_TWO_X, UPGRADE_TWO_Y);
		addUpgradeSlot(inventory, 5, UPGRADE_THREE_X, UPGRADE_THREE_Y);
		addPowerCellSlot(inventory, ElectricCondenserBlockEntity.POWER_CELL_SLOT, 8, 76);

		addPlayerInventory(playerInventory);
		addPlayerHotbar(playerInventory);
		addDataSlots(data);
	}

	/** 进度返回 [0..22]：v16 贴图进度箭头宽 22 像素（AE2-CS crystal_pulverizer 标准）。 */
	public int getScaledProgress() {
		int progress = data.get(0);
		int maxProgress = data.get(1);
		return progress != 0 && maxProgress != 0 ? progress * 22 / maxProgress : 0;
	}

	public int getEnergyStored() {
		return data.get(2);
	}

	public int getMaxEnergyStored() {
		return data.get(3);
	}

	public int getInstalledSpeedCards() {
		return data.get(4);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id == OPEN_SIDE_CONFIG_BUTTON && player instanceof ServerPlayer serverPlayer) {
			// v15: 改用通用 SideConfigMenu — 由 BE (implements IConfigurableSidedMachine) 直接驱动，
			// 不再依赖 ElectricCondenser 专属 SideConfigMenu。
			SideConfigMenu.openFor(serverPlayer, blockEntity);
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot sourceSlot = slots.get(index);
		if (!sourceSlot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack sourceStack = sourceSlot.getItem();
		ItemStack itemStack = sourceStack.copy();

		if (index == ElectricCondenserBlockEntity.OUTPUT_SLOT) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
			sourceSlot.onQuickCraft(sourceStack, itemStack);
		} else if (index < MACHINE_SLOTS) {
			if (!moveItemStackTo(sourceStack, PLAYER_INV_START, HOTBAR_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(ModItems.SPEED_UPGRADE_CARD.get())) {
			if (!moveItemStackTo(sourceStack, 3, 6, false)) {
				return ItemStack.EMPTY;
			}
		} else if (PowerCellItem.isPowerCell(sourceStack)) {
			if (!moveItemStackTo(sourceStack, ElectricCondenserBlockEntity.POWER_CELL_SLOT, ElectricCondenserBlockEntity.POWER_CELL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (sourceStack.is(ModItems.ELECTRIC_BALL.get())) {
			if (!moveItemStackTo(sourceStack, ElectricCondenserBlockEntity.BALL_SLOT, ElectricCondenserBlockEntity.BALL_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (ElectricCondenserBlockEntity.isValidIngot(sourceStack)) {
			if (!moveItemStackTo(sourceStack, ElectricCondenserBlockEntity.INGOT_SLOT, ElectricCondenserBlockEntity.INGOT_SLOT + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (index < PLAYER_INV_END) {
			if (!moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(sourceStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
			return ItemStack.EMPTY;
		}

		if (sourceStack.isEmpty()) {
			sourceSlot.set(ItemStack.EMPTY);
		} else {
			sourceSlot.setChanged();
		}

		sourceSlot.onTake(player, sourceStack);
		return itemStack;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.ELECTRIC_CONDENSER.get());
	}

	private static ElectricCondenserBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof ElectricCondenserBlockEntity condenser) {
			return condenser;
		}
		throw new IllegalStateException("Expected electric condenser block entity at " + blockPos);
	}

	private void addPlayerInventory(Inventory playerInventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
			}
		}
	}

	private void addPlayerHotbar(Inventory playerInventory) {
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(playerInventory, column, HOTBAR_X + column * 18, HOTBAR_Y));
		}
	}

	private void addUpgradeSlot(ItemStackHandler inventory, int slot, int x, int y) {
		addSlot(new SlotItemHandler(inventory, slot, x, y) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
	}

	private void addPowerCellSlot(ItemStackHandler inventory, int slot, int x, int y) {
		addSlot(new SlotItemHandler(inventory, slot, x, y) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
	}
}
