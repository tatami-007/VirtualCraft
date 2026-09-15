package dev.tatami.virtualcraft.machine.advanced;

import java.util.function.Supplier;

import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public enum QuantumComponentTarget {
	POWER_SUPPLY("power_supply", ModItems.POWER_SUPPLY),
	CPU("cpu", ModItems.CPU),
	GPU("gpu", ModItems.GPU),
	MEMORY("memory_stick", ModItems.MEMORY_STICK);

	private final String id;
	private final Supplier<? extends Item> item;

	QuantumComponentTarget(String id, Supplier<? extends Item> item) {
		this.id = id;
		this.item = item;
	}

	public String id() {
		return id;
	}

	public ItemStack icon() {
		return new ItemStack(item.get());
	}

	public Component displayName() {
		return icon().getHoverName();
	}

	public static QuantumComponentTarget byIndex(int index) {
		QuantumComponentTarget[] values = values();
		return values[Math.floorMod(index, values.length)];
	}

	public static QuantumComponentTarget byId(String id) {
		for (QuantumComponentTarget target : values()) {
			if (target.id.equals(id)) {
				return target;
			}
		}
		return POWER_SUPPLY;
	}
}
