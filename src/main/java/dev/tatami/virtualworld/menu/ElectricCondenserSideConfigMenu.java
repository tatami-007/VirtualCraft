package dev.tatami.virtualworld.menu;

import dev.tatami.virtualworld.block.ElectricCondenserBlock;
import dev.tatami.virtualworld.blockentity.ElectricCondenserBlockEntity;
import dev.tatami.virtualworld.core.definitions.VWBlocks;
import dev.tatami.virtualworld.core.definitions.VWMenuTypes;
import dev.tatami.virtualworld.machine.CondenserSideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ElectricCondenserSideConfigMenu extends AbstractContainerMenu {
	private static final int TOP_BUTTON = 0;
	private static final int BOTTOM_BUTTON = 1;
	private static final int LEFT_BUTTON = 2;
	private static final int RIGHT_BUTTON = 3;
	private static final int FRONT_BUTTON = 4;
	private static final int BACK_BUTTON = 5;
	private static final int AUTO_IMPORT_BUTTON = 100;
	private static final int AUTO_EXPORT_BUTTON = 101;
	private static final int BACK_TO_MAIN_BUTTON = 120;

	private final ElectricCondenserBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public ElectricCondenserSideConfigMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
		this(containerId, playerInventory, getBlockEntity(playerInventory, extraData.readBlockPos()), new SimpleContainerData(8));
	}

	public ElectricCondenserSideConfigMenu(int containerId, Inventory playerInventory, ElectricCondenserBlockEntity blockEntity, ContainerData data) {
		super(VWMenuTypes.ELECTRIC_CONDENSER_SIDE_CONFIG.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = playerInventory.player.level();
		this.data = data;
		addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id >= TOP_BUTTON && id <= BACK_BUTTON) {
			blockEntity.cycleSideMode(getDirectionForButton(id));
			return true;
		}
		if (id == AUTO_IMPORT_BUTTON) {
			blockEntity.toggleAutoImport();
			return true;
		}
		if (id == AUTO_EXPORT_BUTTON) {
			blockEntity.toggleAutoExport();
			return true;
		}
		if (id == BACK_TO_MAIN_BUTTON && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(
					new SimpleMenuProvider(
							(containerId, inventory, ignored) -> new ElectricCondenserMenu(containerId, inventory, blockEntity, blockEntity.getData()),
							net.minecraft.network.chat.Component.translatable("block.virtual_world.electric_condenser")),
					buffer -> buffer.writeBlockPos(blockEntity.getBlockPos()));
			return true;
		}
		return super.clickMenuButton(player, id);
	}

	public String getModeLabel(int buttonId) {
		return getModeForButton(buttonId).shortLabel();
	}

	public boolean isAutoImportEnabled() {
		return data.get(6) != 0;
	}

	public boolean isAutoExportEnabled() {
		return data.get(7) != 0;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, VWBlocks.ELECTRIC_CONDENSER.get());
	}

	@Override
	public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	private CondenserSideMode getModeForButton(int buttonId) {
		Direction direction = getDirectionForButton(buttonId);
		return CondenserSideMode.values()[data.get(getDataIndex(direction))];
	}

	private Direction getDirectionForButton(int buttonId) {
		Direction facing = blockEntity.getBlockState().getValue(ElectricCondenserBlock.FACING);
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

	private int getDataIndex(Direction direction) {
		return switch (direction) {
			case UP -> 0;
			case DOWN -> 1;
			case NORTH -> 2;
			case SOUTH -> 3;
			case WEST -> 4;
			case EAST -> 5;
		};
	}

	private static ElectricCondenserBlockEntity getBlockEntity(Inventory playerInventory, BlockPos blockPos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(blockPos);
		if (blockEntity instanceof ElectricCondenserBlockEntity condenser) {
			return condenser;
		}
		throw new IllegalStateException("Expected voltaic forge block entity at " + blockPos);
	}
}

