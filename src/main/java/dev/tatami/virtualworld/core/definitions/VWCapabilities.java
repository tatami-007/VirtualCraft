package dev.tatami.virtualworld.core.definitions;

import dev.tatami.virtualworld.blockentity.ElectricCondenserBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class VWCapabilities {
	private VWCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
				Capabilities.ItemHandler.BLOCK,
				VWBlockEntities.ELECTRIC_CONDENSER.get(),
				(ElectricCondenserBlockEntity blockEntity, net.minecraft.core.Direction side) -> blockEntity.getItemHandler(side));
	}
}

