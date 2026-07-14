package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.registry.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ThunderPoolClient {
	private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
	private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

	private ThunderPoolClient() {
	}

	public static void registerFluidExtensions(RegisterClientExtensionsEvent event) {
		event.registerFluidType(new IClientFluidTypeExtensions() {
			@Override
			public ResourceLocation getStillTexture() {
				return STILL;
			}

			@Override
			public ResourceLocation getFlowingTexture() {
				return FLOW;
			}

			@Override
			public int getTintColor() {
				return 0xFF8A46FF;
			}
		}, ModFluids.THUNDER_POOL_TYPE.get());
	}
}
