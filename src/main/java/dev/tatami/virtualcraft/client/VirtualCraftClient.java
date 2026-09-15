package dev.tatami.virtualcraft.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import dev.tatami.virtualcraft.client.screen.ElectricBallGeneratorScreen;
import dev.tatami.virtualcraft.client.screen.ElectricCondenserScreen;
import dev.tatami.virtualcraft.client.screen.SideConfigScreen;
import dev.tatami.virtualcraft.client.binding.BoundMachineHighlighter;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicShopClientCache;
import dev.tatami.virtualcraft.client.screen.PowMinerScreen;
import dev.tatami.virtualcraft.client.screen.ComputeRackScreen;
import dev.tatami.virtualcraft.client.screen.LightningResonatorScreen;
import dev.tatami.virtualcraft.client.screen.QuantumSimulationChamberScreen;
import dev.tatami.virtualcraft.client.screen.holographic.HolographicIconCache;
import dev.tatami.virtualcraft.registry.ModMenuTypes;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.renderer.BiomeColors;
import dev.tatami.virtualcraft.registry.ModBlocks;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.registry.ModEntityTypes;
import dev.tatami.virtualcraft.client.render.VirtualSnakeKingRenderer;
import dev.tatami.virtualcraft.client.render.VirtualMushroomKingRenderer;
import dev.tatami.virtualcraft.client.render.MushroomAttackTelegraphRenderer;
import dev.tatami.virtualcraft.client.render.MushroomAttackEffectRenderer;
import dev.tatami.virtualcraft.client.render.MushroomRootWaveRenderer;
import dev.tatami.virtualcraft.client.render.MushroomSporeMassRenderer;
import dev.tatami.virtualcraft.client.render.SnakeFangRenderer;
import dev.tatami.virtualcraft.client.render.SnakeSpellNodeRenderer;
import dev.tatami.virtualcraft.client.particle.SnakeParticle;
import dev.tatami.virtualcraft.registry.ModParticleTypes;

public final class VirtualCraftClient {
	private static final int VIRTUAL_GRASS_COLOR = 0x82BFFF;
	private static boolean stoneWorldRenderersRegistered;

	private VirtualCraftClient() {
	}

	public static void prioritizeSmithingRecipes(RecipesUpdatedEvent event) {
		List<RecipeHolder<?>> ordered = new ArrayList<>(event.getRecipeManager().getOrderedRecipes());
		List<RecipeHolder<?>> smithing = ordered.stream()
				.filter(recipe -> recipe.value().getType() == RecipeType.SMITHING)
				.sorted(Comparator.comparingInt(VirtualCraftClient::smithingPriority))
				.toList();
		Iterator<RecipeHolder<?>> replacements = smithing.iterator();
		for (int index = 0; index < ordered.size(); index++) {
			if (ordered.get(index).value().getType() == RecipeType.SMITHING) {
				ordered.set(index, replacements.next());
			}
		}
		event.getRecipeManager().replaceRecipes(ordered);
	}

	private static int smithingPriority(RecipeHolder<?> recipe) {
		if ("virtual_craft".equals(recipe.id().getNamespace())
				&& recipe.id().getPath().startsWith("virtual_snake_king_")
				&& recipe.value() instanceof SmithingTransformRecipe) {
			return 0;
		}
		return recipe.value() instanceof SmithingTransformRecipe ? 1 : 2;
	}

