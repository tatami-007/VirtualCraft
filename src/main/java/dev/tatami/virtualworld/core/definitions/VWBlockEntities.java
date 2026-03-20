package dev.tatami.virtualworld.core.definitions;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.tatami.virtualworld.VirtualWorld;
import dev.tatami.virtualworld.blockentity.ElectricCondenserBlockEntity;

public final class VWBlockEntities {
	private VWBlockEntities() {
	}

	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VirtualWorld.MODID);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricCondenserBlockEntity>> ELECTRIC_CONDENSER = REGISTRY.register("electric_condenser",
			() -> BlockEntityType.Builder.of(ElectricCondenserBlockEntity::new, VWBlocks.ELECTRIC_CONDENSER.get()).build(null));
}

