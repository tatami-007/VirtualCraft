package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
	private ModFluids() {
	}

	public static final DeferredRegister<FluidType> TYPES =
			DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, VirtualCraft.MODID);
	public static final DeferredRegister<Fluid> REGISTRY =
			DeferredRegister.create(Registries.FLUID, VirtualCraft.MODID);

	public static final DeferredHolder<FluidType, FluidType> THUNDER_POOL_TYPE = TYPES.register("thunder_pool",
			() -> new FluidType(FluidType.Properties.create()
					.descriptionId("block.virtual_craft.thunder_pool")
					.density(1200)
					.viscosity(1400)
					.lightLevel(8)
					.canDrown(false)
					.canSwim(false)
					.canPushEntity(false)
					.motionScale(0.0)));

	public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> THUNDER_POOL = REGISTRY.register("thunder_pool",
			() -> new BaseFlowingFluid.Source(properties()));
	public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_THUNDER_POOL = REGISTRY.register("flowing_thunder_pool",
			() -> new BaseFlowingFluid.Flowing(properties()));

	private static BaseFlowingFluid.Properties properties() {
		return new BaseFlowingFluid.Properties(THUNDER_POOL_TYPE, THUNDER_POOL, FLOWING_THUNDER_POOL)
				.block(ModBlocks.THUNDER_POOL)
				.bucket(ModItems.THUNDER_POOL_BUCKET)
				.tickRate(8)
				.slopeFindDistance(4)
				.levelDecreasePerBlock(1)
				.explosionResistance(100.0F);
	}
}