	public static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenuTypes.ELECTRIC_CONDENSER.get(), ElectricCondenserScreen::new);
		event.register(ModMenuTypes.ELECTRIC_BALL_GENERATOR.get(), ElectricBallGeneratorScreen::new);
		// v15 通用面配置 Screen — 所有 IConfigurableSidedMachine 机器共用
		event.register(ModMenuTypes.SIDE_CONFIG.get(), SideConfigScreen::new);
		event.register(ModMenuTypes.POW_MINER.get(), PowMinerScreen::new);
		event.register(ModMenuTypes.COMPUTE_RACK.get(), ComputeRackScreen::new);
		event.register(ModMenuTypes.LIGHTNING_RESONATOR.get(), LightningResonatorScreen::new);
		event.register(ModMenuTypes.QUANTUM_SIMULATION_CHAMBER.get(), QuantumSimulationChamberScreen::new);
		// HolographicShop 重构后不走 menu 系统：客户端收到 OpenHolographicTerminalPayload 后用
		// MuiModApi.openScreen(...) 直接拉起一个 SimpleScreen（非 AbstractContainerScreen）——
		// JEI 默认不识别这种 Screen 为容器 GUI，不会绘 Ingredient Overlay。
		// WirelessPowerTower 重做后无 GUI — 玩家用 LaserBindingTool 直接绑定，无需 Screen
	}

	public static void registerShaders(RegisterShadersEvent event) {
		try {
			dev.tatami.virtualcraft.client.overlay.ShockOverlayShaders.register(event);
		} catch (Exception exception) {
			throw new RuntimeException("Failed to register electric overlay shader", exception);
		}
	}

	public static void renderShockOverlay(RenderGuiLayerEvent.Post event) {
		dev.tatami.virtualcraft.client.overlay.ShockTextOverlay.render(event);
		dev.tatami.virtualcraft.client.overlay.SnakeBossScreenEffects.render(event);
		dev.tatami.virtualcraft.client.overlay.SnakeKingBlessingArmorEffect.render(event);
	}

	public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		event.registerItem(VirtualSwordClientExtensions.INSTANCE,
				ModItems.VIRTUAL_IRON_SWORD.get(),
				ModItems.VIRTUAL_SILVER_SWORD.get(),
				ModItems.VIRTUAL_TITANIUM_SWORD.get(),
				ModItems.VIRTUAL_CHROMIUM_SWORD.get(),
				ModItems.VIRTUAL_SNAKE_KING_SWORD.get(),
				ModItems.VIRTUAL_MUSHROOM_KING_SWORD.get());
	}

	/**
	 * Schedules a staggered Holographic Terminal icon prewarm. Decoding and
	 * uploading every icon during login would make one world tick heavy, so the
	 * cache warms one small icon per client tick instead.
	 */
	public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
		// ModernUI images must be created on its UI thread. The terminal now warms
		// icons on demand when its fragment is built instead of from this game tick.
	}

	/**
	 * Drops the in-memory Holographic Terminal sync cache on disconnect so
	 * that the next session never paints stale balances or wallet addresses
	 * from a previous server. The icon byte cache is also cleared because
	 * resource packs may have changed between sessions.
	 */
	public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		HolographicShopClientCache.invalidate();
		dev.tatami.virtualcraft.client.screen.holographic.HolographicMarketData.invalidate();
		HolographicIconCache.invalidate();
		dev.tatami.virtualcraft.client.highlight.QuantumScanClientState.clear();
		dev.tatami.virtualcraft.client.overlay.MushroomBossOverlay.clear();
	}

	public static void onClientTick(ClientTickEvent.Post event) {
		LightningForestClientEffects.tick();
		SnakeKingSummonPreview.tick();
		SnakeBossCombatFeedback.tick();
		SnakeBossMusic.tick();
		MushroomBossMusic.tick();
	}

	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntityTypes.VIRTUAL_SNAKE_KING.get(), VirtualSnakeKingRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.VIRTUAL_MUSHROOM_KING.get(), VirtualMushroomKingRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SNAKE_FANG.get(), SnakeFangRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.SNAKE_SPELL_NODE.get(), SnakeSpellNodeRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MUSHROOM_SPORE_MASS.get(), MushroomSporeMassRenderer::new);
		event.registerEntityRenderer(ModEntityTypes.MUSHROOM_ROOT_WAVE.get(), MushroomRootWaveRenderer::new);
		if (!stoneWorldRenderersRegistered) {
			stoneWorldRenderersRegistered = true;
			NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent renderEvent) -> {
				if (renderEvent.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
					MushroomKingSummonPreview.render(renderEvent.getPoseStack());
					MushroomAttackTelegraphRenderer.render(renderEvent.getPoseStack());
					MushroomAttackEffectRenderer.render(renderEvent.getPoseStack());
				}
			});
		}
	}

	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ModParticleTypes.SNAKE_DATA_RUNE.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.RUNE));
		event.registerSpriteSet(ModParticleTypes.SNAKE_ARC.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.ARC));
		event.registerSpriteSet(ModParticleTypes.SNAKE_SHARD.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.SHARD));
		event.registerSpriteSet(ModParticleTypes.SNAKE_TRAIL.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.TRAIL));
		event.registerSpriteSet(ModParticleTypes.SNAKE_HIGH_WARNING.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.HIGH_WARNING));
		event.registerSpriteSet(ModParticleTypes.SNAKE_MID_WARNING.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.MID_WARNING));
		event.registerSpriteSet(ModParticleTypes.SNAKE_LOW_WARNING.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.LOW_WARNING));
		event.registerSpriteSet(ModParticleTypes.SNAKE_POISON_RED.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.POISON_RED));
		event.registerSpriteSet(ModParticleTypes.SNAKE_POISON_BLUE.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.POISON_BLUE));
		event.registerSpriteSet(ModParticleTypes.SNAKE_PARRY_FLASH.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.PARRY_FLASH));
		event.registerSpriteSet(ModParticleTypes.MUSHROOM_SPORE_GLOW.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.MUSHROOM_SPORE));
		event.registerSpriteSet(ModParticleTypes.MUSHROOM_ROOT_SHARD.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.MUSHROOM_ROOT));
		event.registerSpriteSet(ModParticleTypes.MUSHROOM_CAP_FLASH.get(), sprites ->
				SnakeParticle.provider(sprites, SnakeParticle.Style.MUSHROOM_FLASH));
	}

	public static void renderSnakeBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
		dev.tatami.virtualcraft.client.overlay.SnakeBossOverlay.render(event);
		if (!event.isCanceled()) {
			dev.tatami.virtualcraft.client.overlay.MushroomBossOverlay.render(event);
		}
	}

	public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
		event.register((state, level, pos, tintIndex) -> {
			if (tintIndex != 0) {
				return 0xFFFFFF;
			}
			return level != null && pos != null
					? BiomeColors.getAverageGrassColor(level, pos)
					: VIRTUAL_GRASS_COLOR;
		}, ModBlocks.VIRTUAL_GRASS_BLOCK.get());
	}

	public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		event.register((stack, tintIndex) -> tintIndex == 0 ? VIRTUAL_GRASS_COLOR : 0xFFFFFF,
				ModItems.VIRTUAL_GRASS_BLOCK.get());
	}
}


