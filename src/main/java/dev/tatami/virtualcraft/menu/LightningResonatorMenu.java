package dev.tatami.virtualcraft.menu;

import dev.tatami.virtualcraft.blockentity.LightningResonatorBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
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

public final class LightningResonatorMenu extends AbstractContainerMenu {
	public static final int ACTIVATE_OVERDRIVE = 1;
	private final LightningResonatorBlockEntity blockEntity;
	private final Level level;
	private final ContainerData data;

	public LightningResonatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
		this(containerId, inventory, resolve(inventory, buffer.readBlockPos()), new SimpleContainerData(8));
	}

	public LightningResonatorMenu(int containerId, Inventory inventory,
			LightningResonatorBlockEntity blockEntity, ContainerData data) {
		super(ModMenuTypes.LIGHTNING_RESONATOR.get(), containerId);
		this.blockEntity = blockEntity;
		this.level = inventory.player.level();
		this.data = data;
		addPlayerSlots(inventory);
		addDataSlots(data);
	}

	public int energy() {
		return data.get(0);
	}

	public int maxEnergy() {
		return data.get(1);
	}

	public int resonance() {
		return data.get(2);
	}

	public int maxResonance() {
		return data.get(3);
	}

	public int overdriveTicks() {
		return data.get(4);
	}

	public int cooldownTicks() {
		return data.get(5);
	}

	public boolean hasPool() {
		return data.get(6) != 0;
	}

	public int efficiency() {
		return data.get(7);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		return id == ACTIVATE_OVERDRIVE && player instanceof ServerPlayer && blockEntity.tryActivateOverdrive();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot source = slots.get(index);
		if (!source.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = source.getItem();
		ItemStack copy = stack.copy();
		if (index < 27 ? !moveItemStackTo(stack, 27, 36, false)
				: !moveItemStackTo(stack, 0, 27, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			source.set(ItemStack.EMPTY);
		} else {
			source.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(level, blockEntity.getBlockPos()),
				player, ModBlocks.LIGHTNING_RESONATOR.get());
	}

	private static LightningResonatorBlockEntity resolve(Inventory inventory, BlockPos pos) {
		BlockEntity blockEntity = inventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof LightningResonatorBlockEntity resonator) {
			return resonator;
		}
		throw new IllegalStateException("Expected lightning resonator at " + pos);
	}

	private void addPlayerSlots(Inventory inventory) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 96 + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, 8 + column * 18, 154));
		}
	}
}
